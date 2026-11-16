package edu.course.bigdata.pagerank;

import java.io.IOException;
import java.util.Locale;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/** Iterative MapReduce PageRank. State rows: node TAB rank TAB comma-separated outlinks. */
public final class PageRank extends Configured implements Tool {
    private static final double SCALE = 1e15;
    private enum Totals { NODES, DANGLING, L1 }

    public static final class Distribute extends Mapper<LongWritable, Text, Text, Text> {
        @Override
        protected void map(LongWritable offset, Text line, Context ctx)
                throws IOException, InterruptedException {
            String[] fields = line.toString().split("\\t", -1);
            if (fields.length != 3 || fields[0].isEmpty()) {
                throw new IOException("Invalid state row: " + line);
            }
            String node = fields[0], links = fields[2];
            double rank = Double.parseDouble(fields[1]);
            if (!Double.isFinite(rank) || rank < 0) {
                throw new IOException("Invalid old rank for " + node);
            }
            // Every vertex emits its structure, including sinks and isolated vertices.
            ctx.write(new Text(node), new Text("S\t" + rank + "\t" + links));
            if (!links.isEmpty()) {
                String[] out = links.split(",");
                Text share = new Text("M\t" + rank / out.length);
                for (String neighbor : out) {
                    ctx.write(new Text(neighbor), share);
                }
            }
        }
    }

    public static final class Collect extends Reducer<Text, Text, Text, Text> {
        private double damping, base;
        @Override
        protected void setup(Context ctx) {
            damping = ctx.getConfiguration().getDouble("pr.damping", 0.85);
            long n = ctx.getConfiguration().getLong("pr.nodes", 0);
            double dangling = ctx.getConfiguration().getDouble("pr.dangling", 0);
            base = (1.0 - damping + damping * dangling) / n;
        }

        @Override
        protected void reduce(Text node, Iterable<Text> messages, Context ctx)
                throws IOException, InterruptedException {
            double incoming = 0, oldRank = 0;
            String links = null;
            for (Text message : messages) {
                String value = message.toString();
                if (value.startsWith("M\t")) {
                    incoming += Double.parseDouble(value.substring(2));
                } else if (value.startsWith("S\t") && links == null) {
                    String[] fields = value.split("\\t", -1);
                    if (fields.length != 3) {
                        throw new IOException("Invalid structure for " + node);
                    }
                    oldRank = Double.parseDouble(fields[1]);
                    links = fields[2];
                } else {
                    throw new IOException("Unexpected or duplicate structure for " + node);
                }
            }
            if (links == null) {
                throw new IOException("Target vertex missing in initial graph: " + node);
            }
            double rank = base + damping * incoming;
            ctx.getCounter(Totals.NODES).increment(1);
            ctx.getCounter(Totals.L1).increment(Math.round(Math.abs(rank - oldRank) * SCALE));
            if (links.isEmpty()) {
                ctx.getCounter(Totals.DANGLING).increment(Math.round(rank * SCALE));
            }
            ctx.write(node, new Text(Double.toString(rank) + "\t" + links));
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println("Usage: PageRank <state> <rounds-dir> <nodes> <sinks> <max-rounds> <damping> <epsilon>");
            return 2;
        }
        Path input = new Path(args[0]), rounds = new Path(args[1]);
        long nodes = Long.parseLong(args[2]), sinks = Long.parseLong(args[3]);
        int limit = Integer.parseInt(args[4]);
        double damping = Double.parseDouble(args[5]), eps = Double.parseDouble(args[6]);
        if (nodes < 1 || sinks < 0 || sinks > nodes || limit < 1 ||
                !(damping > 0 && damping < 1) || !Double.isFinite(eps) || eps < 0) {
            throw new IllegalArgumentException("Invalid rank parameters");
        }
        if (rounds.getFileSystem(getConf()).exists(rounds)) {
            throw new IOException("Rounds directory already exists: " + rounds);
        }
        double dangling = (double) sinks / nodes;
        boolean converged = false;
        int completed = 0;
        for (int round = 1; round <= limit; round++) {
            Path output = new Path(rounds, String.format(Locale.ROOT, "round-%03d", round));
            Job job = Job.getInstance(getConf(), "experiment3-pagerank-round-" + round);
            job.getConfiguration().setLong("pr.nodes", nodes);
            job.getConfiguration().setDouble("pr.damping", damping);
            job.getConfiguration().setDouble("pr.dangling", dangling);
            job.setJarByClass(PageRank.class);
            job.setMapperClass(Distribute.class);
            job.setReducerClass(Collect.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);
            job.setNumReduceTasks(1);
            FileInputFormat.addInputPath(job, input);
            FileOutputFormat.setOutputPath(job, output);
            if (!job.waitForCompletion(true)) return 1;
            if (job.getCounters().findCounter(Totals.NODES).getValue() != nodes) {
                throw new IOException("Output node count changed in round " + round);
            }
            dangling = job.getCounters().findCounter(Totals.DANGLING).getValue() / SCALE;
            double delta = job.getCounters().findCounter(Totals.L1).getValue() / SCALE;
            System.out.printf(Locale.ROOT, "ROUND %d: L1=%.12g dangling=%.12g%n", round, delta, dangling);
            input = output;
            completed = round;
            if (eps > 0 && delta <= eps) {
                converged = true;
                break;
            }
        }
        System.out.printf(Locale.ROOT, "PAGERANK_RESULT\t%d\t%s\t%s%n", completed, input, converged);
        return 0;
    }

    public static void main(String[] args) throws Exception {
        System.exit(ToolRunner.run(new PageRank(), args));
    }
}
