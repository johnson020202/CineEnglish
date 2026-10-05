# 《肖申克的救赎》与《阿甘正传》口语练习专属台词包

本材料包专门针对**英语口语听力精练、原声跟读（Shadowing）、声学发音评分、遮字幕复述与角色对戏（Roleplay）**进行了全面清洗与格式化。

所有台词均对齐主流 **1080p / 720p 蓝光高清版本（23.976 fps）**，已彻底去除所有网页广告、压制组水印、环境音效杂音标签，并完成自然断句合并。

---

## 📁 文件清单与用途

### 1. 《肖申克的救赎》 (The Shawshank Redemption, 1994)
| 文件名 | 格式 | 句子/轮次 | 推荐适用场景 |
| :--- | :--- | :--- | :--- |
| `The_Shawshank_Redemption_1994_Clean.srt` | 标准 SRT | 1,686 条 | 配合电影视频文件播放、CineEnglish 视频时间线模式、PotPlayer/VLC 逐句精听与复读循环 |
| `The_Shawshank_Redemption_1994_Sentences.txt` | 带时间戳文本 | 1,653 句 | 智能合并被断行拆开的完整句子，适合逐句跟读、声学发音评分打分、句子速查 |
| `The_Shawshank_Redemption_1994_Plain.txt` | 纯英文行文本 | 1,653 行 | 无时间戳极简纯文本，适合直接导入 Anki 卡片、各类 TTS 朗读软件与生词高频分析 |
| `The_Shawshank_Redemption_1994_Roleplay.txt` | 角色剧本 | 799 轮对白 | 角色扮演对戏（Andy, Red, Warden, Hadley 等），适合 CineEnglish 对戏模式或喂给 AI 大模型模拟对话 |

### 2. 《阿甘正传》 (Forrest Gump, 1994)
| 文件名 | 格式 | 句子/轮次 | 推荐适用场景 |
| :--- | :--- | :--- | :--- |
| `Forrest_Gump_1994_Clean.srt` | 标准 SRT | 1,547 条 | 配合电影视频播放、视频时间线对齐跟读、口语模仿 |
| `Forrest_Gump_1994_Sentences.txt` | 带时间戳文本 | 1,271 句 | 经典名言与日常高频口语句子（完整清洗句），适合逐句跟读测评与遮字幕复述 |
| `Forrest_Gump_1994_Plain.txt` | 纯英文行文本 | 1,271 行 | 极简纯文本，适合闪卡导入、TTS 发音跟读 |
| `Forrest_Gump_1994_Roleplay.txt` | 角色剧本 | 1,077 轮对白 | 角色扮演对戏（Forrest, Jenny, Mrs. Gump, Lt. Dan, Bubba 等） |

---

## 🚀 导入与使用指引

### 方式一：在 CineEnglish 软件中直接使用（已自动预置入库）
两部电影已自动解析并录入到您本地的 CineEnglish 数据库 (`cineenglish.db`) 中：
- **《The Shawshank Redemption》**：已包含 1,653 句口语练习材料，带起止毫秒时间戳。
- **《Forrest Gump》**：已包含 1,271 句口语练习材料，带起止毫秒时间戳。

启动 CineEnglish 后端与 App 后，在素材库即可直接看到这两部影片，点击即可进入：
1. **逐句跟读 (Shadowing)**：聆听 TTS 美式发音或截取原声，录制跟读并查看声学发音打分。
2. **遮字幕复述 (Recall)**：听完原句后遮住字幕复述，系统根据语义还原度打分。
3. **视频同步练习 (Video Timeline)**：若您本地有视频文件，在 App 中关联本地视频，即可实现句尾自动暂停、片段循环与毫秒级高亮滚动。

### 方式二：在 CineEnglish App 中手动二次导入
如果需要重新导入或自定义微调：
- 进入 App 的 **Material Library**（素材库）-> 点击 **Import Local File**。
- 选择 `*.srt` 或 `*_Sentences.txt` 即可一键导入。

### 方式三：配合播放器（PotPlayer / VLC / IINA / Language Reactor）
- 将 `The_Shawshank_Redemption_1994_Clean.srt` 重命名为与您的电影视频文件同名（例如 `Shawshank.1994.mkv` 和 `Shawshank.1994.srt`）。
- 播放器将自动加载干净无广告的英文字幕，开启 AB 循环或快捷键逐句跳转。

### 方式四：与 AI 进行角色扮演对戏 (AI Roleplay Prompt 建议)
将 `*_Roleplay.txt` 中的对白片段复制给大模型（如 ChatGPT / Claude / Gemini），使用如下 Prompt：

```text
You are playing the role of [RED] from the movie "The Shawshank Redemption". 
I will play the role of [ANDY].
Here is the scene dialogue context from the original script:
...（粘贴对应的台词片段）...
Let's practice English conversational speaking together line by line. 
After each of my replies, briefly give me 1 actionable pronunciation/grammar tip, and then reply in character to keep our roleplay going.
```
