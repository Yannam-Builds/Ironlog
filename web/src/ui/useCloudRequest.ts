import { useEffect, useRef, useState } from "react";
import { requestCloud, type CloudConfig } from "../domain/cloud-ai";

export function useCloudRequest() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<string | string[]>("");
  const request = useRef<AbortController | null>(null);
  const generation = useRef(0);
  const cancel = () => { generation.current++; request.current?.abort(); request.current = null; setPending(false); setResult(""); setError(""); };
  useEffect(() => () => { generation.current++; request.current?.abort(); }, []);
  const start = async (config: CloudConfig, prompt: string, models = false) => {
    request.current?.abort(); const token = ++generation.current;
    const controller = new AbortController(); request.current = controller;
    setPending(true); setError(""); setResult("");
    try {
      const value = await requestCloud(config, prompt, { signal: controller.signal, models });
      if (token !== generation.current) return;
      setResult(value); return value;
    } catch (caught) {
      if (token === generation.current) setError(caught instanceof Error ? caught.message : "Request failed.");
    } finally { if (token === generation.current) { setPending(false); request.current = null; } }
  };
  return { pending, error, result, start, cancel };
}
