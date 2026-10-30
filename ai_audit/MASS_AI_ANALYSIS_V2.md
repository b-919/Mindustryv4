# Mass AI — Current Analysis (v2)

Workspace: `C:\Users\senti\Documents\Coding\Mindustryv4`
Updated: 2026-10-28
Supersedes: `MASS_AI_ANALYSIS.md` (2026-10-09) and the source dumps in `MASS_AI_AUDIT.md` / `MASS_AI_DETAILED.md`.

> The old audit is a raw source snapshot from **2026-10-09**. The mass package changed on **2026-10-10**
> (`MassIntel`, `MassAdrenaline`) and **2026-10-21** (vein growth rewrite, response timers, mass save header).
> It therefore does not describe current behavior. This document is based on the code at HEAD `7f38f4bd07`.

---

## 0. Scope

| Area | Files |
|---|---|
| Facade | `core/src/io/anuke/mindustry/ai/MassAI.java` |
| Core | `MassAIConfig`, `MassInfection`, `MassBuilder`, `BuildingLine`, `SubSection`, `PathTile` |
| Economy | `MassEconomy` |
| Defense | `MassDefense`, `PendingBuild` |
| Squads | `MassSquads`, `MassSquad`, `MassOrder`, `SquadPreset` |
| Support | `MassUtil`, `MassIntel`, `MassAdrenaline`, `MassSave` |

Engine hooks: `Logic:309` (`update`), `Logic:105-107` (infection), `Unit:142` and `TileEntity:205-212`
(damage/reactive turret, both `Team.themass` only), `OverlayRenderer:202`, `Save16..Save20`
(`MassAI.read/write`), `Biomass*Mission`, `UnitHiveSpawner:176`, `HiveDrill:70` (adrenaline).

---

## 1. Tick flow (`MassAI.update`)

```
guard (client / paused / no teams) + sync "massai-debug" setting
cores = MassInfection.update(cores)      // may spawn the first hive
MassAdrenaline.update(cores)             // per-hive enemy count, every 30 ticks
MassIntel.update()                       // enemy air ratio (1 s) + threat heat decay
MassBuilder.update(cores)                // init new cores -> 4 lines; tick all lines
MassDefense.update()                     // pending builds, periodic + threat-response turrets
MassEconomy.update(cores)                // 5 min: drills/cores/spawners/generators
                                         // 2 min: squad-driven unit queue
                                         // every tick: subsection tick
MassSquads.update(cores)                 // roster, scenario, defense, metrics, assignment,
                                         // centroids, orders, patrol nodes, pursuit
```

Squad count `= squadsPerHive(difficulty) × coreCount`; orders re-roll every 40–90 s, targets every 4 s,
patrol nodes every 12 s.

---

## 2. Settings in effect

### 2.1 Global (`MassAIConfig`)

| Group | Value |
|---|---|
| Subsystem cycle | 5 min |
| Unit queue | 2 min |
| Periodic turret | 15–30 min |
| Damage turret cooldown | 10–35 s |
| Threat-response turret | 8–15 min |
| Retaliation pursuit | 15 s timer (ignores the range leash) |
| Post-pursuit patrol hold | 15 s |
| Patrol node loiter | orbit at 1.5 tiles (scaled by body), tangential steering |
| Infection countdown | 5–40 min (hardcoded) |
| Line action | 1 s |
| Line idle grow | 30 s |
| Drill stuck | 15 s |
| Drill connection check | 60 s |
| Turret limit | 10 per type per core |
| Spawners/core | 10 ground / 10 air / 2 heavy |
| Queue target | 3 per spawner |
| Ore limits/core | `{6,6,6,5,4,4,4}` |
| Cores | `hiveLimit(difficulty)`, min spacing 75 tiles, edge margin 5 |
| BFS cap | 2000 nodes |
| Vein growth | branch after 5 tiles, cost 3, 30 % chance, merge 40 %, turn 1.1 / spacing 2.2 / ore-avoid 8 penalties, noise 0.4 |
| Adrenaline | 10 enemies in 40 tiles → turret delay ÷2, bio ×1.5, factory ×1.2 |
| Intel | scan 1 s, 400 units, 0.34 heat/hit, hotspot ≥ 0.5, decay 12 s |

