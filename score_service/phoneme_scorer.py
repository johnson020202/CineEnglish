"""Phoneme-level pronunciation assessment backed by a Wav2Vec2 CTC phone recognizer.

The recognizer is ``facebook/wav2vec2-lv-60-espeak-cv-ft``, a model fine-tuned to emit
espeak IPA phones directly from audio. Scoring happens inside words, so a learner who
says "think" as /sɪŋk/ is flagged at the phone level instead of being silently
"corrected" by a word-level transcriber.

The comparison algorithm (expected phones, near-phone tolerance, function-word
alternates, per-phone confidence) is adapted from OpenPronounce by Jean-François
Lépine (MIT License, https://github.com/Halleck45/OpenPronounce). Inference runs on
onnxruntime rather than torch so the service fits in a small CPU-only VM.
"""

import json
import logging
import math
import os
import re
import struct
import wave
from functools import lru_cache
from typing import Dict, List, Optional, Tuple

import Levenshtein
import numpy as np

logger = logging.getLogger(__name__)

SAMPLING_RATE = 16000

MODEL_DIR = os.environ.get("PHONEME_MODEL_DIR", "/opt/cine_score")
ONNX_PATH = os.environ.get("PHONEME_ONNX_PATH", os.path.join(MODEL_DIR, "phone_int8_mm.onnx"))
VOCAB_PATH = os.environ.get("PHONEME_VOCAB_PATH", os.path.join(MODEL_DIR, "vocab.json"))

# Weights for the overall score. Accuracy dominates because it is the only term that
# reflects what the learner actually pronounced.
SCORE_WEIGHTS = {"accuracy": 0.60, "completeness": 0.20, "fluency": 0.10, "prosody": 0.10}

# A word is reported when the summed confidence of its wrong phones reaches this share
# of its phones, or this many phones outright.
PHONE_ERROR_THRESHOLD = 0.4
PHONE_ERROR_MIN_EDITS = 2
# Hearing a close phone (voicing pair, tense/lax vowel) is a smaller error than a
# completely different one; raters tolerate those and so does the recognizer.
NEAR_PHONE_COST = 0.5
FINAL_EXTRA_COST = 0.25
FINAL_DELETION_COST = 0.5
# An expected phone whose posterior reaches this value where another phone was heard
# is not a confident error.
PHONE_PLAUSIBLE_POSTERIOR = 0.05

_TOKEN_ALIASES = {"th": "t", "kh": "k", "ph": "p", "tʰ": "t", "kʰ": "k", "pʰ": "p",
                  "ai": "aɪ", "ei": "eɪ", "au": "aʊ", "ou": "oʊ"}
_TONE_RE = re.compile(r"\d")

_PHONE_MAP = {
    "en": {
        "ᵻ": "ɪ",
        "ɐ": "ə",
        "ɔ": "ɑ",
        "ɔɹ": "oɹ",
        "ɜ": "ɚ",
        "ɾ": "t",
        "ɫ": "l",
    },
}

_NEAR_PHONES = {
    "en": [
        {"ɪ", "i"}, {"ʊ", "u"}, {"ɛ", "æ"}, {"ʌ", "ɑ", "ə", "a"}, {"æ", "a"}, {"ɛ", "eɪ"},
        {"ð", "z", "d"}, {"θ", "s", "t"}, {"b", "p"}, {"d", "t"}, {"ɡ", "k"}, {"z", "s"},
        {"v", "f"}, {"ʒ", "ʃ"}, {"dʒ", "tʃ"}, {"n", "ŋ"}, {"h", "x"}, {"ɹ", "r"}, {"v", "w"},
    ],
}

