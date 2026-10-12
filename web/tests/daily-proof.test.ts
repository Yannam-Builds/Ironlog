import { expect, it } from "vitest";
import { dailyProof } from "../src/domain/daily-proof";
import type { Workout } from "../src/domain/types";
it("prioritizes the active session and pain review before setup or training readiness", () => {
  const active: Workout = { id:"w",name:"Push",startedAt:1000,status:"active",notes:"",restUsed:false,revision:0,exercises:[] };
  expect(dailyProof([active],false,100,["Chest"]).headline).toBe("Push");
  expect(dailyProof([],false,100,["Chest"]).route).toBe("recovery");
  expect(dailyProof([],false,100,[]).headline).toBe("Set your proof loop");
  expect(dailyProof([],true,100,[]).headline).toBe("First proof awaits");
});
