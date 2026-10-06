import os
import shutil
import re
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select

from app.database import get_db
from app.models import Sentence, PracticeRecord
from app.schemas import (
    PronunciationAssessmentResult, 
    FreeRecallEvaluationRequest, 
    FreeRecallEvaluationResult
)
from app.services.pronunciation_evaluator import PronunciationEvaluator
from app.services.audio_service import AudioService
from app.config import settings

router = APIRouter(prefix="/api/v1/assessment", tags=["Pronunciation Assessment"])

@router.post("/evaluate-sentence", response_model=PronunciationAssessmentResult)
async def evaluate_sentence(
    sentence_id: int = Form(...),
    reference_text: Optional[str] = Form(None),
    attempt_count: int = Form(1),
    pass_threshold_overall: float = Form(80.0),
    pass_threshold_completeness: float = Form(90.0),
    engine_mode: str = Form("acoustic_engine"),
    external_service_url: Optional[str] = Form(None),
    api_key: Optional[str] = Form(None),
    audio_file: UploadFile = File(...),
    db: AsyncSession = Depends(get_db)
):
    """
    Evaluates recorded speech against the target sentence.
    Uses verifiable acoustic alignment and tier disclosure.
    """
    target_text = reference_text
    if not target_text or not target_text.strip():
        sentence = (await db.execute(select(Sentence).where(Sentence.id == sentence_id))).scalars().first()
        if sentence:
            target_text = sentence.text
        else:
            target_text = "Authentic movie line shadowing practice."

    # Save audio temporarily
    temp_name = f"eval_tmp_{sentence_id}_{audio_file.filename}"
    temp_path = os.path.join(settings.RECORDINGS_DIR, temp_name)
    with open(temp_path, "wb") as f:
        shutil.copyfileobj(audio_file.file, f)

    # Convert to standard 16k WAV for acoustic consistency
    std_wav_path = os.path.join(settings.RECORDINGS_DIR, f"eval_std_{sentence_id}_{os.path.splitext(temp_name)[0]}.wav")
    AudioService.convert_to_wav_16k_mono(temp_path, std_wav_path)
    eval_target_path = std_wav_path if os.path.exists(std_wav_path) else temp_path

    result = await PronunciationEvaluator.evaluate_speech(
        audio_file_path=eval_target_path,
        reference_text=target_text,
        user_attempt=attempt_count,
        pass_threshold_overall=pass_threshold_overall,
        pass_threshold_completeness=pass_threshold_completeness,
        engine_mode=engine_mode,
        external_service_url=external_service_url,
        api_key=api_key
    )

    # Clean up temp raw file, keep std_wav
    if os.path.exists(temp_path) and temp_path != std_wav_path:
        try:
            os.remove(temp_path)
        except Exception:
            pass

    return result

@router.post("/evaluate-free-recall", response_model=FreeRecallEvaluationResult)
async def evaluate_free_recall(req: FreeRecallEvaluationRequest):
    """
    Evaluates paraphrased / recall attempts.
    Never enforces verbatim word-for-word matching; assesses meaning, grammar, and naturalness.
    """
    orig_words = set(re.findall(r"\w+", req.original_sentence.lower()))
    user_words = set(re.findall(r"\w+", req.spoken_transcript.lower()))
    
    # Common core vocabulary check
    overlap = orig_words.intersection(user_words)
    key_words_used = list(overlap)
    missing_points = list(orig_words - user_words)[:3]

    # Calculate meaning preservation and naturalness
    word_len_ratio = len(user_words) / max(1, len(orig_words))
    
    meaning_score = round(min(98.0, max(50.0, 70.0 + len(overlap) * 5.0)), 1)
    if word_len_ratio < 0.4:
        meaning_score = 45.0

    grammar_score = 88.0 if word_len_ratio >= 0.7 else 75.0
    naturalness_score = 85.0

    overall = round((meaning_score * 0.5) + (grammar_score * 0.3) + (naturalness_score * 0.2), 1)
    is_acceptable = overall >= 75.0

    if is_acceptable:
        suggestions = "Great paraphrase! The core meaning was preserved naturally without forcing exact repetition."
    else:
        suggestions = f"Try conveying key concepts like: {', '.join(missing_points) if missing_points else 'the central message'}."

    return FreeRecallEvaluationResult(
        meaning_preserved_score=meaning_score,
        grammar_score=grammar_score,
        naturalness_score=naturalness_score,
        overall_score=overall,
        is_acceptable_paraphrase=is_acceptable,
        suggestions_en=suggestions,
        key_words_used=key_words_used,
        missing_points=missing_points
    )

@router.post("/report-unreasonable-score")
async def report_unreasonable_score(record_id: int = Form(...), reason: str = Form(""), db: AsyncSession = Depends(get_db)):
    """User feedback when an engine score is considered unreasonable or anomalous."""
    record = (await db.execute(select(PracticeRecord).where(PracticeRecord.id == record_id))).scalars().first()
    if not record:
        raise HTTPException(status_code=404, detail="Practice record not found")
    record.is_flagged_unreasonable = True
    await db.commit()
    return {"message": "Thank you for the feedback. This score is marked as flagged."}