ALTERNATE_PRONUNCIATIONS = {
    "en": {
        "a": [["ə"], ["eɪ"]],
        "an": [["ən"], ["æn"]],
        "the": [["ð", "ə"], ["ð", "i"], ["ð", "ɪ"]],
        "to": [["t", "ə"], ["t", "u"]],
        "of": [["ʌ", "v"], ["ə", "v"]],
        "and": [["æ", "n", "d"], ["ə", "n", "d"], ["ə", "n"]],
        "for": [["f", "ɔ", "ɹ"], ["f", "ɚ"]],
        "you": [["j", "u"], ["j", "ə"]],
        "are": [["ɑ", "ɹ"], ["ɚ"]],
        "was": [["w", "ʌ", "z"], ["w", "ə", "z"]],
        "that": [["ð", "æ", "t"], ["ð", "ə", "t"]],
        "can": [["k", "æ", "n"], ["k", "ə", "n"]],
        "have": [["h", "æ", "v"], ["h", "ə", "v"]],
        "or": [["ɔ", "ɹ"], ["ɚ"]],
    },
}

_WORD_RE = re.compile(r"\b[\w']+\b")


class ScorerUnavailable(RuntimeError):
    """Raised when the phone model or the espeak backend is missing."""


# ---------------------------------------------------------------------------
# Audio loading
# ---------------------------------------------------------------------------

def load_wav_16k(path: str) -> np.ndarray:
    """Read a 16 kHz mono PCM wav as float32 in [-1, 1].

    The backend converts every recording with ffmpeg before calling the scorer, so a
    plain ``wave`` read is enough here and avoids pulling librosa into the server.
    """
    with wave.open(path, "rb") as f:
        channels, width, rate = f.getnchannels(), f.getsampwidth(), f.getframerate()
        raw = f.readframes(f.getnframes())
    if width != 2:
        raise ScorerUnavailable(f"expected 16-bit PCM, got {width * 8}-bit")
    samples = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    if channels > 1:
        samples = samples.reshape(-1, channels).mean(axis=1)
    if rate != SAMPLING_RATE:
        # Linear resampling is adequate here: the model is robust to it and the backend
        # already asks ffmpeg for 16 kHz, so this is only a safety net.
        idx = np.linspace(0, len(samples) - 1, int(len(samples) * SAMPLING_RATE / rate))
        samples = np.interp(idx, np.arange(len(samples)), samples).astype(np.float32)
    return samples


# ---------------------------------------------------------------------------
# Model
# ---------------------------------------------------------------------------

@lru_cache(maxsize=1)
def _session():
    import onnxruntime as ort

    if not os.path.exists(ONNX_PATH):
        raise ScorerUnavailable(f"phone model not found at {ONNX_PATH}")
    opts = ort.SessionOptions()
    opts.intra_op_num_threads = int(os.environ.get("PHONEME_THREADS", "2"))
    opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    # The default arena keeps a growing pool of freed buffers, which pushed RSS past
    # 630 MB on a 1.7 GB host and got the service OOM-killed. The model is loaded once
    # and inference is single-shot per request, so the arena buys nothing here.
    opts.enable_cpu_mem_arena = False
    return ort.InferenceSession(ONNX_PATH, opts, providers=["CPUExecutionProvider"])


@lru_cache(maxsize=1)
def _vocab() -> Tuple[str, ...]:
    """Vocabulary as a tuple indexed by token id."""
    with open(VOCAB_PATH, encoding="utf-8") as f:
        vocab = json.load(f)
    return tuple(token for token, _ in sorted(vocab.items(), key=lambda kv: kv[1]))


def is_available() -> bool:
    """Cheap check used on the request path; loading the model happens off the event loop."""
    return os.path.exists(ONNX_PATH) and os.path.exists(VOCAB_PATH)


def warmup() -> bool:
    """Load the session once so the first real request does not pay the model load."""
    try:
        _session()
        _vocab()
        logger.info("phoneme scorer ready: %s", ONNX_PATH)
        return True
    except Exception as e:  # noqa: BLE001 - availability probe
        logger.warning("phoneme scorer unavailable: %s", e)
        return False


def _normalize_waveform(waveform: np.ndarray) -> np.ndarray:
    """Match Wav2Vec2FeatureExtractor(do_normalize=True)."""
    return (waveform - waveform.mean()) / math.sqrt(float(waveform.var()) + 1e-7)