### 2.2 Difficulty-derived

| Difficulty | Grace | Generators/core | Squads/hive | Hive cap | Total squads | Spawn speed |
|---|---|---|---|---|---|---|
| training | 10 min | 1 | 2 | 4 | 8 | 0.5× |
| easy | 7 min | 2 | 3 | 8 | 24 | 0.8× |
| normal | 5 min | 3 | 4 | 10 | 40 | 1.0× |
| hard | 3 min | 4 | 6 | 12 | 72 | 1.2× |
| insane | 1 min | 6 | 8 | 14 | 112 | 1.4× |
| eradication | 0.5 min | 10 | 16 | 24 | **384** | 1.8× |

### 2.3 User-facing switches

- Settings → Developer: `massai-debug` (per-event log spam + squad overlay).
- Custom game: `allowRandomInfection`.
- Campaign/mission: `startWithBiomass`.

Everything else is compiled in; there is no tuning UI.

---

## 3. Drift since the 2026-10-09 audit

- `MassIntel` and `MassAdrenaline` did not exist in the audit and are not described anywhere in it.
- `BuildingLine` is no longer the "subdivide/branch" implementation; it is a scored growth system with
  attraction anchors, merging and per-line save fields (~477 lines vs ~327 in the dump).
- `MassAIConfig` gained `RESPONSE_*`, `SUBSECTION_CONNCHECK_INTERVAL`, all `VEIN_*` values,
  `squadsPerHive()`, `hiveLimit()`, adrenaline and intel blocks; it lost `LINE_BRANCH_CHANCE` /
  `LINE_SUBDIVIDE_CHANCE`.
- `SquadPreset` gained `AIR_STRIKE`, `SWARM`, `SIEGE` and the `air` / `antiProduction` flags.
- `MassDefense` gained the threat-response turret and intel hotspot targeting.
- `MassSave` gained a header and two response timers — the version it writes was ignored on read.
- `MASS_AI_ANALYSIS.md` is missing its "quick code-read / bugs" section (numbering jumps 1 → 3).

---

## 4. Findings

Severity: **H** high (data loss / wrong behavior), **M** medium (noticeable), **L** low (cleanup).

### 4.1 Persistence

**H — Save format changed without a readable version.** `MassSave` writes `SAVE_VERSION` then the payload,
but `read()` consumed the int and discarded it, and `afe518bb17` inserted two floats mid-stream. Every
`Save17+` loader called the same reader, so saves written before 2026-10-21 desynchronize (garbage core/line
counts) and corrupt the remainder of the save. **Fixed in §7.**

**M — Loading a save permanently ends grace.** `MassSave.read` sets `disableGrace = true`; `readMap` fires
`WorldLoadEvent` (which calls `reset()`) *before* `MassAI.read`, so the flag survives.

**L — Dropped state.** `unitQueueTimer`, squad orders/timers, adrenaline set and intel composition are not
saved; squads re-roll every load.

### 4.2 Timing constants in the wrong unit

`Timers.delta()` is in ticks (60/s), so seconds are normally written `x * 60f`. These are not:

| Constant | Value | Actual | Intended |
|---|---|---|---|
| `DEFEND_GIVEUP_TIME` | 30 | **0.5 s** | 30 s (log even prints `{0}s`) |
| `DEFEND_GIVEUP_ORDER_TIME` | 60 | **1 s** | 60 s |
| `DEFEND_NOTIFY_INTERVAL` | 5 | 83 ms | 5 s |
| `PURSUIT_GIVEUP_TIME` | 10 | **0.17 s** | 10 s |
| `PURSUIT_MIN_TIME` | 1 | 1 tick | 1 s |
| `THREAT_DECAY_TIME` | 12 | **0.2 s** | 12 s (comment says seconds) |

Consequences: hive defense collapses after ~0.5 s; `giveUpPatrol` clears after 1 s; pursuit's give-up
window is meaningless; threat heat is gone 0.2 s after the last hit, so `MassIntel.hotspot()` (needs 0.5)
only triggers under sustained fire and the threat-based turret retargeting / patrol weighting is mostly dead.
**Fixed in §7.**

