import os
import time
import random
import tempfile
import shutil
from urllib.parse import urlparse
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from html.parser import HTMLParser
import json
import re
from datetime import date, time as datetime_time
from types import SimpleNamespace

from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from pydantic import BaseModel
from google import genai
from dotenv import load_dotenv


# ============================================================
# LOAD ENVIRONMENT VARIABLES
# ============================================================

load_dotenv()


# ============================================================
# FASTAPI APP
# ============================================================

app = FastAPI(
    title="Signal AI Backend",
    version="2.2.0"
)

GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
CANDIDATE_MODELS = list(dict.fromkeys([
    "gemini-3.5-flash-lite",
    "gemini-3.5-flash",
    GEMINI_MODEL,
]))


# ============================================================
# REQUEST / RESPONSE MODELS
# ============================================================

class AnalyzeRequest(BaseModel):
    content: str
    capture_date: str | None = None


class SocialAnalyzeRequest(BaseModel):
    url: str
    capture_date: str | None = None
    shared_text: str | None = None


class AnalyzeResponse(BaseModel):
    title: str
    summary: str
    keywords: str
    category: str
    target_location: str
    target_latitude: float | None = None
    target_longitude: float | None = None
    reminder_date: str | None = None
    reminder_time: str | None = None
    detected_url: str | None = None


# ============================================================
# GEMINI CLIENT
# ============================================================

def get_gemini_client():

    api_key = os.getenv("GEMINI_API_KEY")

    if not api_key:
        raise Exception(
            "GEMINI_API_KEY is missing from .env"
        )

    return genai.Client(
        api_key=api_key
    )


# ============================================================
# ROOT
# ============================================================

@app.get("/")
def root():

    return {
        "status": "Signal backend is running",
        "version": "2.2.0"
    }


# ============================================================
# PARSE AI RESPONSE
# ============================================================

def parse_response(text: str) -> AnalyzeResponse:

    def extract(label: str) -> str:

        marker = f"{label}:"

        start = text.find(marker)

        if start == -1:
            return ""

        value_start = start + len(marker)

        next_line = text.find(
            "\n",
            value_start
        )

        if next_line == -1:

            return (
                text[value_start:]
                .strip()
            )

        return (
            text[
                value_start:next_line
            ]
            .strip()
        )


    try:
        reminder_date = date.fromisoformat(extract("REMINDER_DATE")).isoformat()
    except (TypeError, ValueError):
        reminder_date = None

    try:
        reminder_time = datetime_time.fromisoformat(extract("REMINDER_TIME")).strftime("%H:%M")
    except (TypeError, ValueError):
        reminder_time = None

    detected_url = extract("DETECTED_URL")
    if detected_url and not re.match(r"^https?://\S+$", detected_url, re.I):
        detected_url = None

    return AnalyzeResponse(

        title=(
                extract("TITLE")
                or "Saved memory"
        ),

        summary=(
                extract("SUMMARY")
                or "Content saved in Signal."
        ),

        keywords=(
                extract("KEYWORDS")
                or "saved content"
        ),

        category=(
                extract("CATEGORY")
                or "General"
        ),

        target_location=(
                extract("TARGET_LOCATION")
                or "NONE"
        ),
        target_latitude=parse_coordinate(text, "TARGET_LATITUDE"),
        target_longitude=parse_coordinate(text, "TARGET_LONGITUDE"),
        reminder_date=reminder_date,
        reminder_time=reminder_time,
        detected_url=detected_url,
    )


def parse_coordinate(text: str, label: str) -> float | None:
    value = text.partition(f"{label}:")[2].partition("\n")[0].strip()
    try:
        coordinate = float(value)
        limit = 90 if "LATITUDE" in label.upper() else 180
        return coordinate if -limit <= coordinate <= limit else None
    except (TypeError, ValueError):
        return None


# ============================================================
# CHECK TEMPORARY GEMINI ERROR
# ============================================================

def is_retryable_error(error: Exception) -> bool:

    error_text = str(error).upper()

    retryable_codes = [

        "503",
        "UNAVAILABLE",

        "429",
        "RESOURCE_EXHAUSTED",

        "500",
        "INTERNAL",

        "502",
        "BAD GATEWAY",

        "504",
        "DEADLINE_EXCEEDED",

        "TIMEOUT"
    ]

    return any(
        code in error_text
        for code in retryable_codes
    )


