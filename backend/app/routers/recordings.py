import os
import shutil
import json
from typing import List, Optional
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy import func

from app.database import get_db
from app.models import PracticeRecord, Sentence, Material, ReviewItem
from app.schemas import PracticeRecordResponse
from app.config import settings

router = APIRouter(prefix="/api/v1/recordings", tags=["Recordings & Practice Records"])

@router.post("/upload", response_model=PracticeRecordResponse)
async def upload_recording(
    material_id: int = Form(...),
    sentence_id: int = Form(...),
    practice_mode: str = Form("sentence_shadowing"),
    engine_name: str = Form("CineAcousticEngine"),
    overall_score: float = Form(0.0),
    accuracy_score: float = Form(0.0),
    completeness_score: float = Form(0.0),
    fluency_score: float = Form(0.0),
    prosody_score: float = Form(0.0),
    is_passed: bool = Form(False),
    attempt_count: int = Form(1),
    word_details_json: Optional[str] = Form(None),
    feedback_en: Optional[str] = Form(None),
    audio_file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db)
):
    """
    Save user practice recording persistently.
    Creates practice record and updates SRS review status if needed.
    """
    timestamp_str = datetime.utcnow().strftime("%Y%m%d_%H%M%S")
    file_ext = os.path.splitext(audio_file.filename)[1] or ".m4a"
    saved_filename = f"rec_mat{material_id}_s{sentence_id}_{timestamp_str}{file_ext}"
    saved_path = os.path.join(settings.RECORDINGS_DIR, saved_filename)

    with open(saved_path, "wb") as f:
        shutil.copyfileobj(audio_file.file, f)

    file_url = f"/static/recordings/{saved_filename}"

    record = PracticeRecord(
        material_id=material_id,
        sentence_id=sentence_id,
        practice_mode=practice_mode,
        audio_file_path=file_url,
        duration_ms=0,
        engine_name=engine_name,
        overall_score=overall_score,
        accuracy_score=accuracy_score,
        completeness_score=completeness_score,
        fluency_score=fluency_score,
        prosody_score=prosody_score,
        word_details_json=word_details_json,
        feedback_en=feedback_en,
        is_passed=is_passed,
        attempt_count=attempt_count,
        created_at=datetime.utcnow()
    )
    db.add(record)

    # If the user struggled repeatedly or received low score, register weakness in SRS Review Items
    if overall_score < 75.0 or attempt_count >= 3:
        sent = (await db.execute(select(Sentence).where(Sentence.id == sentence_id))).scalars().first()
        if sent:
            existing_rev = (await db.execute(
                select(ReviewItem).where(ReviewItem.sentence_id == sentence_id)
            )).scalars().first()
            if not existing_rev:
                db.add(ReviewItem(
                    item_type="sentence",
                    content=sent.text,
                    context_sentence=sent.text,
                    material_id=material_id,
                    sentence_id=sentence_id,
                    error_count=attempt_count,
                    confidence_level=0.5
                ))
            else:
                existing_rev.error_count += 1

    await db.commit()
    await db.refresh(record)
    return record

@router.get("", response_model=List[PracticeRecordResponse])
async def list_records(
    material_id: Optional[int] = Query(None),
    sentence_id: Optional[int] = Query(None),
    mode: Optional[str] = Query(None),
    limit: int = Query(50),
    db: AsyncSession = Depends(get_db)
):
    query = select(PracticeRecord).order_by(PracticeRecord.created_at.desc())
    if material_id:
        query = query.where(PracticeRecord.material_id == material_id)
    if sentence_id:
        query = query.where(PracticeRecord.sentence_id == sentence_id)
    if mode:
        query = query.where(PracticeRecord.practice_mode == mode)
    query = query.limit(limit)
    
    result = await db.execute(query)
    return result.scalars().all()

@router.get("/sentence/{sentence_id}/history")
async def get_sentence_recording_history(sentence_id: int, db: AsyncSession = Depends(get_db)):
    """Fetch first, best, and latest recording for a given sentence."""
    records_res = await db.execute(
        select(PracticeRecord)
        .where(PracticeRecord.sentence_id == sentence_id)
        .order_by(PracticeRecord.created_at.asc())
    )
    records = records_res.scalars().all()
    if not records:
        return {"first": None, "best": None, "latest": None, "total_attempts": 0}

    first = records[0]
    latest = records[-1]
    best = max(records, key=lambda r: r.overall_score)

    return {
        "first": first,
        "best": best,
        "latest": latest,
        "total_attempts": len(records)
    }

@router.get("/stats")
async def get_learning_stats(db: AsyncSession = Depends(get_db)):
    """Summary metrics of learning progress."""
    total_records = (await db.execute(select(func.count(PracticeRecord.id)))).scalar() or 0
    passed_records = (await db.execute(
        select(func.count(PracticeRecord.id)).where(PracticeRecord.is_passed == True)
    )).scalar() or 0
    avg_score = (await db.execute(select(func.avg(PracticeRecord.overall_score)))).scalar() or 0.0

    # Calculate storage size
    rec_size_bytes = 0
    if os.path.exists(settings.RECORDINGS_DIR):
        for f in os.listdir(settings.RECORDINGS_DIR):
            fp = os.path.join(settings.RECORDINGS_DIR, f)
            if os.path.isfile(fp):
                rec_size_bytes += os.path.getsize(fp)

    return {
        "total_recordings": total_records,
        "passed_recordings": passed_records,
        "average_score": round(float(avg_score), 1),
        "storage_used_mb": round(rec_size_bytes / (1024 * 1024), 2)
    }

@router.get("/export")
async def export_learning_data(db: AsyncSession = Depends(get_db)):
    """Export all learning records and weaknesses as JSON."""
    records = (await db.execute(select(PracticeRecord).order_by(PracticeRecord.created_at.asc()))).scalars().all()
    reviews = (await db.execute(select(ReviewItem))).scalars().all()
    
    return {
        "exported_at": datetime.utcnow().isoformat(),
        "record_count": len(records),
        "records": [
            {
                "id": r.id,
                "material_id": r.material_id,
                "sentence_id": r.sentence_id,
                "practice_mode": r.practice_mode,
                "overall_score": r.overall_score,
                "accuracy_score": r.accuracy_score,
                "completeness_score": r.completeness_score,
                "fluency_score": r.fluency_score,
                "prosody_score": r.prosody_score,
                "is_passed": r.is_passed,
                "created_at": r.created_at.isoformat(),
                "audio_url": r.audio_file_path
            }
            for r in records
        ],
        "reviews": [
            {
                "content": rev.content,
                "error_count": rev.error_count,
                "is_mastered": rev.is_mastered
            }
            for rev in reviews
        ]
    }
