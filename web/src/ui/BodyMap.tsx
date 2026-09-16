import body from "../generated/body-map.json";
const regions: Record<string, string> = {
  chest: "Push",
  back: "Pull",
  shoulders: "Shoulders",
  rearDelts: "Shoulders",
  arms: "Arms",
  core: "Core",
  quads: "Legs",
  hamstrings: "Legs",
  calves: "Legs",
};
const groupByFine = Object.fromEntries(
  Object.entries(body.MUSCLE_MAP).flatMap(([g, names]) =>
    names.map((n) => [n, regions[g]]),
  ),
);
export function BodyMap({
  scores,
  side = "front",
  onSelect,
}: {
  scores: Record<string, number>;
  side?: "front" | "back";
  onSelect?: (region: string) => void;
}) {
  const paths = side === "front" ? body.MALE_FRONT_PATHS : body.MALE_BACK_PATHS;
  const v = body.VIEW_BOXES.male[side];
  return (
    <svg
      className="body-map"
      viewBox={`${v.x} ${v.y} ${v.width} ${v.height}`}
      preserveAspectRatio="xMidYMin meet"
      role="img"
      aria-label={`${side} muscle recovery estimate. Detailed values listed below.`}
    >
      {Object.entries(paths).map(([muscle, segments]) => {
        const region = groupByFine[muscle],
          value = scores[region];
        const color =
          value === undefined
            ? "var(--cardBorder)"
            : value >= 90
              ? "var(--success)"
              : value >= 72
                ? "var(--warning)"
                : "var(--danger)";
        return (
          <g
            key={muscle}
            className={region ? "body-map-region" : undefined}
            fill={color}
            stroke="var(--bg)"
            strokeWidth="2"
            role={region ? "button" : undefined}
            tabIndex={region ? 0 : undefined}
            aria-label={region ? `Open ${region} recovery evidence` : undefined}
            onClick={() => region && onSelect?.(region)}
            onKeyDown={(event) => { if (region && (event.key === "Enter" || event.key === " ")) { event.preventDefault(); onSelect?.(region); } }}
          >
            {Object.values(segments)
              .flat()
              .map((d, i) => (
                <path key={i} d={d} />
              ))}
          </g>
        );
      })}
    </svg>
  );
}