def _heuristic_fallback(raw_content: str) -> AnalyzeResponse:
    """Keep a useful memory and deterministic Pune geofence point during outages."""
    cleaned = (raw_content or "").strip()
    title = cleaned[:72].strip() or "Saved memory"
    if title.startswith("http"):
        title = "Saved YouTube memory" if is_youtube_url(title) else "Saved memory"
    return AnalyzeResponse(
        title=title,
        summary="Saved for later. AI enrichment is temporarily unavailable.",
        keywords="saved content, offline fallback",
        category="PLACE",
        target_location="Pune fallback location",
        target_latitude=18.5362,
        target_longitude=73.8940,
    )


# ============================================================
# GEMINI GENERATION WITH RETRIES + MODEL FALLBACK
# ============================================================

def generate_with_retry(
        client,
        contents,
        purpose: str = "content",
        config=None,
        models=None,
):

    models = models or CANDIDATE_MODELS


    last_error = None


    for model in models:

        print()
        print("=" * 60)
        print(
            f"TRYING MODEL: {model}"
        )
        print(
            f"PURPOSE: {purpose}"
        )
        print("=" * 60)


        for attempt in range(4):

            try:

                print()

                print(
                    f"Attempt "
                    f"{attempt + 1}/4 "
                    f"using {model}"
                )


                request_options = {
                    "model": model,
                    "contents": contents,
                }
                if config is not None:
                    request_options["config"] = config
                response = client.models.generate_content(**request_options)


                print()
                print("=" * 60)
                print(
                    f"SUCCESS WITH MODEL: {model}"
                )
                print("=" * 60)


                return response


            except Exception as e:

                last_error = e


                print()

                print(
                    "GEMINI ERROR:"
                )

                print(
                    str(e)
                )


                if not is_retryable_error(e):

                    print()

                    print(
                        "This does not appear to be "
                        "a temporary Gemini error."
                    )

                    print(
                        "Moving to next model..."
                    )

                    break


                if attempt < 3:

                    delay = (

                            (2 ** (attempt + 1))

                            +

                            random.uniform(
                                0,
                                1.5
                            )
                    )


                    print()

                    print(
                        "Temporary Gemini error detected."
                    )

                    print(
                        f"Waiting {delay:.1f} seconds "
                        "before retry..."
                    )


                    time.sleep(
                        delay
                    )


        print()

        print(
            f"MODEL FAILED: {model}"
        )

        print(
            "TRYING NEXT MODEL..."
        )


    raise Exception(

        "All Gemini models failed. "

        f"Last error: {last_error}"
    )


# ============================================================
# NORMAL TEXT / URL ANALYSIS
# ============================================================

@app.post(
    "/analyze",
    response_model=AnalyzeResponse
)
def analyze_content(
        request: AnalyzeRequest
):

    try:

        client = get_gemini_client()

        youtube_url = extract_youtube_url(request.content)
        if youtube_url:
            response = analyze_youtube_url(
                client,
                youtube_url,
                context=f"{request.content}\nUser's local capture date: {request.capture_date or date.today().isoformat()}",
            )
            return parse_response(response.text or "")


        prompt = f"""
You are Signal, an AI personal memory assistant.

Analyze the following saved content.

Return exactly in this format:

TITLE:
SUMMARY:
KEYWORDS:
CATEGORY:
TARGET_LOCATION:
REMINDER_DATE:
REMINDER_TIME:
DETECTED_URL:

Rules:

- TITLE: short and useful
- SUMMARY: maximum 2 sentences
- KEYWORDS: 5 to 10 useful keywords separated by commas
- CATEGORY: choose one useful category
- TARGET_LOCATION: identify a real-world place only if clearly mentioned
- If category is PLACE or mentions a specific physical venue/landmark/cafe/restaurant, you MUST populate target_place with clean name, accurate float latitude, and accurate float longitude from world knowledge.
- Return the place name in TARGET_LOCATION and its coordinates as numeric TARGET_LATITUDE and TARGET_LONGITUDE lines. Leave coordinates blank when no specific physical place is supported.
- Return the venue name in TARGET_LOCATION and coordinates in TARGET_LATITUDE and TARGET_LONGITUDE. If there is no specific place, use NONE and leave both coordinates empty.
- If there is no location, write NONE
- REMINDER_DATE: return an explicit future event, appointment, trip, release, or deadline date as YYYY-MM-DD. Resolve relative dates using the supplied user's local capture date. Use NONE when no actionable future date is clearly present; never schedule a historical date or the content's publication date. REMINDER_TIME: explicit local event time as 24-hour HH:MM when stated, otherwise NONE. Do not infer a time.
- DETECTED_URL: return an external website or booking URL explicitly included in the content, excluding the shared social/video URL itself. Otherwise write NONE.

Saved content:

{request.content}

User's local capture date: {request.capture_date or date.today().isoformat()}
"""


        response = generate_with_retry(

            client=client,

            contents=prompt,

            purpose="normal content analysis"
        )


        text = (

                response.text

                or ""
        )


        print()

        print(
            "NORMAL ANALYSIS RESULT:"
        )

        print(
            text
        )


        return parse_response(
            text
        )


    except Exception as e:

        print()

        print(
            "NORMAL ANALYSIS ERROR:"
        )

        print(
            str(e)
        )


        if is_retryable_error(e):
            return _heuristic_fallback(request.content)

        raise HTTPException(

            status_code=500,

            detail=str(e)
        )


