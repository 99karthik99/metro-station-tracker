# Metro Station Tracker — Product Requirements (v0.3)

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
- **Parade Ground / JBS Parade Ground** (Blue ↔ Green)

The full station list (names, order, coordinates) must be built and checked before coding starts. See §8.

**Helpful fact:** the network is almost entirely **elevated**, so GPS should be far more reliable than on an underground metro.

## 4. Core user flow

1. The user opens the app and grants location and notification permissions (asked when the first trip starts, not at install).
2. The user picks a **start station** and an **end station** (searchable list across all lines).
3. The app shows the route preview: stations in order, line colours, any interchange ("Change at Ameerpet → Blue Line"), the total station count and the estimated travel time.
4. The user taps **Start trip**.
5. The app shows an **ongoing notification** that updates as the train passes stations:
   `Next: Begumpet · 4 stations left · ETA 10:42`
   Before an interchange, it says so:
   `Next: Ameerpet — change to Blue Line · 6 stations left · ETA 10:55`
6. Two light alerts before the destination and before each interchange (vibration plus the default notification sound):
   - **Heads-up buzz** one station before: "Next: Ameerpet — get ready to change"
   - **Arrival buzz** when the train reaches the stop: "Get off now"
7. When the destination is reached (or the user taps **End trip**), tracking stops and the notification is cleared.

## 5. Functional requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| F1 | Bundle Hyderabad Metro station data: name, line(s), order on each line, lat/lng | Must |
| F2 | Select start and end stations, with search | Must |
| F3 | Compute the route between any two stations, including interchanges (fewest stations, with a penalty per line change) | Must |
| F4 | Track the user's location in the background during an active trip | Must |
| F5 | Work out the current or last-passed station from location (nearest station along the planned route) | Must |
| F6 | Ongoing notification: next station, stations left, ETA, and the upcoming interchange if any | Must |
| F7 | Update the notification on each station change; ETA can refresh about every 30–60 s | Must |
| F8 | Two buzzes per stop that matters (destination and each interchange): heads-up one station before, and on arrival | Must |
| F9 | Stop tracking automatically on arrival, or manually from the app or the notification's **End trip** action | Must |
| F10 | Trip screen: the route as a vertical station list with the current position highlighted. Tapping a station corrects the current position if detection is wrong | Must |
| F11 | Remember recent or favourite trips | Later |
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
- **Notifications:** NotificationCompat ongoing notification (low importance, silent), plus a separate channel for the single-buzz alerts
- **Data:** `hyderabad_metro.json` in `assets/`, loaded into memory
- **Architecture:** route, ETA and station-detection logic in plain Kotlin with no Android dependencies, so it is unit-testable and can be shared with iOS later (Kotlin Multiplatform)

### Data model (sketch)
```
Line      { id, name, color, stationIds[] (ordered) }
Station   { id, name, lat, lng, lineIds[] }
Segment   { fromStationId, toStationId, lineId, travelSeconds }
Transfer  { stationId(s), fromLineId, toLineId, transferSeconds }
RouteLeg  { lineId, stationIds[] }            // one leg per line ridden
Trip      { startId, endId, legs[], currentLegIndex, currentStationIndex, startedAt, eta }
```

## 8. Pre-build tasks and milestones

**Pre-build**
- Build `hyderabad_metro.json`: every station in order on each line, with coordinates. Possible sources are OpenStreetMap and the official HMRL route map; verify them against each other.
- Estimate the segment travel times (e.g. ~2 min between stations, refined later from real rides).

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
- **Network facts** in §3 are from memory and must be verified against the official HMRL map.

## 11. Open items
Tracked in `qa-claude-asks.md`. Resolved answers get folded into the sections above and logged in `docs/decisions.md`.
