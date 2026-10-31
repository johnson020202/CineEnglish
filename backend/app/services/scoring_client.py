"""HTTP client for the phoneme scorer sidecar.

The API server runs on Python 3.14, where onnxruntime has no wheels, so scoring
happens in a separate Python 3.12 process. This module is the only thing the API
server needs to know about it: no numpy, no onnxruntime, no model files.

When ``PHONEME_SERVICE_URL`` is unset the client falls back to loading the scorer
in-process, which is what the tests use and what a single-interpreter deployment
would do.
"""

import logging
import os
from typing import Dict

logger = logging.getLogger(__name__)

SERVICE_URL = os.environ.get("PHONEME_SERVICE_URL", "").rstrip("/")
TIMEOUT_SECONDS = float(os.environ.get("PHONEME_TIMEOUT", "60"))


class ScorerUnavailable(RuntimeError):
    """The scoring service is not configured or not reachable."""


async def score(wav_path: str, reference_text: str) -> Dict:
    """Score ``wav_path`` against ``reference_text``.

    Returns the sidecar's analysis dict. Raises :class:`ScorerUnavailable` when no
    scorer is configured, so the caller can fall back to duration-only scoring.
    """
    if not reference_text or not reference_text.strip():
        raise ValueError("reference_text is required")

    if SERVICE_URL:
        return await _score_remote(wav_path, reference_text)
    return await _score_in_process(wav_path, reference_text)


async def _score_remote(wav_path: str, reference_text: str) -> Dict:
    import httpx

    try:
        with open(wav_path, "rb") as f:
            files = {"file": (os.path.basename(wav_path), f, "audio/wav")}
            async with httpx.AsyncClient(timeout=TIMEOUT_SECONDS) as client:
                res = await client.post(f"{SERVICE_URL}/score", files=files,
                                        data={"reference_text": reference_text})
    except Exception as e:  # noqa: BLE001 - any transport failure degrades the same way
        raise ScorerUnavailable(f"scoring service unreachable at {SERVICE_URL}: {e}") from e

    if res.status_code == 503:
        raise ScorerUnavailable("scoring service has no model loaded")
    if res.status_code == 422:
        raise ValueError(res.json().get("detail", "invalid scoring request"))
    if res.status_code != 200:
        raise RuntimeError(f"scoring service returned {res.status_code}: {res.text[:200]}")
    return res.json()


async def _score_in_process(wav_path: str, reference_text: str) -> Dict:
    """Load the scorer here. Only works where numpy/onnxruntime are installed."""
    import asyncio

    try:
        from app.services import phoneme_scorer
    except ImportError as e:
        raise ScorerUnavailable(
            "no scoring service configured and the local scorer cannot be imported"
        ) from e

    if not phoneme_scorer.is_available():
        raise ScorerUnavailable("phone model is not installed")
    return await asyncio.to_thread(phoneme_scorer.score_wav, wav_path, reference_text)


def is_configured() -> bool:
    return bool(SERVICE_URL)