# ============================================================
# ANALYZE UPLOADED VIDEO
# ============================================================

@app.post(
    "/analyze-video",
    response_model=AnalyzeResponse
)
async def analyze_video(
        file: UploadFile = File(...)
):

    temp_dir = None

    uploaded_file = None


    try:

        print()
        print("=" * 60)
        print(
            "VIDEO ANALYSIS STARTED"
        )
        print("=" * 60)


        client = get_gemini_client()


        temp_dir = tempfile.mkdtemp(
            prefix="signal_video_"
        )


        file_path = os.path.join(

            temp_dir,

            file.filename
            or "video.mp4"
        )


        with open(
                file_path,
                "wb"
        ) as output:

            while True:

                chunk = await file.read(
                    1024 * 1024
                )

                if not chunk:

                    break

                output.write(
                    chunk
                )


        print(
            f"Video saved: {file_path}"
        )


        print(
            "Uploading video to Gemini..."
        )


        uploaded_file = (

            client.files.upload(

                file=file_path
            )
        )


        max_processing_seconds = 600

        processing_start = time.time()


        while True:

            file_info = client.files.get(

                name=uploaded_file.name
            )


            state = (

                getattr(
                    file_info.state,
                    "name",
                    str(file_info.state)
                )
            )


            print(
                f"Gemini file state: {state}"
            )


            if state == "ACTIVE":

                uploaded_file = (
                    file_info
                )

                break


            if state == "FAILED":

                raise Exception(
                    "Gemini failed to process video."
                )


            elapsed = (

                    time.time()

                    -

                    processing_start
            )


            if elapsed > max_processing_seconds:

                raise Exception(
                    "Gemini video processing "
                    "timed out after 10 minutes."
                )


            print(
                "Gemini is processing video..."
            )


            time.sleep(
                5
            )


        prompt = f"""
You are Signal, an AI personal memory assistant.

Analyze this video carefully.

Use:

- visual content
- spoken audio
- on-screen text
- captions if visible
- signs
- dates
- locations
- names
- events

Return exactly in this format:

TITLE:
SUMMARY:
KEYWORDS:
CATEGORY:
TARGET_LOCATION:
REMINDER_DATE:
REMINDER_TIME:
DETECTED_URL:

Rules:

- TITLE: short and useful
- SUMMARY: maximum 2 sentences
- KEYWORDS: 5 to 10 useful keywords separated by commas
- CATEGORY: choose the most useful category
- TARGET_LOCATION: identify a real-world location only if clearly supported
- If no location is clearly present, write NONE
- Do not invent dates, locations, names, or events
- REMINDER_DATE: return an explicit future event, appointment, trip, release, or deadline date as YYYY-MM-DD; use NONE when no actionable future date is clearly present. Do not use the video publication date. REMINDER_TIME: explicit local event time as 24-hour HH:MM when stated, otherwise NONE. Do not infer a time.
- DETECTED_URL: return a full website URL visibly shown or spoken in the video only when clearly legible; otherwise write NONE.
Today's date is {date.today().isoformat()}.
"""


        print(
            "Analyzing video with Gemini..."
        )


        response = generate_with_retry(

            client=client,

            contents=[

                uploaded_file,

                prompt
            ],

            purpose="uploaded video analysis"
        )


        text = (

                response.text

                or ""
        )


        print()

        print(
            "VIDEO ANALYSIS RESULT:"
        )

        print()

        print(
            text
        )


        return parse_response(
            text
        )


    except Exception as e:

        print()

        print(
            "VIDEO ANALYSIS ERROR:"
        )

        print(
            str(e)
        )


        if is_retryable_error(e):
            return _heuristic_fallback(file.filename or "Uploaded video")

        raise HTTPException(

            status_code=500,

            detail=str(e)
        )


    finally:

        try:

            if uploaded_file:

                client.files.delete(

                    name=uploaded_file.name
                )

        except Exception:

            pass


        if temp_dir:

            try:

                shutil.rmtree(

                    temp_dir,

                    ignore_errors=True
                )

            except Exception:

                pass


