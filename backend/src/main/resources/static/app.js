const API_BASE =
  window.location.protocol === "http:" || window.location.protocol === "https:"
    ? window.location.origin
    : "http://localhost:8081";

const scenes = [
  { id: "interview", name: "求职面试", desc: "Job interview" },
  { id: "restaurant", name: "餐厅点餐", desc: "Restaurant dining" },
  { id: "meeting", name: "商务会议", desc: "Business meeting" },
  { id: "travel", name: "旅行问路", desc: "Asking directions" },
  { id: "shopping", name: "购物交流", desc: "Shopping chat" },
];

let currentScene = "interview";
let conversationHistory = "";
let lastUserText = "";
let mediaRecorder = null;
let audioChunks = [];
let isRecording = false;
let shouldSendRecording = false;
let autoSpeak = true;

let audioContext = null;
let analyser = null;
let mediaStream = null;
let waveAnimationId = null;

let lastGroupEl = null;
let lastGroupRole = null;
let lastMessageAt = 0;

const els = {
  sceneGrid: document.getElementById("sceneGrid"),
  chatBox: document.getElementById("chatBox"),
  micBtn: document.getElementById("micBtn"),
  textInput: document.getElementById("textInput"),
  sendBtn: document.getElementById("sendBtn"),
  status: document.getElementById("status"),
  statusPill: document.getElementById("statusPill"),
  evalBtn: document.getElementById("evalBtn"),
  summaryBtn: document.getElementById("summaryBtn"),
  ttsToggleBtn: document.getElementById("ttsToggleBtn"),
  evalBox: document.getElementById("evalBox"),
  evalTitle: document.getElementById("evalTitle"),
  evalResult: document.getElementById("evalResult"),
  wavePanel: document.getElementById("wavePanel"),
  waveCanvas: document.getElementById("waveCanvas"),
  sceneTitle: document.getElementById("sceneTitle"),
  sceneDesc: document.getElementById("sceneDesc"),
};

function formatTime(date = new Date()) {
  return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
}

function formatDivider(date = new Date()) {
  const today = new Date();
  const sameDay = date.toDateString() === today.toDateString();
  const time = formatTime(date);
  return sameDay ? `今天 ${time}` : `${date.toLocaleDateString("zh-CN")} ${time}`;
}

function escapeHtml(text) {
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/\n/g, "<br>");
}

function setStatus(text, type = "") {
  els.status.textContent = text;
  els.status.className = `status ${type}`;
}

function speak(text) {
  if (!autoSpeak || !("speechSynthesis" in window) || !text) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "en-US";
  utterance.rate = 0.95;
  window.speechSynthesis.speak(utterance);
}

function replayText(text) {
  if (!("speechSynthesis" in window)) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = "en-US";
  window.speechSynthesis.speak(utterance);
}

function ensureTimeDivider(now) {
  if (!lastMessageAt) return;
  const gap = now - lastMessageAt;
  if (gap < 5 * 60 * 1000) return;
  const divider = document.createElement("div");
  divider.className = "time-divider";
  divider.textContent = formatDivider(new Date(now));
  els.chatBox.appendChild(divider);
}

function getOrCreateGroup(role, now) {
  ensureTimeDivider(now);
  const normalized = role === "user" ? "user" : "ai";
  const canReuse =
    lastGroupEl &&
    lastGroupRole === normalized &&
    now - lastMessageAt < 5 * 60 * 1000;

  if (canReuse) return lastGroupEl;

  const group = document.createElement("div");
  group.className = `msg-group ${normalized}`;
  const meta = document.createElement("div");
  meta.className = "group-meta";
  meta.textContent = normalized === "user" ? `你 · ${formatTime(new Date(now))}` : `AI 陪练 · ${formatTime(new Date(now))}`;
  group.appendChild(meta);
  els.chatBox.appendChild(group);

  lastGroupEl = group;
  lastGroupRole = normalized;
  return group;
}

function addMessage(role, text, extras = {}) {
  const now = Date.now();
  const group = getOrCreateGroup(role, now);
  const bubble = document.createElement("div");
  bubble.className = "bubble";

  let html = escapeHtml(text);
  html += `<span class="time">${formatTime(new Date(now))}</span>`;

  if (extras.score) {
    html += `<div class="score">${escapeHtml(extras.score)}</div>`;
  }

  if (role === "ai") {
    html += `<div class="tts-row"><button class="tts-btn" type="button">🔊 重播</button></div>`;
  }

  bubble.innerHTML = html;

  if (role === "ai") {
    bubble.querySelector(".tts-btn").addEventListener("click", () => replayText(text));
  }

  group.appendChild(bubble);
  lastMessageAt = now;
  els.chatBox.scrollTop = els.chatBox.scrollHeight;

  if (role === "ai") speak(text);
}

