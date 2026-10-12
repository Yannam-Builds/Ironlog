import { afterEach, expect, it, vi } from "vitest";
import { cloudEndpoint, cloudPresets, hasCloudSessionKey, requestCloud, setCloudSessionKey } from "../src/domain/cloud-ai";
afterEach(() => { setCloudSessionKey(""); vi.restoreAllMocks(); });
it("refuses credentials and query strings in endpoints before any fetch", async () => {
  const fetcher = vi.fn(); setCloudSessionKey("secret", cloudPresets.OpenAI);
  for (const baseUrl of ["http://example.com/v1", "https://user:pass@example.com", "https://example.com?key=secret", "https://example.com/#key"])
    await expect(requestCloud({ ...cloudPresets.OpenAI, baseUrl, model: "model" }, "Hi", { fetcher })).rejects.toThrow();
  expect(fetcher).not.toHaveBeenCalled();
  expect(cloudEndpoint(cloudPresets.Claude)).toBe("https://api.anthropic.com/v1/messages");
});
it("requires an explicit session key and uses headers without sending cookies or accepting redirects", async () => {
  const fetcher = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({ choices: [{ message: { content: "Advice" } }] })));
  const config = { ...cloudPresets.OpenAI, model: "model" };
  await expect(requestCloud(config, "Hi", { fetcher })).rejects.toThrow("session");
  expect(fetcher).not.toHaveBeenCalled();
  setCloudSessionKey("secret", config);
  await expect(requestCloud(config, "Hi", { fetcher })).resolves.toBe("Advice");
  expect(fetcher.mock.calls[0]?.[0]).not.toContain("secret");
  expect(fetcher).toHaveBeenCalledWith("https://api.openai.com/v1/chat/completions", expect.objectContaining({ credentials: "omit", redirect: "error", headers: expect.objectContaining({ Authorization: "Bearer secret" }) }));
  expect(JSON.stringify(config)).not.toContain("secret");
  setCloudSessionKey(""); expect(hasCloudSessionKey()).toBe(false);
});
it("handles Anthropic text and model listing without exposing provider errors", async () => {
  const config = { ...cloudPresets.Claude, model: "model" };
  setCloudSessionKey("secret", config);
  await expect(requestCloud(config, "Hi", { fetcher: vi.fn(async () => new Response(JSON.stringify({ content: [{ type: "text", text: "Answer" }] }))) })).resolves.toBe("Answer");
  await expect(requestCloud(config, "", { models: true, fetcher: vi.fn(async () => new Response(JSON.stringify({ data: [{ id: "models/model" }] }))) })).resolves.toEqual(["model"]);
  await expect(requestCloud(config, "Hi", { fetcher: vi.fn(async () => new Response("secret", { status: 401 })) })).rejects.toThrow("HTTP 401");
  await expect(requestCloud(config, "Hi", { fetcher: vi.fn(async () => { throw Error("secret url"); }) })).rejects.not.toThrow("secret");
});
it("rejects excessive responses, invalid JSON and empty model output", async () => {
  const config = { ...cloudPresets.OpenAI, model: "model" }; setCloudSessionKey("secret", config);
  for (const body of ["not json", "{}", "x".repeat(1_000_001)])
    await expect(requestCloud(config, "Hi", { fetcher: vi.fn(async () => new Response(body)) })).rejects.toThrow();
});
it("cancels and times out requests without accepting obsolete text", async () => {
  const config = { ...cloudPresets.OpenAI, model: "model" }; setCloudSessionKey("secret", config);
  const fetcher = vi.fn<typeof fetch>(async (_url, init) => new Promise<Response>((_resolve, reject) => init?.signal?.addEventListener("abort", () => reject(Error("abort")))));
  await expect(requestCloud(config, "Hi", { fetcher, timeoutMs: 5 })).rejects.toThrow("timed out");
  const controller = new AbortController();
  const promise = requestCloud(config, "Hi", { fetcher, signal: controller.signal }); controller.abort();
  await expect(promise).rejects.toThrow("cancelled");
});
it("requires a separate credential for a changed endpoint and rejects malformed text blocks", async () => {
  const config = { ...cloudPresets.Claude, model: "model" }; setCloudSessionKey("secret", config);
  const fetcher = vi.fn<typeof fetch>();
  await expect(requestCloud({ ...config, baseUrl: "https://other.example/v1" }, "Hi", { fetcher })).rejects.toThrow("session");
  expect(fetcher).not.toHaveBeenCalled();
  for (const body of [null, { content: {} }, { content: [null] }, { content: [{ type: "text", text: { secret: true } }] }])
    await expect(requestCloud(config, "Hi", { fetcher: vi.fn(async () => new Response(JSON.stringify(body))) })).rejects.toThrow(/^Provider/);
});
