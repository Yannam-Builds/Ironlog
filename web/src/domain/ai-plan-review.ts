import type { Exercise, Gym, Plan, Profile } from "./types";
export const aiEquipmentOptions = ["Barbell", "Dumbbell", "Cable", "Machine", "Bodyweight", "Band", "Kettlebell", "Other"];
export function activeGymEquipment(profile: Profile, gyms: Gym[]) {
  const unavailable = gyms.find(gym => gym.id === profile.activeGymId)?.unavailableEquipment ?? [];
  return aiEquipmentOptions.filter(item => !unavailable.some(value => value.toLowerCase() === item.toLowerCase()));
}
export function unresolvedPlanRows(plans: Plan[], library: Exercise[], reviewed: string[] = []) {
  const known = new Set([...library.map(exercise => exercise.id), ...reviewed]);
  return plans.flatMap(plan => plan.days.flatMap(day => day.exercises.filter(exercise => !known.has(exercise.exerciseId))));
}
export function canImportAiPlans(plans: Plan[], library: Exercise[], reviewed: string[] = []) {
  return plans.length > 0 && plans.every(plan => plan.name.trim() && plan.days.length > 0 && plan.days.every(day => day.name.trim() && day.exercises.length > 0)) && unresolvedPlanRows(plans, library, reviewed).length === 0;
}