Related: `SubSection` adds `SUBSECTION_CONNCHECK_INTERVAL` (3600 ticks) to `lostTimer` and compares it with
`DRILL_STUCK_TIME` (900 ticks), so the first failed 60-s connection check destroys the drill; the
`// 15 minutes` comment is wrong (15 s).

### 4.3 Performance / scaling

**H at high difficulty — `orderSquad` is O(squads × assigned units) per tick.** It scans the whole
`unitSquadAssignments` map once per squad. At eradication caps that is 384 squads × hundreds of units.
**Fixed in §7 (per-squad member lists).**

**M — Attack orders are re-issued every tick.** `force = want == attackTarget` makes `clearOrder()` +
`orderAttackTarget()` + `setDirectTarget()` run every tick for every `ATTACK_UNITS` / `PURSUE` /
`DEFEND_HIVE` unit; the `moved` check is dead in those branches. **Fixed in §7.**

**M — Vestigial global command still costs a scan per tick.** `MassSquads.update` calls
`MassUtil.anyEnemyNearCores(cores)` only to fill `currentCommand` / `enemyNearbyTimer`, which are written to
the save and never used for behavior. **Fixed in §7.**

**M — Dead lines are never retired.** `BuildingLine.dead` is set on merge/stuck/bounds but `MassBuilder`
only removes lines whose core is gone. Dead lines keep running the rebuild scan once per second and keep
their tiles in `lineOccupied`; tiles that were destroyed and given up are never unmarked, so they keep
blocking vein growth and anchor detection. **Fixed in §7.**

**M — Per-tick churn in squad metrics.** `refreshSquadMetrics` rebuilds `tempAssignments` +
`unitSquadAssignments` every tick, `recomputeCentroids` walks the map again, and there is an empty
`for(Team team : Team.all){}` loop. **Fixed in §7: the empty loop is gone, assignments are folded into
per-squad member lists once per tick, and centroids are finalized in one O(squads) pass.**

**M — BFS volume/allocation.** `findPath` / `findPathToAnyLine` allocate a fresh `Queue` + `ObjectMap` per
call and are used by subsection connectivity checks too (up to ~840 drills at 24 cores). `find2x2Ore`
still walks every line tile for every ore position. **Fixed in §7.**

**L — Debug overlay** allocates a map + arrays per frame (debug only).

### 4.4 Logic / robustness

- `SubSection.connected()` uses the air-passable BFS, so it can report "connected" without a vein path;
  stuck drills may never reconnect. **Fixed in §7 (vein-only BFS).**
- `MassBuilder.isAreaClear` is dead code (only `isTurretClear` is used). **Removed in §7.**
- Turret arrays are ordered most-expensive-first while the comment says "most lowcost"; `evilDuo` is not
  counted by `countTurrets`, bypassing the cap; `countNearbyCores` is always 1 because
  `CORE_PROXIMITY_TILES (40) < CORE_MIN_DISTANCE (75)`. **Fixed in §7.**
- `trySpawnTurret` pays resources before knowing whether a vein path exists; reactive turrets ignore the cap.
  **Partly fixed: payment now happens only after a vein path is found.**
- `assignFreshUnits` requires `unit.getSpawner() != null`, so scripted/spawner-less mass units never get orders.
  **Fixed in §7.**
- `ensureRoster` wipes every assignment whenever core count or max evolution changes. **Fixed in §7 (surviving
  slots keep their units).**
- Patrol engagement used a fixed centroid radius (`PATROL_ENGAGE_RADIUS` × preset multiplier), so a unit whose
  weapon out-ranged that circle never triggered the squad and short-range squads reacted to enemies they could
  not reach. **Fixed in §7 (per-unit attack range detection and a 2.5× pursuit leash).**
- Unit damage passed a `null` attacker (`Unit.damage`), so a patrol squad could not retaliate against the unit
  that shot it; and the range leash dropped outranging attackers even though they were actively firing.
  **Fixed in §7 (Fix 11: attacker tracking + 15 s retaliation timer, no leash, patrol return grace).**
