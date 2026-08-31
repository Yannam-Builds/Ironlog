import { useEffect, useState } from "react";
import { Field } from "./components";
import {
  fonts,
  saveTypography,
  typographyPresets,
  useTypography,
} from "./typography";

export function FontPicker() {
  const choice = useTypography();
  const [error, setError] = useState("");
  const [fontFailed, setFontFailed] = useState(false);
  const selected = fonts.find((font) => font.id === choice.family)!;
  useEffect(() => {
    let current = true;
    setFontFailed(false);
    if (!document.fonts?.load) return;
    const family =
      choice.family === "lexend" ? "Lexend" : `"IronLog ${choice.family}"`;
    void document.fonts.load(`400 16px ${family}`, "Bench 65 kg × 8").then(
      (faces) => {
        if (current) setFontFailed(faces.length === 0);
      },
      () => {
        if (current) setFontFailed(true);
      },
    );
    return () => {
      current = false;
    };
  }, [choice.family]);
  const change = (patch: Partial<typeof choice>) => {
    try {
      saveTypography({ ...choice, ...patch });
      setError("");
    } catch (error) {
      setError(String(error instanceof Error ? error.message : error));
    }
  };
  return (
    <div className="font-picker">
      <div className="two-col">
        <Field label="Font family">
          <select
            value={choice.family}
            onChange={(event) => change({ family: event.target.value })}
          >
            {fonts.map((font) => (
              <option key={font.id} value={font.id}>
                {font.name}
                {font.id === "lexend" ? " · landing font" : ""}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Font weight">
          <select
            value={choice.preset}
            onChange={(event) =>
              change({ preset: event.target.value as typeof choice.preset })
            }
          >
            {typographyPresets.map((preset) => (
              <option key={preset.id} value={preset.id}>
                {preset.name}
                {preset.id === "light" ? " · landing feel" : ""}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div className="font-preview" aria-label="Selected font preview">
        <small>
          {selected.name} ·{" "}
          {typographyPresets.find((p) => p.id === choice.preset)!.name}
        </small>
        <h3>One more good set.</h3>
        <p>Bench press · 65 kg × 8 reps</p>
        <span className="font-numerals">0123456789 · 1:30</span>
      </div>
      <p className="muted font-help">
        Lexend is the landing font. Choose Light for its lighter feel. Your
        selection applies to every screen and the website, and stays in this
        browser. Fonts are bundled locally.
      </p>
      <a
        className="font-license"
        href={`${import.meta.env.BASE_URL}${selected.license}`}
        target="_blank"
        rel="noopener noreferrer"
      >
        {selected.name} font license{" "}
        <span className="sr-only">(opens a new tab)</span>
      </a>
      {fontFailed && (
        <div role="status" className="font-load-error">
          <p>
            Couldn't load this font. Your selection is saved; readable fallback
            text is shown. Reconnect and reopen the page, or use Lexend.
          </p>
          <button
            className="button secondary"
            onClick={() => change({ family: "lexend" })}
          >
            Use Lexend instead
          </button>
        </div>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
