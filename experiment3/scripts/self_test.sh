#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 -B tests/test_reference.py
bash scripts/run.sh local tests/one_edge.txt 2
echo 'SELF-TEST PASS'
