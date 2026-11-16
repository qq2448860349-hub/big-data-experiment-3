#!/usr/bin/env python3
"""Prepare the graph and independently check the final Hadoop scores."""
import argparse
import math
from pathlib import Path
import re
import sys


def graph_from(path):
    graph = {}
    for number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
        nodes = line.partition("#")[0].split()
        if not nodes:
            continue
        if len(nodes) not in (1, 2) or any(not re.fullmatch(r"[A-Za-z0-9_.-]+", n) for n in nodes):
            raise ValueError(f"{path}:{number}: expected one or two page IDs (letters, digits, _, ., -)")
        for node in nodes:
            graph.setdefault(node, set())
        if len(nodes) == 2:
            graph[nodes[0]].add(nodes[1])
    if not graph:
        raise ValueError("Input graph is empty")
    return {node: sorted(graph[node]) for node in sorted(graph)}


def serial_rank(graph, rounds, damping):
    # Pull contributions from predecessors; Hadoop independently pushes messages.
    inbound = {node: [] for node in graph}
    sinks = [node for node, out in graph.items() if not out]
    for source, out in graph.items():
        for node in out:
            inbound[node].append(source)
    n = len(graph)
    ranks = dict.fromkeys(graph, 1.0 / n)
    for _ in range(rounds):
        base = (1 - damping) / n + damping * math.fsum(ranks[v] for v in sinks) / n
        ranks = {v: base + damping * math.fsum(ranks[u] / len(graph[u]) for u in src)
                 for v, src in inbound.items()}
    return ranks


def verify(graph, ranks, path):
    actual = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        node, rank, links = line.split("\t")
        if node in actual or node not in graph or links != ",".join(graph[node]):
            raise ValueError(f"Duplicate vertex or changed graph structure: {node}")
        actual[node] = float(rank)
        if not math.isfinite(actual[node]) or actual[node] < 0:
            raise ValueError(f"Invalid rank: {node}")
    if actual.keys() != ranks.keys():
        raise ValueError("Output vertex set differs from input")
    error = max(abs(actual[v] - ranks[v]) for v in ranks)
    total = math.fsum(actual.values())
    if error > 1e-9 or abs(total - 1) > 1e-8:
        raise ValueError(f"Rank mismatch: max error={error:g}, sum={total:.12f}")
    print(f"PASS: {len(actual)} vertices; rank sum={total:.12f}; max error={error:.3g}", file=sys.stderr)
    return actual


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("prepare", "reference", "verify"))
    parser.add_argument("edges", type=Path)
    parser.add_argument("--state", type=Path)
    parser.add_argument("--rounds", type=int, default=10)
    parser.add_argument("--damping", type=float, default=0.85)
    args = parser.parse_args()
    graph = graph_from(args.edges)
    if args.mode == "prepare":
        n = len(graph)
        args.state.write_text("".join(f"{v}\t{1/n:.17g}\t{','.join(out)}\n" for v, out in graph.items()),
                              encoding="utf-8", newline="\n")
        print(n, sum(not out for out in graph.values()), sum(map(len, graph.values())))
        return
    if args.rounds < 1 or not 0 < args.damping < 1:
        raise ValueError("Invalid rounds or damping")
    ranks = serial_rank(graph, args.rounds, args.damping)
    if args.mode == "verify":
        ranks = verify(graph, ranks, args.state)
    print("page\tpagerank")
    for v in sorted(ranks, key=lambda node: (-round(ranks[node], 12), node)):
        print(f"{v}\t{ranks[v]:.12f}")


if __name__ == "__main__":
    main()
