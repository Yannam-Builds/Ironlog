import { useState } from "react";
import { cloudPresets, cloudEndpoint, hasCloudSessionKey, setCloudSessionKey } from "../../domain/cloud-ai";
import { saveProfile } from "../../data/store";
import { Button, Field, Sheet } from "../../ui/components";
import { useApp } from "../../ui/context";
import { useCloudRequest } from "../../ui/useCloudRequest";
import { cloudCoachingEnabled } from "../../domain/coaching-mode";
import { webProviderName } from "../../domain/provider-codec";

export function CloudSettings({ onClose }: { onClose: () => void }) {
  const { data, run, busy } = useApp();
  const [config, setConfig] = useState(() => { const saved = data.profile.cloudConfig ?? cloudPresets.OpenAI; return { ...saved, provider: webProviderName(saved.provider) }; });
  const [mode, setMode] = useState<"built_in" | "cloud_ai">(cloudCoachingEnabled(data.profile) ? "cloud_ai" : "built_in");
  const [key, setKey] = useState("");
  const [, setKeyVersion] = useState(0);
  const keyAvailable = hasCloudSessionKey(config);
  const [error, setError] = useState("");
  const cloud = useCloudRequest();
  const rememberKey = () => { if (key.trim()) { setCloudSessionKey(key, config); setKey(""); setKeyVersion(value => value + 1); } };
  const test = (models: boolean) => {
    try { rememberKey(); setError(""); void cloud.start(config, models ? "" : "Reply OK.", models); }
    catch (caught) { setError(caught instanceof Error ? caught.message : "Invalid settings."); }
  };
  return <Sheet title="Cloud AI" onClose={onClose}>
    <p>Optional: use your own provider account. Requests may incur provider charges. Your key stays in memory until this page reloads; browser scripts can access it. Only provider settings are included in backups.</p>
    <Field label="Coaching mode"><select value={mode} onChange={e => { cloud.cancel(); setMode(e.target.value as typeof mode); }}><option value="built_in">Built-in intelligence</option><option value="cloud_ai">Cloud AI (opt-in)</option></select></Field>
    <Field label="Provider"><select value={config.provider} onChange={e => { cloud.cancel(); setConfig(cloudPresets[e.target.value]); }}>{Object.keys(cloudPresets).map(name => <option key={name}>{name}</option>)}</select></Field>
    <Field label="Base URL"><input type="url" value={config.baseUrl} onChange={e => { cloud.cancel(); setConfig({ ...config, baseUrl: e.target.value }); }} /></Field>
    <Field label="API format"><select value={config.format} onChange={e => { cloud.cancel(); setConfig({ ...config, format: e.target.value as "openai" | "anthropic" }); }}><option value="openai">OpenAI compatible</option><option value="anthropic">Anthropic messages</option></select></Field>
    <Field label="Model"><input list="cloud-models" value={config.model} onChange={e => { cloud.cancel(); setConfig({ ...config, model: e.target.value }); }} placeholder="Enter a current model ID" /></Field>
    <datalist id="cloud-models">{Array.isArray(cloud.result) && cloud.result.map(model => <option key={model} value={model} />)}</datalist>
    <Field label="API key (this session only)"><input type="password" autoComplete="off" value={key} onChange={e => setKey(e.target.value)} placeholder={keyAvailable ? "A session key is available" : "Your provider key"} /></Field>
    <Button variant="secondary" onClick={() => { cloud.cancel(); setCloudSessionKey(""); setKey(""); setKeyVersion(value => value + 1); }}>Forget session keys</Button>
    <Button disabled={cloud.pending || (!key.trim() && !keyAvailable)} variant="secondary" onClick={() => test(true)}>Fetch models</Button>
    <Button disabled={cloud.pending || (!key.trim() && !keyAvailable) || !config.model.trim()} variant="secondary" onClick={() => test(false)}>Test connection</Button>
    {cloud.pending && <Button variant="secondary" onClick={cloud.cancel}>Cancel request</Button>}
    {(error || cloud.error) && <p role="alert">{error || cloud.error}</p>}
    {typeof cloud.result === "string" && cloud.result && <p role="status">Connection responded.</p>}
    <Button disabled={busy || (mode === "cloud_ai" && !config.model.trim()) || cloud.pending} onClick={() => {
      try { if (mode === "cloud_ai") cloudEndpoint(config); rememberKey(); setError(""); void run(() => saveProfile({ cloudConfig: config, intelligenceMode: mode }), "Cloud settings saved").then(ok => { if (ok) onClose(); }); }
      catch (caught) { setError(caught instanceof Error ? caught.message : "Invalid settings."); }
    }}>Save settings</Button>
  </Sheet>;
}
