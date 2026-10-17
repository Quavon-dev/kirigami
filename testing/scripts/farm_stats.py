import os
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

METRICS = ["platform_spawns", "piglin_spawns", "golems", "cows_left", "wheat_age", "cane_blocks", "drops"]


def parse(line: str) -> tuple[str, dict[str, float]]:
    fields = line.rstrip("\n").split("\t")
    group = re.sub(r"-r\d+$", "", fields[0])
    group = re.sub(r"^[a-z]+\d+-", "", group)
    values = {k: float(v) for k, v in re.findall(r"(\w+)=(-?\d+)", "\t".join(fields[3:]).split("farm_drops:")[0])}
    drops = re.findall(r"=(\d+)", line.split("farm_drops:")[1]) if "farm_drops:" in line else []
    values["drops"] = float(sum(int(d) for d in drops))
    return group, values


def main(path: str, prefixes: list[str]) -> None:
    groups: dict[str, list[dict[str, float]]] = defaultdict(list)
    for line in Path(path).read_text().splitlines():
        if f"ticks={os.environ.get('TICKS', '120000')}\t" in line and any(line.startswith(p + "-") for p in prefixes):
            group, values = parse(line)
            groups[group].append(values)
    base = groups.get("V", [])
    found = list(dict.fromkeys(k for rows in groups.values() for r in rows for k in r))
    metrics = METRICS if "platform_spawns" in found else [k for k in found if k not in ("drops", "ticks")]
    print("group n " + " ".join(metrics))
    for group, rows in sorted(groups.items()):
        cells = []
        for metric in metrics:
            sample = [r[metric] for r in rows if metric in r]
            ref = [r[metric] for r in base if metric in r]
            if len(sample) < 2 or len(ref) < 2:
                cells.append("-")
                continue
            mean, ref_mean = statistics.mean(sample), statistics.mean(ref)
            se = (statistics.variance(sample) / len(sample) + statistics.variance(ref) / len(ref)) ** 0.5
            delta = (mean / ref_mean - 1) * 100 if ref_mean else 0.0
            t = (mean - ref_mean) / se if se else 0.0
            cells.append(f"{mean:.1f}({delta:+.1f}%,t{t:+.1f})")
        print(group, len(rows), " ".join(cells))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
