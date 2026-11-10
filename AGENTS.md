# Agent entry point

Read [docs/agent-handoff.md](docs/agent-handoff.md) before substantial work.
Then read [docs/next-steps.md](docs/next-steps.md) and the feature documents relevant
to the user's current request. The handoff distinguishes implemented behavior,
approved direction, optional ideas and unverified claims.

## Collaboration

- This is the user's first Android game. They have CS/programming experience.
  Implement autonomously when requested; explain significant Android/architecture
  decisions clearly. Avoid overengineering and unnecessary dependencies.
- Follow the current request. A documented next priority is not permission to
  implement the whole backlog or redesign approved art.
- The user reviews actual emulator/phone builds and gives precise visual feedback.
  Functional tests do not establish aesthetic approval.
- Preserve responsive taps, group transfers, offline operation and saved progress.
- No timers, lives, energy, intrusive ads or artificial pressure. No music by default.
- Portrait polish comes first. Landscape work is explicitly deferred.
- Do not assume larger tube capacity or a longer solution means greater difficulty.

## Workspace and verification

- When Android Studio MCP tools are available, use studio_ read/search/edit/build
  tools so unsaved IDE buffers and edit conflict detection are respected. Do not
  use shell commands to read, search or rewrite source files in that environment.
- Inspect relevant source before changes. Preserve unrelated user edits. If an IDE
  edit reports a conflict, read the latest buffer and retry through the edit tools.
- Keep android.injected.androidTest.leaveApksInstalledAfterRun=true.
  Instrumentation previously uninstalled the playtest app and lost its real save.
  Do not clear data or uninstall to make tests pass. Use isolated test storage.
- Logical puzzle state is independent of animation. Never let visual physics change
  legal moves, certificates or persisted histories.
- Keep level IDs/boards and E1 seed meanings stable unless intentionally versioned.
- Build and run appropriate checks when changing code. State exactly what ran;
  do not imply phone performance or sound quality was tested on an emulator.
- Shipped art and font sources are inside app/artwork and app/typography as Base64.
  Gradle generates normal PNG/font resources offline. Do not edit build outputs,
  package comparison sheets, depend on the previous agent's .codex image directory,
  or regenerate approved images to fix native labels/layout.
