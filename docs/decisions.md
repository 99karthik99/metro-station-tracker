# Decision log

One line per decision, newest last. Details live in the PRD (`requirements-prd-claude.md`) and the Q&A files.

| # | Date | Decision | Reason | Source |
|---|------|----------|--------|--------|
| D1 | 2026-10-27 | Build Android first; iOS later | Owner's phone is Android; background location and notifications are easiest to get right natively | qa-human-asks Q2 |
| D2 | 2026-10-27 | Native Kotlin + Jetpack Compose | Most reliable for background location; core logic in plain Kotlin can be shared with iOS via KMP later | qa-human-asks Q2 |
| D3 | 2026-10-27 | Test on the owner's Android phone (USB/wireless debugging); emulator for UI and fake GPS routes | Real GPS and background behaviour matter for this app | qa-human-asks Q1 |
| D4 | 2026-10-27 | v1 is offline-only with bundled station data; no backend or accounts | Keeps v1 simple and private | PRD §6 |
| D5 | 2026-10-28 | v1 targets Hyderabad Metro (Red, Blue, Green lines) | Owner's city | qa-claude-asks Q1 |
| D6 | 2026-10-28 | Support interchanges from v1 (graph routing + interchange guidance) | Interchanges are unavoidable on real trips; designing for them early avoids rework | qa-claude-asks Q2 |
| D7 | 2026-10-28 | Single buzz one station before each interchange and before the destination | Owner prefers a light alert over a repeating alarm | qa-claude-asks Q3 |
| D8 | 2026-10-28 | No map in v1: trip screen is a vertical station list (tap to correct position); notification has only an End trip action | Core value is the notification; extra notification buttons are hard to use on a moving train | qa-claude-asks Q4 |
| D9 | 2026-10-28 | Build in milestones: M1 single-line tracking (with interchange-ready data and routing), M2 interchange tracking. Refines D6 | Prove that GPS station detection works on a real ride before tackling the hardest case | qa-claude-asks Q5 |
| D10 | 2026-10-28 | Two buzzes per key stop (heads-up one station before, and on arrival). Supersedes D7 | A single buzz is easy to miss in a pocket on a noisy train | qa-claude-asks Q6 |
| D11 | 2026-10-28 | Primary test device: Nothing Phone 2; test on an aggressive-OEM phone before public release | Near-stock Android hides the background-kill issues common on Indian-market brands | qa-claude-asks Q8 |
| D12 | 2026-11-04 | Build for the owner first (M1–M2 installed from Android Studio, no store); public release (M3) only after ~2 weeks of reliable personal use, with no release-blocking shortcuts in the meantime | Keeps the learning goal on track without closing the door on other users | qa-claude-asks Q7 |
| D13 | 2026-11-05 | Station data generated from OpenStreetMap by a committed script (`tools/build_station_data.py`), not hand-written | Reproducible and reviewable; both directions of each line are cross-checked automatically | Station-data work |
| D14 | 2026-11-05 | Store only lines, stations and transfers; compute segment times from distance and derive station→line membership | One source of truth per fact; no hand-entered times to drift out of date | Station-data work |
