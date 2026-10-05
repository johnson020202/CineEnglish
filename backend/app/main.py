import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager

from app.config import settings
from app.database import init_db
from app.routers import (
    auth,
    materials,
    subtitles,
    recordings,
    assessment,
    dictionary,
    ai_proxy
)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Initialize SQLite database and create tables
    await init_db()
    yield

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="CineEnglish Backend API: Movie English Speaking, Pronunciation Scoring & Audio Management",
    lifespan=lifespan
)

# Enable CORS for Android client & local inspection
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount Static Directories for Audio and Media
app.mount("/static/recordings", StaticFiles(directory=settings.RECORDINGS_DIR), name="recordings")
app.mount("/static/media", StaticFiles(directory=settings.MEDIA_DIR), name="media")
app.mount("/static/tts", StaticFiles(directory=settings.TTS_CACHE_DIR), name="tts")

# Include Routers
app.include_router(auth.router)
app.include_router(materials.router)
app.include_router(subtitles.router)
app.include_router(recordings.router)
app.include_router(assessment.router)
app.include_router(dictionary.router)
app.include_router(ai_proxy.router)

@app.get("/")
async def root():
    return {
        "app": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "status": "online",
        "docs": "/docs"
    }

@app.get("/health")
async def health_check():
    return {"status": "healthy"}
