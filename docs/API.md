# CineEnglish 后端 RESTful API 文档

基础路径：`/api/v1`

---

## 1. 认证模块 (Authentication)

### 注册账号
- **POST** `/api/v1/auth/register`
- 请求体：
  ```json
  {
    "username": "user1",
    "password": "mypassword123"
  }
  ```

### 获取 Token (OAuth2 标准)
- **POST** `/api/v1/auth/token`
- Content-Type: `application/x-www-form-urlencoded`
- 响应：
  ```json
  {
    "access_token": "eyJhbGciOi...",
    "token_type": "bearer",
    "user_id": 1,
    "username": "user1"
  }
  ```

---

## 2. 素材与台词时间轴 (Materials & Sentences)

### 获取素材库列表
- **GET** `/api/v1/materials`

### 创建素材
- **POST** `/api/v1/materials`
- 请求体：
  ```json
  {
    "title": "Friends S01E01",
    "year": 1994,
    "season": 1,
    "episode": 1,
    "media_type": "tv_show",
    "release_version": "720p.WEB-DL",
    "subtitle_source": "opensubtitles"
  }
  ```

### 获取某作品的所有台词句子
- **GET** `/api/v1/materials/{material_id}/sentences`

### 调整整体时间轴偏移
- **POST** `/api/v1/materials/{material_id}/sentences/shift-time`
- 表单参数：
  - `offset_ms`: 毫秒偏移量（正数延后，负数提前）

---

## 3. 字幕搜索与导入 (Subtitles)

### 搜索 OpenSubtitles 官方英文字幕
- **GET** `/api/v1/subtitles/search?query=Inception&year=2010`
- 仅返回英文（en）字幕，包含版本、格式、听障标识 (HI) 与评分。

### 在线下载并导入为素材
- **POST** `/api/v1/subtitles/download-and-import`
- 请求体：
  ```json
  {
    "subtitle_id": "19543821",
    "movie_name": "Inception",
    "release": "BDRip.x264"
  }
  ```

### 上传本地字幕文件解析
- **POST** `/api/v1/subtitles/import-file`
- Content-Type: `multipart/form-data`
- 支持格式：`.srt`, `.vtt`, `.ass`, `.txt`
- 服务端自动清洗样式代码、听障噪音标记，并合并断行碎句。

---

## 4. 发音评分与复述评估 (Pronunciation Assessment)

### 句子发音打分 (Acoustic Evaluation)
- **POST** `/api/v1/assessment/evaluate-sentence`
- Content-Type: `multipart/form-data`
- 参数：
  - `sentence_id`: 句子 ID
  - `attempt_count`: 当前尝试次数
  - `pass_threshold_overall`: 通过门槛总分 (如 80.0)
  - `pass_threshold_completeness`: 完整度门槛 (如 90.0)
  - `audio_file`: 录音文件 (WAV/M4A/AAC)
- 响应：
  ```json
  {
    "engine_name": "CineAcousticEngine",
    "engine_version": "1.2.0",
    "assessment_tier": "phoneme_acoustic",
    "overall_score": 86.5,
    "accuracy_score": 88.0,
    "completeness_score": 95.0,
    "fluency_score": 82.0,
    "prosody_score": 80.0,
    "is_passed": true,
    "word_details": [
      {
        "word": "consequence",
        "score": 74.0,
        "is_problematic": true,
        "problem_type": "mispronounced",
        "start_ms": 350,
        "end_ms": 700
      }
    ],
    "feedback_en": "Focus on the sound in 'consequence'—keep the articulation clean and clear.",
    "confidence_evidence": "Acoustic duration profile, energy peaks & syllable boundary alignment.",
    "can_auto_advance": true
  }
  ```

### 自由复述语义评估 (Free Recall Evaluation)
- **POST** `/api/v1/assessment/evaluate-free-recall`
- 请求体：
  ```json
  {
    "original_sentence": "I have to leave now before it gets too dark.",
    "spoken_transcript": "I need to take off immediately since night is falling.",
    "user_audio_duration_ms": 3200
  }
  ```
- 响应：包含含义保留度、语法自然度评分与建议，不强制逐词死板匹配。

---

## 5. 录音持久化存储与学习数据 (Recordings & Stats)

### 上传并持久化用户练习录音
- **POST** `/api/v1/recordings/upload`
- Content-Type: `multipart/form-data`
- 服务端保存音频到持久化目录，生成记录，并在表现薄弱时自动录入艾宾浩斯复习表。

### 导出全部学习数据与录音列表
- **GET** `/api/v1/recordings/export`

---

## 6. 纯英词典与美式示范 (Dictionary & AI)

### 纯英词典查询
- **GET** `/api/v1/dictionary/lookup?word=hesitate&simplify_level=1`
- 仅提供美式音标、纯英释义、例句与简明版解释，无中文整句翻译。

### 美式参考声音合成与文件缓存
- **GET** `/api/v1/ai/synthesize-american-voice?text=Hello+world&voice=alloy&speed=1.0`
- 按参数哈希缓存，避免重复计费，支持离线循环播放。
