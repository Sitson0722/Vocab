# Vocab

An offline-first Android vocabulary learner that tracks comprehension and production separately,
uses varied contextual material, and schedules retrieval with a deterministic local engine.

## Build

Pushes and pull requests run lint, unit tests, and a Debug APK build in GitHub Actions. Download the
`vocab-debug-apk` artifact from a successful workflow run; Android Studio is not required.

Version 0.2 provides a usable offline learning loop: fixed-format vocabulary import, AI extraction
from arbitrary text through a configurable OpenAI-compatible provider, a persistent Room database,
separate comprehension and production reviews, hints and response evidence, due/new queues, a word
browser, and statistics. Provider API keys are encrypted by Android Keystore and excluded from
Android backup. Advanced AI-material admission, backup/restore, richer discrimination/transfer
exercises, and signed tag releases remain tracked in [IMPLEMENTATION_AUDIT.md](IMPLEMENTATION_AUDIT.md).

Version 0.3 adds IPA phonetics and anytime review. Users choose any review quantity; the app ranks
all previously studied items by predicted retention, difficulty, and time since review. Stability
growth is spacing-sensitive, so immediate repetition provides much less long-term evidence than a
successful delayed retrieval.

Version 0.4 separates new-context comprehension, isolated word meaning, and active production into
three independent memory/mastery models. A typed material library stores sentences, phrases,
collocations, and diagnostic proper nouns with fingerprints, style tags, provenance, and per-display
usage logs. AI can replenish this library automatically or manually in a requested style, and review
can filter by previously generated style. DeepSeek (`https://api.deepseek.com/v1`) and `v4flash` are
the defaults while the provider remains OpenAI-compatible and replaceable.

No API keys or signing material belong in the repository. Provider configuration and release-secret
names will be documented when those integrations land.
