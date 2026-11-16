import importlib.util
import math
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("rank_reference", ROOT / "scripts/rank_reference.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RankTest(unittest.TestCase):
    def test_known_dangling_case(self):
        graph = module.graph_from(ROOT / "tests/one_edge.txt")
        first = module.serial_rank(graph, 1, .85)
        self.assertAlmostEqual(first["A"], .2875)
        self.assertAlmostEqual(first["B"], .7125)
        final = module.serial_rank(graph, 70, .85)
        self.assertAlmostEqual(final["A"], 1 / 2.85)
        self.assertAlmostEqual(final["B"], 1.85 / 2.85)

    def test_demo_graph(self):
        graph = module.graph_from(ROOT / "data/graph.txt")
        self.assertEqual(len(graph), 8)
        self.assertEqual(sum(map(len, graph.values())), 8)
        self.assertEqual([v for v in graph if not graph[v]], ["G", "H"])
        ranks = module.serial_rank(graph, 10, .85)
        self.assertAlmostEqual(math.fsum(ranks.values()), 1)
        self.assertEqual(max(ranks, key=ranks.get), "C")

    def test_verify_rejects_wrong_output(self):
        graph = {"A": ["B"], "B": []}
        ranks = module.serial_rank(graph, 1, .85)
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / "ranks.tsv"
            state.write_text("A\t0.2875\tB\nB\t0.7125\t\n", encoding="utf-8")
            actual = module.verify(graph, ranks, state)
            for node in ranks:
                self.assertAlmostEqual(actual[node], ranks[node])
            state.write_text("A\t0.5\tB\nB\t0.5\t\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Rank mismatch"):
                module.verify(graph, ranks, state)

    def test_duplicates_and_isolate(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "graph.txt"
            path.write_text("A B\nA B # duplicate\nC\n", encoding="utf-8")
            self.assertEqual(module.graph_from(path), {"A": ["B"], "B": [], "C": []})


if __name__ == "__main__":
    unittest.main()