def _log_posteriors(waveform: np.ndarray) -> np.ndarray:
    """Frame-level log posteriors, shape (frames, vocab)."""
    inputs = _normalize_waveform(waveform).astype(np.float32)[None, :]
    logits = _session().run(["logits"], {"input_values": inputs})[0][0]
    logits = logits.astype(np.float64)
    logits -= logits.max(axis=1, keepdims=True)
    return logits - np.log(np.exp(logits).sum(axis=1, keepdims=True))


# ---------------------------------------------------------------------------
# Phone normalization and CTC decoding
# ---------------------------------------------------------------------------

def normalize_phone(phone: str, lang: str = "en") -> str:
    phone = _TONE_RE.sub("", phone.replace("ː", ""))
    phone = _TOKEN_ALIASES.get(phone, phone)
    return _PHONE_MAP.get(lang, {}).get(phone, phone)


def normalize_phones(phones: List[str], lang: str = "en") -> List[str]:
    return [p for p, _ in _normalize_indexed(phones, lang)]


def _normalize_indexed(phones: List[str], lang: str):
    out: List[Tuple[str, List[int]]] = []
    for index, phone in enumerate(phones):
        phone = normalize_phone(phone, lang)
        if not phone:
            continue
        if out and (out[-1][0] == phone or (out[-1][0] == "ɚ" and phone == "ɹ")):
            out[-1][1].append(index)
            continue
        out.append((phone, [index]))
    return out


def _is_special(token: str) -> bool:
    return token.startswith("<") and token.endswith(">")


def decode_ctc(log_posteriors: np.ndarray, vocab: Tuple[str, ...], blank_id: int = 0,
               lang: str = "en") -> Dict:
    """Greedy CTC decoding into normalized phones with confidences and frame spans."""
    ids = log_posteriors.argmax(axis=1)
    tokens, confidences, spans = [], [], []
    start = 0
    for end in range(1, len(ids) + 1):
        if end < len(ids) and ids[end] == ids[start]:
            continue
        token = vocab[ids[start]]
        if ids[start] != blank_id and not _is_special(token):
            tokens.append(token)
            confidences.append(float(np.exp(log_posteriors[start:end, ids[start]].max())))
            spans.append((start, end))
        start = end

    phones, merged_confidences, merged_spans = [], [], []
    for phone, sources in _normalize_indexed(tokens, lang):
        phones.append(phone)
        merged_confidences.append(max(confidences[i] for i in sources))
        merged_spans.append((spans[sources[0]][0], spans[sources[-1]][1]))
    return {
        "phones": phones,
        "confidences": merged_confidences,
        "spans": merged_spans,
        "log_posteriors": log_posteriors,
        "vocab": vocab,
    }


# ---------------------------------------------------------------------------
# Reference phones (espeak)
# ---------------------------------------------------------------------------

@lru_cache(maxsize=2048)
def _expected_phones(text: str, lang: str = "en") -> Tuple[Tuple[str, ...], Tuple[Tuple[str, ...], ...]]:
    """Return (words, phones per word) for ``text``, phonemized with espeak."""
    from phonemizer import phonemize
    from phonemizer.separator import Separator

    words = tuple(w.lower() for w in _WORD_RE.findall(text))
    if not words:
        return (), ()

    sep = Separator(phone=" ", word=" | ", syllable="")
    try:
        out = phonemize(" ".join(words), language="en-us", backend="espeak", strip=True,
                        preserve_punctuation=False, separator=sep)
        groups = [g.split() for g in out.split("|")]
        if len(groups) != len(words):
            raise ValueError(f"expected {len(words)} groups, got {len(groups)}")
    except Exception as e:  # noqa: BLE001 - fall back to one espeak call per word
        logger.debug("batch phonemization failed (%s), phonemizing per word", e)
        groups = []
        for word in words:
            try:
                out = phonemize(word, language="en-us", backend="espeak", strip=True,
                                preserve_punctuation=False,
                                separator=Separator(phone=" ", word="", syllable=""))
                groups.append(out.split())
            except Exception:  # noqa: BLE001
                groups.append([])
    return words, tuple(tuple(normalize_phones(g, lang)) for g in groups)


