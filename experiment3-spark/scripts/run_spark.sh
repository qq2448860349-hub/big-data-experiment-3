#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export HADOOP_CONF_DIR=/home/vboxuser/hadoop/etc/hadoop
spark=/home/vboxuser/spark-3.5.8-bin-hadoop3/bin/spark-submit
if [[ ! -x "$spark" ]]; then
    echo "Spark not found at: $spark" >&2
    exit 1
fi
if [[ "$(hdfs getconf -confKey fs.defaultFS)" != hdfs://localhost:9000 ]]; then
    echo "Unexpected HDFS address; check core-site.xml" >&2
    exit 1
fi
mkdir -p records
work="$(mktemp -d "$PWD/records/spark-$(date +%Y%m%d-%H%M%S)-XXXXXX")"
base="/user/vboxuser/experiment3/$(basename "$work")"
echo "Run directory: $work"
hdfs dfs -mkdir "$base"
hdfs dfs -put "$PWD/data/graph.tsv" "$base/graph.tsv"
"$spark" --master yarn --deploy-mode client \
    --num-executors 1 --executor-cores 1 --executor-memory 512m \
    --conf spark.dynamicAllocation.enabled=false \
    --conf spark.pyspark.python=/usr/bin/python3 \
    --conf spark.ui.showConsoleProgress=false \
    "$PWD/spark_pagerank.py" "hdfs://localhost:9000$base/graph.tsv" "hdfs://localhost:9000$base/output" \
    2>&1 | tee "$work/spark.log"
hdfs dfs -test -e "$base/output/_SUCCESS"
hdfs dfs -get "$base/output/part-00000" "$work/ranking.txt"
python3 verify_results.py "$work/ranking.txt"
echo "Results: $work/ranking.txt"
