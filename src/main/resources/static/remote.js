const STORAGE_KEY = "tvremote.target";

const gate = document.getElementById("gate");
const remote = document.getElementById("remote");
const connectForm = document.getElementById("connect-form");
const connectBtn = document.getElementById("connect-btn");
const hostInput = document.getElementById("host");
const portInput = document.getElementById("port");
const gateError = document.getElementById("gate-error");
const statusEl = document.getElementById("status");
const led = document.getElementById("led");
const volume = document.getElementById("volume");
const volumeValue = document.getElementById("volume-value");
const textInput = document.getElementById("text");
const keyboard = document.getElementById("keyboard");
const keyboardToggle = document.getElementById("keyboard-toggle");
const remoteControls = remote.querySelectorAll("button, input");

const labels = {
  UP: "Yukarı",
  DOWN: "Aşağı",
  LEFT: "Sol",
  RIGHT: "Sağ",
  OK: "OK",
  BACK: "Geri",
  HOME: "Home",
  MUTE: "Sessiz",
  DEL: "Silindi",
  ENTER: "Ara",
};

let busy = false;
let connected = false;

function loadSavedTarget() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
  } catch {
    return null;
  }
}

function saveTarget(host, port) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ host, port }));
}

function setGateError(message) {
  if (!message) {
    gateError.hidden = true;
    gateError.textContent = "";
    return;
  }
  gateError.hidden = false;
  gateError.textContent = message;
}

function setState(state, text) {
  led.dataset.state = state;
  statusEl.textContent = text;
}

function setBusy(next) {
  busy = next;
  remoteControls.forEach((el) => {
    if (el.id === "disconnect") return;
    el.disabled = next || !connected;
  });
}

function showGate() {
  connected = false;
  remote.hidden = true;
  gate.hidden = false;
  setBusy(false);
  hostInput.focus();
}

function showRemote(host, port, model) {
  connected = true;
  gate.hidden = true;
  remote.hidden = false;
  setGateError("");
  setState("ok", model ? `${model}` : `${host}:${port}`);
  setBusy(false);
}

async function post(url, payload) {
  const res = await fetch(url, {
    method: "POST",
    headers: payload ? { "Content-Type": "application/json" } : undefined,
    body: payload ? JSON.stringify(payload) : undefined,
  });
  let body = {};
  try {
    body = await res.json();
  } catch {
    body = {};
  }
  if (res.status === 401) {
    showGate();
    throw new Error(body.error || "Önce TV'ye bağlan");
  }
  if (!res.ok) {
    throw new Error(body.error || "Komut gönderilemedi");
  }
  return body;
}

async function connect(host, port) {
  connectBtn.disabled = true;
  connectBtn.textContent = "Bağlanıyor…";
  setGateError("");
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 12000);
  try {
    const res = await fetch("/remote/api/connect", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ host, port }),
      signal: controller.signal,
    });
    let body = {};
    try {
      body = await res.json();
    } catch {
      body = {};
    }
    if (!res.ok) {
      throw new Error(body.error || "TV'ye bağlanılamadı");
    }
    saveTarget(body.host, body.port);
    showRemote(body.host, body.port, body.model);
  } catch (err) {
    const message = err.name === "AbortError"
      ? "Bağlantı zaman aşımına uğradı"
      : err.message;
    setGateError(message);
  } finally {
    clearTimeout(timer);
    connectBtn.disabled = false;
    connectBtn.textContent = "Giriş";
  }
}

async function disconnect() {
  try {
    await post("/remote/api/disconnect");
  } catch {
    // ignore
  }
  showGate();
}

async function sendKey(name) {
  if (busy || !connected) return;
  setBusy(true);
  setState("busy", "Gönderiliyor…");
  try {
    await post(`/remote/api/key/${name}`);
    setState("ok", labels[name] || name);
  } catch (err) {
    setState("error", err.message);
  } finally {
    setBusy(false);
  }
}

async function sendVolume(value) {
  if (busy || !connected) return;
  setBusy(true);
  setState("busy", "Ses ayarlanıyor…");
  try {
    await post(`/remote/api/volume/${value}`);
    setState("ok", `Ses ${value}`);
  } catch (err) {
    setState("error", err.message);
  } finally {
    setBusy(false);
  }
}

async function sendText() {
  const text = textInput.value.trim();
  if (!text || busy || !connected) return;
  setBusy(true);
  setState("busy", "Yazılıyor…");
  try {
    await post("/remote/api/text", { text });
    textInput.value = "";
    setState("ok", "Yazıldı");
  } catch (err) {
    setState("error", err.message);
  } finally {
    setBusy(false);
    textInput.focus();
  }
}

const saved = loadSavedTarget();
if (saved?.host) hostInput.value = saved.host;
if (saved?.port) portInput.value = saved.port;

connectForm.addEventListener("submit", (event) => {
  event.preventDefault();
  connect(hostInput.value.trim(), Number(portInput.value));
});

document.getElementById("disconnect").addEventListener("click", disconnect);

document.querySelectorAll("[data-key]").forEach((button) => {
  button.addEventListener("click", () => sendKey(button.dataset.key));
});

keyboardToggle.addEventListener("click", () => {
  const open = keyboard.hidden;
  keyboard.hidden = !open;
  keyboardToggle.setAttribute("aria-expanded", String(open));
  keyboardToggle.textContent = open ? "Kapat" : "Klavye";
  if (open) textInput.focus();
});

document.getElementById("send-text").addEventListener("click", sendText);

textInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    sendText();
  }
});

volume.addEventListener("input", () => {
  volumeValue.textContent = volume.value;
});

volume.addEventListener("change", () => sendVolume(volume.value));

const keyMap = {
  ArrowUp: "UP",
  ArrowDown: "DOWN",
  ArrowLeft: "LEFT",
  ArrowRight: "RIGHT",
  Enter: "OK",
  Backspace: "BACK",
  Escape: "BACK",
  KeyH: "HOME",
  KeyM: "MUTE",
};

document.addEventListener("keydown", (event) => {
  if (!connected) return;
  if (event.target === volume || event.target === textInput) return;
  const name = keyMap[event.code];
  if (!name) return;
  event.preventDefault();
  sendKey(name);
});

fetch("/remote/api/status")
  .then((res) => res.json())
  .then((status) => {
    if (status.connected) {
      showRemote(status.host, status.port);
    } else {
      showGate();
    }
  })
  .catch(() => showGate());
