# AI 英语口语陪练（SpeakMate）

> 七牛云 XEngineer 第三批议题 · **题目一：AI 英语口语陪练**

场景化英语口语练习：按住说话、云端语音识别、AI 角色对话、表达评测与课后总结。

## Demo 视频

https://www.bilibili.com/video/BV1wcEp62EW9/

B 站主页：https://space.bilibili.com/34282046991

## 功能

- 场景选择：面试 / 餐厅 / 会议 / 旅行 / 购物
- 按住说话：录音波形 + 硅基流动 SenseVoice 识别
- AI 对话：Qwen 角色扮演 + 打字机回复 + TTS
- 评测与总结：课后表达能力评测与学习总结

## 技术栈

| 模块 | 技术 |
|------|------|
| 后端 | Java 8 + Spring Boot 2.7 |
| 前端 | HTML / CSS / JavaScript |
| ASR | 硅基流动 SenseVoice |
| LLM | 硅基流动 Qwen2.5 |

## 快速启动

1. 双击桌面 `start-english-tutor.bat`
2. 浏览器打开 http://localhost:8081/
3. 右上角显示「后端已连接」即可使用

## 原创说明

- 语音识别 multipart 思路参考本人项目 [voice-input-app](https://github.com/Teku715/voice-input-app)
- 场景对话、评测、总结、UI 交互为本项目开发

## 作者

- GitHub：Teku715
- B 站：bili_34282046991