# ============================================================
# DETECT YOUTUBE URL
# ============================================================

def is_youtube_url(
        url: str
) -> bool:
    hostname = urlparse(url.strip()).hostname or ""
    hostname = hostname.lower().removeprefix("www.")
    return hostname == "youtu.be" or hostname == "youtube.com" or hostname.endswith(".youtube.com")


def extract_youtube_url(raw_content: str) -> str | None:
    for candidate in re.findall(r"https?://[^\s<>\"']+", raw_content or ""):
        candidate = candidate.rstrip(".,);]}")
        parsed = urlparse(candidate)
        if is_youtube_url(candidate) and (
            parsed.hostname == "youtu.be"
            or parsed.path.startswith("/watch")
            or parsed.path.startswith("/shorts/")
            or parsed.path.startswith("/live/")
            or parsed.path.startswith("/embed/")
        ):
            return candidate
    return None


class _MetaParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.values = {}

    def handle_starttag(self, tag, attrs):
        if tag.lower() != "meta":
            return
        attrs = dict(attrs)
        key = attrs.get("property") or attrs.get("name")
        if key in {"og:title", "og:description", "description"}:
            self.values[key] = attrs.get("content", "")


class URLUnfurler:
    """Retrieve lightweight oEmbed/OpenGraph metadata without downloading media."""

    def unfurl(self, url: str) -> str:
        metadata = []
        oembed_url = "https://www.youtube.com/oembed?" + urlencode({"url": url, "format": "json"})
        try:
            request = Request(oembed_url, headers={"User-Agent": "Mozilla/5.0 (compatible; Echo/1.0)"})
            with urlopen(request, timeout=6) as response:
                data = json.loads(response.read(128_000).decode("utf-8", errors="replace"))
            metadata.extend([f"Video title: {data.get('title', '')}", f"Video author: {data.get('author_name', '')}"])
        except Exception:
            pass
        try:
            request = Request(url, headers={"User-Agent": "Mozilla/5.0 (compatible; Echo/1.0)"})
            with urlopen(request, timeout=8) as response:
                parser = _MetaParser()
                parser.feed(response.read(512_000).decode("utf-8", errors="replace"))
            metadata.extend([
                f"Page title: {parser.values.get('og:title', '')}",
                f"Page description: {parser.values.get('og:description', parser.values.get('description', ''))}",
            ])
        except Exception:
            pass
        metadata.append(f"Video URL: {url}")
        return "\n".join(line for line in metadata if line.split(": ", 1)[-1].strip())


