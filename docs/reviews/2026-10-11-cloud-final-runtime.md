# Cloud AI final runtime verification — 2026-10-11

Executed 2026-10-11 22:03–22:08 UTC (2026-10-12 Europe/Warsaw), against Vite at `http://localhost:5183/Ironlog/app/`, using the isolated Playwright CLI session `cloudfinal`.

Only a synthetic profile, synthetic gym and synthetic plan were used. Provider requests targeted an HTTPS `.invalid` endpoint and were intercepted by Playwright. The only credential entered was an explicitly synthetic test token. No live provider request, real key, user database, private configuration, source edit or broad test/build was involved.

## Results

| Check | Runtime result |
| --- | --- |
| Fetch models | Explicit click produced the intercepted model list containing `synthetic-model`. The password field cleared after the synthetic token entered the in-memory key store. |
| Test connection | Explicit click displayed `Connection responded.` for the intercepted text response. |
| HTTP error | Intercepted HTTP 401 displayed the bounded provider status guidance; the provider response body was not echoed. A corresponding browser HTTP error entry is expected. |
| Cancellation | Cancel restored enabled request controls, removed the pending control and showed no error. A held response fulfilled after cancellation did not display connection success. |
| Endpoint credential isolation | Changing the base URL disabled Test connection when no key existed for that endpoint. Restoring the original endpoint restored access to its session key. |
| Settings persistence | Saved Custom provider, HTTPS base URL, model and OpenAI format survived reload. |
| Credential persistence | Serialized every IndexedDB table and checked for the synthetic token: absent. Reload retained settings while Test connection became disabled, demonstrating that the session key had been forgotten. |
| Active gym equipment | A synthetic active gym with unavailable Cable and Machine initialized the quiz with those checkboxes unchecked and Barbell checked. |
| Manual AI review | A JSON plan containing `Runtime Unknown Press 472` previewed as one unresolved exercise. Import remained disabled and the database contained zero plans. |
| Rematch and explicit import | Matching the unresolved row to existing Bench Press enabled Import reviewed plan. Explicit import saved exactly one synthetic plan containing the resolved exercise and returned to Plans. |

No blocking runtime issue was found in these exercised flows.

## Limits

This run does not establish live provider availability, current model IDs, provider billing, production browser CORS behavior or provider-specific interoperability. The stream/JSON limits and strict Anthropic body validation are covered by the focused automated verification separately; they were not repeated in this browser pass. Cloud generation retry, all insight surfaces, timeout expiry, IndexedDB quota/write failures and importing after a simulated failed database write were not exercised here. The active-gym fixture was seeded directly into the isolated database rather than created through the gym editor. Verification used a desktop Chromium session; mobile layout and other browser engines were not rechecked.

## Coaching-mode and activation follow-up — 2026-10-11 22:21–22:23 UTC

Used a second isolated Playwright CLI session, `cloudfinalextra`, against the same Vite server and only synthetic data. No live provider requests were sent; an HTTPS `.invalid` interception was registered for the synthetic provider.

- A profile explicitly set to built-in mode hid Ask Cloud AI actions despite retaining a cloud configuration.
- A saved lowercase native-style `openai` provider displayed the correct `OpenAI` option in Cloud AI settings. An in-browser synthetic Android backup encode/decode roundtrip also exported lowercase native provider representation and decoded the provider to `OpenAI`. The file-picker import UI was not repeated.
- Selecting Cloud AI mode, entering an explicitly synthetic session token and saving persisted `cloud_ai`. Reload showed Cloud AI mode selected. The plan quiz exposed Create with Cloud AI while that mode was active.
- The `ai_activated` badge was durably recorded after cloud mode plus provider/model and synthetic key were saved. It survived reload, Forget session keys and switching back to built-in mode.
- Saving built-in mode retained the model/base URL/provider configuration. After the profile update settled, Ask Cloud AI actions were hidden; the quiz cloud-generation button was absent. Reload showed built-in mode selected with the retained synthetic model.

One immediate DOM count sampled before the asynchronous profile save/rerender settled still observed the old cloud controls. Subsequent settled and reload checks confirmed the correct built-in state; this was a verification timing issue, not a persistent UI finding.

This follow-up did not exercise goal-mode workout-proof accrual, midflight coaching-mode cancellation, a live provider, or native file-picker imports. No additional blocker was found.
