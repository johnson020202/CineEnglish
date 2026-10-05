from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from datetime import datetime

# User Schemas
class UserBase(BaseModel):
    username: str

class UserCreate(UserBase):
    password: str

class UserResponse(UserBase):
    id: int
    created_at: datetime
    class Config:
        from_attributes = True

class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user_id: int
    username: str

# Sentence Schemas
class SentenceBase(BaseModel):
    index: int
    start_ms: int
    end_ms: int
    text: str
    raw_text: Optional[str] = None
    speaker: Optional[str] = None

class SentenceUpdate(BaseModel):
    text: Optional[str] = None
    start_ms: Optional[int] = None
    end_ms: Optional[int] = None
    speaker: Optional[str] = None

class SentenceResponse(SentenceBase):
    id: int
    material_id: int
    audio_clip_path: Optional[str] = None
    tts_audio_path: Optional[str] = None
    class Config:
        from_attributes = True

# Material Schemas
class MaterialBase(BaseModel):
    title: str
    year: Optional[int] = None
    season: Optional[int] = None
    episode: Optional[int] = None
    media_type: str = "movie"
    release_version: Optional[str] = None
    subtitle_source: str = "local"

class MaterialCreate(MaterialBase):
    pass

class MaterialResponse(MaterialBase):
    id: int
    duration_ms: int
    sentence_count: int
    video_file: Optional[str] = None
    audio_file: Optional[str] = None
    created_at: datetime
    sentences: Optional[List[SentenceResponse]] = None
    class Config:
        from_attributes = True

# Word Evaluation Details
class WordAssessment(BaseModel):
    word: str
    score: float
    is_problematic: bool = False
    problem_type: Optional[str] = None  # omitted, mispronounced, inserted, low_score
    start_ms: Optional[int] = None
    end_ms: Optional[int] = None
    phonemes: Optional[List[Dict[str, Any]]] = None

# Assessment Result
class PronunciationAssessmentResult(BaseModel):
    engine_name: str
    engine_version: str
    assessment_tier: str  # phoneme_acoustic, text_matching, ai_reference_only
    overall_score: float
    accuracy_score: float
    completeness_score: float
    fluency_score: float
    prosody_score: float
    is_passed: bool
    word_details: List[WordAssessment]
    feedback_en: str
    confidence_evidence: str
    can_auto_advance: bool

# Practice Record Schemas
class PracticeRecordResponse(BaseModel):
    id: int
    material_id: int
    sentence_id: int
    practice_mode: str
    audio_file_path: str
    duration_ms: int
    engine_name: str
    overall_score: float
    accuracy_score: float
    completeness_score: float
    fluency_score: float
    prosody_score: float
    word_details_json: Optional[str] = None
    feedback_en: Optional[str] = None
    is_passed: bool
    attempt_count: int
    created_at: datetime
    class Config:
        from_attributes = True

# Dictionary & Vocabulary
class DictionaryDefinition(BaseModel):
    word: str
    phonetic: Optional[str] = None
    definition_en: str
    simple_explanation: str
    context_sentence: Optional[str] = None
    example_sentences_en: List[str] = []
    source_attribution: str = "Reliable Lexicon + AI Simplified"

class VocabularyCreate(BaseModel):
    word: str
    phonetic: Optional[str] = None
    definition_en: str
    simple_explanation: Optional[str] = None
    context_sentence: Optional[str] = None
    material_id: Optional[int] = None
    sentence_id: Optional[int] = None

class VocabularyResponse(VocabularyCreate):
    id: int
    created_at: datetime
    class Config:
        from_attributes = True

# Subtitle Search & Download
class SubtitleSearchResult(BaseModel):
    subtitle_id: str
    movie_name: str
    year: Optional[int] = None
    season_number: Optional[int] = None
    episode_number: Optional[int] = None
    release: Optional[str] = None
    language: str = "en"
    format: str = "srt"
    hearing_impaired: bool = False
    download_count: Optional[int] = 0
    ratings: Optional[float] = 0.0

class SubtitleDownloadRequest(BaseModel):
    subtitle_id: str
    movie_name: str
    year: Optional[int] = None
    season_number: Optional[int] = None
    episode_number: Optional[int] = None
    release: Optional[str] = None

# AI Service Config & Test
class AIServiceTestRequest(BaseModel):
    capability: str  # chat_coach, speech_recognition, text_to_speech, audio_understanding, pronunciation_evaluation
    base_url: Optional[str] = ""
    api_key: Optional[str] = None
    model_name: Optional[str] = "gpt-4o-mini"
    protocol: str = "openai_compatible"

class AIServiceTestResponse(BaseModel):
    capability: str
    is_supported: bool
    is_success: bool
    latency_ms: int
    model_used: str
    sample_output: Optional[str] = None
    error_message: Optional[str] = None
    available_models: Optional[List[str]] = None

class ModelsListResponse(BaseModel):
    success: bool
    models: List[str]
    count: int
    error: Optional[str] = None

# Free Recall & Shadow Evaluation Request
class FreeRecallEvaluationRequest(BaseModel):
    original_sentence: str
    spoken_transcript: str
    user_audio_duration_ms: int
    context_background: Optional[str] = None

class FreeRecallEvaluationResult(BaseModel):
    meaning_preserved_score: float  # 0 to 100
    grammar_score: float           # 0 to 100
    naturalness_score: float       # 0 to 100
    overall_score: float           # 0 to 100
    is_acceptable_paraphrase: bool
    suggestions_en: str
    key_words_used: List[str]
    missing_points: List[str]

# Role Play Schemas
class RolePlayDialogueTurn(BaseModel):
    speaker: str
    text: str
    is_user_turn: bool = False
    audio_url: Optional[str] = None

class RolePlayExtendRequest(BaseModel):
    material_title: str
    character_name: str
    user_character_name: str
    dialogue_history: List[RolePlayDialogueTurn]
    difficulty_level: str = "intermediate"  # simple, intermediate, native
