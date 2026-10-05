import { defaultProfile, type PlateSetup, type Profile } from "./types";

export function nativeDefaultPlateSetup(): PlateSetup {
  const platesKg = [...defaultProfile.platesKg];
  return { barKg: 20, platesKg, plateInventory: platesKg.map(weightKg => ({ weightKg, quantity: 4 })) };
}

export function basePlateSetup(profile: Profile): PlateSetup {
  if (profile.activeGymId)
    return profile.basePlateSetup
      ? { ...profile.basePlateSetup, plateInventory: profile.basePlateSetup.plateInventory }
      : nativeDefaultPlateSetup();
  return { barKg: profile.barKg, platesKg: profile.platesKg, plateInventory: profile.plateInventory };
}
