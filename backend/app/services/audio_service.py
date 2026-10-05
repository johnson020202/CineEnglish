import os
import subprocess
import json
import logging
from typing import Optional, Tuple

logger = logging.getLogger(__name__)

class AudioService:
    @staticmethod
    def get_ffmpeg_cmd() -> str:
        # Check local paths or system path
        if os.path.exists("/Users/xindao/.local/bin/ffmpeg"):
            return "/Users/xindao/.local/bin/ffmpeg"
        return "ffmpeg"

    @staticmethod
    def get_ffprobe_cmd() -> str:
        if os.path.exists("/Users/xindao/.local/bin/ffprobe"):
            return "/Users/xindao/.local/bin/ffprobe"
        return "ffprobe"

    @classmethod
    def convert_to_wav_16k_mono(cls, input_path: str, output_path: str) -> bool:
        """Convert any recorded audio to 16kHz 16-bit PCM mono WAV for speech/pronunciation engines."""
        cmd = [
            cls.get_ffmpeg_cmd(),
            "-y",
            "-i", input_path,
            "-ar", "16000",
            "-ac", "1",
            "-c:a", "pcm_s16le",
            output_path
        ]
        try:
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
            return True
        except Exception as e:
            logger.error(f"FFmpeg conversion failed: {e}")
            return False

    @classmethod
    def extract_audio_segment(cls, media_path: str, start_ms: int, end_ms: int, output_path: str) -> bool:
        """Extract a snippet from a video/audio file given start and end milliseconds."""
        start_sec = max(0.0, start_ms / 1000.0)
        duration_sec = max(0.1, (end_ms - start_ms) / 1000.0)
        
        cmd = [
            cls.get_ffmpeg_cmd(),
            "-y",
            "-ss", f"{start_sec:.3f}",
            "-i", media_path,
            "-t", f"{duration_sec:.3f}",
            "-vn",
            "-c:a", "aac",
            "-b:a", "128k",
            output_path
        ]
        try:
            subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
            return True
        except Exception as e:
            logger.error(f"Failed to extract audio segment: {e}")
            return False

    @classmethod
    def get_media_duration_ms(cls, file_path: str) -> int:
        """Get media duration in milliseconds using ffprobe."""
        cmd = [
            cls.get_ffprobe_cmd(),
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            file_path
        ]
        try:
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
            duration_sec = float(res.stdout.decode().strip())
            return int(duration_sec * 1000)
        except Exception as e:
            logger.warning(f"Could not get duration via ffprobe: {e}")
            return 0

    @classmethod
    def check_audio_quality(cls, file_path: str) -> Tuple[bool, str]:
        """
        Check if audio has sufficient volume or is corrupted/silent.
        Returns: (is_good, reason_if_bad)
        """
        if not os.path.exists(file_path) or os.path.getsize(file_path) < 1024:
            return False, "Audio file is empty or too short"
            
        cmd = [
            cls.get_ffmpeg_cmd(),
            "-i", file_path,
            "-af", "volumedetect",
            "-vn",
            "-sn",
            "-dn",
            "-f", "null",
            "/dev/null"
        ]
        try:
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            output = res.stderr
            # Parse max_volume
            for line in output.splitlines():
                if "max_volume:" in line:
                    parts = line.split("max_volume:")
                    if len(parts) > 1:
                        max_db = float(parts[1].replace("dB", "").strip())
                        if max_db < -45.0:
                            return False, "Audio is too quiet. Please speak closer to the microphone."
            return True, "OK"
        except Exception as e:
            logger.warning(f"Volume detection skipped: {e}")
            return True, "OK"
