# Workspace and artifact inventory

The implementation lives in the isolated `IronLogWeb` worktree. `UnifiedPort` retains its unrelated dirty development work; `_github_publish_ironlog` retains its own main checkout. No reset, wholesale copy, history rewrite, signing-key rotation or physical-phone installation was performed.

Source remains in `app/` and `web/`; durable evidence/review records are in `docs/reviews/`. Browser helpers, generated snapshots/screenshots and synthetic downloads are ignored under `web/output/playwright/` and `web/.playwright-cli/`. Android captures/AVDs stay ignored under `output/android/`; stable signed distribution copies go under `output/release-pre-alpha.*`.

Inventory of root ignored output folders during closure:

| Folder | Files | Approximate MB | Disposition |
|---|---:|---:|---|
| android | 87 | 8675.7 | Retained QA AVD and reproducible native evidence; no user data |
| playwright | 25 | 1.7 | Retained older runtime evidence |
| release-pre-alpha.10 | 5 | 51.3 | Retained signed update baseline |
| releases | 3 | 51.3 | Retained previous distribution copy |
| vercel-source | 995 | 78.8 | Retained prior staging; previous deletion was automatically rejected |

The earlier staging deletion rejection is not bypassed using another command. No recursive deletion/move was needed to ship this work. Private signing/local configuration stays local and ignored; no secrets were printed or added to the review. Native compiled source/schema is unchanged by organizing diagnostics. Historical quarantine references are not a production source set and are retained rather than guessed disposable.
