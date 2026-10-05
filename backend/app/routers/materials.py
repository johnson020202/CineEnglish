import os
import shutil
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload

from app.database import get_db
from app.models import Material, Sentence
from app.schemas import (
    MaterialResponse, MaterialCreate, 
    SentenceResponse, SentenceUpdate
)
from app.config import settings
from app.services.audio_service import AudioService

router = APIRouter(prefix="/api/v1/materials", tags=["Materials & Sentences"])

@router.get("", response_model=List[MaterialResponse])
async def list_materials(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Material).order_by(Material.updated_at.desc()))
    materials = result.scalars().all()
    return materials

@router.post("", response_model=MaterialResponse)
async def create_material(data: MaterialCreate, db: AsyncSession = Depends(get_db)):
    material = Material(
        title=data.title,
        year=data.year,
        season=data.season,
        episode=data.episode,
        media_type=data.media_type,
        release_version=data.release_version,
        subtitle_source=data.subtitle_source
    )
    db.add(material)
    await db.commit()
    await db.refresh(material)
    return material

@router.get("/{material_id}", response_model=MaterialResponse)
async def get_material_detail(material_id: int, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(Material)
        .options(selectinload(Material.sentences))
        .where(Material.id == material_id)
    )
    material = result.scalars().first()
    if not material:
        raise HTTPException(status_code=404, detail="Material not found")
    return material

@router.delete("/{material_id}")
async def delete_material(material_id: int, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Material).where(Material.id == material_id))
    material = result.scalars().first()
    if not material:
        raise HTTPException(status_code=404, detail="Material not found")
    await db.delete(material)
    await db.commit()
    return {"message": "Material deleted successfully"}

@router.get("/{material_id}/sentences", response_model=List[SentenceResponse])
async def get_sentences(material_id: int, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(Sentence)
        .where(Sentence.material_id == material_id)
        .order_by(Sentence.index.asc())
    )
    return result.scalars().all()

@router.put("/sentences/{sentence_id}", response_model=SentenceResponse)
async def update_sentence(sentence_id: int, update_data: SentenceUpdate, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Sentence).where(Sentence.id == sentence_id))
    sentence = result.scalars().first()
    if not sentence:
        raise HTTPException(status_code=404, detail="Sentence not found")
    
    if update_data.text is not None:
        sentence.text = update_data.text
    if update_data.start_ms is not None:
        sentence.start_ms = update_data.start_ms
    if update_data.end_ms is not None:
        sentence.end_ms = update_data.end_ms
    if update_data.speaker is not None:
        sentence.speaker = update_data.speaker
        
    await db.commit()
    await db.refresh(sentence)
    return sentence

@router.post("/{material_id}/sentences/shift-time")
async def shift_subtitles_timeline(material_id: int, offset_ms: int = Form(...), db: AsyncSession = Depends(get_db)):
    """Adjust all subtitle timestamps by offset_ms (positive or negative)."""
    result = await db.execute(select(Sentence).where(Sentence.material_id == material_id))
    sentences = result.scalars().all()
    for s in sentences:
        s.start_ms = max(0, s.start_ms + offset_ms)
        s.end_ms = max(100, s.end_ms + offset_ms)
    await db.commit()
    return {"message": f"Successfully shifted {len(sentences)} sentences by {offset_ms}ms"}

@router.post("/{material_id}/sentences/merge")
async def merge_two_sentences(sentence_id1: int = Form(...), sentence_id2: int = Form(...), db: AsyncSession = Depends(get_db)):
    """Manually merge two adjacent sentences."""
    s1 = (await db.execute(select(Sentence).where(Sentence.id == sentence_id1))).scalars().first()
    s2 = (await db.execute(select(Sentence).where(Sentence.id == sentence_id2))).scalars().first()
    if not s1 or not s2 or s1.material_id != s2.material_id:
        raise HTTPException(status_code=400, detail="Invalid sentences for merge")
        
    # Combine texts
    s1.text = f"{s1.text.strip()} {s2.text.strip()}"
    s1.end_ms = max(s1.end_ms, s2.end_ms)
    await db.delete(s2)
    await db.commit()
    await db.refresh(s1)
    return {"message": "Sentences merged successfully", "merged_sentence": s1}

@router.post("/{material_id}/attach-media")
async def attach_media_file(
    material_id: int,
    file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db)
):
    """Upload media (video or audio) and associate with material."""
    result = await db.execute(select(Material).where(Material.id == material_id))
    material = result.scalars().first()
    if not material:
        raise HTTPException(status_code=404, detail="Material not found")
        
    ext = os.path.splitext(file.filename)[1].lower()
    save_name = f"media_mat_{material_id}_{file.filename}"
    save_path = os.path.join(settings.MEDIA_DIR, save_name)
    
    with open(save_path, "wb") as f:
        shutil.copyfileobj(file.file, f)
        
    duration = AudioService.get_media_duration_ms(save_path)
    if ext in [".mp4", ".mkv", ".webm", ".mov", ".avi"]:
        material.video_file = f"/static/media/{save_name}"
    else:
        material.audio_file = f"/static/media/{save_name}"
    material.duration_ms = duration
    
    await db.commit()
    await db.refresh(material)
    return {
        "message": "Media attached successfully", 
        "video_file": material.video_file,
        "audio_file": material.audio_file,
        "duration_ms": duration
    }
