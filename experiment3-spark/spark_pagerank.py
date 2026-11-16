#!/usr/bin/env python3
"""Ten rounds of PageRank using Spark RDDs on the teaching graph."""
import sys


def parse_graph(lines):
    graph = {}
    for line in lines:
        fields = line.rstrip("\r\n").split("\t")
        if len(fields) != 2 or not fields[0] or fields[0] in graph:
            raise ValueError("Expected one unique page and one Tab per line: " + repr(line))
        page, outgoing = fields
        targets = tuple(outgoing.split(",")) if outgoing else ()
        if any(not target for target in targets) or len(set(targets)) != len(targets):
            raise ValueError("Invalid or duplicate outlink: " + repr(line))
        graph[page] = targets
    if not graph or any(target not in graph for targets in graph.values() for target in targets):
        raise ValueError("The graph must list each page, including pages with no outlinks.")
    return graph


def emit_contributions(item):
    _, (targets, rank) = item
    if targets:
        share = rank / len(targets)
        for target in targets:
            yield target, share


def format_pair(item):
    page, rank = item
    return f"({page},{rank:.10f})"


def run(input_uri, output_uri):
    from pyspark.sql import SparkSession

    spark = SparkSession.builder.appName("experiment3-spark-pagerank").getOrCreate()
    sc = spark.sparkContext
    sc.setLogLevel("WARN")
    try:
        # Small teaching graph: validate the page list before distributing it.
        graph = parse_graph(sc.textFile(input_uri).collect())
        count = len(graph)
        partitions = 2
        links = sc.parallelize(list(graph.items()), partitions).partitionBy(partitions).cache()
        ranks = links.mapValues(lambda _: 1.0 / count).cache()
        for round_no in range(1, 11):
            joined = links.join(ranks, partitions).cache()
            dangling = joined.values().filter(lambda pair: not pair[0]).map(lambda pair: pair[1]).sum()
            base = (1.0 - 0.85 + 0.85 * dangling) / count
            incoming = joined.flatMap(emit_contributions).reduceByKey(lambda a, b: a + b, partitions)
            new_ranks = links.leftOuterJoin(incoming, partitions).mapValues(
                lambda pair: base + 0.85 * (pair[1] if pair[1] is not None else 0.0)
            ).cache()
            if new_ranks.count() != count:
                raise RuntimeError("Page count changed")
            ranks.unpersist()
            joined.unpersist()
            ranks = new_ranks
            print(f"SPARK ROUND {round_no}/10", flush=True)
        ordered = ranks.collect()
        ordered.sort(key=lambda pair: (-round(pair[1], 12), pair[0]))
        sc.parallelize([format_pair(pair) for pair in ordered], 1).saveAsTextFile(output_uri)
    finally:
        spark.stop()


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: spark_pagerank.py <input URI> <output URI>")
    run(*sys.argv[1:])
