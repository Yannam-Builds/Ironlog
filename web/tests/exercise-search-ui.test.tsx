import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import { ExercisePicker } from "../src/features/Plans";
import { AppProvider } from "../src/ui/context";
import { defaultProfile, type AppSnapshot } from "../src/domain/types";
import { deriveSnapshot } from "../src/domain/engine";
const data: AppSnapshot = {profile:defaultProfile, plans:[], workouts:[], measurements:[], photos:[], checkins:[], gyms:[], exercises:[
  {id:"press",name:"Incline Bench Press",aliases:["Upper pec builder"],muscle:"Chest",equipment:"Barbell",category:"strength",movementPattern:"push",difficulty:"beginner",tracking:"weight_reps"},
  {id:"row",name:"Cable Row",muscle:"Back",equipment:"Cable",category:"strength",tracking:"weight_reps"},
]};
afterEach(cleanup);
it("finds aliases and combined native metadata instead of only name substrings", async()=>{
  render(<AppProvider value={{data,derived:deriveSnapshot(data),busy:false,run:async action=>{await action();return true;}}}><ExercisePicker onPick={()=>{}} onClose={()=>{}} /></AppProvider>);
  fireEvent.change(screen.getByLabelText("Exercise name"),{target:{value:"Upper pec builder"}});
  await waitFor(()=>expect(screen.queryByRole("button",{name:/Cable Row/})).not.toBeInTheDocument());
  expect(await screen.findByRole("button",{name:/Incline Bench Press/})).toBeVisible();
  fireEvent.change(screen.getByLabelText("Exercise name"),{target:{value:"zzzzz"}});
  await waitFor(()=>expect(screen.queryByRole("button",{name:/Incline Bench Press/})).not.toBeInTheDocument());
  fireEvent.change(screen.getByLabelText("Exercise name"),{target:{value:"Chest Barbell beginner"}});
  expect(await screen.findByRole("button",{name:/Incline Bench Press/})).toBeVisible();
});
