import { Button } from "../../ui/components";
import { useApp } from "../../ui/context";
import { useCloudRequest } from "../../ui/useCloudRequest";
import { useEffect } from "react";
import { cloudCoachingEnabled } from "../../domain/coaching-mode";
export function CloudInsight({ title, prompt }: { title: string; prompt: string }) {
  const { data } = useApp(); const cloud = useCloudRequest();
  const configKey = JSON.stringify([data.profile.cloudConfig, data.profile.intelligenceMode]);
  useEffect(() => { cloud.cancel(); }, [prompt, configKey]);
  if (!data.profile.cloudConfig || !cloudCoachingEnabled(data.profile)) return null;
  return <section className="card"><h2>{title}</h2><p>Optional provider response. Review its advice before changing your training. Only this request’s training summary is sent.</p>
    <details><summary>Review data to send</summary><pre className="code-preview">{prompt}</pre></details>
    <Button disabled={!data.profile.cloudConfig || cloud.pending} onClick={() => { if (data.profile.cloudConfig) void cloud.start(data.profile.cloudConfig, prompt); }}>Ask Cloud AI</Button>
    {!data.profile.cloudConfig && <p>Configure your provider in Training Intelligence → Cloud AI.</p>}
    {cloud.pending && <Button variant="secondary" onClick={cloud.cancel}>Cancel request</Button>}
    {cloud.error && <p role="alert">{cloud.error}</p>}
    {typeof cloud.result === "string" && cloud.result && <p className="cloud-response" style={{ whiteSpace: "pre-wrap" }}>{cloud.result}</p>}
  </section>;
}
