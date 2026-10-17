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

## Updating Paper

A workflow (`.github/workflows/update-paper.yml`) checks Paper every Monday and can be started by hand from the Actions tab. It moves `paperRef` to Paper's newest commit, applies all patches, builds the server and runs the vanilla parity check. If everything passes, it opens or updates a pull request on `update/paper`. If a patch no longer applies, the build fails or parity breaks, it opens an issue with the error instead.

To do the same by hand, or to fix a conflict the workflow reported:

1. Run `.github/scripts/update-paper.sh`. It updates `paperRef`, applies the patches and builds.
2. If a Minecraft patch conflicts, `kirigami-server/src/minecraft/java` stops in the middle of `git am`. Resolve the conflict so that both Paper's change and Kirigami's change survive, `git add` the file and run `git am --continue`. Conflicts in Paper's own code show up the same way in `paper-server/`.
3. Save the resolved patches with `./gradlew rebuildAllServerPatches`, then run `./gradlew applyAllPatches` once more from scratch; it must finish without conflicts.
4. Build the jar and run `testing/scripts/parity.sh` against the official server (`testing/scripts/fetch-vanilla.sh <minecraft-version> server.jar` downloads it).
5. Commit `gradle.properties` and the changed patch files as `chore: update Paper to <commit>`.

Watch for Paper changes that read shared state from code Kirigami runs in parallel, for example new static fields in hoppers. Such state has to stay per thread or behind a lock, like the existing hopper flags.

## Updating Minecraft

When Paper moves to a new Minecraft version, the update workflow opens an issue instead of a pull request.

1. Keep the old version maintainable: create a branch `ver/<old-version>` from `main`. Fixes for the old version are tagged there, for example `v26.3-5`; such tags update the Docker tag `26.3` but not `latest`.
2. On `main`, set `mcVersion` and `apiVersion` in `gradle.properties` to the new version and `paperRef` to Paper's first stable commit for it.
3. Port the patches as described above. Expect more conflicts than for a Paper update, and check every changed vanilla mechanic against Kirigami's parallel code.
4. Run the parity check with the new official server, the farm tests and a soak test before the first release.
5. Release the first build as `v<new-version>-1`; the build number starts again at 1.

## Pull requests

- Keep pull requests focused on one change and describe how you tested it.
- Commit messages use `<type>: <description>`, for example `fix: keep hopper order across cell borders`. Types: feat, fix, perf, refactor, test, docs, ci, chore.
- Write code that reads like the surrounding code. Avoid comments other than the `// MT` markers.
- Behaviour that differs from vanilla must be optional and off by default.

By contributing, you agree that your contribution is licensed under the GNU General Public License v3.0, like the rest of Kirigami.
