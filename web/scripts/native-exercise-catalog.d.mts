import type { Exercise } from "../src/domain/types";
interface NativeExercise {
  id: string;
  name: string;
  trackingType?: string;
  category?: string;
  equipment?: string;
  primaryMuscle?: string;
  primaryMuscles?: string[];
  secondaryMuscles?: string[];
  aliases?: string[];
  isBodyweight?: boolean;
  requiresExternalLoad?: boolean;
  movementPattern?: string;
  difficulty?: string;
}
export function nativeTrackingNormalizer(source: string): (entry: NativeExercise, category: string, equipment: string) => string;
export function nativeExerciseCatalog(entries: NativeExercise[], normalizerSource: string): Exercise[];