- Patrol movement sent every member to the same node; units that arrived early simply cleared their order and
  stood there until the squad centroid (or the rest of the squad) caught up. **Fixed in §7 (Fix 12: arrived
  units orbit the node).**
- Turret footprints could cover vein mouths (line starts and drill/generator neighbours) or overwrite tiles in
  the middle of a main line / subsection path, silently cutting resource flow. **Fixed in §7 (Fix 13:
  entrance reservation + reroute around the new block).**
- `MassAI.update` kept ticking every subsystem with zero mass cores: periodic/threat turret timers, the 5-min
  economy scans, spawner world-scan, and intel composition scans all ran even though turret placement itself
  returned early. **Fixed in §7 (Fix 14: idle gate).**
- Grace is advanced inside `MassSquads.update` and only while cores exist; the first hit on any mass
  unit/tile ends grace for the whole faction.
- **Pathfinding waypoints were half a tile off.** `BaseUnit.waypointX/Y` and `refineChunkCorridor`
  converted packed tile coordinates with `tile * tilesize + tilesize / 2f`, but the world convention
  (rendering centers, `World.tileWorld` → `scl2`) puts tile centers at `tile * tilesize`. HPA/fine/fallback
  waypoints therefore landed on tile **corners** — exactly where diagonal cliffs meet. `Pathfinder` also used
  `floor` for start/goal tiles, another half-tile shift. **Fixed in §7 (Fix 10).**
- **HPA pruned real detours.** `pruneLeadingWaypoints` skipped every waypoint "behind" the goal direction,
  even when that waypoint was the only route around a cliff/lake, and skipped anything within 3 tiles.
  The unit then steered straight into the obstacle. **Fixed in §7.**
- **Direct fallbacks steered through cliffs.** When the local A* returned empty (unreachable/over budget),
  `moveWithPathfinding`/`followOrderPath`, the attack-state target chase and the exhausted-flow-field core
  chase all called `moveTo(target)` with no clearance check, driving units into corners. **Fixed in §7
  (body-width segment checks + re-center/repath).**
- `MassInfection.spawnInitialHive` validates one tile only (no footprint/liquid/placeable check) and uses
  the first player's core only. **Footprint fixed in §7; player selection unchanged.**
- Unused config: `INFECTION_MIN`, `INFECTION_MAX`, `ROSTER_INTERVAL`, `ATTACK_ARRIVE_DIST`, and
  `MAX_CORES` (only the `hiveLimit` fallback). **Infection bounds wired, dead constants removed in §7.**

### 4.5 Balance

- Total squads jump 8 → 24 → 40 → 72 → 112 → **384**; the insane→eradication step is 3.4×.
- Unbounded derived counts: drills `ORE_LIMITS × cores` (up to ~840), generators up to 240, spawners up
  to 22/core. Combined with the BFS and per-squad costs above, eradication is the CPU worst case.
- Four turret cadences (periodic, threat response, damage, adrenaline) may be more aggressive than the old
  periodic+damage design.

---

## 5. Status of the 2026-10-09 recommendations

Done: package split, `BuildingLine` read/write swap, unified turret cost table, heavy-spawner and
core-expansion cost fixes, division clamp, enemy-core cache, spawner registry, per-tick turret cache,
staggered line timers, scratch arrays, `MassAIConfig`.

Not done from the old audit: tests for the pure parts (weights, grace mapping, `canPlaceCore`). Everything else
(save-version enforcement, `find2x2Ore` line distances, BFS reuse, assignment churn, dead code) was addressed
in this pass; see §7.

---

## 6. Fix plan for this pass

### Fix 1 — enforceable mass save versioning
Version matrix (the outer save version selects the mass layout):

| Outer save version | Mass block layout |
|---|---|
| 16–18 | legacy: no header, no response timers, legacy tail |
| 19 | timestamp: before 2026-10-21 18:21:29 +02:00 → legacy; after → format 4 |
| 20 | format 4: header + response timers + legacy tail |
| **21 (new writer)** | **format 5: header + response timers, legacy tail removed** |

