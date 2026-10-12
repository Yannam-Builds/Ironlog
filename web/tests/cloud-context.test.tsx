import { cleanup, fireEvent, render, screen, act } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { AIPlan } from "../src/features/plans/AIPlan";
import { CloudInsight } from "../src/features/intelligence/CloudInsight";
import { AppProvider, type AppContextValue } from "../src/ui/context";
import { cloudPresets, setCloudSessionKey } from "../src/domain/cloud-ai";
import { defaultProfile } from "../src/domain/types";
import { deriveSnapshot } from "../src/domain/engine";
afterEach(() => { cleanup(); setCloudSessionKey(""); vi.unstubAllGlobals(); });
function setup() {
  const config = { ...cloudPresets.OpenAI, model:"model" }; setCloudSessionKey("test-key",config);
  const data = { profile:{...defaultProfile,cloudConfig:config},plans:[],workouts:[],exercises:[],measurements:[],photos:[],checkins:[],gyms:[] };
  const value: AppContextValue = { data,derived:deriveSnapshot(data),busy:false,run:async work => { await work();return true; } };
  let resolve!: (response: Response) => void;
  const fetcher = vi.fn(() => new Promise<Response>(done => { resolve = done; })); vi.stubGlobal("fetch",fetcher);
  return {value,fetcher,reply:async (text:string) => { await act(async () => { resolve(new Response(JSON.stringify({choices:[{message:{content:text}}]}))); }); }};
}
it("ignores a late insight response after its request context changes", async () => {
  const {value,reply} = setup();
  const view = render(<AppProvider value={value}><CloudInsight title="Summary" prompt="First workout" /></AppProvider>);
  fireEvent.click(screen.getByRole("button",{name:"Ask Cloud AI"}));
  view.rerender(<AppProvider value={value}><CloudInsight title="Summary" prompt="Second workout" /></AppProvider>);
  await reply("Obsolete advice"); expect(screen.queryByText("Obsolete advice")).not.toBeInTheDocument();
});
it("changing quiz inputs cancels generation and prevents late paste transitions or correction retries", async () => {
  const {value,fetcher,reply} = setup();
  render(<AppProvider value={value}><AIPlan onClose={() => undefined} /></AppProvider>);
  fireEvent.click(screen.getByRole("button",{name:"Get started"})); fireEvent.click(screen.getByRole("button",{name:"Create with Cloud AI"}));
  fireEvent.change(screen.getByLabelText("Goal"),{target:{value:"Strength"}});
  await reply("invalid generated plan");
  expect(screen.getByRole("heading",{name:"Plan details"})).toBeInTheDocument();
  expect(screen.queryByLabelText("AI response JSON")).not.toBeInTheDocument(); expect(fetcher).toHaveBeenCalledTimes(1);
});
