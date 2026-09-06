# Vocab

## 0.8.0 · 学习闭环重构（待 Actions 验证）

今天一键开始，默认每天 10 分钟：先教，再回忆，系统自动混合理解和产出。
新版本只维护两条活跃能力，采用固定版本 FSRS-6、可追溯作答证据和可恢复的短轮学习。
旧学习历史及备份兼容保留，初次打开无需配置 AI 即可体验离线示例。

- [本轮实现与验证状态](docs/ANDROID_REFACTOR_STATUS.md)
- [统一算法与交互设计](docs/LEARNING_REDESIGN.md)
- [交互原型](docs/prototype/index.html)（设计演示，Android 已单独实现）
- [FSRS 版本与数值对照](docs/FSRS.md)

**仅使用现有 GitHub Actions 构建和测试，不安装本地 Android 环境。**
以下为历史版本记录，不作为 0.8.0 的功能或验证声明。

## 0.7.0

- Long flashcards can be scrolled vertically without leaving the study screen.
- Settings can export and transactionally restore a versioned JSON backup containing all vocabulary, progress, corpus, usage and attempt history, provider settings, and API key.
- API keys remain encrypted by Android Keystore in app storage; exported backup files contain the key in plain text and must be kept private.

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

Version 0.5 fixes the statistics crash and makes review startup local-first. AI replenishment now
runs in the background. Choice questions are replaced by reveal-optional, self-graded flashcards,
with an additional conventional English-Chinese card mode. Before learning or review, users can
select adaptive/context/isolated/production dimensions and either interaction mode. DeepSeek now
defaults to `https://api.deepseek.com` with model `deepseek-v4-flash`.

Version 0.5.1 advances immediately after self-grading and prevents duplicate taps while a grade is
saved. Bilingual mode now uses explicit English-to-Chinese cards (word/IPA -> labelled Chinese
definition) or Chinese-to-English production cards. AI and manual import instructions require a
Chinese definition.

Version 0.6 integrates useful ideas from the companion `vocab-dual-dim` project: a daily mixed quota
that schedules due reviews before new learning, non-consecutive dimensions of the same word, a skip
action that does not mutate memory, automatic session summaries, readable L0-L5 indicators, and a
strict mastered state requiring all three dimensions at 85% mastery/30 stable days, 90 elapsed days,
and at least three distinct used materials. Its fixed interval table is retained only as an
explanatory level mapping; Vocab continues to use the more precise continuous retention model.

No API keys or signing material belong in the repository. Provider configuration and release-secret
names will be documented when those integrations land.
