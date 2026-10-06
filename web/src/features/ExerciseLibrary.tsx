import { useEffect, useMemo, useState } from "react";
import { useApp, navigate } from "../ui/context";
import { Button, Icon, IconButton, Sheet } from "../ui/components";
import { deleteCustomExercise, setExerciseFavorite } from "../data/store";
import { queryExerciseSearch } from "../domain/exercise-search";
import type { Exercise } from "../domain/types";
import { ExercisePicker } from "./Plans";

type Filters = {
  scope: "all" | "favorites" | "custom";
  muscle: string;
  equipment: string;
  category: string;
  movementPattern: string;
  difficulty: string;
  bodyweight: boolean;
};
const emptyFilters: Filters = {
  scope: "all",
  muscle: "",
  equipment: "",
  category: "",
  movementPattern: "",
  difficulty: "",
  bodyweight: false,
};
const title = (value: string) =>
  value ? value[0].toUpperCase() + value.slice(1) : value;
const normal = (value: string) =>
  value
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
const equipmentTags = [
  "Barbell",
  "Dumbbell",
  "Cable",
  "Machine",
  "Bodyweight",
  "Band",
  "Kettlebell",
  "Other",
];

export function ExerciseLibrary() {
  const { data, run, busy } = useApp();
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [showFilters, setShowFilters] = useState(false);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<Exercise>();
  const [limit, setLimit] = useState(100);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(query), 180);
    return () => window.clearTimeout(timer);
  }, [query]);
  useEffect(() => setLimit(100), [debounced, filters]);
  const favorites = useMemo(
    () => new Set(data.profile.favoriteExerciseIds ?? []),
    [data.profile.favoriteExerciseIds],
  );
  const unavailable =
    data.gyms.find((gym) => gym.id === data.profile.activeGymId)
      ?.unavailableEquipment ?? [];
  const options = useMemo(() => {
    const values = (
      key: "equipment" | "category" | "movementPattern" | "difficulty",
    ) =>
      [
        ...new Set(
          data.exercises
            .map((ex) => ex[key]?.trim())
            .filter((v): v is string => !!v),
        ),
      ].sort();
    return {
      muscle: [
        ...new Set(
          data.exercises
            .flatMap((ex) => [
              ex.muscle,
              ...(ex.primaryMuscles ?? []),
              ...(ex.secondaryMuscles ?? []),
            ])
            .filter(Boolean),
        ),
      ].sort(),
      equipment: values("equipment").filter((value) =>
        equipmentTags.some((tag) => normal(tag) === normal(value)),
      ),
      category: values("category"),
      movementPattern: values("movementPattern"),
      difficulty: ["beginner", "intermediate", "advanced", "expert"].filter(
        (value) => values("difficulty").includes(value),
      ),
    };
  }, [data.exercises]);
  const list = queryExerciseSearch(
    data.exercises.filter((ex) => {
      const haystack = normal(
        [
          ex.name,
          ex.muscle,
          ...(ex.primaryMuscles ?? []),
          ...(ex.secondaryMuscles ?? []),
          ex.equipment,
          ex.category ?? "",
          ex.movementPattern ?? "",
          ex.difficulty ?? "",
        ].join(" "),
      );
      return (
        (!filters.muscle || haystack.includes(normal(filters.muscle))) &&
        (!filters.equipment ||
          normal(ex.equipment) === normal(filters.equipment)) &&
        (!filters.category ||
          normal(ex.category ?? "") === normal(filters.category)) &&
        (!filters.movementPattern ||
          normal(ex.movementPattern ?? "") ===
            normal(filters.movementPattern)) &&
        (!filters.difficulty ||
          normal(ex.difficulty ?? "") === normal(filters.difficulty)) &&
        (!filters.bodyweight || ex.isBodyweight === true) &&
        (filters.scope !== "favorites" || favorites.has(ex.id)) &&
        (filters.scope !== "custom" || ex.custom === true) &&
        !unavailable.some((value) => normal(value) === normal(ex.equipment))
      );
    }),
    debounced,
  ).sort(
    (a, b) =>
      Number(favorites.has(b.id)) - Number(favorites.has(a.id)) ||
      a.name.localeCompare(b.name),
  );
  const activeFilters = Object.entries(filters).filter(([key, value]) =>
    key === "scope" ? value !== "all" : !!value,
  );
  const recent = [
    ...new Set(
      data.workouts
        .filter((workout) => workout.status === "completed")
        .sort(
          (a, b) =>
            (b.completedAt ?? b.startedAt) - (a.completedAt ?? a.startedAt),
        )
        .flatMap((workout) => workout.exercises.map((ex) => ex.name)),
    ),
  ].slice(0, 5);
  const canCreate =
    !!query.trim() &&
    !data.exercises.some((ex) => normal(ex.name) === normal(query));
  const toggle = (key: keyof Filters, value: string | boolean) =>
    setFilters((current) =>
      key === "scope" && value === "all"
        ? { ...current, scope: "all", bodyweight: false }
        : {
            ...current,
            [key]: current[key] === value ? emptyFilters[key] : value,
          },
    );

  return (
    <section className="exercise-library">
      <div className="library-toolbar">
        <div className="library-active-filters">
          {activeFilters.map(([key, value]) => {
            const label =
              key === "bodyweight" ? "BW Only" : title(String(value));
            return (
              <button
                key={key}
                className="library-chip active"
                aria-label={`Remove ${label} filter`}
                onClick={() =>
                  setFilters((current) => ({
                    ...current,
                    [key]: emptyFilters[key as keyof Filters],
                  }))
                }
              >
                {label}
                <Icon name="close" size={16} />
              </button>
            );
          })}
          {unavailable.length > 0 && (
            <span className="library-gym-filter">Gym filtered</span>
          )}
          {!activeFilters.length && !unavailable.length && (
            <span className="muted">All exercises</span>
          )}
        </div>
        <Button
          variant="secondary"
          className={
            activeFilters.length
              ? "library-filter-button active"
              : "library-filter-button"
          }
          onClick={() => setShowFilters(true)}
        >
          <Icon name="filter-list" size={18} />
          Filters
        </Button>
      </div>
      <div className="library-search">
        <Icon name="search" size={18} />
        <input
          aria-label="Search exercises"
          placeholder="Search exercises…"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          maxLength={160}
        />
        {query && (
          <IconButton
            name="close"
            label="Clear search"
            onClick={() => setQuery("")}
          />
        )}
      </div>
      <div className="library-summary">
        <p className="muted" aria-live="polite">
          {list.length} exercise{list.length === 1 ? "" : "s"}
        </p>
        <Button variant="ghost" onClick={() => setCreating(true)}>
          <Icon name="plus" size={18} />
          Create custom exercise
        </Button>
      </div>
      {!!recent.length && !debounced.trim() && !activeFilters.length && (
        <div className="library-recent">
          <span className="eyebrow">Recently used</span>
          <div className="library-recent-chips">
            {recent.map((name) => (
              <button
                className="library-chip"
                key={name}
                onClick={() => setQuery(name)}
              >
                {name}
              </button>
            ))}
          </div>
        </div>
      )}
      <div className="library-rows">
        {list.slice(0, limit).map((ex) => (
          <article className="library-row" key={ex.id}>
            <button
              className="library-exercise"
              aria-label={`View progress for ${ex.name}`}
              onClick={() => navigate(`exercise/${encodeURIComponent(ex.id)}`)}
            >
              <span className="library-name">
                <strong>{ex.name}</strong>
                {ex.custom && <span className="library-custom">Custom</span>}
              </span>
              <small>
                {[ex.muscle, ex.equipment, title(ex.difficulty ?? "")]
                  .filter(Boolean)
                  .join(" · ")}
              </small>
            </button>
            <div className="library-row-actions">
              <IconButton
                name={favorites.has(ex.id) ? "star-filled" : "star"}
                label={`${favorites.has(ex.id) ? "Unfavorite" : "Favorite"} ${ex.name}`}
                aria-pressed={favorites.has(ex.id)}
                disabled={busy}
                onClick={() =>
                  void run(() =>
                    setExerciseFavorite(ex.id, !favorites.has(ex.id)),
                  )
                }
              />
              {ex.custom && (
                <IconButton
                  name="delete-outline"
                  label={`Delete ${ex.name}`}
                  disabled={busy}
                  onClick={() => setDeleting(ex)}
                />
              )}
            </div>
          </article>
        ))}
        {!list.length && (
          <div className="empty">
            <h2>No exercises found</h2>
            <p className="muted">Try a different filter or search term.</p>
          </div>
        )}
      </div>
      {list.length > limit && (
        <Button
          variant="secondary"
          className="library-more"
          onClick={() => setLimit((current) => current + 100)}
        >
          Show more exercises ({list.length - limit} remaining)
        </Button>
      )}
      {canCreate && (
        <div className="library-missing">
          <p className="muted">Can't find it?</p>
          <Button variant="secondary" onClick={() => setCreating(true)}>
            <Icon name="plus" />
            Add exercise
          </Button>
        </div>
      )}
      {showFilters && (
        <Sheet title="Filters" onClose={() => setShowFilters(false)}>
          <fieldset className="choice-fieldset library-filter-group">
            <legend>Scope</legend>
            <div className="choice-chips">
              {(["all", "favorites", "custom"] as const).map((scope) => (
                <button
                  key={scope}
                  className={`library-chip ${filters.scope === scope && (scope !== "all" || !filters.bodyweight) ? "active" : ""}`}
                  aria-pressed={filters.scope === scope && (scope !== "all" || !filters.bodyweight)}
                  onClick={() => toggle("scope", scope)}
                >
                  {title(scope)}
                </button>
              ))}
              <button
                className={`library-chip ${filters.bodyweight ? "active" : ""}`}
                aria-pressed={filters.bodyweight}
                onClick={() => toggle("bodyweight", true)}
              >
                BW Only
              </button>
            </div>
          </fieldset>
          {(
            [
              ["muscle", "Muscle group"],
              ["equipment", "Equipment"],
              ["category", "Category"],
              ["movementPattern", "Movement pattern"],
              ["difficulty", "Difficulty"],
            ] as const
          ).map(
            ([key, label]) =>
              !!options[key].length && (
                <fieldset className="choice-fieldset library-filter-group" key={key}>
                  <legend>{label}</legend>
                  <div className="choice-chips">
                    {options[key].map((value) => (
                      <button
                        className={`library-chip ${filters[key] === value ? "active" : ""}`}
                        aria-pressed={filters[key] === value}
                        key={value}
                        onClick={() => toggle(key, value)}
                      >
                        {title(value)}
                      </button>
                    ))}
                  </div>
                </fieldset>
              ),
          )}
          <div className="library-filter-actions">
            <Button variant="ghost" onClick={() => setFilters(emptyFilters)}>
              Clear filters
            </Button>
            <Button onClick={() => setShowFilters(false)}>
              Show exercises
            </Button>
          </div>
        </Sheet>
      )}
      {creating && (
        <ExercisePicker
          createOnly
          initialQuery={query}
          onClose={() => setCreating(false)}
          onPick={(ex) => {
            setCreating(false);
            setQuery(ex.name);
            setFilters(emptyFilters);
          }}
        />
      )}
      {deleting && (
        <Sheet
          title={`Delete "${deleting.name}"?`}
          onClose={() => setDeleting(undefined)}
        >
          <p>This will permanently remove this custom exercise.</p>
          <Button
            variant="danger"
            disabled={busy}
            onClick={() =>
              void run(
                () => deleteCustomExercise(deleting.id),
                "Exercise deleted",
              ).then((ok) => {
                if (ok) setDeleting(undefined);
              })
            }
          >
            Delete exercise
          </Button>
          <Button variant="secondary" onClick={() => setDeleting(undefined)}>
            Cancel
          </Button>
        </Sheet>
      )}
    </section>
  );
}
