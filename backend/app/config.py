import os
from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    APP_NAME: str = "CineEnglish Backend API"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    
    # Server Host & Port
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    
    # Security & Auth
    SECRET_KEY: str = os.getenv("SECRET_KEY", "cineenglish-insecure-secret-key-change-in-production")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 30  # 30 days for personal use
    
    # Storage Paths
    BASE_DIR: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    DATA_DIR: str = os.getenv("DATA_DIR", os.path.join(BASE_DIR, "data"))
    RECORDINGS_DIR: str = os.path.join(DATA_DIR, "recordings")
    SUBTITLES_DIR: str = os.path.join(DATA_DIR, "subtitles")
    MEDIA_DIR: str = os.path.join(DATA_DIR, "media")
    TTS_CACHE_DIR: str = os.path.join(DATA_DIR, "tts_cache")
    
    # Database
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL", 
        f"sqlite+aiosqlite:///{os.path.join(DATA_DIR, 'cineenglish.db')}"
    )
    
    # OpenSubtitles API
    OPENSUBTITLES_API_KEY: Optional[str] = os.getenv("OPENSUBTITLES_API_KEY", "")
    OPENSUBTITLES_BASE_URL: str = "https://api.opensubtitles.com/api/v1"
    
    # Default AI Services Settings (Optional backend-managed keys)
    DEFAULT_AI_BASE_URL: str = os.getenv("DEFAULT_AI_BASE_URL", "https://api.openai.com/v1")
    DEFAULT_AI_API_KEY: Optional[str] = os.getenv("DEFAULT_AI_API_KEY", "")
    DEFAULT_CHAT_MODEL: str = os.getenv("DEFAULT_CHAT_MODEL", "gpt-4o-mini")
    DEFAULT_TTS_MODEL: str = os.getenv("DEFAULT_TTS_MODEL", "tts-1")
    DEFAULT_STT_MODEL: str = os.getenv("DEFAULT_STT_MODEL", "whisper-1")
    
    class Config:
        env_file = ".env"
        extra = "allow"

settings = Settings()

# Ensure directories exist
os.makedirs(settings.DATA_DIR, exist_ok=True)
os.makedirs(settings.RECORDINGS_DIR, exist_ok=True)
os.makedirs(settings.SUBTITLES_DIR, exist_ok=True)
os.makedirs(settings.MEDIA_DIR, exist_ok=True)
os.makedirs(settings.TTS_CACHE_DIR, exist_ok=True)
