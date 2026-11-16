#!/usr/bin/env python3
"""Check the eight-page Spark output against the existing MapReduce reference values."""
import math
import re
import sys
from pathlib import Path

EXPECTED = {
    "C": 0.352722545627, "A": 0.326000819006, "B": 0.162769655796,
    "D": 0.046732599006, "G": 0.035996025369, "E": 0.025259451732,
    "F": 0.025259451732, "H": 0.025259451732,
}


def verify(path):
    rows = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"\(([A-H]),([0-9]+\.[0-9]{10})\)", line)
        if not match or match[1] in rows:
            raise ValueError("Invalid or repeated result: " + repr(line))
        rows[match[1]] = float(match[2])
    if rows.keys() != EXPECTED.keys():
        raise ValueError("Expected exactly eight pages, A through H")
    error = max(abs(rows[page] - EXPECTED[page]) for page in rows)
    if error > 1e-9 or abs(math.fsum(rows.values()) - 1.0) > 1e-8:
        raise ValueError(f"Rank mismatch: max error {error:.3g}")
    print(path.read_text(encoding="utf-8"), end="")
    print(f"SPARK PAGERANK PASS: 8 pages; max error={error:.3g}")


if __name__ == "__main__":
    verify(Path(sys.argv[1]))
