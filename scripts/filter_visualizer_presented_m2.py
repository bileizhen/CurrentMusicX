"""Keep only complete presentation windows inside a recorded preset/soak interval.

Android uptime and actualPresent use CLOCK_MONOTONIC. Periodic native records bound
the steady phase conservatively: discard its first five seconds and all transition
windows, including the ECO check after Dark. No FPS values are fabricated.
"""
import argparse
import csv
import json
from pathlib import Path


def filter_windows(rows, records):
    bounds = {}
    for record in records:
        phase = record["phase"]
        if not (phase.startswith("preset-") or phase == "soak"):
            continue
        stamp = int(record["uptimeMs"]) * 1_000_000
        low, high = bounds.get(phase, (stamp, stamp))
        bounds[phase] = (min(low, stamp), max(high, stamp))
    valid, excluded = [], []
    for row in rows:
        bound = bounds.get(row["phase"])
        if bound and int(row["firstPresentNs"]) >= bound[0] and int(row["lastPresentNs"]) <= bound[1]:
            valid.append(row)
        else:
            excluded.append(row)
    return valid, excluded


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv")
    parser.add_argument("records")
    parser.add_argument("output")
    args = parser.parse_args()
    with open(args.csv, encoding="utf-8-sig", newline="") as source:
        reader = csv.DictReader(source)
        fields = reader.fieldnames
        rows = list(reader)
    records = [json.loads(line) for line in Path(args.records).read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    valid, excluded = filter_windows(rows, records)
    for name, values in ((args.output, valid), (args.output + ".excluded.csv", excluded)):
        with open(name, "w", encoding="utf-8", newline="") as target:
            writer = csv.DictWriter(target, fieldnames=fields)
            writer.writeheader()
            writer.writerows(values)
    print(f"Kept {len(valid)} complete windows; excluded {len(excluded)} transition/outside windows")
    for phase in sorted({row["phase"] for row in valid}):
        matching = [row for row in valid if row["phase"] == phase]
        intervals = sum(int(row["frames"]) - 1 for row in matching)
        duration = sum(int(row["lastPresentNs"]) - int(row["firstPresentNs"]) for row in matching) / 1e9
        print(f"{phase}: {len(matching)} windows, {intervals} intervals, sampled={duration:.3f}s, weighted FPS={intervals / duration:.3f}")


if __name__ == "__main__":
    main()
