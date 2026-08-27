# Implementation audit against the product notes

Reviewed sources: `session.md` (first-principles model and full sense lifecycle),
`interations.md` (six interaction primitives and evidence collection), and
`DEVELOPMENT_GUIDE.md` (MVP requirements and acceptance criteria).

This audit intentionally does not equate a scaffold or interface with a finished feature.

| Area | Required outcome | Current status |
|---|---|---|
| Install/launch | Visible launcher entry and installable APK | **Implemented in 0.1.1**: explicit adaptive, round, and legacy icons plus launcher activity |
| Learning identity | Stable word, sense, phrase, usage-pattern identities | **Implemented foundation in 0.2**: Room identity per `(term, definition)` sense with phrase/example; richer phrase entities remain |
| Sense coverage | Separate polysemous senses; no omissions or semantic duplicates | **Partial**: distinct senses and exact duplicate rejection work; semantic AI dedup/coverage catalogue remains |
| Learner model | Independent contextual comprehension, isolated meaning, and production evidence | **Implemented in 0.4**: each dimension has independent retention, mastery, stability, difficulty, and history |
| Discrimination | Track near-synonym, sense, and collocation boundaries | **Not implemented**; must be explicit evidence, not folded into correctness |
| Scheduling | Deterministic, persistent, explainable, adaptive review | **Partial**: tested persistent scheduling uses correctness/hints/time/dimension/difficulty; richer recall prediction, context diversity, and “why now?” remain |
| Mastery evidence | Multiple contexts, delayed retrieval, no single-answer graduation | **Implemented foundation in 0.4**: spacing/novelty-sensitive gain and diversity evidence caps; broader transfer domains remain |
| Encounter | Brief concept/form/context model with clarification branch | **Not implemented** |
| Comprehension | Contextual meaning and sense-choice tasks | **Implemented foundation in 0.2**: contextual meaning choices with persisted grading |
| Production | Recall, hinted recall, constrained/free use, nuanced feedback | **Partial**: typed recall, progressive hint evidence, and correction exist; open-use semantic feedback remains |
| Collocation | Selection/completion and usage-pattern practice | **Not implemented** |
| Transfer | Unannounced cross-domain/context testing | **Not implemented** |
| Daily flow | One adaptive stream rather than six mode menus | **Implemented foundation in 0.2**: due review and new learning feed a single question flow |
| Attempt evidence | Correctness, latency, hints, uncertainty, error taxonomy | **Partial**: attempts persist correctness, answer, latency, hints, and dimension; uncertainty/error taxonomy remains |
| Material variety | Fingerprints, recent exclusion, contextual invariance | **Implemented foundation in 0.4**: typed materials, fingerprints, least-used selection, style tags, and display logs |
| AI provider | Replaceable OpenAI-compatible endpoint/model/key | **Implemented in 0.1.1 foundation**: settings UI, HTTPS validation, Keystore-encrypted key, provider-neutral client |
| AI admission | Structured schema, sense binding, ambiguity/quality/dedup checks | **Partial**: structured parsing, type/sense checks, target-presence rules, fingerprints and duplicate rejection exist; deeper ambiguity scoring remains |
| Offline fallback | Cached qualified material supports due reviews | **Partial**: imported contexts persist and all review works offline; managed AI cache/replenishment remains |
| Browse/progress | Browse senses/phrases and view dimension/coverage progress | **Partial**: word/sense browser and dimension statistics exist; per-sense coverage detail remains |
| Import/export | Vocabulary import and versioned backup/restore without secrets | **Partial**: fixed-format and AI arbitrary-text import exist; full database export/restore remains |
| Accessibility | Dark mode, scaling, reader semantics, screen-size checks | **Partial**: Compose/Material defaults only; dedicated verification missing |
| CI | Lint, JVM tests, Debug APK artifact | **Implemented and passing** |
| Release | Signed tag release, checksum, clear missing-secret failure | **Not implemented** |

## Corrected delivery interpretation

The prior `0.1.0` build should be called a technical bootstrap, not an MVP. The shortest honest path
to a useful vertical slice is:

1. Room schema for word/sense/phrase/material/mastery/schedule/attempt/coverage/generation job.
2. Seed two senses and multiple qualified materials, then implement Encounter -> Comprehension ->
   Production in one working daily session.
3. Persist attempts and independently update each dimension with delayed/multi-context evidence.
4. Add sense/collocation discrimination, hints, error diagnosis, and adaptive queue ranking.
5. Admit AI material only through structured validation, sense binding, fingerprinting, and cache
   thresholds; keep the seeded/cached path fully offline.
6. Add browsing, statistics, settings limits, import/export, migrations, accessibility tests, and
   signed tag releases.

## Product extensions worth preserving

- Treat uncertainty (“I know it but cannot translate precisely”) as distinct evidence, not failure.
- Separate target-word failure from sentence-comprehension and spelling failures.
- Make mastery an evidence portfolio: dimension, distinct fingerprint count, delayed days,
  collocation coverage, discrimination evidence, and transfer domains.
- Show a short “why now?” explanation for every scheduled item.
- Use AI to propose targeted interventions after deterministic diagnosis, never to mutate mastery.
