"""Standalone phoneme-scoring sidecar.

The main API server runs on Python 3.14, for which onnxruntime has no wheels, so the
phone recognizer lives in its own process on Python 3.12 and is called over HTTP.

Run with: uvicorn score_api:app --host 127.0.0.1 --port 8009
"""

import logging
import os
import tempfile

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from phoneme_scorer import ScorerUnavailable, score_wav, warmup

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("cine.score")

app = FastAPI(title="CineEnglish Phoneme Scorer", version="2.0.0")


@app.on_event("startup")
def _startup() -> None:
    # Load the session eagerly: the first request should not pay a 2-3 s model load,
    # and a broken install must surface in the logs rather than as a user-facing 500.
    warmup()


@app.get("/health")
def health() -> dict:
    from phoneme_scorer import ONNX_PATH

    return {"status": "ok", "model": ONNX_PATH, "model_present": os.path.exists(ONNX_PATH)}


@app.post("/score")
async def score(file: UploadFile = File(...), reference_text: str = Form(...)) -> dict:
    """Score a recording against ``reference_text``; returns the raw phone analysis."""
    suffix = os.path.splitext(file.filename or "")[1] or ".wav"
    fd, path = tempfile.mkstemp(suffix=suffix, prefix="cine-score-")
    try:
        with os.fdopen(fd, "wb") as buffer:
            buffer.write(await file.read())
        return score_wav(path, reference_text)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    except ScorerUnavailable as e:
        logger.error("scorer unavailable: %s", e)
        raise HTTPException(status_code=503, detail="phoneme model unavailable") from e
    except Exception as e:  # noqa: BLE001
        logger.exception("scoring failed")
        raise HTTPException(status_code=500, detail="scoring failed") from e
    finally:
        try:
            os.remove(path)
        except OSError:
            pass
