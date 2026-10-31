#!/usr/bin/env bash
# Install the phoneme scorer sidecar on the API host.
#
# Run this ON the server, from a checkout of this repository:
#   bash score_service/deploy.sh
#
# It assumes: Ubuntu, espeak-ng and ffmpeg installable via apt, and the model files
# already present in /opt/cine_score (phone_int8_mm.onnx + vocab.json).
set -euo pipefail

MODEL_DIR="${PHONEME_MODEL_DIR:-/opt/cine_score}"
APP_DIR="${SCORE_APP_DIR:-/opt/cine_score/service}"

echo "== model files =="
ls -lh "$MODEL_DIR"/phone_int8_mm.onnx "$MODEL_DIR"/vocab.json

echo "== system packages =="
if ! command -v espeak-ng >/dev/null; then
  apt-get update -qq && apt-get install -y espeak-ng
fi
if ! command -v ffmpeg >/dev/null; then
  apt-get install -y ffmpeg
fi

echo "== python 3.12 + deps =="
# onnxruntime has no wheels for Python 3.14, which is what the host ships, so the
# sidecar gets its own 3.12 interpreter.
if ! command -v uv >/dev/null; then
  curl -LsSf https://astral.sh/uv/install.sh | sh
  export PATH="$HOME/.local/bin:$PATH"
fi
uv python install 3.12
uv venv --python 3.12 "$APP_DIR/venv"
uv pip install --python "$APP_DIR/venv/bin/python" \
  -i https://pypi.tuna.tsinghua.edu.cn/simple \
  -r "$(dirname "$0")/requirements.txt"

echo "== install code =="
mkdir -p "$APP_DIR"
cp "$(dirname "$0")"/phoneme_scorer.py "$(dirname "$0")"/score_api.py "$APP_DIR"/

echo "== systemd unit =="
cat > /etc/systemd/system/cine-score.service <<UNIT
[Unit]
Description=CineEnglish Phoneme Scorer
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=$APP_DIR
Environment="PHONEME_MODEL_DIR=$MODEL_DIR"
Environment="PHONEME_THREADS=2"
ExecStart=$APP_DIR/venv/bin/uvicorn score_api:app --host 127.0.0.1 --port 8009 --workers 1
Restart=always
RestartSec=10
# The model needs ~600 MB; refuse to start rather than swap the host to death.
MemoryMax=900M

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable --now cine-score
sleep 5
systemctl is-active cine-score
curl -s http://127.0.0.1:8009/health
echo
echo "Now set PHONEME_SERVICE_URL=http://127.0.0.1:8009 in the API server's .env"