Why the timestamp for v19: v19 was the newest writer from 2026-08-13 until v20 was added on 2026-10-28,
so it spans the 2026-10-21 header change. v17/v18 predate the header; v20 postdates it.

Implementation:

- `MassSave`: `SAVE_VERSION = 5`, `FORMAT_HEADERED = 4`; `read(stream, format)` validates the header and
  reads/writes response timers only for format ≥ 4 and the legacy tail only for format < 5.
- `MassAI.read(stream, format)` overload.
- `Save17`: keep the save timestamp it already skips; `massSaveVersion(savedTime)` selects the layout.
- `Save21 extends Save20` + register in `SaveIO.versionArray` so new saves are unambiguous.

### Fix 2 — timing units

- `DEFEND_GIVEUP_TIME` 30 s, `DEFEND_GIVEUP_ORDER_TIME` 60 s, `DEFEND_NOTIFY_INTERVAL` 5 s,
  `PURSUIT_GIVEUP_TIME` 10 s, `PURSUIT_MIN_TIME` 1 s, `THREAT_DECAY_TIME` 12 s → all `* 60f`.
- Defense log prints seconds (`/60f`) instead of ticks.

### Fix 3 — dead code and dead lines

- Remove `MassSquads.currentCommand`, `enemyNearbyTimer`, `MassUtil.enemyNearby`, `anyEnemyNearCores` and the
  per-tick scan; legacy save reads consume and discard the fields.
- Remove the empty `Team.all` loop in `refreshSquadMetrics`.
- `MassBuilder`: retire `BuildingLine.dead` lines (unmark + remove) after each update.
- `BuildingLine`: unmark a tile from `lineOccupied` when it is abandoned (`gaveUp`) and no longer a mass vein.

This pass also covered: per-squad member lists, dropping the per-tick attack re-order, vein-only subsection
connectivity, turret ordering/cap consistency, shared BFS buffers, `find2x2Ore` bounding boxes, infection
constant wiring, and the initial-hive footprint check (see §7).

Remaining backlog: `ensureRoster` assignment preservation, balance caps for eradication counts, and unit
tests for the pure helpers. Reactive turrets still bypass the turret cap by design.

---

## 7. Implementation log

### Fix 1 — enforceable mass save versioning (done)

- `MassSave` now has `SAVE_VERSION = 5`, `FORMAT_HEADERED = 4`, `FORMAT_LEGACY = 0`, and
  `read(stream, format)` validates that the stream header equals the layout selected by the outer version.
  Unknown headers throw an `IOException` instead of silently desyncing.
- Layouts: legacy (no header, no response timers, legacy tail), format 4 (header + response timers + tail),
  format 5 (header + response timers, tail removed).
- `MassAI.read(stream)` became `MassAI.read(stream, format)`; `Save16` reads legacy.
- `Save17` keeps the save timestamp it already consumed and adds `massSaveVersion(savedTime)`:
  `v17/v18 → legacy`, `v19 → timestamp < 2026-10-21 18:21:29 +02:00 ? legacy : 4`,
  `v20 → 4`, `v21+ → 5`.
- `Save21 extends Save20` (registered in `SaveIO.versionArray`) is the new writer, so new saves are
  version 21 with mass format 5.
- `Save20` gained the `(int version)` constructor needed by `Save21`.

### Fix 2 — timing units (done)

`DEFEND_GIVEUP_TIME` 30 s, `DEFEND_GIVEUP_ORDER_TIME` 60 s, `DEFEND_NOTIFY_INTERVAL` 5 s,
`PURSUIT_GIVEUP_TIME` 10 s, `PURSUIT_MIN_TIME` 1 s, `THREAT_DECAY_TIME` 12 s — all now multiplied by 60.
The defense give-up log divides by 60 so it prints seconds.

### Fix 3 — dead code and dead lines (done)

- Removed `MassSquads.currentCommand`, `enemyNearbyTimer`, `MassUtil.enemyNearby`,
  `MassUtil.anyEnemyNearCores` and the per-tick scan that fed them. Legacy save reads still consume the
  fields and discard them.
- Removed the empty `Team.all` loop in `refreshSquadMetrics` and the now-unused
  `MassAIConfig.ENEMY_SCAN_SIZE`.
