import os
import shutil
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db
from app.models import Material, Sentence
from app.schemas import MaterialResponse, SubtitleDownloadRequest
from app.services.opensubtitles_client import OpenSubtitlesClient, SubtitleParser
from app.config import settings

router = APIRouter(prefix="/api/v1/subtitles", tags=["Subtitles & Search"])

@router.get("/search")
async def search_subtitles(
    query: str = Query(..., description="Movie or TV show name"),
    season: Optional[int] = Query(None),
    episode: Optional[int] = Query(None),
    year: Optional[int] = Query(None),
    api_key: Optional[str] = Query(None)
):
    """Search OpenSubtitles API v1 for English subtitles."""
    client = OpenSubtitlesClient(api_key=api_key)
    res = await client.search_subtitles(query, season, episode, year)
    return res

@router.post("/download-and-import", response_model=MaterialResponse)
async def download_and_import_subtitle(
    req: SubtitleDownloadRequest,
    api_key: Optional[str] = Query(None),
    db: AsyncSession = Depends(get_db)
):
    """Download subtitle by file_id from OpenSubtitles and import into Material Library."""
    client = OpenSubtitlesClient(api_key=api_key)
    raw_content = await client.download_subtitle(req.subtitle_id)
    if not raw_content:
        raise HTTPException(
            status_code=400, 
            detail="Failed to download subtitle from OpenSubtitles. Please check API key, quota, or network."
        )

    # Save raw file
    file_name = f"opensub_{req.subtitle_id}.srt"
    save_path = os.path.join(settings.SUBTITLES_DIR, file_name)
    with open(save_path, "w", encoding="utf-8") as f:
        f.write(raw_content)

    # Parse and clean
    parsed_items = SubtitleParser.parse_srt_or_vtt(raw_content)
    if not parsed_items:
        raise HTTPException(status_code=422, detail="Subtitle format not recognized or contains no valid dialogue.")

    material = Material(
        title=req.movie_name,
        year=req.year,
        season=req.season_number,
        episode=req.episode_number,
        media_type="tv_show" if req.season_number is not None else "movie",
        release_version=req.release or "OpenSubtitles",
        subtitle_source="opensubtitles",
        subtitle_file=save_path,
        sentence_count=len(parsed_items),
        duration_ms=parsed_items[-1]["end_ms"] if parsed_items else 0
    )
    db.add(material)
    await db.commit()
    await db.refresh(material)

    for item in parsed_items:
        s = Sentence(
            material_id=material.id,
            index=item["index"],
            start_ms=item["start_ms"],
            end_ms=item["end_ms"],
            text=item["text"],
            raw_text=item.get("raw_text"),
            speaker=item.get("speaker")
        )
        db.add(s)

    await db.commit()
    await db.refresh(material)
    return material

@router.post("/import-file", response_model=MaterialResponse)
async def import_local_subtitle(
    title: str = Form(...),
    year: Optional[int] = Form(None),
    season: Optional[int] = Form(None),
    episode: Optional[int] = Form(None),
    release_version: Optional[str] = Form("Custom Import"),
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db)
):
    """Import local subtitle file (SRT, VTT, ASS, TXT) with intelligent line cleaning and merging."""
    ext = os.path.splitext(file.filename)[1].lower()
    content_bytes = await file.read()
    
    # Try decoding
    text_content = ""
    for enc in ["utf-8", "utf-8-sig", "latin-1", "gbk"]:
        try:
            text_content = content_bytes.decode(enc)
            break
        except UnicodeDecodeError:
            continue

    if not text_content:
        raise HTTPException(status_code=400, detail="Cannot decode subtitle file. Please provide UTF-8 encoded text.")

    save_path = os.path.join(settings.SUBTITLES_DIR, f"local_{file.filename}")
    with open(save_path, "w", encoding="utf-8") as f:
        f.write(text_content)

    if ext in [".srt", ".vtt"]:
        parsed_items = SubtitleParser.parse_srt_or_vtt(text_content)
    elif ext == ".ass":
        # Handle ASS dialogue lines
        # Transform Dialogue: 0,0:01:20.00,0:01:23.00,Default,,0,0,0,,Text into standard format
        vtt_like = "WEBVTT\n\n"
        for line in text_content.splitlines():
            if line.startswith("Dialogue:"):
                parts = line.split(",", 9)
                if len(parts) >= 10:
                    start_t = parts[1].strip()
                    end_t = parts[2].strip()
                    txt = parts[9].strip()
                    vtt_like += f"{start_t} --> {end_t}\n{txt}\n\n"
        parsed_items = SubtitleParser.parse_srt_or_vtt(vtt_like)
    else:
        # Plain text
        parsed_items = SubtitleParser.parse_raw_txt(text_content)

    if not parsed_items:
        raise HTTPException(status_code=422, detail="No readable dialogue found in file.")

    material = Material(
        title=title,
        year=year,
        season=season,
        episode=episode,
        media_type="tv_show" if season is not None else "movie",
        release_version=release_version,
        subtitle_source="local_file",
        subtitle_file=save_path,
        sentence_count=len(parsed_items),
        duration_ms=parsed_items[-1]["end_ms"] if parsed_items else 0
    )
    db.add(material)
    await db.commit()
    await db.refresh(material)

    for item in parsed_items:
        s = Sentence(
            material_id=material.id,
            index=item["index"],
            start_ms=item["start_ms"],
            end_ms=item["end_ms"],
            text=item["text"],
            raw_text=item.get("raw_text"),
            speaker=item.get("speaker")
        )
        db.add(s)

    await db.commit()
    await db.refresh(material)
    return material
