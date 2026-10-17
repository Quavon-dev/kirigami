## What and why

<!-- What changes, and which problem it solves. Link issues with "Fixes #123". -->

## Type

- [ ] Gameplay or threading change (can affect vanilla behaviour)
- [ ] Performance
- [ ] Plugin compatibility
- [ ] Build, CI, tests or docs only

## How it was tested

- [ ] `./gradlew applyAllPatches` and `./gradlew :kirigami-server:createPaperclipJar` work
- [ ] `testing/scripts/parity.sh` is identical to vanilla, if gameplay code changed
- [ ] Farm tests within noise of vanilla, if spawning, AI or block ticking changed
- [ ] `testing/scripts/compat.sh` with `PAPER_JAR` passes, if plugin-facing code changed
- [ ] Benchmark numbers before and after (`bench.sh` or `lab.sh`, idle machine), if the change is about performance

## Behaviour differences

<!-- Anything that now differs from vanilla or from Paper, and the setting that switches it back. Write "None" if there are none. -->

## Checklist

- [ ] Changed lines in Minecraft and Paper code carry a trailing `// MT` and live in the patch of their feature
- [ ] No credentials, tokens or private server data in code, configs, tests or results
- [ ] README, CONTRIBUTING or BACKLOG updated where behaviour, settings or tests changed
