#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:-yarn}"
graph="${2:-data/graph.txt}"
limit="${3:-10}"
damping="${4:-0.85}"
epsilon="${5:-0}"
if [[ "$mode" != local && "$mode" != yarn ]]; then
    echo "Usage: bash scripts/run.sh [local|yarn] [graph] [rounds] [damping] [epsilon]" >&2
    exit 2
fi
if [[ ! -f build/experiment3.jar ]]; then
    echo "Build first: bash scripts/build.sh" >&2
    exit 2
fi
mkdir -p records
work="$(mktemp -d "$PWD/records/$mode-$(date +%Y%m%d-%H%M%S)-XXXXXX")"
echo "Run directory: $work"
cp -- "$graph" "$work/graph.txt"
meta="$(python3 scripts/rank_reference.py prepare "$work/graph.txt" --state "$work/initial.tsv")"
read -r nodes sinks edges <<< "$meta"
echo "Input: $nodes vertices, $edges directed edges, $sinks dangling vertices"
flags=()
if [[ "$mode" == yarn ]]; then
    if [[ "$(hdfs getconf -confKey fs.defaultFS)" != hdfs://localhost:9000 ]]; then
        echo 'Expected fs.defaultFS=hdfs://localhost:9000; check core-site.xml.' >&2
        exit 2
    fi
    base="/user/vboxuser/experiment3/$(basename "$work")"
    hdfs dfs -mkdir -p "$base/input"
    hdfs dfs -put "$work/initial.tsv" "$base/input/initial.tsv"
    input="$base/input"
    rounds="$base/rounds"
    flags=(-Dmapreduce.framework.name=yarn)
else
    input="$work/initial.tsv"
    rounds="$work/rounds"
    flags=(-Dmapreduce.framework.name=local -Dfs.defaultFS=file:///)
fi
hadoop jar build/experiment3.jar edu.course.bigdata.pagerank.PageRank \
    "${flags[@]}" "$input" "$rounds" "$nodes" "$sinks" "$limit" "$damping" "$epsilon" \
    2>&1 | tee "$work/hadoop.log"
result="$(grep $'^PAGERANK_RESULT\t' "$work/hadoop.log" | tail -n 1)"
IFS=$'\t' read -r marker completed output converged <<< "$result"
echo "Completed rounds: $completed; reached epsilon: $converged"
if [[ "$mode" == yarn ]]; then
    hdfs dfs -ls "$output"
    hdfs dfs -get "$output/part-r-00000" "$work/final-state.tsv"
else
    cp "$output/part-r-00000" "$work/final-state.tsv"
fi
python3 scripts/rank_reference.py verify "$work/graph.txt" --state "$work/final-state.tsv" \
    --rounds "$completed" --damping "$damping" | tee "$work/ranking.tsv"
echo "Results: $work/ranking.tsv"
echo "PAGERANK PASS"