- `MassBuilder` retires dead lines after their update: the line is removed from `activeLines` and its path
  tiles are unmarked, while its start tile stays reserved as a vein anchor so subsections keep a
  connection point.
- `BuildingLine` unmarks a tile from `lineOccupied` when it is abandoned (`gaveUp`) and is no longer a
  mass vein.

### Verification

- `gradlew :core:compileJava --offline --rerun-tasks` → **BUILD SUCCESSFUL** (JDK 17).
- `gradlew :tests:test --tests ApplicationTests.massSaveFormats --tests ApplicationTests.massDifficultyScaling` → **BUILD SUCCESSFUL**.
- `ApplicationTests.save`, `ApplicationTests.load` and `contentIdShiftPreservesSave` → **BUILD SUCCESSFUL** (Save21 write/load path).
- Full `ApplicationTests` run: 16 tests, 1 pre-existing failure (`loadSector`, null `Vars.weather` in the headless harness); unrelated to the mass changes.
- Manual read-through of the write/read symmetry for formats 0, 4 and 5; no other `MassAI.read` callers exist in the repo.

### Fix 4 — squad ordering (done)

- `MassSquad` now owns a `members` array. `refreshSquadMetrics` fills it once per tick, `assignFreshUnits`
  appends newly assigned units and updates that squad's counters/type map, and `finalizeMetrics` divides the
  centroids in one O(squads) pass.
- `orderSquad` iterates `squad.members` instead of the global `unitSquadAssignments` map, removing the
  O(squads × units) scan (the main high-difficulty cost).
- Removed the per-tick `force` re-order; `setDirectTarget` is still refreshed every tick for attack-target
  orders, and the move order is reissued only when the target moved more than a tile.
- `assignFreshUnits` no longer requires `unit.getSpawner() != null`, so scripted/spawner-less mass units get
  orders too.

### Fix 5 — connectivity and defense (done)

- `MassBuilder.isVeinConnected` is a vein-only BFS; `SubSection.connected()` uses it, so air paths no
  longer mask a disconnected drill. `tryReconnect()` keeps the air-path BFS (it has to lay new veins).
- Turret arrays are now cheapest-first (`Scatter < Cyclone`, `Salvo < Fuse < Ripple < Duo`) and `evilDuo` is
  counted, so the free fallback can no longer bypass the cap.
- `trySpawnTurret` looks for a vein path before charging the core; bases without a connection are skipped
  instead of producing an unfeedable turret. The dead `countNearbyCores` / `CORE_PROXIMITY_TILES` path is
  gone (cores are ≥ 75 tiles apart, so it was always 1).

### Fix 6 — BFS and ore search (done)

- `MassBuilder.findPath` / `findPathToAnyLine` / `isVeinConnected` share two static scratch
  `Queue`/`ObjectMap` buffers (cleared per call) instead of allocating per query.
- `BuildingLine` keeps a bounding box of its placed tiles (`addTile` updates it, save reads rebuild it).
  `find2x2Ore` skips lines whose bounding box is already farther than the best source distance found so far.

### Fix 7 — settings and infection (done)

- `INFECTION_MIN` / `INFECTION_MAX` now drive `MassInfection.update/reset` through
  `MassInfection.randomInfectionTime()`; `Logic.play()` uses `MassAI.randomInfectionTime()` instead of a
  duplicated literal.
- `spawnInitialHive` validates the full hive footprint (margin, air, placeable, dry, away from enemy cores)
  before choosing the farthest site.
- Removed the unused `ROSTER_INTERVAL`, `ATTACK_ARRIVE_DIST` and `ENEMY_SCAN_SIZE` config values; removed the
  dead `MassBuilder.isAreaClear` and `MassIntel.hasComposition`.

### Fix 8 — regression tests (done)

- `ApplicationTests.massDifficultyScaling` pins the grace / generator / squad / hive scaling table.
- `ApplicationTests.massSaveFormats` round-trips the current format and parses hand-built legacy (0) and
  format-4 blocks, and asserts that a mismatched header is rejected instead of desyncing.

### Fix 9 — roster preservation and per-unit engagement (done)