# ---------------------------------------------------------------------------
# Comparison
# ---------------------------------------------------------------------------

def _align(expected: List[str], heard: List[str]) -> List[set]:
    alignment: List[set] = [set() for _ in expected]
    for tag, i1, i2, j1, j2 in Levenshtein.opcodes(list(expected), list(heard)):
        if tag == "equal":
            for k, l in zip(range(i1, i2), range(j1, j2)):
                alignment[k].add(l)
        elif tag == "replace":
            len_i, len_j = i2 - i1, j2 - j1
            for k in range(i1, i2):
                start = j1 + int((k - i1) * len_j / len_i)
                end = j1 + int((k - i1 + 1) * len_j / len_i)
                if start == end:
                    alignment[k].add(min(start, j2 - 1))
                else:
                    alignment[k].update(range(start, end))
        elif tag == "insert":
            k = i1 - 1 if i1 > 0 else i1
            if k < len(alignment):
                alignment[k].update(range(j1, j2))
    return alignment


def _substitution_cost(expected: str, heard: str, lang: str) -> float:
    for group in _NEAR_PHONES.get(lang, []):
        if expected in group and heard in group:
            return NEAR_PHONE_COST
    return 1.0


@lru_cache(maxsize=8)
def _token_ids_by_phone(vocab: Tuple[str, ...], lang: str) -> Dict[str, List[int]]:
    ids: Dict[str, List[int]] = {}
    for index, token in enumerate(vocab):
        if not _is_special(token):
            ids.setdefault(normalize_phone(token, lang), []).append(index)
    return ids


def _plausibility(recognition: Dict, phone: str, start: int, end: int, lang: str) -> float:
    if recognition is None or end <= start:
        return 0.0
    ids = _token_ids_by_phone(recognition["vocab"], lang).get(phone)
    if not ids:
        return 0.0
    return float(np.exp(recognition["log_posteriors"][start:end][:, ids].max()))


def _pronunciations(word: str, expected_seg: List[str], previous_last: Optional[str], lang: str):
    alternates = ALTERNATE_PRONUNCIATIONS.get(lang, {}).get(word, [])
    candidates = [list(expected_seg)] + [normalize_phones(alt, lang) for alt in alternates]
    if previous_last is not None and len(expected_seg) > 1 and expected_seg[0] == previous_last:
        candidates.append(list(expected_seg[1:]))
    return candidates


def _phone_reports(candidate: List[str], actual: List[str], matched: List[int],
                   recognition: Dict, region: Tuple[int, int], lang: str) -> List[Dict]:
    local = _align(candidate, actual)
    spans = recognition["spans"]

    def frames(j: int):
        return spans[matched[j]]

    reports = []
    last = len(candidate) - 1
    for k, expected in enumerate(candidate):
        heard_indices = sorted(local[k])
        heard = [actual[j] for j in heard_indices]
        if heard == [expected]:
            confidence = 0.0
        elif expected in heard:
            confidence = FINAL_EXTRA_COST if k == last else 1.0
        else:
            if heard:
                cost = max(_substitution_cost(expected, h, lang) for h in heard)
                start = min(frames(j)[0] for j in heard_indices)
                end = max(frames(j)[1] for j in heard_indices)
            else:
                cost = FINAL_DELETION_COST if k == last else 1.0
                previous = [j for kk in range(k) for j in local[kk]]
                following = [j for kk in range(k + 1, len(candidate)) for j in local[kk]]
                start = frames(max(previous))[0] if previous else region[0]
                end = frames(min(following))[1] if following else region[1]
            plausibility = _plausibility(recognition, expected, start, end, lang)
            confidence = cost * (1.0 - min(1.0, plausibility / PHONE_PLAUSIBLE_POSTERIOR))
        reports.append({"expected": expected, "heard": "".join(heard),
                        "confidence": round(confidence, 3)})
    return reports


