import json
import subprocess
import sys
from collections import Counter

JFR = "/opt/homebrew/opt/openjdk@25/bin/jfr"


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit("usage: jfr_inclusive.py <recording.jfr> [thread-name] [top-n]")
    recording = sys.argv[1]
    thread = sys.argv[2] if len(sys.argv) > 2 else "Server thread"
    top = int(sys.argv[3]) if len(sys.argv) > 3 else 60

    raw = subprocess.run(
        [JFR, "print", "--json", "--stack-depth", "256", "--events", "jdk.ExecutionSample", recording],
        check=True, capture_output=True, text=True,
    ).stdout
    events = json.loads(raw)["recording"]["events"]

    inclusive = Counter()
    samples = 0
    for event in events:
        values = event["values"]
        if values["sampledThread"]["javaName"] != thread:
            continue
        samples += 1
        seen = set()
        for frame in values["stackTrace"]["frames"]:
            method = frame["method"]
            name = f'{method["type"]["name"]}.{method["name"]}'
            if name not in seen:
                seen.add(name)
                inclusive[name] += 1

    print(f"{samples} samples on '{thread}'")
    for name, count in inclusive.most_common(top):
        print(f"{100.0 * count / max(samples, 1):6.2f}%  {name}")


if __name__ == "__main__":
    main()
