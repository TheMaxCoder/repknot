# Repknot

A personal workout companion. Native Android, Kotlin + Compose, dark-only.
Product scope: [PRD](Repknot-PRD.md). Boundaries: [architecture](docs/architecture.md).
Current work and next steps: [project status](docs/status.md).

## Agent handoff

Start at `AGENTS.md`, then `docs/status.md`. Use “continue the current task” to
resume, or give a new task directly. Agents choose direct implementation, a task
execution plan, and/or a short architecture decision record according to scope.
Plans are updated in place; no task framework or mandatory approval step is needed.
`CLAUDE.md` imports the shared instructions. Fresh-session loading in all three
tools is still to be verified; see status. If a tool doesn't load them automatically,
explicitly ask it to read `AGENTS.md` until its native adapter is configured.
Historical setup proposals are background, not competing operational instructions.

## Open and run

Open **`android/`** in Android Studio and let Gradle sync. Run the `app` configuration
on an emulator or device running Android 8.0 (API 26) or newer.

Install Android SDK platform 36 and build tools 36.0.0. Set `ANDROID_HOME` to your SDK,
or put `sdk.dir=/your/sdk/path` in untracked `android/local.properties`.
Use a Gradle-compatible JDK (the installed Android Studio JBR 25 is used here).
If needed, set `JAVA_HOME` to Android Studio's `jbr` directory.

```sh
scripts/check
cd android
./gradlew :app:installDebug
```

The check script builds the debug APK, runs lint, and runs unit tests when present.
The skeleton has no business logic/tests yet; NO-SOURCE is not test coverage.
Device and visual verification are separate from the build.

## Structure

- `android/app`: launch screen, theme, and future feature UI.
- `android/domain`: pure Kotlin business rules, initially empty.
- `android/data`: Android storage/integration module, initially empty.
- `AGENTS.md`: shared agent guidance; `CLAUDE.md` imports it.

Versions are pinned in `android/gradle/libs.versions.toml` and the Gradle wrapper.
The initial versions reuse installed compatible tooling rather than chasing latest releases.
Room, Supabase, sign-in, and workout logging are the next slice, not implemented yet.
No CI or restrictive hooks. Private exports belong outside Git (the root
`private-data/` directory is ignored); never commit credentials or personal records.
