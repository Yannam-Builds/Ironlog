import { useId, useState } from "react";
import { saveSpacing, useSpacing } from "./spacing";
export function SpacingPicker() {
  const value = useSpacing();
  const [error, setError] = useState("");
  const id = useId();
  const change = (next: number) => {
    try {
      saveSpacing(next);
      setError("");
    } catch (error) {
      setError(error instanceof Error ? error.message : String(error));
    }
  };
  return (
    <div className="spacing-picker">
      <label className="field" htmlFor={id}>
        <span>UI spacing · {value}%</span>
      </label>
      <input
        id={id}
        type="range"
        min="85"
        max="125"
        step="5"
        value={value}
        aria-label="UI spacing"
        aria-describedby={`${id}-help`}
        aria-valuetext={`${value} percent, ${value < 100 ? "compact" : value > 100 ? "spacious" : "default"}`}
        onChange={(event) => change(Number(event.target.value))}
      />
      <div className="row">
        <small>Compact</small>
        <small>Spacious</small>
      </div>
      <p id={`${id}-help`} className="font-help">
        Adjust padding and gaps across screens, cards, forms and sheets. Text,
        artwork and minimum touch targets keep their size. Saved in this
        browser.
      </p>
      <button
        className="button secondary"
        onClick={() => change(100)}
        disabled={value === 100}
      >
        Reset spacing
      </button>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
