# 032 — Public README, license and screenshots

Status: done (2026-11-12)
Starting commit: `79f5e8d`

## Outcome
A visitor-facing README (owner's voice, claims linked to evidence, honest "Not done
yet"), an MIT license, and four app screenshots taken with invented data.

## Decisions and discoveries
- License: MIT (owner asked for something short and standard).
- README rules agreed with the owner: no buzzwords or emoji, no counts that go stale,
  every claim backed by a file, test or ADR, and the owner has the final edit. The owner
  renamed the process section to "How I built it" and dropped the line about who wrote
  the code.
- Screenshots come from an emulator signed in as the synthetic test2 account, never the
  owner's phone. Invented demo data was seeded for test2 only: 14 `demo-*` exercises,
  program `demo-ppl` (active), 12 sessions (2026-10-15 to 2026-11-09) and 5 check-ins.
  The two 2025 synthetic test2 sessions were left in place (deleting was not permitted
  by the session's safety rules).
- The emulator's waist goal was set to 85 cm so screenshots do not show the default.
- Emulator: a separate local AVD (API 36, x86_64, 1080×2340) reusing an existing system
  image outside the repo; it may not survive a reboot. Status bar uses system UI demo mode.

## Verification
- Fresh clone without `.env`, SDK via `ANDROID_HOME`: `scripts/check --offline` passed.
- README links resolve; claims checked against code (second-user pgTAP checks, Room
  migration tests 4→9, the broad-dip rule).
- Screenshots reviewed: no email, account or real data visible.

## Handoff
Next: owner reads the README once more, then makes the GitHub repository public
(outward-facing; needs the owner's go-ahead). Optional later: commit a generalized demo
seed script so a new backend need not start empty.
