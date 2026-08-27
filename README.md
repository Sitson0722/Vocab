# Vocab

An offline-first Android vocabulary learner that tracks comprehension and production separately,
uses varied contextual material, and schedules retrieval with a deterministic local engine.

## Build

Pushes and pull requests run lint, unit tests, and a Debug APK build in GitHub Actions. Download the
`vocab-debug-apk` artifact from a successful workflow run; Android Studio is not required.

The current `0.1.0` foundation includes the Compose shell and a tested two-dimensional scheduling
kernel. Persistence, the full exercise loop, validated AI generation, backup/restore, and signed tag
releases are staged in [PLAN.md](PLAN.md).

No API keys or signing material belong in the repository. Provider configuration and release-secret
names will be documented when those integrations land.
