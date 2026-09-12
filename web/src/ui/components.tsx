import {
  useEffect,
  useId,
  useRef,
  useState,
  type ReactNode,
  type ButtonHTMLAttributes,
} from "react";
import { createPortal } from "react-dom";
import { badgeDefinition } from "../domain/badges";
import { themeNames, themes } from "./theme";
import { useOptionalApp } from "./context";
export const asset = (name: string) =>
  `${import.meta.env.BASE_URL}assets/${/^(forgefox_|iron_grade_|recovery_circuit_|ic_forge_).*\.png$/.test(name) ? `optimized/${name.replace(/\.png$/, ".webp")}` : name}`;
export function Icon({ name, size = 22 }: { name: string; size?: number }) {
  // These are the exact 24dp paths used by the Kotlin app's Compose
  // Icons.Outlined bottom navigation. Keep them filled: rendering these as
  // generic stroked outlines materially changes their silhouettes.
  const composeNavigationPaths: Record<string, string> = {
    home: "M12,5.69l5,4.5V18h-2v-6H9v6H7v-7.81l5,-4.5M12,3L2,12h3v8h6v-6h2v6h6v-8h3L12,3z",
    plans: "M20.57,14.86L22,13.43 20.57,12 17,15.57 8.43,7 12,3.43 10.57,2 9.14,3.43 7.71,2 5.57,4.14 4.14,2.71 2.71,4.14l1.43,1.43L2,7.71l1.43,1.43L2,10.57 3.43,12 7,8.43 15.57,17 12,20.57 13.43,22l1.43,-1.43L16.29,22l2.14,-2.14 1.43,1.43 1.43,-1.43 -1.43,-1.43L22,16.29l-1.43,-1.43z",
    log: "M7,15h7v2L7,17zM7,11h10v2L7,13zM7,7h10v2L7,9zM19,3h-4.18C14.4,1.84 13.3,1 12,1c-1.3,0 -2.4,0.84 -2.82,2L5,3c-0.14,0 -0.27,0.01 -0.4,0.04 -0.39,0.08 -0.74,0.28 -1.01,0.55 -0.18,0.18 -0.33,0.4 -0.43,0.64 -0.1,0.23 -0.16,0.49 -0.16,0.77v14c0,0.27 0.06,0.54 0.16,0.78s0.25,0.45 0.43,0.64c0.27,0.27 0.62,0.47 1.01,0.55 0.13,0.02 0.26,0.03 0.4,0.03h14c1.1,0 2,-0.9 2,-2L21,5c0,-1.1 -0.9,-2 -2,-2zM12,2.75c0.41,0 0.75,0.34 0.75,0.75s-0.34,0.75 -0.75,0.75 -0.75,-0.34 -0.75,-0.75 0.34,-0.75 0.75,-0.75zM19,19L5,19L5,5h14v14z",
    stats: "M3.5,18.49l6,-6.01 4,4L22,6.92l-1.41,-1.41 -7.09,7.97 -4,-4L2,16.99l1.5,1.5z",
    settings: "M19.43,12.98c0.04,-0.32 0.07,-0.64 0.07,-0.98 0,-0.34 -0.03,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64l-2,-3.46c-0.09,-0.16 -0.26,-0.25 -0.44,-0.25 -0.06,0 -0.12,0.01 -0.17,0.03l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98l-0.38,-2.65C14.46,2.18 14.25,2 14,2h-4c-0.25,0 -0.46,0.18 -0.49,0.42l-0.38,2.65c-0.61,0.25 -1.17,0.59 -1.69,0.98l-2.49,-1c-0.06,-0.02 -0.12,-0.03 -0.18,-0.03 -0.17,0 -0.34,0.09 -0.43,0.25l-2,3.46c-0.13,0.22 -0.07,0.49 0.12,0.64l2.11,1.65c-0.04,0.32 -0.07,0.65 -0.07,0.98 0,0.33 0.03,0.66 0.07,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.09,0.16 0.26,0.25 0.44,0.25 0.06,0 0.12,-0.01 0.17,-0.03l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.03,0.24 0.24,0.42 0.49,0.42h4c0.25,0 0.46,-0.18 0.49,-0.42l0.38,-2.65c0.61,-0.25 1.17,-0.59 1.69,-0.98l2.49,1c0.06,0.02 0.12,0.03 0.18,0.03 0.17,0 0.34,-0.09 0.43,-0.25l2,-3.46c0.12,-0.22 0.07,-0.49 -0.12,-0.64l-2.11,-1.65zM17.45,11.27c0.04,0.31 0.05,0.52 0.05,0.73 0,0.21 -0.02,0.43 -0.05,0.73l-0.14,1.13 0.89,0.7 1.08,0.84 -0.7,1.21 -1.27,-0.51 -1.04,-0.42 -0.9,0.68c-0.43,0.32 -0.84,0.56 -1.25,0.73l-1.06,0.43 -0.16,1.13 -0.2,1.35h-1.4l-0.19,-1.35 -0.16,-1.13 -1.06,-0.43c-0.43,-0.18 -0.83,-0.41 -1.23,-0.71l-0.91,-0.7 -1.06,0.43 -1.27,0.51 -0.7,-1.21 1.08,-0.84 0.89,-0.7 -0.14,-1.13c-0.03,-0.31 -0.05,-0.54 -0.05,-0.74s0.02,-0.43 0.05,-0.73l0.14,-1.13 -0.89,-0.7 -1.08,-0.84 0.7,-1.21 1.27,0.51 1.04,0.42 0.9,-0.68c0.43,-0.32 0.84,-0.56 1.25,-0.73l1.06,-0.43 0.16,-1.13 0.2,-1.35h1.39l0.19,1.35 0.16,1.13 1.06,0.43c0.43,0.18 0.83,0.41 1.23,0.71l0.91,0.7 1.06,-0.43 1.27,-0.51 0.7,1.21 -1.07,0.85 -0.89,0.7 0.14,1.13zM12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM12,14c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z",
  };
  const paths: Record<string, ReactNode> = {
    back: <path d="m14 5-7 7 7 7" />,
    next: <path d="m9 5 7 7-7 7" />,
    fire: <path d="M12 22c4 0 7-2.7 7-6.5 0-2.7-1.5-5.1-4.1-7.2.1 2-1 3.4-2.1 4.1.2-3.7-1.7-7.1-5-9.4.2 3.8-2.8 6.3-2.8 10.6C5 18.4 8 22 12 22Z" />,
    program: <><rect x="6" y="4" width="14" height="16" rx="2" /><path d="M3 8v13h13M10 8h6M10 12h6M10 16h4" /></>,
    import: <><path d="M12 3v12m-4-4 4 4 4-4M5 20h14" /></>,
    spark: <><path d="m12 2 1.2 4.1L17 8l-3.8 1.9L12 14l-1.2-4.1L7 8l3.8-1.9ZM5 14l.8 2.2L8 17l-2.2.8L5 20l-.8-2.2L2 17l2.2-.8ZM19 13l.7 1.8 1.8.7-1.8.7L19 18l-.7-1.8-1.8-.7 1.8-.7Z" /></>,
    list: <path d="M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01" />,
    search: <><circle cx="10" cy="10" r="6" /><path d="m15 15 6 6" /></>,
    filter: <path d="M4 6h16M7 12h10M10 18h4" />,
    camera: <><rect x="3" y="6" width="18" height="14" rx="2" /><circle cx="12" cy="13" r="4" /><path d="m8 6 1.5-3h5L16 6" /></>,
    calendar: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4M17 3v4M3 10h18M8 14h.01M12 14h.01M16 14h.01M8 18h.01M12 18h.01" /></>,
    body: <><rect x="5" y="3" width="14" height="18" rx="2" /><circle cx="12" cy="8" r="1" /><path d="M9 13h6M10 17h4" /></>,
    plus: <path d="M12 4v16M4 12h16" />,
    check: <path d="m4 12 5 5L20 6" />,
    close: <path d="m5 5 14 14M5 19 19 5" />,
    more: (
      <>
        <circle cx="12" cy="5" r="1" />
        <circle cx="12" cy="12" r="1" />
        <circle cx="12" cy="19" r="1" />
      </>
    ),
    timer: (
      <>
        <circle cx="12" cy="13" r="8" />
        <path d="M12 9v5l3 2M9 2h6" />
      </>
    ),
    trash: (
      <>
        <path d="M4 6h16M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7M14 10v7" />
      </>
    ),
    edit: (
      <>
        <path d="m4 16 12-12 4 4L8 20H4Z" />
        <path d="m13 7 4 4" />
      </>
    ),
    share: (
      <>
        <circle cx="5" cy="12" r="3" />
        <circle cx="19" cy="5" r="3" />
        <circle cx="19" cy="19" r="3" />
        <path d="m8 10 8-4M8 14l8 4" />
      </>
    ),
  };
  const composePath = composeNavigationPaths[name];
  return (
    <svg
      className={`ironlog-icon ironlog-icon-${name}${name === "log" || name === "stats" ? " auto-mirror" : ""}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {composePath ? <path d={composePath} fill="currentColor" stroke="none" /> : paths[name] ?? paths.next}
    </svg>
  );
}
export function Button({
  children,
  variant = "primary",
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
}) {
  return (
    <button {...props} className={`button ${variant} ${className}`}>
      {children}
    </button>
  );
}
export function IconButton({
  name,
  label,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { name: string; label: string }) {
  return (
    <button {...props} className="icon-button" aria-label={label}>
      <Icon name={name} />
    </button>
  );
}
export function Switch({
  checked,
  onChange,
  label,
  disabled = false,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <label className="ironlog-switch-row">
      <span>{label}</span>
      <span className="ironlog-switch">
        <input
          type="checkbox"
          role="switch"
          checked={checked}
          disabled={disabled}
          onChange={(event) => onChange(event.target.checked)}
        />
        <i aria-hidden="true" />
      </span>
    </label>
  );
}
export function RollingTimerText({ value }: { value: string }) {
  return (
    <span className="rolling-timer" aria-label={value}>
      {Array.from(value).map((character, index) => (
        <span
          aria-hidden="true"
          className="rolling-timer-slot"
          key={`${index}-${character}`}
        >
          {character}
        </span>
      ))}
    </span>
  );
}
export function Sheet({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const app = useOptionalApp();
  const ref = useRef<HTMLDialogElement>(null);
  const id = useId();
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const dialog = ref.current;
    dialog?.showModal();
    const old = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      dialog?.close();
      document.body.style.overflow = old;
      previous?.focus();
    };
  }, []);
  return createPortal(
    <dialog
      ref={ref}
      tabIndex={-1}
      aria-labelledby={id}
      className="sheet"
      onKeyDown={(e) => {
        if (e.key !== "Tab" || e.altKey || e.ctrlKey || e.metaKey) return;
        const dialog = e.currentTarget;
        // Native modal dialogs make the page inert, but some engines still
        // move Tab to browser chrome after the last control. Wrap explicitly
        // so keyboard users stay in the sheet until they dismiss it.
        const controls = Array.from(
          dialog.querySelectorAll<HTMLElement>(
            'button, a[href], input, select, textarea, summary, [tabindex], [contenteditable="true"]',
          ),
        ).filter(
          (node) =>
            node.tabIndex >= 0 &&
            !node.matches(":disabled") &&
            !node.closest("[inert]") &&
            node.getClientRects().length > 0 &&
            getComputedStyle(node).visibility !== "hidden",
        );
        const first = controls[0];
        const last = controls.at(-1);
        if (!first) {
          e.preventDefault();
          dialog.focus();
        } else if (
          e.shiftKey &&
          (document.activeElement === first ||
            document.activeElement === dialog)
        ) {
          e.preventDefault();
          last?.focus();
        } else if (
          !e.shiftKey &&
          (document.activeElement === last || document.activeElement === dialog)
        ) {
          e.preventDefault();
          first.focus();
        }
      }}
      onCancel={(e) => {
        e.preventDefault();
        close.current();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          const r = e.currentTarget.getBoundingClientRect();
          if (
            e.clientX < r.left ||
            e.clientX > r.right ||
            e.clientY < r.top ||
            e.clientY > r.bottom
          )
            close.current();
        }
      }}
    >
      <header className="sheet-head">
        <h2 id={id}>{title}</h2>
        <IconButton name="close" label={`Close ${title}`} onClick={onClose} />
      </header>
      <div className="sheet-body">
        {app?.error && (
          <div className="error" role="alert">
            {app.error}
          </div>
        )}
        {children}
      </div>
    </dialog>,
    document.body,
  );
}
export function NumberWheel({
  label,
  value,
  min,
  max,
  step = 1,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (n: number) => void;
}) {
  const id = useId();
  const [input, setInput] = useState(String(value));
  useEffect(() => setInput(String(value)), [value]);
  const shift = (delta: number) =>
    onChange(
      Math.min(max, Math.max(min, Number((value + delta * step).toFixed(2)))),
    );
  return (
    <div className="number-wheel">
      <label htmlFor={id}>{label}</label>
      <div className="wheel-control">
        <button
          type="button"
          aria-label={`Decrease ${label}`}
          onClick={() => shift(-1)}
          disabled={value <= min}
        >
          −
        </button>
        <input
          id={id}
          type="number"
          inputMode="decimal"
          min={min}
          max={max}
          step={step}
          value={input}
          onChange={(e) => {
            setInput(e.target.value);
            if (e.target.value !== "" && e.target.validity.valid)
              onChange(Number(e.target.value));
          }}
          onBlur={() => {
            const number = input.trim() === "" ? value : Number(input);
            const clamped = Math.min(
              max,
              Math.max(min, Number.isFinite(number) ? number : value),
            );
            const next = Number(
              (min + Math.round((clamped - min) / step) * step).toFixed(2),
            );
            setInput(String(next));
            onChange(next);
          }}
        />
        <button
          type="button"
          aria-label={`Increase ${label}`}
          onClick={() => shift(1)}
          disabled={value >= max}
        >
          +
        </button>
      </div>
      <div className="wheel-neighbors" aria-hidden="true">
        {Math.max(min, value - step)} <span>·</span>{" "}
        {Math.min(max, value + step)}
      </div>
    </div>
  );
}
export function ThemePicker({
  value,
  onChange,
}: {
  value: string;
  onChange: (id: string) => void;
}) {
  return (
    <div className="theme-picker" role="group" aria-label="Appearance">
      {Object.entries(themeNames).map(([id, label]) => (
        <button
          key={id}
          aria-pressed={id === value}
          onClick={() => onChange(id)}
        >
          <span
            className="swatch"
            style={{
              background: themes[id].bg,
              borderColor: themes[id].accent,
            }}
          >
            <i style={{ background: themes[id].accent }} />
          </span>
          <span>
            {label}
            {id === "monet" && <small>Browser fallback</small>}
          </span>
          {id === value && <Icon name="check" size={18} />}
        </button>
      ))}
    </div>
  );
}
export function Fox({
  pose = "20_clipboard",
  className = "",
}: {
  pose?: string;
  className?: string;
}) {
  return (
    <img
      className={`fox ${className}`}
      src={asset(`forgefox_${pose}.png`)}
      alt=""
      loading="lazy"
    />
  );
}
export function Grade({
  grade = "uncalibrated",
  size = 72,
}: {
  grade?: string;
  size?: number;
}) {
  const key = grade.toLowerCase().split(" ")[0];
  return (
    <img
      src={asset(`iron_grade_${key}.png`)}
      width={size}
      height={size}
      className="grade-art"
      alt={`${grade} grade`}
    />
  );
}
export function AchievementBadge({
  id,
  size = 48,
}: {
  id: string;
  size?: number;
}) {
  const definition = badgeDefinition(id);
  return (
    <img
      src={asset(`badges/${definition.icon}`)}
      width={size}
      height={size}
      className="achievement-badge"
      alt=""
    />
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="empty">
      <Fox pose="07_determined" />
      <h2>{title}</h2>
      {children}
    </div>
  );
}
export function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}
export function Progress({
  value,
  max = 100,
  label,
}: {
  value: number;
  max?: number;
  label: string;
}) {
  return (
    <div
      className="progress"
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={Math.min(max, value)}
    >
      <i
        style={{ width: `${Math.min(100, Math.max(0, (value / max) * 100))}%` }}
      />
    </div>
  );
}
