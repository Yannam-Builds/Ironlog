# Native exercise integrity — 8 October 2026

Confirmed against real temporary ObjectBox stores, not source-string assertions:
1. 32 concurrent creates for whitespace/case-normalized identity could persist duplicates.
2. Blank and colliding renames were accepted, allowing invalid catalog identity.
3. Catalog mapping discarded aliases/source tags and reduced multiple primary relationships to the scalar first muscle.

All three regression tests failed before implementation. After the change all three pass, including persisted-row rollback, exact-one create and export metadata checks. The duplicate check and parent/child write share one write transaction. Rename validates before mutation, re-reads in the transaction and excludes only the edited identity. Catalog reads batch primary relationships in one read transaction; historical workout snapshots and ObjectBox schema IDs are untouched.

Focused final command on JDK21: :app:testDebugUnitTest --tests com.ironlog.app.data.repository.ExerciseRepositoryIntegrityTest --no-daemon; BUILD SUCCESSFUL in34s, 3 tests /0 failures /0 errors. Full gates and signed release follow integration.

ObjectBox diagnostics: the initial red and first green run emitted cached read-cursor destruction warnings during JVM teardown. Final focused run after catalog read transaction emitted none; this is a narrow observation, not a claim that every repository/whole-suite teardown is fixed. Official lifecycle documentation identifies cached per-thread readers and closeThreadResources: https://objectbox.io/docfiles/java/current/io/objectbox/BoxStore.html . Retain the full-suite diagnostics investigation until the final gate.