- `ensureRoster` now snapshots assignments, reuses the squad in every slot whose preset is unchanged (keeping
  its order/timers), and keeps only the units that slot's preset still accepts and has room for. Units from
  removed/changed slots return to the pool and are re-placed by `assignFreshUnits` in the same tick; a hive
  expansion or evolution no longer wipes every assignment.
- `detectPatrolEngagement` no longer uses a centroid circle. For each patrol squad it takes a broad-phase
  query covering the furthest member reach plus the squad's spread, then tests each enemy against every
  member's own effective attack range (`max(weapon mount range, type.range)`, air targets filtered).
  Any detection makes the whole squad pursue. `giveUpPatrol` now only pauses patrol movement — it no longer
  makes the squad blind.
- Pursuit leash is `2.5 ×` the detecting unit's attack range (`PURSUE_RANGE_MULTIPLIER`), checked from the
  closest member every tick so spread squads do not oscillate. Removed the fixed `PATROL_ENGAGE_RADIUS` and
  the preset multipliers.

### Fix 10 — pathfinding corners (done)

- **Coordinate convention:** world tile centers are at `tile * tilesize` (`World.tileWorld` rounds with
  `scl2`; rendering centers textures on `tile.worldx()`). `BaseUnit.waypointX/Y` and the corridor-refine
  legs no longer add `tilesize / 2f`; `Pathfinder.findUnitPath`/`findFallbackWaypoint`, the open-world
  window origin and `ChunkWaypointGraph.findPath` round tile coordinates instead of flooring/truncating.
  HPA/fine waypoints now target tile centers instead of corners.
- **HPA:** `pruneLeadingWaypoints` only drops waypoints that are effectively underfoot (< 0.6 tile) or
  behind the goal **and** the direct line to a near goal is body-clear; the 3-tile blind skip is gone.
  The immediate waypoint-advance distance is 0.75 tile, and blocked coarse hops recover after 6 frames
  (fine: 12) instead of grinding for 14–44. `refineChunkCorridor` keeps the refined prefix when a later
  leg falls outside the loaded window, instead of reverting the whole corridor to straight door-to-door
  hops.
- **Body-width clearance:** `BaseUnit.inlineClear` is now protected, uses the actual body half-width
  (with 1 unit of tolerance), and `hopClear(...)` only samples the next ~3 tiles so far goals stay cheap.
  `blockedAtAngle` looks further ahead (≥16 units) and samples the real body width.
- **Waypoint following (`GroundUnit`, `TankUnit`):** the next waypoint is only advanced to when its hop is
  body-clear; a blocked hop re-centers on the current tile and forces a repath instead of pushing into the
  corner. Empty/unreachable path fallbacks only straight-line when `hopClear` passes; otherwise they
  re-center/hold. The attack-state target chase uses `moveWithPathfinding` instead of a raw `moveTo`, and
  the exhausted flow-field core chase re-centers when the direct line is blocked.

### Fix 11 — patrol retaliation (done)

- `Unit` now records the bullet owner on hit (`lastDamager`) and passes it to the Mass AI; `Unit.damage` no
  longer sends `null` as the attacker, and clears the field after use so stale attackers don't leak into
  explosion damage (that path falls back to the nearest enemy).
- `MassAI.onUnitDamage` → `MassSquads.notifyUnitDamaged`. It finds the damaged unit's squad (or the nearest
  patrol squad within `DEFEND_RADIUS` when the unit is unassigned) and only reacts if that squad is patrolling
  or already retaliating; attack/defend squads keep their current job.
- Retaliation is a separate pursuit mode: `retaliating = true`, no range leash (`pursuitRange = 0`), 15 s
  `RETALIATE_TIME` timer. If any member is already within its attack range, the timer is refreshed and the
  squad keeps fighting; otherwise the timer counts down. Every fresh hit from the same damager resets it;
  a different damager retargets. At 0 the squad gives up and returns to patrol.
- `exitPursuit` now sets `patrolGrace = PATROL_RETURN_TIME` (15 s) and pushes `nextOrderTime` out, so a
  squad that just finished a pursuit stays on patrol before it can roll an attack order again;
  `guaranteeAttackSquad` skips squads inside that grace window.
