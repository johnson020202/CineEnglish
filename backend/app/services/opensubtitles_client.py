import os
import re
import httpx
import logging
from typing import List, Dict, Any, Optional, Tuple
from app.config import settings

logger = logging.getLogger(__name__)

class SubtitleParser:
    """Parses SRT, VTT, ASS, and TXT subtitle files into structured sentence objects."""

    @staticmethod
    def parse_time_str_to_ms(time_str: str) -> int:
        """Converts '00:01:23,456' or '00:01:23.456' to milliseconds."""
        time_str = time_str.replace(',', '.')
        parts = time_str.split(':')
        if len(parts) == 3:
            hours = int(parts[0])
            minutes = int(parts[1])
            seconds = float(parts[2])
            return int((hours * 3600 + minutes * 60 + seconds) * 1000)
        elif len(parts) == 2:
            minutes = int(parts[0])
            seconds = float(parts[1])
            return int((minutes * 60 + seconds) * 1000)
        return 0

    @classmethod
    def clean_subtitle_text(cls, text: str) -> Tuple[str, Optional[str]]:
        """
        Cleans HTML tags, ASS style codes, bracketed sound effects [sighs] / (screams).
        Extracts speaker if formatted like 'JOHN: Hello there'.
        Returns (clean_text, speaker_name)
        """
        speaker = None
        
        # Remove ASS override tags like {\an8}, {\pos(100,200)}
        text = re.sub(r"\{.*?\}", "", text)
        
        # Remove HTML tags <i>, <b>, <font color="...">, etc.
        text = re.sub(r"<[^>]+>", "", text)
        
        # Remove sound effect descriptions: [Music], (Laughter), [Door bangs]
        text = re.sub(r"\[.*?\]", "", text)
        text = re.sub(r"\(.*?\)", "", text)
        text = re.sub(r"♪.*?♪", "", text)
        text = re.sub(r"♫.*?♫", "", text)
        
        # Detect speaker prefix: "JOHN: Hey" or "MARY : Why?"
        speaker_match = re.match(r"^([A-Z0-9\s'-]{2,20}):\s*(.*)", text)
        if speaker_match:
            speaker = speaker_match.group(1).strip()
            text = speaker_match.group(2).strip()

        # Clean multiple spaces and whitespace
        text = re.sub(r"\s+", " ", text).strip()
        return text, speaker

    @classmethod
    def parse_srt_or_vtt(cls, content: str) -> List[Dict[str, Any]]:
        """Parses SRT or WebVTT content."""
        blocks = re.split(r"\n\s*\n", content.strip())
        raw_items = []
        
        for block in blocks:
            lines = [line.strip() for line in block.splitlines() if line.strip()]
            if not lines:
                continue
            
            # Find the line containing the timestamp arrow "-->"
            arrow_idx = -1
            for idx, line in enumerate(lines):
                if "-->" in line:
                    arrow_idx = idx
                    break
            
            if arrow_idx == -1:
                continue
                
            time_line = lines[arrow_idx]
            match = re.search(r"(\d+:\d+:\d+[,\.]\d+|\d+:\d+[,\.]\d+)\s*-->\s*(\d+:\d+:\d+[,\.]\d+|\d+:\d+[,\.]\d+)", time_line)
            if not match:
                continue
                
            start_ms = cls.parse_time_str_to_ms(match.group(1))
            end_ms = cls.parse_time_str_to_ms(match.group(2))
            
            # Dialog lines are after arrow line
            dialog_lines = lines[arrow_idx + 1:]
            raw_dialog = " ".join(dialog_lines)
            clean_dialog, speaker = cls.clean_subtitle_text(raw_dialog)
            
            if clean_dialog:
                raw_items.append({
                    "start_ms": start_ms,
                    "end_ms": end_ms,
                    "text": clean_dialog,
                    "raw_text": raw_dialog,
                    "speaker": speaker
                })

        return cls.merge_broken_sentences(raw_items)

    @classmethod
    def parse_raw_txt(cls, content: str) -> List[Dict[str, Any]]:
        """
        Parses pure text transcripts without timestamps.
        Assigns progressive timestamps with reasonable durations.
        """
        lines = content.strip().splitlines()
        sentences = []
        curr_time = 0
        idx = 0
        for line in lines:
            line = line.strip()
            if not line:
                continue
            clean_text, speaker = cls.clean_subtitle_text(line)
            if not clean_text:
                continue
            words_count = len(clean_text.split())
            duration = max(2000, words_count * 400)
            sentences.append({
                "index": idx,
                "start_ms": curr_time,
                "end_ms": curr_time + duration,
                "text": clean_text,
                "raw_text": line,
                "speaker": speaker
            })
            curr_time += duration + 500
            idx += 1
        return sentences

    @classmethod
    def merge_broken_sentences(cls, items: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        Merges lines split across multiple subtitle frames that belong to the same sentence.
        E.g. If previous item does not end with sentence-final punctuation (. ? !), 
        and time gap is small (< 1200ms), merge them into one natural sentence.
        """
        if not items:
            return []
            
        merged = []
        curr = items[0].copy()
        
        for next_item in items[1:]:
            prev_text = curr["text"]
            ends_sentence = bool(re.search(r"[\.\?!]\s*$", prev_text))
            time_gap = next_item["start_ms"] - curr["end_ms"]
            same_speaker = (curr.get("speaker") == next_item.get("speaker"))

            # Merge if not terminated and gap is short and same speaker
            if not ends_sentence and time_gap < 1200 and same_speaker:
                curr["end_ms"] = next_item["end_ms"]
                curr["text"] = f"{curr['text']} {next_item['text']}"
                curr["raw_text"] = f"{curr.get('raw_text', '')}\n{next_item.get('raw_text', '')}"
            else:
                merged.append(curr)
                curr = next_item.copy()
                
        merged.append(curr)
        
        # Re-index
        for idx, item in enumerate(merged):
            item["index"] = idx
            
        return merged


class OpenSubtitlesClient:
    """Official OpenSubtitles REST API v1 Client."""

    def __init__(self, api_key: Optional[str] = None):
        self.api_key = api_key or settings.OPENSUBTITLES_API_KEY
        self.base_url = settings.OPENSUBTITLES_BASE_URL
        self.headers = {
            "User-Agent": "CineEnglish v1.0.0",
            "Content-Type": "application/json"
        }
        if self.api_key:
            self.headers["Api-Key"] = self.api_key

    async def search_subtitles(
        self,
        query: str,
        season_number: Optional[int] = None,
        episode_number: Optional[int] = None,
        year: Optional[int] = None
    ) -> Dict[str, Any]:
        """Search English subtitles on OpenSubtitles API."""
        if not self.api_key:
            return {
                "success": False,
                "error": "OPENSUBTITLES_API_KEY_NOT_CONFIGURED",
                "message": "OpenSubtitles API key is not configured. You can configure it in settings or import local subtitle files.",
                "data": []
            }

        params: Dict[str, Any] = {
            "query": query,
            "languages": "en"
        }
        if season_number is not None:
            params["season_number"] = season_number
        if episode_number is not None:
            params["episode_number"] = episode_number
        if year is not None:
            params["year"] = year

        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                res = await client.get(
                    f"{self.base_url}/subtitles",
                    params=params,
                    headers=self.headers
                )
                if res.status_code == 200:
                    payload = res.json()
                    results = []
                    for item in payload.get("data", []):
                        attrs = item.get("attributes", {})
                        files = attrs.get("files", [])
                        file_id = files[0].get("file_id") if files else str(item.get("id"))
                        feature = attrs.get("feature_details", {})
                        results.append({
                            "subtitle_id": str(file_id),
                            "movie_name": feature.get("movie_name", attrs.get("release", query)),
                            "year": feature.get("year"),
                            "season_number": feature.get("season_number"),
                            "episode_number": feature.get("episode_number"),
                            "release": attrs.get("release", "Standard"),
                            "language": "en",
                            "format": attrs.get("format", "srt"),
                            "hearing_impaired": bool(attrs.get("hearing_impaired", False)),
                            "download_count": attrs.get("download_count", 0),
                            "ratings": attrs.get("ratings", 0.0)
                        })
                    return {"success": True, "data": results}
                elif res.status_code == 401:
                    return {"success": False, "error": "INVALID_API_KEY", "message": "Invalid OpenSubtitles API key."}
                elif res.status_code == 429:
                    return {"success": False, "error": "RATE_LIMITED", "message": "OpenSubtitles daily download/query limit reached."}
                else:
                    return {"success": False, "error": f"HTTP_{res.status_code}", "message": res.text}
        except Exception as e:
            logger.error(f"OpenSubtitles API error: {e}")
            return {"success": False, "error": "NETWORK_ERROR", "message": str(e)}

    async def download_subtitle(self, file_id: str) -> Optional[str]:
        """Request download link and fetch raw subtitle content."""
        if not self.api_key:
            return None

        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                download_res = await client.post(
                    f"{self.base_url}/download",
                    json={"file_id": int(file_id)},
                    headers=self.headers
                )
                if download_res.status_code == 200:
                    link = download_res.json().get("link")
                    if link:
                        content_res = await client.get(link)
                        if content_res.status_code == 200:
                            return content_res.text
        except Exception as e:
            logger.error(f"Failed to download subtitle {file_id}: {e}")
        return None