def compare_phones(recognition: Dict, text_reference: str, lang: str = "en") -> Dict:
    """Compare recognized phones against the phones espeak expects for ``text_reference``."""
    heard = recognition["phones"]
    words, groups = _expected_phones(text_reference, lang)
    expected = [p for g in groups for p in g]

    phone_error_rate = (Levenshtein.distance(expected, heard) / len(expected)) if expected \
        else (1.0 if heard else 0.0)

    alignment = _align(expected, heard)
    n_frames = len(recognition["log_posteriors"])
    spans = recognition["spans"]

    word_reports, errors, words_with_errors = [], [], []
    offset = 0
    previous_last = None
    for position, (word, group) in enumerate(zip(words, groups)):
        if not group:
            continue
        indices = range(offset, offset + len(group))
        offset += len(group)
        matched = sorted(set().union(*(alignment[i] for i in indices)))
        actual = [heard[j] for j in matched]

        candidates = _pronunciations(word, group, previous_last, lang)
        distances = [Levenshtein.distance(c, actual) for c in candidates]
        distance = min(distances)
        candidate = candidates[distances.index(distance)]
        previous_last = group[-1]

        if matched:
            region = (spans[matched[0]][0], spans[matched[-1]][1])
        else:
            before = [j for i in range(indices[0]) for j in alignment[i]]
            after = [j for i in range(indices[-1] + 1, len(expected)) for j in alignment[i]]
            region = (spans[max(before)][0] if before else 0,
                      spans[min(after)][1] if after else n_frames)

        phone_reports = _phone_reports(candidate, actual, matched, recognition, region, lang) if distance else []
        weighted = sum(p["confidence"] for p in phone_reports)
        word_reports.append({
            "position": position,
            "word": word,
            "expected": list(group),
            "actual": actual,
            "distance": distance,
            "phones": phone_reports,
            "weighted_edits": weighted,
            "start_frame": region[0],
            "end_frame": region[1],
        })

        length = len(group)
        if distance and (weighted / length >= PHONE_ERROR_THRESHOLD or weighted >= PHONE_ERROR_MIN_EDITS):
            errors.append({
                "position": position,
                "word": word,
                "expected": "".join(group),
                "actual": "".join(actual),
                "phone_distance": distance,
                "confidence": round(min(1.0, max(weighted / length, weighted / PHONE_ERROR_MIN_EDITS)), 3),
                "phones": phone_reports,
            })
            words_with_errors.append(word)

    return {
        "words": words,
        "word_reports": word_reports,
        "phone_error_rate": round(phone_error_rate, 4),
        "errors": errors,
        "words_with_errors": words_with_errors,
    }


# ---------------------------------------------------------------------------
# Public entry point
# ---------------------------------------------------------------------------

def score_wav(wav_path: str, reference_text: str) -> Dict:
    """Score a 16 kHz mono wav against ``reference_text``.

    Returns the raw analysis: overall score, per-word phone feedback, phone error
    rate and timing. The caller maps it onto the API schema.
    """
    if not reference_text or not reference_text.strip():
        raise ValueError("reference_text is required")

    waveform = load_wav_16k(wav_path)
    duration_s = len(waveform) / SAMPLING_RATE
    if duration_s < 0.15:
        raise ValueError("recording is too short to assess")

    log_posteriors = _log_posteriors(waveform)
    recognition = decode_ctc(log_posteriors, _vocab(), blank_id=0, lang="en")
    comparison = compare_phones(recognition, reference_text, lang="en")

    return {
        "duration_s": round(duration_s, 2),
        "phones_heard": recognition["phones"],
        "phones_confidence": [round(c, 3) for c in recognition["confidences"]],
        "comparison": comparison,
        "frames": len(log_posteriors),
    }
