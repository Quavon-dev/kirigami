# Contributing to Kirigami

Kirigami's first rule is that it behaves like vanilla Minecraft; speed comes second. Every change is judged by that.

## Reporting problems

Use the [issue templates](https://github.com/Quavon-dev/kirigami/issues/new/choose):

- **Bug report** for crashes, errors and lag.
- **Behaves differently than vanilla** for farms, contraptions or mechanics that work in vanilla but not in Kirigami.

Before reporting, start the server once with `-Dkirigami.regionTicking=false -Dkirigami.parallelSpawning=false`. If the problem stays, it is probably not caused by Kirigami's threading, and testing on plain Paper helps narrow it down. Questions go to [Discussions](https://github.com/Quavon-dev/kirigami/discussions), security problems to the [security policy](SECURITY.md).

Kirigami is not affiliated with PaperMC. Please do not report Kirigami problems to Paper.

## Development setup

Java 25 is required. Kirigami uses Paper's paperweight fork layout, so the repository only contains Kirigami's changes on top of a pinned Paper commit.

```bash
./gradlew applyAllPatches
./gradlew :kirigami-server:createPaperclipJar
```

| Change to | Edit in | Then |
|---|---|---|
| Minecraft code | `kirigami-server/src/minecraft/java` (a git repository after `applyAllPatches`) | commit there, run `./gradlew rebuildAllServerPatches` |
| Paper's own code | `paper-server/` (also a git repository) | commit there, run `./gradlew rebuildAllServerPatches` |
| Kirigami's own code | `kirigami-server/src/main/java/kirigami` | commit normally |

Each patch in `kirigami-server/minecraft-patches/features` is one feature. Fix existing behaviour inside the patch it belongs to; start a new patch only for a new feature. Mark changed lines in Minecraft and Paper files with a trailing `// MT` so they are easy to find when Paper updates.

## Testing

Run the tests that match your change. The scripts are in `testing/scripts` and write their results to `testing/results`.

| Change | Required check |
|---|---|
| Anything that can affect gameplay | `parity.sh <official-server.jar> <kirigami.jar>` must report all counters identical to vanilla. CI runs the same check on every pull request. |
| Spawning, mob AI, block ticks | Farm tests with `farm-matrix.sh` and `farm_stats.py`, within noise of vanilla |
| Plugin interaction | `det.sh` and farm runs with the `testing/plugins/mtprobe` probe plugin, 0 overlaps |
| Performance | `bench.sh` numbers before and after, measured on an otherwise idle machine |

`testing/scripts/soak.sh` runs a multi-hour stability test across all three dimensions. The scripts start servers through `testing/scripts/server.sh`, which enables the `/mtbench` benchmark command with `-Dkirigami.benchTools=true`; the command is off on normal servers.

## Pull requests

- Keep pull requests focused on one change and describe how you tested it.
- Commit messages use `<type>: <description>`, for example `fix: keep hopper order across cell borders`. Types: feat, fix, perf, refactor, test, docs, ci, chore.
- Write code that reads like the surrounding code. Avoid comments other than the `// MT` markers.
- Behaviour that differs from vanilla must be optional and off by default.

By contributing, you agree that your contribution is licensed under the GNU General Public License v3.0, like the rest of Kirigami.
