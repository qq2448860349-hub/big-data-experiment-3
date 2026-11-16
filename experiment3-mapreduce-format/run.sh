#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
jar=/home/vboxuser/experiment3/build/experiment3.jar
spark_dir=/home/vboxuser/experiment3-spark
graph="$spark_dir/data/graph.tsv"
shopt -s nullglob
spark_results=("$spark_dir"/records/spark-20261116-125138-*/ranking.txt)
if [[ $# -gt 0 ]]; then
    spark_results=("$1")
fi
if [[ ! -f "$jar" || ! -f "$graph" ]]; then
    echo 'Missing the existing Java JAR or the shared Spark adjacency list.' >&2
    exit 1
fi
if [[ ${#spark_results[@]} -ne 1 || ! -f "${spark_results[0]:-}" ]]; then
    echo 'Expected the successful Spark ranking.txt; specify its absolute filename as an argument.' >&2
    exit 1
fi
if [[ "$(hdfs getconf -confKey fs.defaultFS)" != hdfs://localhost:9000 ]]; then
    echo 'Unexpected HDFS address; check core-site.xml.' >&2
    exit 1
fi
mkdir -p records
work="$(mktemp -d "$PWD/records/mapreduce-$(date +%Y%m%d-%H%M%S)-XXXXXX")"
base="/user/vboxuser/experiment3/$(basename "$work")"
echo "Run directory: $work"
cp -- "$graph" "$work/graph.tsv"
cp -- "${spark_results[0]}" "$work/spark-ranking.txt"
metadata="$(python3 adapt_graph.py prepare "$work/graph.tsv" "$work/initial.tsv")"
read -r nodes sinks <<< "$metadata"
hdfs dfs -mkdir "$base"
hdfs dfs -mkdir "$base/input"
hdfs dfs -put "$work/graph.tsv" "$base/graph.tsv"
hdfs dfs -put "$work/initial.tsv" "$base/input/initial.tsv"
hadoop jar "$jar" edu.course.bigdata.pagerank.PageRank -Dmapreduce.framework.name=yarn \
    "$base/input" "$base/rounds" "$nodes" "$sinks" 10 0.85 0 2>&1 | tee "$work/hadoop.log"
final="$base/rounds/round-010"
hdfs dfs -test -e "$final/_SUCCESS"
hdfs dfs -get "$final/part-r-00000" "$work/final-state.tsv"
python3 adapt_graph.py format "$work/graph.tsv" "$work/final-state.tsv" "$work/mapreduce-ranking.txt"
cat "$work/mapreduce-ranking.txt"
diff -u "$work/spark-ranking.txt" "$work/mapreduce-ranking.txt"
echo "MATCH: MapReduce and Spark rankings are identical to 10 decimal places."
echo "Results: $work/mapreduce-ranking.txt"
