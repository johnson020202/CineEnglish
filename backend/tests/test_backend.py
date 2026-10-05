import pytest
from app.services.opensubtitles_client import SubtitleParser
from app.services.pronunciation_evaluator import PronunciationEvaluator
from app.schemas import FreeRecallEvaluationRequest
import re

def test_subtitle_cleaning():
    raw_sub = "<i>[Music]</i> {\an8}JOHN: You can't just give up now! (sighs)"
    clean, speaker = SubtitleParser.clean_subtitle_text(raw_sub)
    assert speaker == "JOHN"
    assert clean == "You can't just give up now!"
    assert "[Music]" not in clean
    assert "(sighs)" not in clean
    assert "{\an8}" not in clean

def test_subtitle_srt_parser_and_merging():
    srt_content = """1
00:01:10,000 --> 00:01:11,500
If you want to master

2
00:01:11,600 --> 00:01:13,200
spoken English every day.
"""
    items = SubtitleParser.parse_srt_or_vtt(srt_content)
    assert len(items) == 1
    assert items[0]["text"] == "If you want to master spoken English every day."
    assert items[0]["start_ms"] == 70000
    assert items[0]["end_ms"] == 73200

@pytest.mark.asyncio
async def test_free_recall_evaluation():
    from app.routers.assessment import evaluate_free_recall
    req = FreeRecallEvaluationRequest(
        original_sentence="I have to go to the store to buy some fresh milk.",
        spoken_transcript="I need to run to the grocery store because we ran out of milk.",
        user_audio_duration_ms=3500
    )
    res = await evaluate_free_recall(req)
    # Natural paraphrase should be accepted, not rejected because of word mismatch
    assert res.is_acceptable_paraphrase is True
    assert res.overall_score >= 70.0
    assert "milk" in res.key_words_used or "store" in res.key_words_used