def analyze_youtube_url(client, url: str, context: str = ""):
    from google.genai import types

    is_community_post = urlparse(url).path.startswith("/post/")
    if is_community_post:
        canonical_url = f"https://www.youtube.com{urlparse(url).path}"
        prompt = f"""You are Echo, an AI personal memory assistant. This is a public YouTube Community post. Use URL Context to inspect the post image and read any text printed inside it. Report the post's actual message; never describe it merely as a post, image, or page. If the image contains a question, quote that question in the summary and make the title reflect it. If retrieval genuinely fails, say so explicitly instead of inventing generic details. Return exactly in this format:
TITLE:\nSUMMARY:\nKEYWORDS:\nCATEGORY:\nTARGET_LOCATION:\nTARGET_LATITUDE:\nTARGET_LONGITUDE:
REMINDER_DATE:\nREMINDER_TIME:\nDETECTED_URL:
Rules: concise title; summary at most 2 sentences; 5-10 comma-separated keywords. If category is PLACE or mentions a specific physical venue/landmark/cafe/restaurant, you MUST populate target_place with clean name, accurate float latitude, and accurate float longitude from world knowledge. Put place name in TARGET_LOCATION and coordinates in the two coordinate fields; otherwise use NONE and leave coordinates blank. REMINDER_DATE is an explicit future actionable event date in YYYY-MM-DD format or NONE. DETECTED_URL is a clearly present full URL or NONE.
URL: {canonical_url}
User-provided context: {context}"""
        try:
            response = generate_with_retry(
                client,
                prompt,
                purpose="YouTube Community post URL-context analysis",
                config=types.GenerateContentConfig(tools=[{"url_context": {}}]),
                # URL Context is much more reliable on the stronger vision-capable
                # model for image-based Community posts than the lightweight default.
                models=list(dict.fromkeys(["gemini-2.5-flash", *CANDIDATE_MODELS])),
            )
        except Exception as post_error:
            print(f"Community post URL context failed; using metadata fallback: {post_error}")
            fallback_prompt = prompt + "\nAvailable page metadata:\n" + URLUnfurler().unfurl(url)
            response = generate_with_retry(client, fallback_prompt, purpose="YouTube Community post metadata fallback")
    else:
        prompt = f"""You are Echo, an AI personal memory assistant. Analyze this YouTube video using its audiovisual content. Return exactly in this format:
TITLE:\nSUMMARY:\nKEYWORDS:\nCATEGORY:\nTARGET_LOCATION:\nTARGET_LATITUDE:\nTARGET_LONGITUDE:\nREMINDER_DATE:\nREMINDER_TIME:\nDETECTED_URL:
Rules: concise title; summary at most 2 sentences; 5-10 comma-separated keywords. Use the video's content and user-provided context. If category is PLACE or mentions a specific physical venue/landmark/cafe/restaurant, you MUST populate target_place with clean name, accurate float latitude, and accurate float longitude from world knowledge. Put place name in TARGET_LOCATION and coordinates in the two coordinate fields; otherwise use NONE and leave coordinates blank. Do not invent places. REMINDER_DATE is an explicit future actionable event date in YYYY-MM-DD format or NONE. DETECTED_URL is a clearly present full URL or NONE. Today's date is {date.today().isoformat()}.

User-provided context: {context}"""
        try:
            contents = [types.Part.from_uri(file_uri=url, mime_type="video/*"), prompt]
            response = generate_with_retry(client, contents, purpose="direct YouTube URL analysis")
        except Exception as stream_error:
            print(f"Direct YouTube analysis failed; using page metadata: {stream_error}")
            fallback_prompt = prompt + "\nAnalyze this available title/description metadata as a fallback; do not claim audiovisual details not present.\n" + URLUnfurler().unfurl(url)
            response = generate_with_retry(client, fallback_prompt, purpose="YouTube metadata fallback")

    # A video can be unrelated to a place explicitly named in the shared text.
    # Resolve that user-provided venue independently so video content cannot
    # drown out a valid location hint.
    if context and re.search(r"\b(cafe|caf[eé]|bakery|restaurant|hotel|landmark|temple|museum|park|venue)\b", context, re.I):
        place_prompt = f"""Find the specific real-world venue named in this user-provided text and return only these fields:
TARGET_LOCATION:
TARGET_LATITUDE:
TARGET_LONGITUDE:
Use the clean venue name and accurate numeric coordinates from world knowledge. If no specific venue is identifiable, use NONE and leave coordinates blank. Context: {context}"""
        try:
            place_response = generate_with_retry(client, place_prompt, purpose="shared text location extraction")
            place = parse_response(place_response.text or "")
            if place.target_location != "NONE" and place.target_latitude is not None and place.target_longitude is not None:
                combined = response.text or ""
                for label, value in (
                    ("TARGET_LOCATION", place.target_location),
                    ("TARGET_LATITUDE", str(place.target_latitude)),
                    ("TARGET_LONGITUDE", str(place.target_longitude)),
                ):
                    pattern = rf"(?m)^{label}:.*$"
                    if re.search(pattern, combined):
                        combined = re.sub(pattern, f"{label}: {value}", combined)
                    else:
                        combined += f"\n{label}: {value}"
                response = SimpleNamespace(text=combined)
        except Exception as location_error:
            print(f"Shared-text location extraction failed: {location_error}")
    return response


