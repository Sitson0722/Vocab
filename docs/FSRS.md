# Fixed FSRS implementation

`ReviewScheduler` uses the FSRS-6 default parameters and formulas from
[py-fsrs at 9446cb06605c597a063aeee49f7d188d42e34dc2](https://github.com/open-spaced-repetition/py-fsrs/blob/9446cb06605c597a063aeee49f7d188d42e34dc2/fsrs/scheduler.py).
The MIT notice is in `THIRD_PARTY_NOTICES.md` and packaged as `assets/fsrs-license.txt`.

Configuration is fixed: desired retention 0.90, no learning/relearning steps, no fuzz,
maximum interval 36500 days. Complete elapsed days select the short-term versus delayed
update, matching the reference. Intervals use ties-to-even rounding, a minimum of one day,
and no dimension multiplier. The local session planner separately allows one cooled repair
per sense, subject to time and intervening actions. This repair does not replace the long-term due date.

The 17 fixtures in `app/src/test/resources/fsrs6-reference.json` were generated from that
pinned upstream Python scheduler with `learning_steps=()`, `relearning_steps=()`, and
`enable_fuzzing=False`. They cover initialization, Good/Hard/Again, sub-day reviews, long gaps,
and recovery after lapses. `ReviewSchedulerTest` checks Kotlin stability, difficulty and interval
against these independent numeric outputs. They are fixtures, not evidence that CI has already passed.

Legacy S/D values are not FSRS parameters. Their original values and due dates remain stored
until a real new-protocol review initializes that record under the new engine. Old attempt
logs, third-dimension progress and mastery fields stay archived. Guided recall after initial
teaching never updates FSRS. Prompted success maps to Again; response speed has no grading effect.

Build and test only through the existing `.github/workflows/ci.yml`. No local JDK, SDK,
Gradle distribution or Android environment is required or installed for this delivery.
