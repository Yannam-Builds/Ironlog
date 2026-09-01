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
  const paths: Record<string, ReactNode> = {
    home: (
      <>
        <path d="m3 10 9-7 9 7v10H3Z" />
        <path d="M9 20v-7h6v7" />
      </>
    ),
    plans: (
      <>
        <rect x="5" y="3" width="14" height="18" rx="2" />
        <path d="M9 8h6M9 12h6M9 16h4" />
      </>
    ),
    log: (
      <>
        <path d="M5 8v8M2 10v4M19 8v8M22 10v4M5 12h14" />
      </>
    ),
    stats: (
      <>
        <path d="M4 20V12M10 20V7M16 20V3M22 20V9" />
      </>
    ),
    settings: (
      <>
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v3M12 19v3M2 12h3M19 12h3m-15-8 2 2m10 10 2 2M4 20l2-2M18 6l2-2" />
      </>
    ),
    back: <path d="m14 5-7 7 7 7" />,
    next: <path d="m9 5 7 7-7 7" />,
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
  return (
    <svg
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
      {paths[name] ?? paths.next}
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
