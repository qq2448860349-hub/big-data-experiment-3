#!/usr/bin/env python3
"""Convert the course adjacency list to the Java runner's state and format its output."""
import math
import re
import sys
from pathlib import Path

NAME = re.compile(r"[A-Za-z0-9_.-]+")


def graph_from(path):
    graph = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        page, out = line.split("\t")
        links = tuple(out.split(",")) if out else ()
        if (page in graph or not NAME.fullmatch(page)
                or any(not NAME.fullmatch(link) for link in links)
                or len(set(links)) != len(links)):
            raise ValueError(f"Invalid or duplicate page or link: {line!r}")
        graph[page] = links
    if not graph or any(link not in graph for links in graph.values() for link in links):
        raise ValueError("The input must list every page, including pages with no outlinks.")
    return graph


def prepare(graph, target):
    initial = 1.0 / len(graph)
    with target.open("x", encoding="utf-8", newline="\n") as stream:
        for page, links in sorted(graph.items()):
            stream.write(f"{page}\t{initial!r}\t{','.join(links)}\n")
    print(len(graph), sum(not links for links in graph.values()))


def format_state(graph, state, target):
    ranks = {}
    for line in state.read_text(encoding="utf-8").splitlines():
        page, score, out = line.split("\t")
        if page in ranks or page not in graph or out != ",".join(graph[page]):
            raise ValueError(f"Incorrect page or adjacency: {page}")
        rank = float(score)
        if not math.isfinite(rank) or rank < 0:
            raise ValueError(f"Invalid rank: {score}")
        ranks[page] = rank
    if ranks.keys() != graph.keys() or abs(math.fsum(ranks.values()) - 1.0) > 1e-8:
        raise ValueError("Page set or score sum differs from the expected result.")
    with target.open("x", encoding="utf-8", newline="\n") as stream:
        for page in sorted(ranks, key=lambda node: (-round(ranks[node], 12), node)):
            stream.write(f"({page},{ranks[page]:.10f})\n")


if __name__ == "__main__":
    mode, graph_path, *paths = sys.argv[1:]
    pages = graph_from(Path(graph_path))
    if mode == "prepare" and len(paths) == 1:
        prepare(pages, Path(paths[0]))
    elif mode == "format" and len(paths) == 2:
        format_state(pages, *map(Path, paths))
    else:
        raise SystemExit("Use: prepare <graph> <initial> or format <graph> <state> <ranking>")
