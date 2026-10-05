import httpx
import logging
from typing import List, Optional
from datetime import datetime, timedelta
from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select

from app.database import get_db
from app.models import Vocabulary, ReviewItem, Sentence
from app.schemas import (
    DictionaryDefinition, 
    VocabularyCreate, 
    VocabularyResponse
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/dictionary", tags=["Dictionary & Vocabulary"])

# Pre-packaged core English lexicon for immediate high-speed offline fallback
LOCAL_LEXICON = {
    "consequence": {
        "phonetic": "/ˈkɑːnsəkwens/",
        "definition": "A result or effect of an action or condition.",
        "simple": "What happens because of something else you did.",
        "examples": ["Every action has a natural consequence.", "He accepted the consequence without fear."]
    },
    "reckon": {
        "phonetic": "/ˈrekən/",
        "definition": "To establish by calculation, or consider/regard in a specified way.",
        "simple": "To think, believe, or estimate something.",
        "examples": ["I reckon we have about ten minutes left.", "Do you reckon it will rain tonight?"]
    },
    "hesitate": {
        "phonetic": "/ˈhezɪteɪt/",
        "definition": "Pause before saying or doing something that you are unsure about.",
        "simple": "To wait a moment because you are not sure what to do.",
        "examples": ["Do not hesitate to ask questions.", "She hesitated before opening the door."]
    },
    "inevitable": {
        "phonetic": "/ɪnˈevɪtəbl/",
        "definition": "Certain to happen; unavoidable.",
        "simple": "Something that is sure to happen and cannot be stopped.",
        "examples": ["Change is an inevitable part of life.", "The outcome was sad but inevitable."]
    }
}

@router.get("/lookup", response_model=DictionaryDefinition)
async def lookup_word(
    word: str = Query(..., description="English word or phrase"),
    context: Optional[str] = Query(None, description="Current sentence context"),
    simplify_level: int = Query(1, description="1 = Normal, 2 = Simpler, 3 = Child-level")
):
    """
    Look up an English word strictly with English-only definitions, 
    American phonetics, and graded simple explanations. No Chinese translations.
    """
    clean_word = word.strip().lower()
    
    # 1. Check built-in quick dictionary
    if clean_word in LOCAL_LEXICON:
        info = LOCAL_LEXICON[clean_word]
        simple = info["simple"]
        if simplify_level >= 2:
            simple = f"Very easy meaning: {simple}"
        return DictionaryDefinition(
            word=word,
            phonetic=info["phonetic"],
            definition_en=info["definition"],
            simple_explanation=simple,
            context_sentence=context,
            example_sentences_en=info["examples"],
            source_attribution="CineEnglish Built-in Core Lexicon"
        )

    # 2. Query public Free Dictionary API (English to English)
    try:
        async with httpx.AsyncClient(timeout=4.0) as client:
            res = await client.get(f"https://api.dictionaryapi.dev/api/v2/entries/en/{clean_word}")
            if res.status_code == 200:
                data = res.json()[0]
                phonetic = data.get("phonetic") or ""
                meanings = data.get("meanings", [])
                def_en = "Standard definition."
                examples = []
                if meanings:
                    first_meaning = meanings[0]
                    defs = first_meaning.get("definitions", [])
                    if defs:
                        def_en = defs[0].get("definition", def_en)
                        ex = defs[0].get("example")
                        if ex:
                            examples.append(ex)
                
                simple = def_en
                if simplify_level > 1:
                    simple = f"Basic explanation: {def_en.split(';')[0]}"
                    
                return DictionaryDefinition(
                    word=word,
                    phonetic=phonetic,
                    definition_en=def_en,
                    simple_explanation=simple,
                    context_sentence=context,
                    example_sentences_en=examples,
                    source_attribution="Free Dictionary API (English-English)"
                )
    except Exception as e:
        logger.warning(f"Public dictionary query failed: {e}")

    # 3. Dynamic Fallback Definition
    return DictionaryDefinition(
        word=word,
        phonetic=f"/{word}/",
        definition_en=f"A word used in context: '{context or word}'.",
        simple_explanation=f"Notice how '{word}' behaves within the phrase.",
        context_sentence=context,
        example_sentences_en=[f"Listen to how the actor pronounces '{word}' in the scene."],
        source_attribution="Contextual Fallback"
    )

@router.post("/vocabulary", response_model=VocabularyResponse)
async def add_vocabulary(data: VocabularyCreate, db: AsyncSession = Depends(get_db)):
    """Add word to personal vocabulary book with sentence context."""
    vocab = Vocabulary(
        word=data.word,
        phonetic=data.phonetic,
        definition_en=data.definition_en,
        simple_explanation=data.simple_explanation,
        context_sentence=data.context_sentence,
        material_id=data.material_id,
        sentence_id=data.sentence_id
    )
    db.add(vocab)
    
    # Also register for SRS review
    review_item = ReviewItem(
        item_type="word",
        content=data.word,
        context_sentence=data.context_sentence,
        material_id=data.material_id,
        sentence_id=data.sentence_id,
        confidence_level=0.7
    )
    db.add(review_item)

    await db.commit()
    await db.refresh(vocab)
    return vocab

@router.get("/vocabulary", response_model=List[VocabularyResponse])
async def list_vocabularies(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(Vocabulary).order_by(Vocabulary.created_at.desc()))
    return result.scalars().all()

@router.delete("/vocabulary/{vocab_id}")
async def delete_vocabulary(vocab_id: int, db: AsyncSession = Depends(get_db)):
    v = (await db.execute(select(Vocabulary).where(Vocabulary.id == vocab_id))).scalars().first()
    if not v:
        raise HTTPException(status_code=404, detail="Vocabulary item not found")
    await db.delete(v)
    await db.commit()
    return {"message": "Vocabulary deleted"}

@router.get("/reviews")
async def get_daily_reviews(limit: int = Query(20), db: AsyncSession = Depends(get_db)):
    """Fetch due SRS review items for today."""
    now = datetime.utcnow()
    result = await db.execute(
        select(ReviewItem)
        .where(ReviewItem.is_mastered == False, ReviewItem.is_ignored == False)
        .where(ReviewItem.next_review_at <= now)
        .order_by(ReviewItem.next_review_at.asc())
        .limit(limit)
    )
    items = result.scalars().all()
    return items

@router.post("/reviews/{review_id}/submit")
async def submit_review_progress(
    review_id: int, 
    quality: int = Query(..., description="1 = Fail/Forgot, 2 = Hard, 3 = Good, 4 = Easy"), 
    db: AsyncSession = Depends(get_db)
):
    """Update SRS interval according to spaced repetition rules."""
    item = (await db.execute(select(ReviewItem).where(ReviewItem.id == review_id))).scalars().first()
    if not item:
        raise HTTPException(status_code=404, detail="Review item not found")
        
    intervals_days = [1, 2, 4, 7, 15, 30]
    if quality >= 3:
        item.srs_stage = min(len(intervals_days) - 1, item.srs_stage + 1)
        if item.srs_stage >= len(intervals_days) - 1:
            item.is_mastered = True
    elif quality == 1:
        item.srs_stage = max(0, item.srs_stage - 1)
        item.error_count += 1
        
    next_days = intervals_days[item.srs_stage]
    item.last_reviewed_at = datetime.utcnow()
    item.next_review_at = datetime.utcnow() + timedelta(days=next_days)
    
    await db.commit()
    return {
        "message": "Review stage updated",
        "srs_stage": item.srs_stage,
        "is_mastered": item.is_mastered,
        "next_review_days": next_days
    }

@router.post("/reviews/{review_id}/toggle-ignore")
async def toggle_ignore_review(review_id: int, db: AsyncSession = Depends(get_db)):
    """Mark item as false-positive or ignore from weakness review."""
    item = (await db.execute(select(ReviewItem).where(ReviewItem.id == review_id))).scalars().first()
    if not item:
        raise HTTPException(status_code=404, detail="Review item not found")
    item.is_ignored = not item.is_ignored
    await db.commit()
    return {"message": "Toggled ignore status", "is_ignored": item.is_ignored}
