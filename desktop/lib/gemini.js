// Gemini REST, same request shape and prompts as android/.../Gemini.java (prompts.json is
// generated from it by scripts/sync-ui.js).
"use strict";
const PROMPTS = require("./prompts.json");
const API = "https://generativelanguage.googleapis.com/v1beta/";

// Gemini's refusals sorted by what the user can do (Gemini.Failure on Android has the same).
function kind(status, body) {
  const b = String(body || "");
  if (status === 429) return /PerDay|per day|daily/.test(b) ? "daily" : "minute";
  if (/API_KEY_INVALID|API key not valid|API key expired|API_KEY_EXPIRED/.test(b)) return "key";
  if (/SERVICE_DISABLED|has not been used|is disabled/.test(b)) return "disabled";
  if (status === 401) return "key";
  if (status === 403) return "denied";
  if (status === 404) return "model";
  return "other";
}
function retrySeconds(body) {
  const b = String(body || "");
  const m = /"retryDelay"\s*:\s*"(\d+)/.exec(b);
  if (m) return Number(m[1]);
  const r = /retry in (\d+)/.exec(b);
  return r ? Number(r[1]) + 1 : 0;
}
const ADVICE = {
  minute: "Geminiの1分あたりの上限に達しました（無料枠は1分に数回までです）。少し待ってから「再開」を押してください。",
  daily: "Geminiの1日の無料枠を使い切りました。枠は日本時間の夕方（太平洋時間の0時）に戻るので、その後「再開」を押してください。急ぐときは、メニューの「Gemini APIキー・モデル」で別のモデルに変える（枠はモデルごと）か、Google AI Studioで請求先を設定してください。",
  key: "Gemini APIキーが無効か、期限が切れています。メニューの「Gemini APIキー・モデル」で、Google AI Studioのキーを入れ直してください。",
  disabled: "このAPIキーのGoogle Cloudプロジェクトで、Gemini API（Generative Language API）が有効になっていません。Google AI Studioでキーを作り直すか、Google CloudでAPIを有効にしてください。",
  denied: "Gemini APIへのアクセスが拒否されました。APIキーの制限（Androidアプリ・IPアドレスなど）や、請求先の設定を確認してください。",
  model: "このモデルは使えません。メニューの「Gemini APIキー・モデル」で「モデル一覧」から選び直してください。",
  other: "時間を空けて再試行してください。",
};

// Told about every call (purpose, HTTP status): the backend counts today's calls for the screen.
let onCall = null;
function setOnCall(f) {
  onCall = f;
}

async function request(key, pathAndQuery, body, fetchImpl = fetch, purpose = "read") {
  const res = await fetchImpl(API + pathAndQuery, {
    method: body ? "POST" : "GET",
    headers: { "x-goog-api-key": key, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(90000),
  });
  const text = await res.text();
  if (onCall) onCall(purpose, res.status);
  if (!res.ok) {
    let message = "";
    try {
      message = JSON.parse(text).error.message;
    } catch {}
    message = String(message).split(key).join("[APIキー]");
    const k = kind(res.status, text);
    const e = new Error(`Gemini HTTP ${res.status}\n${message}\n${ADVICE[k]}`);
    Object.assign(e, { status: res.status, kind: k, retrySeconds: retrySeconds(text), stopsAll: k !== "model" && k !== "other" });
    throw e;
  }
  return JSON.parse(text);
}

async function models(key, fetchImpl) {
  const out = [];
  let token = "";
  for (let i = 0; i < 10; i++) {
    const r = await request(key, "models?pageSize=100" + (token ? "&pageToken=" + encodeURIComponent(token) : ""), null, fetchImpl, "models");
    for (const m of r.models || [])
      if (String(m.name).includes("gemini") && (m.supportedGenerationMethods || []).includes("generateContent")) out.push(m.name.replace("models/", ""));
    token = r.nextPageToken || "";
    if (!token) break;
  }
  return out;
}

// images: Buffers of JPEG (already downscaled by the caller), or of `mime`; note: more text
// after the images (what the card's QR code says).
async function generate({ key, model, format }, kind, images, fetchImpl, mime = "image/jpeg", note = "") {
  if (!images.length) throw new Error("画像を追加してください");
  if (images.length > 20) throw new Error("1件の認識は20ページまでです");
  if (!/^[A-Za-z0-9._-]+$/.test(model)) throw new Error("モデル名が不正です");
  const { prompt, keys } = PROMPTS[kind];
  let bytes = 0;
  const parts = [{ text: prompt }];
  for (const b of images) {
    bytes += b.length;
    if (bytes > 12 * 1024 * 1024) throw new Error("画像データが大きすぎます。ページを分けてください");
    parts.push({ inlineData: { mimeType: mime, data: b.toString("base64") } });
  }
  if (note) parts.push({ text: note });
  const schema = { type: "object", properties: Object.fromEntries(keys.map((k) => [k, { type: "string" }])), required: keys };
  const config = { temperature: 0.1 };
  if (format === "modern") config.responseFormat = { text: { mimeType: "application/json", schema } };
  else Object.assign(config, { responseMimeType: "application/json", responseSchema: schema });
  const r = await request(key, `models/${model}:generateContent`, { contents: [{ role: "user", parts }], generationConfig: config }, fetchImpl, kind === "card" ? "read" : kind);
  const cand = (r.candidates || [])[0];
  if (!cand) throw new Error("認識結果がありません。画像やAPI設定を確認してください");
  if ((cand.finishReason || "STOP") !== "STOP") throw new Error("認識が完了しませんでした: " + cand.finishReason);
  let t = (cand.content.parts || []).filter((x) => !x.thought).map((x) => x.text || "").join("").trim();
  if (t.startsWith("```")) t = t.replace(/^```(?:json)?\s*/, "").replace(/\s*```$/, "");
  const out = JSON.parse(t);
  for (const k of keys) if (typeof out[k] !== "string") throw new Error("認識結果の形式が不正です。再試行してください");
  return out;
}

// The organisation's official site via Gemini with Google Search (only when the card has neither a
// web address nor a company e-mail). Returns a URL or "".
async function officialSite({ key, model }, p, fetchImpl) {
  if (!/^[A-Za-z0-9._-]+$/.test(model)) throw new Error("モデル名が不正です");
  const who = [p.company || (p.kind === "shop" ? p.name : ""), p.address].filter(Boolean).join(" / ");
  if (!String(p.company || p.name || "").trim()) return "";
  const prompt =
    "次の会社・団体・お店の公式ウェブサイトのトップページのURLを、Google検索で確認して1つだけ答えてください。" +
    "公式サイトが見つからない、または確信が持てないときは NONE とだけ答えてください。URL以外は書かないでください。\n" + who;
  const r = await request(key, `models/${model}:generateContent`, { contents: [{ role: "user", parts: [{ text: prompt }] }], tools: [{ google_search: {} }] }, fetchImpl, "logo");
  const t = ((r.candidates || [])[0]?.content?.parts || []).map((x) => x.text || "").join(" ");
  const m = /https?:\/\/[^\s"'<>)\]]+/.exec(t);
  return m && !/vertexaisearch|google\./.test(m[0]) ? m[0] : "";
}

module.exports = { models, generate, officialSite, kind, retrySeconds, ADVICE, setOnCall };
