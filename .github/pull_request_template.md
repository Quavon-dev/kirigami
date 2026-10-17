## What and why

## How it was tested

- [ ] `./gradlew applyAllPatches` and `./gradlew :kirigami-server:createPaperclipJar` work
- [ ] Deterministic suite (`testing/scripts/det.sh`) is identical to vanilla, if gameplay code changed
- [ ] Farm tests within noise of vanilla, if spawning, AI or block ticking changed
- [ ] Benchmark numbers (`testing/scripts/bench.sh`), if the change is about performance
