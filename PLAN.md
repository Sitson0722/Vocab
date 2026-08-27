# Vocab v1 implementation plan

## Product slice

V1 is an offline-first Android application that proves the complete learning loop: browse seeded
words and distinct senses, start a mixed daily session, answer comprehension and production
prompts, receive immediate feedback, and retain independent mastery/scheduling state. AI-generated
material is behind a replaceable interface; cached, validated material keeps review usable offline.

## Architecture

- Kotlin, Jetpack Compose, Material 3, single `app` module.
- UI state flows one way: Compose screens -> view model/use case -> repositories -> Room.
- Domain models and the scheduling engine remain Android-free and deterministic so they can be
  covered by fast JVM tests.
- Room stores vocabulary, materials, attempts, coverage, mastery, schedules, and generation jobs.
  Export/import uses a versioned JSON envelope and never includes service credentials.
- `MaterialGenerator` is the provider-neutral AI boundary. Implementations return structured DTOs;
  validation, fingerprinting, deduplication, and admission happen locally before persistence.

## Delivery stages

1. **Bootstrap and CI**: buildable Compose project, Gradle wrapper, unit tests, debug APK artifact,
   and tag-triggered signed release workflow.
2. **Learning kernel**: sense-based models, separate comprehension/production state, deterministic
   scheduler, queue ranking, response normalization, and tests for all progression rules.
3. **Persistence**: Room schema, migrations, repositories, seed vocabulary, attempts and schedules.
4. **Core UI**: Today dashboard, one-card session, graded hints, feedback, sense/phrase browser,
   progress split by dimension, history and session summary.
5. **Coverage and variety**: stable sense IDs, phrase coverage states, material fingerprints,
   recent-material exclusion, ambiguity/report flags, and multi-context mastery evidence.
6. **AI pipeline**: configurable HTTPS endpoint, structured request/response contract, schema and
   semantic validation, retryable generation jobs, cache thresholds, and offline fallback.
7. **Data and polish**: vocabulary import, versioned backup/restore, limits/settings, accessibility,
   dark mode, error states, and larger-font/screen-size checks.
8. **Release hardening**: instrumentation tests, database migration tests, dependency/security
   review, signed `v*` release with checksum, and README/operator documentation.

## Scheduling details

Each `(sense or phrase, dimension)` pair owns a schedule. An attempt is graded from correctness,
hint count, response time, and self-rating. The engine adjusts stability gradually, bounds same-day
retries, and derives the next due time without AI input. Mastery additionally requires evidence
from multiple material fingerprints and separated review days; one correct answer cannot graduate a
dimension. Queue priority combines overdue time, lower predicted recall, target importance,
dimension imbalance, context-diversity deficit, and a penalty for recently seen fingerprints.

## AI admission pipeline

Generation requests include a stable target sense, allowed phrase, exercise type, level, structured
schema, and recent fingerprints. Results pass JSON/schema checks, target/answer checks, ambiguity
rules, basic safety checks, and normalized fingerprint/similarity checks. Only accepted materials
enter the active pool. Rejected results remain traceable in `GenerationJob`; they never alter
attempt history or schedules.

## V1 acceptance path

CI must pass `lint`, JVM tests, and `assembleDebug`, then upload reports and an installable APK.
The app must launch without network access and offer a seeded `subtle` session demonstrating two
independent dimensions and multiple contexts. Subsequent increments complete persistence, AI,
import/export, and signed releases against the acceptance criteria in `DEVELOPMENT_GUIDE.md`.
