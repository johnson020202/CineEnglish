#!/usr/bin/env bash
# Start the phoneme scorer sidecar. Expects espeak-ng, ffmpeg and the model files
# (phone_int8_mm.onnx + vocab.json) in PHONEME_MODEL_DIR.
set -euo pipefail
cd "$(dirname "$0")"
export PHONEME_MODEL_DIR="${PHONEME_MODEL_DIR:-/opt/cine_score}"
exec uvicorn score_api:app --host 127.0.0.1 --port "${SCORE_PORT:-8009}" --workers 1
