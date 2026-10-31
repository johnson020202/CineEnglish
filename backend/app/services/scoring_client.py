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

def _service_url() -> str:
    """Read the scorer address at call time.

    ``.env`` is parsed by pydantic Settings and does not populate ``os.environ``, so
    reading the environment directly here would silently ignore the configured URL
    and every request would fall back to duration-only scoring.
    """
    try:
        from app.config import settings
        url = getattr(settings, "PHONEME_SERVICE_URL", "") or ""
    except Exception:  # noqa: BLE001 - config import must not break scoring
        url = ""
    return (url or os.environ.get("PHONEME_SERVICE_URL", "")).rstrip("/")


def _timeout() -> float:
    try:
        from app.config import settings
        return float(getattr(settings, "PHONEME_TIMEOUT", 60))
    except Exception:  # noqa: BLE001
        return float(os.environ.get("PHONEME_TIMEOUT", "60"))


class ScorerUnavailable(RuntimeError):
    """The scoring service is not configured or not reachable."""


async def score(wav_path: str, reference_text: str) -> Dict:
    """Score ``wav_path`` against ``reference_text``.

    Returns the sidecar's analysis dict. Raises :class:`ScorerUnavailable` when no
    scorer is configured, so the caller can fall back to duration-only scoring.
    """
    if not reference_text or not reference_text.strip():
        raise ValueError("reference_text is required")

    if _service_url():
        return await _score_remote(wav_path, reference_text)
    return await _score_in_process(wav_path, reference_text)


async def _score_remote(wav_path: str, reference_text: str) -> Dict:
    import httpx

    url = _service_url()
    try:
        with open(wav_path, "rb") as f:
            files = {"file": (os.path.basename(wav_path), f, "audio/wav")}
            async with httpx.AsyncClient(timeout=_timeout()) as client:
                res = await client.post(f"{url}/score", files=files,
                                        data={"reference_text": reference_text})
    except Exception as e:  # noqa: BLE001 - any transport failure degrades the same way
        raise ScorerUnavailable(f"scoring service unreachable at {url}: {e}") from e

    if res.status_code == 503:
        raise ScorerUnavailable("scoring service has no model loaded")
    if res.status_code == 422:
        raise ValueError(res.json().get("detail", "invalid scoring request"))
    if res.status_code != 200:
        raise RuntimeError(f"scoring service returned {res.status_code}: {res.text[:200]}")
    return res.json()


async def _score_in_process(wav_path: str, reference_text: str) -> Dict:
    """Fall back to a scorer in this process.

    Not used in production: the API server runs on Python 3.14, where onnxruntime
    and numpy are unavailable. It exists so a single-interpreter install can still
    score, provided the caller put ``score_service`` on sys.path.
    """
    import asyncio
    import sys

    if "score_service" not in sys.path:
        import os as _os
        sys.path.insert(0, _os.path.join(_os.path.dirname(__file__), "..", "..", "..", "score_service"))

    try:
        import phoneme_scorer
    except ImportError as e:
        raise ScorerUnavailable(
            "no PHONEME_SERVICE_URL configured and the in-process scorer is unavailable"
        ) from e

    if not phoneme_scorer.is_available():
        raise ScorerUnavailable("phone model is not installed")
    return await asyncio.to_thread(phoneme_scorer.score_wav, wav_path, reference_text)


def is_configured() -> bool:
    return bool(_service_url())
