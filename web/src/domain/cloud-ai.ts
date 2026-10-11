export interface CloudConfig {
  provider: string;
  baseUrl: string;
  model: string;
  format: "openai" | "anthropic";
}
export const cloudPresets: Record<string, CloudConfig> = {
  OpenAI: { provider: "OpenAI", baseUrl: "https://api.openai.com/v1", model: "", format: "openai" },
  Gemini: { provider: "Gemini", baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai", model: "", format: "openai" },
  Claude: { provider: "Claude", baseUrl: "https://api.anthropic.com/v1", model: "", format: "anthropic" },
  DeepSeek: { provider: "DeepSeek", baseUrl: "https://api.deepseek.com/v1", model: "", format: "openai" },
  Kimi: { provider: "Kimi", baseUrl: "https://api.moonshot.ai/v1", model: "", format: "openai" },
  OpenRouter: { provider: "OpenRouter", baseUrl: "https://openrouter.ai/api/v1", model: "", format: "openai" },
  Custom: { provider: "Custom", baseUrl: "", model: "", format: "openai" },
};
// Deliberately memory-only: never persisted, backed up, logged, or put in a URL.
const sessionKeys = new Map<string, string>();
const keyScope = (config: CloudConfig) => `${cloudEndpoint(config)}|${config.format}`;
export function setCloudSessionKey(key: string, config?: CloudConfig) {
  if (!config) { sessionKeys.clear(); return; }
  const scope = keyScope(config); if (key.trim()) sessionKeys.set(scope, key.trim()); else sessionKeys.delete(scope);
}
export function hasCloudSessionKey(config?: CloudConfig) {
  if (!config) return false;
  try { return sessionKeys.has(keyScope(config)); } catch { return false; }
}
export function cloudEndpoint(config: CloudConfig, models = false) {
  let url: URL;
  try { url = new URL(config.baseUrl); } catch { throw Error("Enter a valid provider base URL."); }
  const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
  if ((url.protocol !== "https:" && !(import.meta.env.DEV && loopback && url.protocol === "http:")) || url.username || url.password || url.search || url.hash)
    throw Error("Use an HTTPS base URL without credentials, query parameters or fragments.");
  url.pathname = url.pathname.replace(/\/+$/, "") + (models ? "/models" : config.format === "anthropic" ? "/messages" : "/chat/completions");
  return url.href;
}
export async function requestCloud(config: CloudConfig, prompt: string, options: {
  signal?: AbortSignal; fetcher?: typeof fetch; models?: boolean; timeoutMs?: number;
} = {}): Promise<string | string[]> {
  const endpoint = cloudEndpoint(config, options.models);
  const sessionKey = sessionKeys.get(keyScope(config));
  if (!sessionKey) throw Error("Enter your own API key for this session in Cloud AI settings.");
  if (!options.models && !config.model.trim()) throw Error("Choose or enter a model in Cloud AI settings.");
  if (options.signal?.aborted) throw Error("Request cancelled.");
  const controller = new AbortController();
  const cancel = () => controller.abort();
  options.signal?.addEventListener("abort", cancel, { once: true });
  let timedOut = false;
  const timer = setTimeout(() => { timedOut = true; controller.abort(); }, options.timeoutMs ?? 60_000);
  const anthropic = config.format === "anthropic";
  try {
    const response = await (options.fetcher ?? fetch)(endpoint, {
      method: options.models ? "GET" : "POST", signal: controller.signal,
      redirect: "error", credentials: "omit", cache: "no-store",
      headers: anthropic ? { "Content-Type": "application/json", "x-api-key": sessionKey, "anthropic-version": "2023-06-01", "anthropic-dangerous-direct-browser-access": "true" } : { "Content-Type": "application/json", Authorization: `Bearer ${sessionKey}` },
      body: options.models ? undefined : JSON.stringify({ model: config.model.trim(), messages: [{ role: "user", content: prompt.slice(0, 80_000) }], max_tokens: 4096, temperature: 0.5 }),
    });
    if (!response.ok) throw Error(`Provider returned HTTP ${response.status}. Check your model, endpoint, key and account limits.`);
    const reader = response.body?.getReader();
    if (!reader) throw Error("Provider returned an empty response.");
    const chunks: Uint8Array[] = []; let size = 0;
    try {
      while (true) {
        const next = await reader.read(); if (next.done) break;
        size += next.value.byteLength;
        if (size > 1_000_000) { await reader.cancel(); throw Error("Provider response is too large."); }
        chunks.push(next.value);
      }
    } finally { reader.releaseLock(); }
    if (controller.signal.aborted) throw Error("Request cancelled.");
    const bytes = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    let payload: Record<string, unknown>;
    try { payload = JSON.parse(new TextDecoder().decode(bytes)); } catch { throw Error("Provider returned invalid JSON."); }
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) throw Error("Provider response must be a JSON object.");
    if (options.models) {
      if (!Array.isArray(payload.data)) throw Error("Provider did not return a model list.");
      return [...new Set(payload.data.flatMap(item => typeof item?.id === "string" ? [item.id.replace(/^models\//, "")] : []))].slice(0, 500);
    }
    let content: unknown;
    if (anthropic) {
      if (!Array.isArray(payload.content) || payload.content.some(item => !item || typeof item !== "object" || (item.type === "text" && typeof item.text !== "string"))) throw Error("Provider returned invalid text blocks.");
      content = payload.content.filter(item => item.type === "text").map(item => item.text).join("\n");
    } else {
      if (!Array.isArray(payload.choices)) throw Error("Provider returned invalid choices.");
      content = payload.choices[0]?.message?.content;
    }
    if (typeof content !== "string" || !content.trim()) throw Error("Provider did not return a text response.");
    return content.trim();
  } catch (error) {
    if (controller.signal.aborted) throw Error(timedOut ? "Request timed out. Your saved data is unchanged; you can retry." : "Request cancelled.");
    // Never echo raw transport errors, which can contain a URL or credential.
    if (error instanceof Error && /^(Provider |Enter |Choose |Use )/.test(error.message)) throw error;
    throw Error("Could not reach the provider. Check browser access (CORS), your connection and Cloud AI settings. Manual prompt/paste still works.");
  } finally {
    clearTimeout(timer); options.signal?.removeEventListener("abort", cancel);
  }
}
