# Metro Station Tracker — Product Requirements (v0.5)

> Source of truth: `requirements-prd-human.txt` (owner-edited). This file expands it into buildable requirements.
> Items marked **[Assumption]** are Claude's guesses and need confirmation. Decisions are logged in `docs/decisions.md`.

## 1. Objective

An Android app that tracks a **Hyderabad Metro** rider's journey. The rider picks a start and end station. While they travel, the app uses their location to keep an ongoing notification showing:

- The **next station**
- **How many stations are left** to the destination
- **ETA** at the destination
- Where to **change lines**, when the route has an interchange

**Project goal:** primarily a learning project, built first for the owner's daily commute, but done without shortcuts so it can be released publicly later (see §8 Milestones).

## 2. Target user and platform

- Hyderabad Metro commuters, or anyone on an unfamiliar route who doesn't want to miss their stop or interchange.
- **Platform:** Android only for v1. iOS comes later.
- **[Assumption]** Minimum Android version 8.0 (API 26). Target the latest stable API.

## 3. Metro network (v1 scope)

Hyderabad Metro Rail has three operational lines:

| Line | Corridor |
|------|----------|
| Red | Miyapur ↔ LB Nagar |
| Blue | Nagole ↔ Raidurg |
| Green | JBS Parade Ground ↔ MG Bus Station |

Interchange stations:
- **Ameerpet** (Red ↔ Blue)
- **MG Bus Station** (Red ↔ Green)
- **Parade Ground** (Blue ↔ Green). Note: *JBS Parade Ground* is a different station, the Green Line terminus one stop north.

In total there are **57 stations** (27 Red + 23 Blue + 10 Green, minus 3 shared interchanges). The data is in `core/src/main/resources/hyderabad_metro.json`, generated from OpenStreetMap by `tools/build_station_data.py`.

**Helpful fact:** the network is almost entirely **elevated**, so GPS should be far more reliable than on an underground metro.

## 4. Core user flow

1. **Plan screen** (home): the user picks a **start station** (defaults to the station nearest them) and an **end station** (searchable across all lines), or taps a **recent trip**.
2. The route preview appears on the same screen: total time, stop count, number of changes, a colour bar per line, and one row per leg ("Red Line to Ameerpet · 10 stops", "Change at Ameerpet ~5 min", "Blue Line to Uppal · 12 stops").
3. The user taps **Start trip**. The first time, a **permission explanation screen** comes before the Android permission prompts (see §4a).
4. **Trip screen**: a line diagram with passed and far-away stations collapsed. It highlights the next station, shows interchanges with a "change to <line>" tag, and has the destination at the bottom. Tapping a station corrects the current position.
5. An **ongoing notification** updates as the train passes stations:
   - Title: `Next: ESI Hospital`
   - Text: `15 stops left · change at Ameerpet in 3`
   - Header: `Uppal in 39 min · 09:53`. **Minutes to destination** come first, with the arrival clock time after. The header is visible even when the notification is collapsed.
   - A progress bar and an **End trip** action.
   - Minutes are recalculated on every station change and at least once a minute. Under 1 min it shows `Uppal in <1 min`; after arrival the header shows `arrived`.
6. Two light alerts (vibration + default sound) before each interchange and before the destination:
   - **Heads-up** one stop before. At an interchange it names the **direction**, matching platform signage: "Next: Ameerpet, change here · Take Blue Line towards Nagole".
   - **Arrival**: "Get off now: Uppal".
7. The trip **ends automatically 2 minutes after arrival** (or when the user taps **End trip**). Tracking stops and the notification is cleared.
8. Tracking never starts automatically; the user always taps Start trip.

### 4a. Permissions
- Ask only when the user first taps Start trip, never at install.
- Show our own explanation screen first (why location, why notifications, and that location stays on the phone). Then show the Android prompts.
- Needed: **precise location, "While using the app"**, and **notifications** (Android 13+). Background location is never requested.
- If the user picks **Approximate** location (Android 12+), that is accurate only to about 1–3 km, so it can't tell stations apart (they are ~0.6–1.7 km apart). Show a recovery screen explaining this, with a button to the app's settings.
- If notifications are denied, explain that alerts won't show. Tracking still works on the trip screen.
- If a permission is denied permanently, Android won't prompt again, so the recovery screen links to the app's settings page.

## 5. Functional requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| F1 | Bundle Hyderabad Metro station data: name, line(s), order on each line, lat/lng | Must |
| F2 | Select start and end stations, with search | Must |
| F3 | Compute the route between any two stations, including interchanges (fewest stations, with a penalty per line change) | Must |
| F4 | Track the user's location in the background during an active trip | Must |
| F5 | Work out the current or last-passed station from location (nearest station along the planned route) | Must |
| F6 | Ongoing notification: next station, stations left, **minutes to destination + arrival time**, and the upcoming interchange if any | Must |
| F7 | Update the notification on each station change; ETA can refresh about every 30–60 s | Must |
| F8 | Two buzzes per stop that matters (destination and each interchange): heads-up one station before, and on arrival | Must |
| F9 | Stop tracking automatically on arrival, or manually from the app or the notification's **End trip** action | Must |
| F10 | Trip screen: the route as a vertical station list with the current position highlighted. Tapping a station corrects the current position if detection is wrong | Must |
| F11 | Recent trips on the plan screen (one tap to repeat) | Must |
| F14 | Default the start station to the nearest station | Should |
| F15 | Auto-end the trip 2 minutes after arrival | Must |
| F12 | Map screen | Later |
| F13 | Notification has one action (**End trip**); tapping the notification opens the trip screen | Must |