function addMessageTypewriter(role, fullText, extras = {}) {
  const now = Date.now();
  const group = getOrCreateGroup(role, now);
  const bubble = document.createElement("div");
  bubble.className = "bubble";

  const contentEl = document.createElement("span");
  contentEl.className = "msg-text";
  const timeEl = document.createElement("span");
  timeEl.className = "time";
  timeEl.textContent = formatTime(new Date(now));
  bubble.appendChild(contentEl);
  bubble.appendChild(timeEl);

  const ttsRow = document.createElement("div");
  ttsRow.className = "tts-row";
  ttsRow.hidden = true;
  const ttsBtn = document.createElement("button");
  ttsBtn.className = "tts-btn";
  ttsBtn.type = "button";
  ttsBtn.textContent = "🔊 重播";
  ttsRow.appendChild(ttsBtn);
  bubble.appendChild(ttsRow);

  group.appendChild(bubble);
  lastMessageAt = now;

  const chars = [...fullText];
  let index = 0;

  function revealNext() {
    if (index < chars.length) {
      contentEl.textContent += chars[index];
      index += 1;
      els.chatBox.scrollTop = els.chatBox.scrollHeight;
      setTimeout(revealNext, 12);
      return;
    }
    if (extras.score) {
      const scoreEl = document.createElement("div");
      scoreEl.className = "score";
      scoreEl.textContent = extras.score;
      bubble.insertBefore(scoreEl, ttsRow);
    }
    ttsRow.hidden = false;
    ttsBtn.addEventListener("click", () => replayText(fullText));
    speak(fullText);
  }

  revealNext();
}

function buildScoreText(data) {
  if (data.score) {
    const comment = data.scoreComment ? ` · ${data.scoreComment}` : "";
    return `评分 ${data.score}/10${comment}`;
  }
  return "评分 8/10 · 表达不错";
}

function cleanReplyForDisplay(data) {
  let text = String(data.cleanReply || data.reply || "").trim();
  text = text.replace(/\[(?:评分|Correction|Score|AI|纠错)[^\]]*\]/gi, " ");
  text = text.replace(/\[评分[^\]]*/gi, " ");
  text = text.replace(/评分[：:]\s*\d+\s*\/\s*10/gi, " ");
  text = text.replace(/"?\s*instead\s*"?/gi, " ");
  text = text.replace(/\b(\w+)(\s+\1\b)+/gi, "$1");
  text = text.replace(/\s+/g, " ").trim();
  return text;
}

function showTyping() {
  const group = getOrCreateGroup("ai", Date.now());
  const bubble = document.createElement("div");
  bubble.className = "bubble";
  bubble.id = "typingBubble";
  bubble.innerHTML = `<div class="typing"><span></span><span></span><span></span></div>`;
  group.appendChild(bubble);
  els.chatBox.scrollTop = els.chatBox.scrollHeight;
  return bubble;
}

function removeTyping() {
  document.getElementById("typingBubble")?.remove();
}

function initScenes() {
  els.sceneGrid.innerHTML = "";
  scenes.forEach((scene) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `scene-btn${scene.id === currentScene ? " active" : ""}`;
    btn.innerHTML = `<div class="name">${scene.name}</div><div class="desc">${scene.desc}</div>`;
    btn.onclick = () => selectScene(scene.id);
    els.sceneGrid.appendChild(btn);
  });
  const active = scenes.find((s) => s.id === currentScene);
  els.sceneTitle.textContent = active.name;
  els.sceneDesc.textContent = active.desc;
}

function selectScene(id) {
  currentScene = id;
  initScenes();
  resetChat(false);
}

function resetChat(showWelcome = true) {
  els.chatBox.innerHTML = "";
  els.evalBox.style.display = "none";
  conversationHistory = "";
  lastUserText = "";
  lastGroupEl = null;
  lastGroupRole = null;
  lastMessageAt = 0;
  els.evalBtn.disabled = true;
  els.summaryBtn.disabled = true;
  setStatus("");
  if (showWelcome) {
    addMessage("ai", "Welcome! Pick a scenario and start speaking. I'm your AI English tutor.");
  }
}

function pickRecorderMimeType() {
  if (MediaRecorder.isTypeSupported("audio/webm;codecs=opus")) return "audio/webm;codecs=opus";
  if (MediaRecorder.isTypeSupported("audio/webm")) return "audio/webm";
  if (MediaRecorder.isTypeSupported("audio/ogg;codecs=opus")) return "audio/ogg;codecs=opus";
  return "";
}

function resizeCanvas() {
  const canvas = els.waveCanvas;
  const rect = canvas.getBoundingClientRect();
  canvas.width = Math.floor(rect.width * devicePixelRatio);
  canvas.height = Math.floor(rect.height * devicePixelRatio);
}