@app.post("/analyze-social", response_model=AnalyzeResponse)
def analyze_social(request: SocialAnalyzeRequest):
    try:
        client = get_gemini_client()
        if is_youtube_url(request.url):
            response = analyze_youtube_url(
                client,
                request.url,
                context=f"{request.shared_text or ''}\nUser's local capture date: {request.capture_date or date.today().isoformat()}",
            )
        else:
            metadata = URLUnfurler().unfurl(request.url)
            prompt = f"""You are Echo, an AI personal memory assistant. Analyze this saved URL and any accessible public page content using the metadata and shared text below. Do not invent details if the page cannot be retrieved.
Return exactly:
TITLE:
SUMMARY:
KEYWORDS:
CATEGORY:
TARGET_LOCATION:
TARGET_LATITUDE:
TARGET_LONGITUDE:
REMINDER_DATE:
REMINDER_TIME:
DETECTED_URL:
Keep the summary to two sentences and use 5-10 comma-separated keywords. Do not invent details absent from the page or metadata. If category is PLACE or mentions a specific physical venue/landmark/cafe/restaurant, you MUST populate target_place with clean name, accurate float latitude, and accurate float longitude from world knowledge. Put its name in TARGET_LOCATION and numeric coordinates in TARGET_LATITUDE / TARGET_LONGITUDE. Otherwise use NONE and leave coordinates blank. REMINDER_DATE is an explicit future actionable event date in YYYY-MM-DD format or NONE. DETECTED_URL is an external website/booking URL explicitly contained in the post, excluding the shared social/video URL, or NONE. User's local capture date: {request.capture_date or date.today().isoformat()}.

{metadata}"""
            prompt += f"\nShared text or caption: {request.shared_text or '(none)'}"
            try:
                from google.genai import types
                response = generate_with_retry(
                    client,
                    prompt,
                    purpose="social URL context analysis",
                    config=types.GenerateContentConfig(tools=[{"url_context": {}}]),
                    models=list(dict.fromkeys(["gemini-2.5-flash", *CANDIDATE_MODELS])),
                )
            except Exception as retrieval_error:
                print(f"Social URL context unavailable; using shared text and page metadata: {retrieval_error}")
                response = generate_with_retry(client, prompt, purpose="social URL metadata analysis")
        return parse_response(response.text or "")
    except Exception as error:
        if is_retryable_error(error):
            return _heuristic_fallback(request.url)
        raise HTTPException(status_code=500, detail=str(error))


@app.post("/analyze-image", response_model=AnalyzeResponse)
async def analyze_image(
        file: UploadFile = File(...),
        ocr_text: str = Form(""),
        capture_date: str = Form(""),
):
    """Use Gemini vision together with on-device OCR for shared photos."""
    try:
        from google.genai import types

        image_bytes = await file.read()
        if not image_bytes:
            raise HTTPException(status_code=400, detail="Image is empty")
        if len(image_bytes) > 20 * 1024 * 1024:
            raise HTTPException(status_code=413, detail="Image exceeds 20 MB")

        image_part = types.Part.from_bytes(
            data=image_bytes,
            mime_type=file.content_type or "image/jpeg",
        )
        prompt = f"""You are Signal, an AI personal memory assistant. Analyze this image visually and use on-device OCR text as supporting evidence. Correct OCR errors only when the image makes the intended text clear. Read complex layouts, event posters, tickets, screenshots, dates, and printed website links. Never invent unreadable text.

Return exactly:
TITLE:
SUMMARY:
KEYWORDS:
CATEGORY:
TARGET_LOCATION:
TARGET_LATITUDE:
TARGET_LONGITUDE:
REMINDER_DATE:
REMINDER_TIME:
DETECTED_URL:

Use a concise title, a summary of at most two sentences, and 5-10 comma-separated keywords. Return an explicit future event, appointment, trip, release, or deadline date as YYYY-MM-DD only when clearly present; otherwise REMINDER_DATE: NONE. Return an event time as 24-hour HH:MM only when explicitly stated; otherwise REMINDER_TIME: NONE. Do not treat a publication/capture date or historical date as a reminder. Return a full website URL only when legible in the image or OCR; otherwise DETECTED_URL: NONE. Return location coordinates only for a clearly identified real place; otherwise TARGET_LOCATION: NONE and leave coordinates blank. Today's date in the user's local timezone is {capture_date or date.today().isoformat()}.

On-device OCR text:
{ocr_text[:12000] or '(No text detected by OCR)'}"""
        response = generate_with_retry(
            client=get_gemini_client(),
            contents=[image_part, prompt],
            purpose="shared image analysis",
        )
        return parse_response(response.text or "")
    except HTTPException:
        raise
    except Exception as error:
        if is_retryable_error(error):
            return _heuristic_fallback(ocr_text or file.filename or "Saved image")
        raise HTTPException(status_code=500, detail=str(error))
