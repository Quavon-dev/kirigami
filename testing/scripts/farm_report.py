import re
import sys
from collections import Counter

COMPOUND = re.compile(r"\{[^{}]*\}")
ITEM_ID = re.compile(r'id: "minecraft:([a-z_]+)"')
ITEM_COUNT = re.compile(r"count: (\d+)")
COUNT = re.compile(r"Test (passed|failed)(?:[.,] [Cc]ount: (\d+))?")
SCORE = re.compile(r"#([a-z]+) has (\d+) \[")


def main() -> None:
    log, name, kind, preset, ticks = sys.argv[1:6]
    items = Counter()
    tests = []
    scores = {}
    with open(log, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            if "has the following block data" in line:
                for compound in COMPOUND.findall(line.split("has the following block data", 1)[1]):
                    item = ITEM_ID.search(compound)
                    count = ITEM_COUNT.search(compound)
                    if item:
                        items[item.group(1)] += int(count.group(1)) if count else 1
            score = SCORE.search(line)
            if score:
                scores[score.group(1)] = int(score.group(2))
            match = COUNT.search(line)
            if match:
                tests.append(int(match.group(2)) if match.group(2) else (1 if match.group(1) == "passed" else 0))
    cows, platform_mobs, villagers = (tests + [None] * 3)[:3]
    spawns = "\t".join(f"{key}={value}" for key, value in sorted(scores.items()) if key.startswith("p") and key != "platform" and key != "piglins")
    drops = " ".join(f"{key}={value}" for key, value in sorted(items.items()))
    print(
        f"{name}\t{kind}\t{preset}\tticks={ticks}\tcows_left={cows}\tpiglin_spawns={scores.get('piglins')}"
        f"\tgolems={scores.get('golems')}\tplatform_spawns={scores.get('platform')}\tvillagers={villagers}\tplatform_mobs={platform_mobs}\twheat_grown={scores.get('wheat')}\twheat_age={scores.get('wheatage')}\tcane_blocks={scores.get('cane')}\t{spawns}\tfarm_drops: {drops}"
    )


if __name__ == "__main__":
    main()