function drawWaveform() {
  if (!analyser) return;
  const canvas = els.waveCanvas;
  const ctx = canvas.getContext("2d");
  const bufferLength = analyser.frequencyBinCount;
  const dataArray = new Uint8Array(bufferLength);
  analyser.getByteFrequencyData(dataArray);

  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const barWidth = Math.max(2, canvas.width / bufferLength);
  for (let i = 0; i < bufferLength; i += 2) {
    const value = dataArray[i] / 255;
    const barHeight = value * canvas.height * 0.85;
    const x = i * barWidth * 0.5;
    const gradient = ctx.createLinearGradient(0, canvas.height, 0, canvas.height - barHeight);
    gradient.addColorStop(0, "#ef4444");
    gradient.addColorStop(1, "#22d3ee");
    ctx.fillStyle = gradient;
    ctx.fillRect(x, canvas.height - barHeight, barWidth * 0.8, barHeight);
  }
  waveAnimationId = requestAnimationFrame(drawWaveform);
}

function startWaveformFromStream() {
  resizeCanvas();
  audioContext = new (window.AudioContext || window.webkitAudioContext)();
  const source = audioContext.createMediaStreamSource(mediaStream);
  analyser = audioContext.createAnalyser();
  analyser.fftSize = 128;
  source.connect(analyser);
  els.wavePanel.classList.add("active");
  drawWaveform();
}

function stopWaveform() {
  if (waveAnimationId) cancelAnimationFrame(waveAnimationId);
  waveAnimationId = null;
  analyser = null;
  els.wavePanel.classList.remove("active");
  if (audioContext) {
    audioContext.close().catch(() => {});
    audioContext = null;
  }
}

function releaseMediaStream() {
  if (mediaStream) {
    mediaStream.getTracks().forEach((track) => track.stop());
    mediaStream = null;
  }
}

function formatFetchError(error) {
  if (error?.message === "Failed to fetch") {
    return "无法连接后端。请双击「启动英语口语陪练.bat」，用 http://localhost:8081 打开页面（不要直接双击 index.html）";
  }
  return error?.message || "未知错误";
}

async function transcribeAudio(blob, format) {
  const formData = new FormData();
  formData.append("file", blob, `speech.${format}`);
  formData.append("format", format);
  let resp;
  try {
    resp = await fetch(`${API_BASE}/api/speech`, { method: "POST", body: formData });
  } catch (error) {
    throw new Error(formatFetchError(error));
  }
  const data = await resp.json().catch(() => ({}));
  if (!resp.ok || data.error) {
    throw new Error(data.error || `语音识别失败 (${resp.status})`);
  }
  return (data.text || data.transcript || "").trim();
}

async function handleRecordingStop() {
  const send = shouldSendRecording;
  shouldSendRecording = false;
  isRecording = false;
  els.micBtn.classList.remove("recording");
  stopWaveform();
  releaseMediaStream();

  if (!send) {
    setStatus("", "");
    return;
  }

  const blob = new Blob(audioChunks, { type: mediaRecorder?.mimeType || "audio/webm" });
  audioChunks = [];
  mediaRecorder = null;

  if (blob.size < 800) {
    els.textInput.value = "";
    setStatus("录音太短，请按住多说一会", "error");
    return;
  }

  els.textInput.value = "识别中...";
  els.textInput.disabled = true;
  setStatus("正在识别语音...", "success");

  const format = blob.type.includes("ogg") ? "ogg" : "webm";
  try {
    const text = await transcribeAudio(blob, format);
    els.textInput.disabled = false;
    if (!text) {
      els.textInput.value = "";
      setStatus("未识别到内容，请用英文再说一次", "error");
      return;
    }
    els.textInput.value = text;
    setStatus("", "");
    await new Promise((r) => setTimeout(r, 300));
    els.textInput.value = "";
    await sendTextWithContent(text);
  } catch (error) {
    els.textInput.disabled = false;
    els.textInput.value = "";
    setStatus(`识别失败: ${formatFetchError(error)}`, "error");
  }
}

async function startRecording() {
  if (isRecording) return;
  if (!navigator.mediaDevices?.getUserMedia) {
    setStatus("浏览器不支持麦克风，请用文本输入", "error");
    return;
  }
  shouldSendRecording = false;
  audioChunks = [];
  els.textInput.value = "";
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    startWaveformFromStream();

    const mimeType = pickRecorderMimeType();
    mediaRecorder = mimeType
      ? new MediaRecorder(mediaStream, { mimeType })
      : new MediaRecorder(mediaStream);
    mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) audioChunks.push(event.data);
    };
    mediaRecorder.onstop = () => {
      handleRecordingStop().catch((error) => {
        setStatus(`处理录音失败: ${error.message}`, "error");
      });
    };
    mediaRecorder.start(200);

    isRecording = true;
    els.micBtn.classList.add("recording");
    setStatus("正在录音... 松开发送", "success");
  } catch (error) {
    releaseMediaStream();
    stopWaveform();
    setStatus(`无法启动麦克风: ${error.message}`, "error");
  }
}

