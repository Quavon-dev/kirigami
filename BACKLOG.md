# Backlog

## Mob cap bookkeeping on the main thread

`NaturalSpawner.createState` and `LocalMobCapCalculator` take about 9% of main-thread time in the spread village benchmark (800 villagers, 64 players). Most of it is `LocalMobCapCalculator.getPlayersNear`, which builds the list of nearby players per chunk while counting every mob, and `canSpawnForCategoryLocal` during spawning.

Ideas:
- Precompute the per-chunk player lists in parallel before counting.
- Count mobs per cell in parallel and merge the counts in vanilla order.

Profile: `testing/results/vil-new.jfr` (local only, not committed), analysed with `testing/scripts/jfr_inclusive.py`.

Tried on 2026-10-17: prefilling the per-chunk player lists in parallel (and running the spawn pre-check in parallel once the cache is complete) cut `getPlayersNear` from 9% to 3% of main-thread samples, but median tick time did not change measurably (46.7 ms vs 46.3–47.2 ms). JFR only samples the main thread while it runs, so 9% of samples is about 1 ms of wall time. Reverted to keep the spawning path simple; only worth revisiting together with a larger spawning rework.

## World pauses for plugin events

With LuckPerms, EssentialsX, WorldEdit, WorldGuard, CoreProtect and ViaVersion installed, worker threads spend about 5% of their time waiting at world pauses (spread village benchmark), mostly for WorldGuard's creature spawn check. Every event with listeners pauses the world when it fires from a parallel item.

Ideas:
- Count pauses per event type to see which ones matter.
- Skip the pause for events whose listeners only read the event object (needs a way for plugins to declare it).

## Ticking dimensions in parallel

Measured on 2026-10-17 in the soak scenario (48 bots, about 36/8/4 across overworld, Nether and End at the time of measurement, 800 villagers and the hopper hotspot in the overworld, six plugins): the overworld took 88% of level tick time, the Nether 10% and the End 2%. Overlapping the Nether and End with the overworld would therefore save at most about 12%.

The cost would be large: cross-dimension teleports need a queue (entities coming from the overworld would no longer tick in the Nether or End in the same tick as in vanilla), commands and plugin events from different dimensions would need a world pause that also covers each dimension's serial code, and global state such as the scoreboard and maps would need locks. Worth revisiting only for servers whose Nether or End carry a large share of the load, for example big gold or ender pearl farms.
