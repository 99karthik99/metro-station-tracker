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
| D15 | 2026-11-07 | Plan and route preview on one screen; recent trips on it in M1; start station defaults to the nearest | Fewest taps for a daily repeated commute | Mockups v1 |
| D16 | 2026-11-07 | Tracking only starts when the user taps Start trip; never automatically | Auto-start would need always-on background location (strict Play review, battery cost) | Mockups v1 |
| D17 | 2026-11-07 | Interchange alerts name the direction ("towards Nagole"), not just the line; trip auto-ends 2 min after arrival | Platforms are signed by end station; auto-end protects battery if the user forgets | Mockups v1 |
| D18 | 2026-11-07 | Separate `:core` plain-Kotlin Gradle module for data, routing and ETA logic; `:app` depends on it | The compiler enforces "no Android code in core logic"; fast JVM tests; ready for Kotlin Multiplatform | Core module work |
| D19 | 2026-11-07 | Station JSON lives in `:core` as a Java resource (moved from app assets) | Data sits with the code that parses it; the same file serves tests and the app | Core module work |
| D20 | 2026-11-07 | Routing picks the fastest route (Dijkstra on time, 300 s per change); first-cut travel time = 25 s dwell + distance at 45 km/h | Simple, explainable, gives ~47 min Miyapur→LB Nagar; constants to be calibrated on real rides | Core module work |
| D21 | 2026-11-07 | Notification header leads with minutes to destination ("Uppal in 39 min · 09:53"); trip screen header likewise | Riders act on "how long left", not clock time; the header line stays visible when the notification is collapsed | Owner request |
| D22 | 2026-11-07 | Notification header is "Arrive in 39 min · 09:53", without the destination name (refines D21); every notification line puts its key words first | Saves space; long station names would truncate the minutes; the rider already knows the destination | Owner request |
| D23 | 2026-11-14 | Location via Google Play services' Fused Location Provider; high accuracy every 5 s during M1, to be relaxed for battery once detection is tuned | Fused combines GPS, Wi-Fi and cell for the best accuracy per battery; frequent fixes give rich data from the first rides | Tracking work |
| D24 | 2026-11-14 | Every trip's location fixes are saved to a CSV trace on the phone (app-private storage) | Real rides can be replayed in unit tests to tune station detection without riding again | Tracking work |
| D25 | 2026-11-14 | Safety net: a trip ends itself 30 min after its estimated arrival; nearest-station default only applies when location permission is already granted | Prevents a forgotten trip draining the battery; keeps "permissions only at Start trip" (D16) true | Tracking work |
