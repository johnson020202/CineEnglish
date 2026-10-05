# CineEnglish 后端自部署与运行指南

CineEnglish 后端基于 Python FastAPI + SQLite 异步架构设计，并通过 FFmpeg 进行音频切片、转码与质量检测。支持直接在宿主机运行或使用 Docker Compose 一键部署。

---

## 1. 架构与服务划分原则

在 CineEnglish 中，**自部署后端**与**外部 AI 服务**有严格的职责边界，不可混淆：

| 服务类别 | 作用范围 | 配置位置 | 典型地址 |
| :--- | :--- | :--- | :--- |
| **自部署后端 (Backend)** | 负责用户数据、练习记录、原始录音持久化保存、SRT/VTT字幕解析清洗、声学发音评分 | App 设置中的 `Backend Base URL` | `http://192.168.x.x:8000` 或域名 `https://api.yourdomain.com` |
| **AI 独立服务 (AI Service)** | 负责文本辅导、美式语音生成 (TTS)、语音转写与音频理解 | App 设置中的 `AI Base URL` & `AI API Key` | `https://api.openai.com/v1` 或自建兼容端点 |

---

## 2. Docker Compose 一键部署（推荐）

### 前置要求
- 已安装 Docker 与 Docker Compose
- 开放 8000 端口（或通过反向代理转发）

### 部署步骤
1. 进入 backend 目录：
   ```bash
   cd backend
   ```
2. 复制环境配置文件：
   ```bash
   cp .env.example .env
   # 按需编辑 .env 中的参数，例如 SECRET_KEY 与 OPENSUBTITLES_API_KEY
   ```
3. 启动容器：
   ```bash
   docker compose up -d --build
   ```
4. 查看服务状态与日志：
   ```bash
   docker compose ps
   docker compose logs -f cineenglish-backend
   ```
5. 打开浏览器访问健康检查：
   - API 欢迎页：`http://localhost:8000/`
   - Swagger 交互文档：`http://localhost:8000/docs`

---

## 3. 宿主机直接运行（开发与调试）

### 环境要求
- Python 3.9+
- 系统已安装 FFmpeg（用于音频处理与体积格式探测）

### 启动命令
```bash
cd backend

# 创建虚拟环境
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 启动服务
export PYTHONPATH=.
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

---

## 4. HTTPS 反向代理配置（Nginx 示例）

Android 9.0+ 默认推荐使用加密传输。如果你在公网或云服务器部署，可使用 Nginx 配置 HTTPS：

```nginx
server {
    listen 80;
    server_name cine.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name cine.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/cine.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/cine.yourdomain.com/privkey.pem;

    client_max_body_size 100M; # 允许大音频和视频上传

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 5. 数据持久化、备份与迁移

CineEnglish 后端的所有数据均保存在 `data/` 目录（在 Docker 中挂载为卷 `cine_data`）：
- `data/cineenglish.db`：SQLite 主数据库（包含用户、影视素材、句子时间轴、评分记录、生词与复习进度）
- `data/recordings/`：用户历次口语跟读的高保真录音文件
- `data/subtitles/`：下载与导入的原始字幕文件
- `data/media/`：关联的影视视频与音频切片
- `data/tts_cache/`：美式示范发音缓存（按参数哈希命中，节省 API 计费）

### 备份命令
```bash
# 备份全部数据与录音
tar -czvf cineenglish_backup_$(date +%Y%m%d).tar.gz data/
```

### 恢复命令
```bash
# 恢复备份
tar -xzvf cineenglish_backup_20261002.tar.gz
docker compose restart
```