- Defense still wins: a squad converted to `DEFEND_HIVE` clears its retaliation state; the existing hive
  defense overrides patrol/pursuit squads in range.

### Fix 12 — patrol node loitering (done)

- `orderSquad` no longer sends every patrol member to the same point with a plain move order. When a unit is
  within its patrol arrival distance of the current node (and the node hasn't advanced yet), it gets an
  orbit target instead: a point ahead of the unit **along the tangent** of a circle around the node, plus a
  radial correction toward the desired radius (`PATROL_ORBIT_RADIUS`), recomputed each tick. Units that have
  not reached the node keep heading for it, so the early arrivals circle while the rest catch up.
- Orbit radius scales up for large bodies (`hitsize + tilesize`) and spreads per unit by id, and
  `patrolArriveDist` is kept wider than the orbit radius so a unit cannot flip between orbiting and
  re-approaching every tick. Orbit points over cliffs/liquid try the mirrored tangent and fall back to the
  node.
- Orbiting targets reissue more often (`0.5` tile threshold) so the circle reads as movement rather than a
  series of stops.

### Fix 13 — turrets vs veins (done)

- `MassBuilder` now keeps a `veinEntrances` reservation set. Building-line start tiles (the core mouth) and
  the cardinal mouths of every drill/generator subsection (plus the actual last vein before the target) are
  reserved. `isTurretClear` rejects any footprint tile that is a reserved mouth, even while it is still air,
  so a turret can no longer spawn on the point where resources enter the network.
- `PendingBuild.place` snapshots the mass veins inside the block footprint **before** the block is placed,
  then calls `MassBuilder.repairVeins` after placement (pending vein builds skip this).
- Main lines: `repairLine` finds the replaced run, removes it from `lineOccupied`, BFSes an air-only bypass
  around the new block from the upstream tile to the downstream tile, re-lays the vein blocks with the
  correct backwards rotations, rebuilds `line.tiles`, updates `lineOccupied`, and clears `attempts`/`gaveUp`
  (their indices refer to the old list). If no bypass exists the line is safely truncated upstream instead of
  repeatedly failing to rebuild the new block.
- Subsections: any subsection whose path contained a replaced vein calls `tryReconnect()`; success rebuilds
  the path around the turret, failure marks the subsection for destruction and boosts its ore so a
  replacement is spawned.
- The entrance set is rebuilt on world load through the normal line/subsection constructors, so saves are
  covered without a format change. BFS uses the shared pathfinding scratch.

### Fix 14 — idle gate when no hive exists (done)

- `MassAI.update` now runs `MassInfection.update` first (the infection countdown must still reach the spawn
  point) and returns immediately when the mass has no cores.
- On the transition from active to no cores it calls `resetRuntime()` once: builder, economy, defense
  (including pending turret/vein builds), squads, adrenaline, intel and the enemy-core cache are dropped.
  The infection countdown is deliberately not reset.
- `massIdle` starts true on world load and is set false by `MassAI.read`, so a loaded save with no remaining
  hive is validated and cleaned on its first update. When a hive appears (infection, mission, or start with
  biomass) the subsystems resume from clean state.
- Result: no periodic turret timers, threat-response timers, 5-minute economy scans, spawner world-scans,
  intel scans, or squad work while the Mass AI has no core.

### Remaining backlog (next pass)

Balance caps for eradication counts (384 squads, ~840 drills, 240 generators) are a tuning decision left
untouched. Reactive turrets still bypass the turret cap by design. A fully clearance-aware flow field/HPA
(per body-size obstacle inflation) would be the next step if large units still clip narrow gaps.


### Caveats

- The v19 split relies on the save timestamp; a machine with a badly wrong clock could misclassify an old
  v19 file. v17/v18 and v20+ are selected by version alone.
- Saves written between 2026-10-21 and this change were version 19 with the header; they are recognized by
  their timestamp.
- `MASS_AI_AUDIT.md` / `MASS_AI_DETAILED.md` remain historical source dumps; regenerate or delete them if
  they should not be read as current.

