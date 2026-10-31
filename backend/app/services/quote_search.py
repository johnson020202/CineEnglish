"""Quote-level subtitle search: find a movie by a line of dialogue, then pull its subtitles.

Two sources, used in this order:

1. **Subzin** (``/api/search?q=``) — full-text search over ~63M dialogue lines. Free and
   key-less, and the only source here that answers "which film says this line?". It is an
   undocumented internal endpoint, so every call is wrapped: a failure returns an empty
   list rather than breaking the screen.
2. **OpenSubtitles XML-RPC** (``SearchSubtitles``) — an official API that also supports
   full-text matching (``MatchedBy=fulltext``). Used as a fallback when Subzin is down.
   Only the search is called; anonymous downloads return a placeholder file, so actual
   subtitle fetching stays with the authenticated OpenSubtitles.com v1 client.

Subtitle *download* is deliberately left to ``OpenSubtitlesClient``: it already handles
the API key and quota, and it is the only path with a documented licence position.
"""

import logging
from typing import Any, Dict, List, Optional

import httpx

logger = logging.getLogger(__name__)

SUBZIN_SEARCH = "https://www.subzin.com/api/search"
OPENSUBTITLES_XMLRPC = "https://api.opensubtitles.org/xml-rpc"

_UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
_TIMEOUT = 12.0


class QuoteSearchResult(Dict[str, Any]):
    """A movie that says the searched line, with the matching quotes."""


async def search_by_quote(query: str, limit: int = 10) -> List[Dict[str, Any]]:
    """Find movies whose dialogue contains ``query``.

    Returns ``[{imdb_id, title, year, slug, quotes: [{text, time_ms}], score}]``.
    An empty list means "nothing found or the source is unavailable" — never raises.
    """
    query = (query or "").strip()
    if not query:
        return []

    results = await _search_subzin(query, limit)
    if results:
        return results

    logger.info("Subzin returned nothing for %r, falling back to OpenSubtitles XML-RPC", query)
    return await _search_opensubtitles_fulltext(query, limit)


async def _search_subzin(query: str, limit: int) -> List[Dict[str, Any]]:
    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT, headers={"User-Agent": _UA}) as client:
            res = await client.get(SUBZIN_SEARCH, params={"q": query})
            if res.status_code != 200:
                logger.warning("Subzin returned %s", res.status_code)
                return []
            payload = res.json()
    except Exception as e:  # noqa: BLE001 - undocumented endpoint, must degrade quietly
        logger.warning("Subzin search failed: %s", e)
        return []

    out: List[Dict[str, Any]] = []
    for item in payload.get("results", [])[:limit]:
        movie = item.get("movie") or {}
        quotes = [
            {"text": q.get("text", "").strip(), "time_ms": _seconds_to_ms(q.get("time"))}
            for q in (item.get("quoteMatches") or [])[:3]
            if q.get("text")
        ]
        if not movie.get("id") or not quotes:
            continue
        out.append({
            "imdb_id": movie.get("id"),
            "title": movie.get("title", ""),
            "year": movie.get("year"),
            "slug": movie.get("slug", ""),
            "score": item.get("score"),
            "quotes": quotes,
            "source": "subzin",
        })
    return out


async def _search_opensubtitles_fulltext(query: str, limit: int) -> List[Dict[str, Any]]:
    """Full-text search through the OpenSubtitles XML-RPC API (no key required)."""
    login_body = (
        '<?xml version="1.0"?><methodCall><methodName>LogIn</methodName><params>'
        '<param><value><string></string></value></param>'
        '<param><value><string></string></value></param>'
        '<param><value><string>en</string></value></param>'
        '<param><value><string>CineEnglish/1.0</string></value></param>'
        '</params></methodCall>'
    )
    try:
        async with httpx.AsyncClient(timeout=_TIMEOUT, headers={"User-Agent": _UA}) as client:
            res = await client.post(OPENSUBTITLES_XMLRPC, content=login_body,
                                    headers={"Content-Type": "text/xml"})
            token = _extract_xml_value(res.text, "token")
            if not token:
                logger.warning("OpenSubtitles XML-RPC login returned no token")
                return []

            search_body = (
                '<?xml version="1.0"?><methodCall><methodName>SearchSubtitles</methodName><params>'
                f'<param><value><string>{token}</string></value></param>'
                '<param><value><array><data><value><struct>'
                f'<member><name>query</name><value><string>{_xml_escape(query)}</string></value></member>'
                '<member><name>sublanguageid</name><value><string>eng</string></value></member>'
                '</struct></value></data></array></value></param>'
                '</params></methodCall>'
            )
            res = await client.post(OPENSUBTITLES_XMLRPC, content=search_body,
                                    headers={"Content-Type": "text/xml"})
    except Exception as e:  # noqa: BLE001
        logger.warning("OpenSubtitles XML-RPC search failed: %s", e)
        return []

    # The XML-RPC response is a nested XML document; pulling the few fields we need with
    # string scans avoids adding an XML-RPC dependency for one fallback call.
    out: List[Dict[str, Any]] = []
    seen = set()
    for block in res.text.split("<struct>")[1:]:
        movie_name = _extract_xml_value(block, "MovieName")
        imdb_id = _extract_xml_value(block, "IDMovieImdb")
        if not movie_name or not imdb_id or imdb_id in seen:
            continue
        seen.add(imdb_id)
        year = _extract_xml_value(block, "MovieYear")
        out.append({
            "imdb_id": f"tt{imdb_id}",
            "title": movie_name,
            "year": int(year) if year.isdigit() else None,
            "slug": "",
            "score": None,
            "quotes": [],
            "source": "opensubtitles_xmlrpc",
        })
        if len(out) >= limit:
            break
    return out


def _seconds_to_ms(value) -> Optional[int]:
    try:
        return int(float(value) * 1000)
    except (TypeError, ValueError):
        return None


def _xml_escape(text: str) -> str:
    return (text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace('"', "&quot;"))


def _extract_xml_value(xml: str, field: str) -> str:
    """Pull ``<name>field</name>...<value><string>X</string>`` out of an XML-RPC payload."""
    marker = f"<name>{field}</name>"
    idx = xml.find(marker)
    if idx == -1:
        return ""
    tail = xml[idx + len(marker):]
    start = tail.find("<string>")
    if start == -1:
        return ""
    start += len("<string>")
    end = tail.find("</string>", start)
    return tail[start:end].strip() if end != -1 else ""