function stopRecording() {
  if (!isRecording || !mediaRecorder) return;
  shouldSendRecording = true;
  try {
    if (mediaRecorder.state !== "inactive") mediaRecorder.stop();
  } catch (_) {
    shouldSendRecording = false;
    isRecording = false;
    els.micBtn.classList.remove("recording");
    stopWaveform();
    releaseMediaStream();
  }
}

async function sendText() {
  const text = els.textInput.value.trim();
  if (!text || text === "识别中...") return;
  els.textInput.value = "";
  await sendTextWithContent(text);
}

async function sendTextWithContent(text) {
  lastUserText = text;
  addMessage("user", text);
  showTyping();
  setStatus("AI 回复中...");
  els.sendBtn.disabled = true;

  try {
    const resp = await fetch(`${API_BASE}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ scene: currentScene, message: text, history: conversationHistory }),
    });
    const data = await resp.json();
    removeTyping();
    const cleanReply = cleanReplyForDisplay(data) || "Could you tell me a bit more?";
    const scoreText = buildScoreText(data);

    addMessageTypewriter("ai", cleanReply, { score: scoreText });
    conversationHistory += `\n用户：${text}\nAI：${cleanReply}\n`;
    setStatus("", "");
    els.evalBtn.disabled = false;
    els.summaryBtn.disabled = false;
  } catch (error) {
    removeTyping();
    addMessageTypewriter("ai", `Network error: ${error.message}`);
    setStatus("网络错误", "error");
  } finally {
    els.sendBtn.disabled = false;
  }
}

async function evaluateLast() {
  if (!lastUserText) return;
  setStatus("评测中...");
  try {
    const resp = await fetch(`${API_BASE}/api/evaluate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text: lastUserText, scene: currentScene }),
    });
    const data = await resp.json();
    els.evalTitle.textContent = "发音与表达评测";
    els.evalResult.textContent = data.evaluation || data.raw || "暂无评测结果";
    els.evalBox.style.display = "block";
    setStatus("", "");
  } catch (error) {
    setStatus(`评测失败: ${error.message}`, "error");
  }
}

async function requestSummary() {
  setStatus("生成总结中...");
  try {
    const resp = await fetch(`${API_BASE}/api/summary`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ history: conversationHistory, scene: currentScene }),
    });
    const data = await resp.json();
    els.evalTitle.textContent = "课后学习总结";
    els.evalResult.textContent = data.summary || "";
    els.evalBox.style.display = "block";
    setStatus("", "");
  } catch (error) {
    setStatus(`总结失败: ${error.message}`, "error");
  }
}

async function checkHealth() {
  try {
    const resp = await fetch(`${API_BASE}/api/health`);
    const ok = resp.ok;
    els.statusPill.textContent = ok ? "后端已连接 · 云端语音识别" : "后端异常";
    els.statusPill.style.color = ok ? "#34d399" : "#f87171";
  } catch {
    els.statusPill.textContent = "后端未启动 :8081";
    els.statusPill.style.color = "#f87171";
  }
}

els.sendBtn.addEventListener("click", sendText);
els.textInput.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendText();
  }
});
els.evalBtn.addEventListener("click", evaluateLast);
els.summaryBtn.addEventListener("click", requestSummary);
els.ttsToggleBtn.addEventListener("click", () => {
  autoSpeak = !autoSpeak;
  els.ttsToggleBtn.textContent = `自动播报：${autoSpeak ? "开" : "关"}`;
});

els.micBtn.addEventListener("mousedown", startRecording);
els.micBtn.addEventListener("mouseup", stopRecording);
els.micBtn.addEventListener("mouseleave", stopRecording);
els.micBtn.addEventListener("touchstart", (e) => { e.preventDefault(); startRecording(); });
els.micBtn.addEventListener("touchend", (e) => { e.preventDefault(); stopRecording(); });

window.addEventListener("resize", () => {
  if (isRecording) resizeCanvas();
});

window.resetChat = resetChat;
window.evaluateLast = evaluateLast;
window.requestSummary = requestSummary;

if (window.location.protocol === "file:") {
  setStatus("请用 http://localhost:8081 打开（双击桌面启动脚本），file:// 无法上传语音", "error");
}

initScenes();
resetChat();
checkHealth();
