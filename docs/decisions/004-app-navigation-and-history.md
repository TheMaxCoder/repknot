# 004 — App navigation and recorded history

Status: accepted
Date: 2026-11-02

## Context

The owner approved the navigation proposal in task 030: Home, History, Progress as
bottom destinations, Settings in the header, and direct exercise-history access.
The current single `screen` string always returns exercise details to Progress and
cannot preserve independent browsing flows. Progress's comparable exercise lineage
omits unknown units and earlier unit variations; it is not a complete workout archive.

## Decision

- Use three top-level destinations with independent, saveable detail stacks and view
  state. Back pops the current stack; at a non-Home root it returns to Home. A current
  tab reselect returns to that tab's root. Settings is a detail of its originating tab.
- Use the existing Compose/Material dependencies and a small app navigation state model;
  do not introduce a navigation package solely for this three-tab slice. Keep routing
  state separate from the current workout's persisted ID and upload state.
- History reads Room snapshots, including completed phone-only sessions. It never
  requests upload or changes a session. Its exercise directory groups performed
  exercises by exercise ID from completed sessions with recorded sets.
- Share exercise history between Progress, workout detail and the directory. Keep
  existing Progress conclusions, but display full recorded history, including mixed or
  missing units, rather than limiting historical records to the analysis lineage.
- The logger remains focused, outside bottom navigation. Leaving it preserves the
  draft; Home provides Resume (or access to the current finished summary). Existing
  explicit upload/finish/discard and current-session rules remain intact.

## Alternatives

- A single global screen string loses origin and per-tab state.
- Four or five bottom destinations add unnecessary primary navigation; Settings is
  occasional, and exercise browsing fits History's Workouts/Exercises tabs.
- A new navigation library is reasonable if deep links or more complex routing become
  a concrete requirement; it is not needed to ship this bounded UI.
- Reusing Progress's computed exposures as the archive would hide recorded information.

## Consequences

Navigation transitions and history filtering/order need focused JVM tests. Read-only
snapshot access may be added to the DAO, without schema or sync changes. UI state must
survive tab switches/configuration changes, including unsaved logger fields. History
details are read-only; record editing, new sync actions and search filters are separate
future requests. Revisit the small navigation model when app links or richer flows are used.