### Routing
- Model the network as a graph: stations are nodes, adjacent stations on a line are edges, and an interchange is a "transfer" edge with a time cost (walking and waiting, e.g. ~5 min).
- Use Dijkstra on travel time. With only three lines this is tiny and instant.
- **Stations left** counts stations the train stops at; the interchange station is counted once.

### ETA logic
- **Simple:** sum of the remaining segment times, plus transfer times for the remaining interchanges.
- **Better (Should):** adjust the estimate by how far ahead of or behind schedule the user is so far on this trip.
- No live train data in v1.

### Location / station detection
- Use the fused location provider (GPS + Wi-Fi + cell).
- Treat a station as "passed" when the user is within a radius (e.g. 150–300 m) or once they are clearly nearer the following station.
- Only move forward along the planned route, and never go backwards because of a noisy reading.
- At an interchange, confirm the line change once the user starts moving along the new line.
- If there's no location fix for a while, estimate progress from elapsed time and segment durations.
- **Off-route** (the user boards the wrong train or skips the interchange): detect it and show "You seem to be off route". Recalculating the route is a Should.

## 6. Non-functional requirements

- **Battery:** location only during an active trip. Use a balanced-accuracy, moderate-interval (~10–20 s) foreground service.
- **Offline:** works fully offline because the station data is bundled. No backend in v1.
- **Privacy:** location stays on the device, with no accounts and no analytics in v1.
- **Reliability:** the trip must survive the app being swiped away (foreground service with a persistent notification).

## 7. Technical approach

- **Language/UI:** Kotlin + Jetpack Compose
- **Background work:** foreground service (`location` type) + FusedLocationProviderClient
- **Notifications:** NotificationCompat ongoing notification (low importance, silent), plus a separate channel for the heads-up and arrival buzzes
- **Data:** `hyderabad_metro.json` is a Java resource in the `:core` module, loaded into memory by `NetworkLoader`. It is generated, not hand-edited: fix the data in OSM or in the script's `NAME_FIXES`, then re-run the script.
- **Architecture:** two Gradle modules:
  - `:core`: plain Kotlin/JVM, so **Android imports are impossible**. Holds network data, routing (`RoutePlanner`, Dijkstra over station+line states), ETA and later station detection. Unit tests run on the JVM in seconds. It can become a Kotlin Multiplatform module for iOS later.
  - `:app`: Android UI (Compose), the tracking service and notifications. Depends on `:core`.

### Data model (sketch)
```
// Stored in hyderabad_metro.json
Line      { id, name, color, stationIds[] (ordered) }
Station   { id, name, lat, lng }                  // which lines a station is on is derived from Line.stationIds
Transfer  { stationId, seconds }                  // interchange walk + wait, default 300 s

// Computed in code
segmentSeconds(a, b) = dwell (25 s) + distance(a, b) / cruise speed (45 km/h)   // TravelTimeModel; calibrate from real rides
RouteLeg  { lineId, stationIds[] }            // one leg per line ridden
Trip      { startId, endId, legs[], currentLegIndex, currentStationIndex, startedAt, eta }
```

## 8. Pre-build tasks and milestones

**Pre-build**
- ✅ `hyderabad_metro.json` generated from OpenStreetMap (57 stations, both directions of each line cross-checked).
- ⏳ Owner to sanity-check station names and order against the metro map/signage.
- Segment travel times are computed from distance (see §7) and calibrated on the first real rides.

**Milestones**
| Milestone | Scope | Done when |
|---|---|---|
| **M1** | Full network data, routing *with* interchanges, route preview; live tracking + notification + alerts for **single-line** trips | A real single-line ride tracks correctly on the owner's phone |
| **M2** | Live tracking **across interchanges**, off-route detection | Real interchange rides (e.g. via Ameerpet) track correctly |
| **M3** | Public release: Play Store listing, privacy policy, testing on other phone brands | ~2 weeks of reliable daily use on the owner's commute |

## 9. Out of scope for v1
- Live train timings or crowd data
- Ticketing or fares
- Other cities
- iOS app
- Map screen

## 10. Key risks
- **Station detection accuracy** on a real train is unproven. Validate it first on a real ride (see Q5).
- **OEM battery killers** (Xiaomi, Realme, Oppo, Vivo, …) can stop the foreground service mid-trip. The owner's test phone (Nothing Phone 2, near-stock Android) is lenient, so it **will hide this problem**. Test on at least one aggressive-OEM phone before M3, and guide users to exempt the app from battery optimisation.
- **Location permission:** start the tracking service while the app is on screen, so only "while using the app" location permission is needed. This avoids `ACCESS_BACKGROUND_LOCATION`, which Google Play reviews strictly.
- **Station data** comes from OpenStreetMap, which is community-maintained and may lag behind renames or new stations. Re-run the generator before release.
- **Licence:** OSM data is ODbL. The app must show "© OpenStreetMap contributors" (e.g. on an About screen) before public release.

## 11. Open items
Tracked in `qa-claude-asks.md`. Resolved answers get folded into the sections above and logged in `docs/decisions.md`.
