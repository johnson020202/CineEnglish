from datetime import datetime
from sqlalchemy import Column, Integer, String, Text, Float, Boolean, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from app.database import Base

class User(Base):
    __tablename__ = "users"
    
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(64), unique=True, index=True, nullable=False)
    hashed_password = Column(String(256), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    records = relationship("PracticeRecord", back_populates="user", cascade="all, delete-orphan")
    vocabularies = relationship("Vocabulary", back_populates="user", cascade="all, delete-orphan")
    reviews = relationship("ReviewItem", back_populates="user", cascade="all, delete-orphan")

class Material(Base):
    __tablename__ = "materials"
    
    id = Column(Integer, primary_key=True, index=True)
    title = Column(String(256), index=True, nullable=False)
    year = Column(Integer, nullable=True)
    season = Column(Integer, nullable=True)
    episode = Column(Integer, nullable=True)
    media_type = Column(String(32), default="movie")  # movie, tv_show, clip, audio_only
    release_version = Column(String(128), nullable=True)  # WEB-DL, BluRay, etc.
    subtitle_source = Column(String(64), default="local")  # opensubtitles, local, manual
    subtitle_file = Column(String(512), nullable=True)
    video_file = Column(String(512), nullable=True)
    audio_file = Column(String(512), nullable=True)
    duration_ms = Column(Integer, default=0)
    sentence_count = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    sentences = relationship("Sentence", back_populates="material", cascade="all, delete-orphan", order_by="Sentence.index")
    records = relationship("PracticeRecord", back_populates="material", cascade="all, delete-orphan")

class Sentence(Base):
    __tablename__ = "sentences"
    
    id = Column(Integer, primary_key=True, index=True)
    material_id = Column(Integer, ForeignKey("materials.id"), nullable=False, index=True)
    index = Column(Integer, nullable=False, index=True)
    start_ms = Column(Integer, default=0, nullable=False)
    end_ms = Column(Integer, default=0, nullable=False)
    text = Column(Text, nullable=False)  # Cleaned English text
    raw_text = Column(Text, nullable=True)  # Raw subtitle text before cleaning
    speaker = Column(String(128), nullable=True)  # Speaker name if available
    audio_clip_path = Column(String(512), nullable=True)  # Extracted original audio snippet
    tts_audio_path = Column(String(512), nullable=True)  # Cached TTS audio snippet
    
    material = relationship("Material", back_populates="sentences")
    records = relationship("PracticeRecord", back_populates="sentence", cascade="all, delete-orphan")

class PracticeRecord(Base):
    __tablename__ = "practice_records"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)
    material_id = Column(Integer, ForeignKey("materials.id"), nullable=False, index=True)
    sentence_id = Column(Integer, ForeignKey("sentences.id"), nullable=False, index=True)
    practice_mode = Column(String(32), default="sentence_shadowing")  # sentence_shadowing, recall_recite, recall_free, role_play
    audio_file_path = Column(String(512), nullable=False)
    duration_ms = Column(Integer, default=0)
    
    # Pronunciation Engine & Evidence
    engine_name = Column(String(64), default="standard_acoustic")  # openpronounce, whisper_alignment, audio_llm_feedback, speech_ace
    engine_version = Column(String(32), default="1.0")
    assessment_tier = Column(String(32), default="phoneme_acoustic")  # phoneme_acoustic, text_matching, ai_reference_only
    
    # Scores (0.0 to 100.0)
    overall_score = Column(Float, default=0.0)
    accuracy_score = Column(Float, default=0.0)
    completeness_score = Column(Float, default=0.0)
    fluency_score = Column(Float, default=0.0)
    prosody_score = Column(Float, default=0.0)
    
    # Granular analysis & structured feedback
    word_details_json = Column(Text, nullable=True)  # JSON string of word/phoneme error tags
    feedback_en = Column(Text, nullable=True)  # Concise English feedback (1-2 points)
    is_passed = Column(Boolean, default=False)
    attempt_count = Column(Integer, default=1)
    is_flagged_unreasonable = Column(Boolean, default=False)  # User reported score unreasonable
    created_at = Column(DateTime, default=datetime.utcnow, index=True)
    
    user = relationship("User", back_populates="records")
    material = relationship("Material", back_populates="records")
    sentence = relationship("Sentence", back_populates="records")

class Vocabulary(Base):
    __tablename__ = "vocabularies"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)
    word = Column(String(128), nullable=False, index=True)
    phonetic = Column(String(128), nullable=True)
    definition_en = Column(Text, nullable=False)  # Standard English dictionary definition
    simple_explanation = Column(Text, nullable=True)  # Graded simplified English
    context_sentence = Column(Text, nullable=True)
    material_id = Column(Integer, ForeignKey("materials.id"), nullable=True)
    sentence_id = Column(Integer, ForeignKey("sentences.id"), nullable=True)
    audio_snippet_url = Column(String(512), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    user = relationship("User", back_populates="vocabularies")

class ReviewItem(Base):
    __tablename__ = "review_items"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True, index=True)
    item_type = Column(String(32), default="word")  # word, phrase, sentence
    content = Column(Text, nullable=False)
    context_sentence = Column(Text, nullable=True)
    material_id = Column(Integer, ForeignKey("materials.id"), nullable=True)
    sentence_id = Column(Integer, ForeignKey("sentences.id"), nullable=True)
    error_count = Column(Integer, default=1)
    confidence_level = Column(Float, default=0.5)  # 0.0 to 1.0, low confidence not marked as permanent weakness
    srs_stage = Column(Integer, default=0)  # 0, 1, 2, 3, 4, 5...
    next_review_at = Column(DateTime, default=datetime.utcnow, index=True)
    last_reviewed_at = Column(DateTime, nullable=True)
    is_mastered = Column(Boolean, default=False)
    is_ignored = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    
    user = relationship("User", back_populates="reviews")
