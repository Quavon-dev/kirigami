# Backlog

## Mob cap bookkeeping on the main thread

`NaturalSpawner.createState` and `LocalMobCapCalculator` take about 9% of main-thread time in the spread village benchmark (800 villagers, 64 players). Most of it is `LocalMobCapCalculator.getPlayersNear`, which builds the list of nearby players per chunk while counting every mob, and `canSpawnForCategoryLocal` during spawning.

Ideas:
- Precompute the per-chunk player lists in parallel before counting.
- Count mobs per cell in parallel and merge the counts in vanilla order.

Profile: `testing/results/vil-new.jfr` (local only, not committed), analysed with `testing/scripts/jfr_inclusive.py`.

Tried on 2026-10-17: prefilling the per-chunk player lists in parallel (and running the spawn pre-check in parallel once the cache is complete) cut `getPlayersNear` from 9% to 3% of main-thread samples, but median tick time did not change measurably (46.7 ms vs 46.3–47.2 ms). JFR only samples the main thread while it runs, so 9% of samples is about 1 ms of wall time. Reverted to keep the spawning path simple; only worth revisiting together with a larger spawning rework.

## World pauses for plugin events

Measured on 2026-10-17 in the spread village benchmark (64 bots, 800 villagers) with LuckPerms, EssentialsX, WorldEdit, WorldGuard, CoreProtect and ViaVersion:

- Before: 61.3 ms median tick, 44.9 ms without plugins. WorldGuard's creature spawn listener turned off parallel spawn search completely.
- Now the spawn search runs in parallel up to the spawn event and the event fires during the serial commit, in vanilla order: 42.7 ms with all six plugins, 40.6 ms without plugins. 60.9 million searches checked with `-Dkirigami.verifySpawning=true`, 0 mismatches.
- About 19 pauses per tick remain, 15 of them `BlockPhysicsEvent` (WorldGuard).
- Tried on 2026-10-17: an admin setting that runs listed events under a plugin lock without pausing the world. With `BlockPhysicsEvent` listed, pauses dropped from 19 to 4 per tick, but the median tick did not change (43.4 ms vs 43.1 ms). Each pause costs too little to matter, so the setting was not kept.

Revisit only if a plugin shows up with many more pauses per tick in `/kirigami`.

Pauses are counted per event type, and `/kirigami` lists the top five with the plugins that listen to them. A phase that spends more than half of its time in plugin pauses, and is slower than its last measured serial run, falls back to serial for 600 ticks. Serial time is measured while the fallback is active; before the first fallback any mostly-paused phase falls back.

## Entities added during a parallel phase

`addFreshEntity` inside a cell is deferred until the colour finishes, because the entity tick list, entity sections and the tracker's entity map are not safe to change from worker threads. A TNT primed by an explosion is therefore not pushed by explosions later in the same tick, as it would be in vanilla. In the redstone lab (`lab.sh` with `REDSTONE=1`) the 240-block TNT line stopped early in about 5 of 22 parallel runs and in 0 of 9 serial runs (measured on 2026-10-18), always after the primed TNT fell through the blown-up floor and exploded below the line. Without the redstone field it never stopped (0 of 6 either way). Adding entities immediately would need those structures to accept concurrent inserts.

## Vanilla order for work queued by parallel scheduled ticks

Scheduled ticks in different groups run colour by colour, so the sub-tick numbers of ticks they schedule (`LevelTicks` `nextSubTickCount`) and the order of block events they queue follow the order cells run, not vanilla order. Two distant ticks that each schedule a repeater can therefore give those repeaters a different order later, when they are close enough to run serially. Possible fix: record each parallel tick's index in the vanilla list with everything it schedules or queues, buffer per cell, and merge by that index after the phase. Reads such as `willTickThisTick` and `hasScheduledTick` would then have to see the buffers.

## Tripwire string broken by fluid ticks

Not tested. With `parallel.phases.scheduled-ticks: true`, tripwire and hook ticks keep the scheduled tick phase serial, but water or lava that flows into tripwire string breaks it from a fluid tick. The string then updates its hooks up to 41 blocks away, and the hook's redstone output reaches about 15 blocks further, so two unlinked fluid ticks in neighbouring cells could affect each other. Possible fix: keep the fluid tick phase serial while a fluid tick is queued next to tripwire string, or test it with a contraption that floods a tripwire on a cell border.

## Ticking dimensions in parallel

Measured on 2026-10-17 in the soak scenario (48 bots, about 36/8/4 across overworld, Nether and End at the time of measurement, 800 villagers and the hopper hotspot in the overworld, six plugins): the overworld took 88% of level tick time, the Nether 10% and the End 2%. Overlapping the Nether and End with the overworld would therefore save at most about 12%.

The cost would be large: cross-dimension teleports need a queue (entities coming from the overworld would no longer tick in the Nether or End in the same tick as in vanilla), commands and plugin events from different dimensions would need a world pause that also covers each dimension's serial code, and global state such as the scoreboard and maps would need locks. Worth revisiting only for servers whose Nether or End carry a large share of the load, for example big gold or ender pearl farms.
