import { useState } from "react";
import { saveExerciseNextNote } from "../data/store";
import { useApp } from "../ui/context";
import { Button, Field, Sheet } from "../ui/components";

export function ExerciseNextNote({ exerciseId }: { exerciseId: string }) {
  const { data, busy, run } = useApp();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const saved =
    data.profile.exerciseNextNotes?.[`exercise_next_note:${exerciseId}`] ?? "";
  if (!data.exercises.some((e) => e.id === exerciseId)) return null;
  const save = (note: string) =>
    run(
      () => saveExerciseNextNote(exerciseId, note),
      "Setup reminder saved",
    ).then((ok) => {
      if (ok) setEditing(false);
    });
  return (
    <div className="setup-reminder">
      {saved && (
        <p
          className="exercise-note"
          style={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}
        >
          <strong>Setup reminder</strong>
          <br />
          {saved}
        </p>
      )}
      <button
        className="text-button"
        onClick={() => {
          setDraft(saved);
          setEditing(true);
        }}
      >
        {saved ? "Edit setup reminder" : "Add setup reminder"}
      </button>
      {editing && (
        <Sheet title="Exercise setup" onClose={() => setEditing(false)}>
          <p>Saved for this exercise across routines and future sessions.</p>
          <Field label="Setup reminder">
            <textarea
              maxLength={4000}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="Seat height, grip or equipment setup"
            />
          </Field>
          <small className="muted">{draft.length} / 4,000 characters</small>
          <Button disabled={busy} onClick={() => save(draft)}>
            Save reminder
          </Button>
          {saved && (
            <Button variant="ghost" disabled={busy} onClick={() => save("")}>
              Remove reminder
            </Button>
          )}
        </Sheet>
      )}
    </div>
  );
}
