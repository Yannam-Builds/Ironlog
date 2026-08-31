import type { Exercise } from "./domain/types";
export async function loadCatalog(): Promise<Exercise[]> {
  const response = await fetch(
    `${import.meta.env.BASE_URL}data/exercises.json`,
  );
  if (!response.ok)
    throw Error(
      "The bundled exercise library could not be loaded. Open IronLog online once before using it offline.",
    );
  return response.json();
}
