const providers: Record<string, string> = { openai: "OpenAI", gemini: "Gemini", claude: "Claude", deepseek: "DeepSeek", kimi: "Kimi", openrouter: "OpenRouter", custom: "Custom" };
export const webProviderName = (value: string) => providers[value.trim().toLowerCase()] ?? "Custom";
export const nativeProviderKey = (value: string) => webProviderName(value).toLowerCase();
