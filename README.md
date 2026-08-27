# Vocab

An offline-first Android vocabulary learner that tracks comprehension and production separately,
uses varied contextual material, and schedules retrieval with a deterministic local engine.

## Build

Pushes and pull requests run lint, unit tests, and a Debug APK build in GitHub Actions. Download the
`vocab-debug-apk` artifact from a successful workflow run; Android Studio is not required.

The current foundation includes the Compose shell, launcher assets, a tested two-dimensional
scheduling kernel, and configurable OpenAI-compatible provider settings. Provider API keys are
encrypted by Android Keystore and excluded from Android backup. Persistence, the full exercise
loop, validated AI-material admission, backup/restore, and signed tag releases remain staged in
[PLAN.md](PLAN.md) and are tracked honestly in [IMPLEMENTATION_AUDIT.md](IMPLEMENTATION_AUDIT.md).

No API keys or signing material belong in the repository. Provider configuration and release-secret
names will be documented when those integrations land.
