import { useEffect, useId, useRef, useState } from "react";

/** Compose's brush uses physical pixels, independently of its dp layout. */
export function NativeCardShine() {
  const id = `card-shine-${useId().replace(/[^a-zA-Z0-9_-]/g, "")}`;
  const ref = useRef<SVGSVGElement>(null);
  const [size, setSize] = useState({ width: 1, height: 1 });
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    const measure = () => {
      const box = node.getBoundingClientRect();
      const density = window.devicePixelRatio || 1;
      setSize({ width: Math.max(1, box.width * density), height: Math.max(1, box.height * density) });
    };
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const updateMotion = () => setReduced(preference.matches);
    const observer = new ResizeObserver(measure);
    observer.observe(node);
    window.addEventListener("resize", measure);
    preference.addEventListener("change", updateMotion);
    measure();
    updateMotion();
    return () => {
      observer.disconnect();
      window.removeEventListener("resize", measure);
      preference.removeEventListener("change", updateMotion);
    };
  }, []);
  return <svg ref={ref} className="card-shine-layer" aria-hidden="true" preserveAspectRatio="none" viewBox={`0 0 ${size.width} ${size.height}`}>
    <defs><linearGradient id={id} gradientUnits="userSpaceOnUse" x1="-600" y1="0" x2="300" y2={size.height}>
      <stop offset="0" stopColor="var(--accent)" stopOpacity="0.03" />
      <stop offset="0.35" stopColor="var(--accent)" stopOpacity="0.2" />
      <stop offset="0.65" stopColor="var(--accent)" stopOpacity="0.28" />
      <stop offset="1" stopColor="var(--accent)" stopOpacity="0.04" />
      {!reduced && <><animate attributeName="x1" values="-600;1800;-600" dur="10s" calcMode="linear" repeatCount="indefinite" /><animate attributeName="x2" values="300;2700;300" dur="10s" calcMode="linear" repeatCount="indefinite" /></>}
    </linearGradient></defs>
    <rect width={size.width} height={size.height} fill={`url(#${id})`} />
  </svg>;
}
