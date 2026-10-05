# CineEnglish 依赖与开源许可证清单

本项目遵循开源规范，使用的第三方库与组件均遵循对应开源许可证。

---

## 1. Android 端核心依赖

| 组件库 | 版本 | 许可证 | 作用说明 |
| :--- | :--- | :--- | :--- |
| **Jetpack Compose** (BOM) | 2024.05.00 | Apache-2.0 | 现代响应式声明式原生 UI 工具包 |
| **Material 3** | 1.2.1 | Apache-2.0 | Google 官方 Material Design 3 视觉系统 |
| **Navigation Compose** | 2.7.7 | Apache-2.0 | 原生应用路由与导航状态管理 |
| **AndroidX Room** | 2.6.1 | Apache-2.0 | 本地 SQLite 抽象持久层与 ORM 映射 |
| **DataStore Preferences** | 1.1.1 | Apache-2.0 | 异步响应式轻量设置持久化存储 |
| **AndroidX Media3 (ExoPlayer)** | 1.3.1 | Apache-2.0 | 视频时间线播放、音频变速率音高保真播放 |
| **WorkManager** | 2.9.0 | Apache-2.0 | 离线录音队列持久化后台同步与退避重试 |
| **Security Crypto (Keystore)** | 1.1.0-alpha06 | Apache-2.0 | Android Keystore 硬件级 AES-GCM 密钥保护 |
| **Retrofit / OkHttp** | 2.11.0 / 4.12.0 | Apache-2.0 | 网络 HTTP 请求客户端与拦截器 |
| **Kotlin Coroutines** | 1.8.1 | Apache-2.0 | 结构化异步并发调度与 StateFlow |

---

## 2. 后端服务端核心依赖

| 组件库 | 版本 | 许可证 | 作用说明 |
| :--- | :--- | :--- | :--- |
| **FastAPI** | >=0.110.0 | MIT | 高性能现代 Python 异步 Web 框架 |
| **Uvicorn** | >=0.28.0 | BSD-3-Clause | 极速 ASGI 生产级服务器 |
| **SQLAlchemy** | >=2.0.28 | MIT | 统一数据库 ORM 框架，支持 SQLite/PostgreSQL |
| **aiosqlite** | >=0.20.0 | MIT | 异步 SQLite 驱动程序 |
| **Pydantic** | >=2.6.0 | MIT | 数据结构与数据校验模式 |
| **FFmpeg** | 7.x / 9.x | LGPL / GPL | 音频格式标准化转码 (16kHz WAV)、切片与音量检测 |
| **PyJWT** | >=2.8.0 | MIT | 安全登录与私有令牌鉴权 |
| **Passlib / Bcrypt** | >=1.7.4 | BSD / Apache-2.0 | 用户凭据安全哈希加密 |
| **HTTPX** | >=0.27.0 | BSD-3-Clause | 异步调用 OpenSubtitles API 与外部 AI 端点 |

---

## 3. 开源参考项目评估

在开发前已核实以下开源项目的架构设计与许可证兼容性：
1. **OpenPronounce / GOPT**: 评估了声学发音评分与强制对齐规范。App 服务端提供透明分级体系（`phoneme_acoustic`、`text_matching` 与 `ai_reference_only`），绝不使用纯文本模型冒充听出发音错误。
2. **OpenSubtitles REST API v1**: 遵循官方接口协议，支持独立 API Key 配置与额度限制友好提示，支持本地文件降级导入。
3. **Enjoy / 1000h.org**: 借鉴了沉浸式原声跟读与遮字幕复述理念，坚持采用纯英文释义（English-English Dictionary）与简明版解释，避免中文干扰语感建立。
