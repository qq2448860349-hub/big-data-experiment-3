package edu.course.bigdata.hbase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.NamespaceDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;
import org.apache.hadoop.hbase.util.Bytes;

/** All writes and destructive exercises are confined to this run's new namespace. */
public final class HBaseLab {
    private final Connection connection;
    private final Admin admin;
    private final String namespace;

    private HBaseLab(Connection connection, Admin admin, String namespace) {
        this.connection = connection;
        this.admin = admin;
        this.namespace = namespace;
    }

    private TableName tableName(String name) {
        if (!name.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Expected an unqualified lab table name");
        }
        return TableName.valueOf(namespace, name);
    }

    private static String[] parts(String field) {
        String[] components = field.split(":", 2);
        if (components[0].isEmpty() || (components.length == 2 && components[1].isEmpty())) {
            throw new IllegalArgumentException("Invalid field: " + field);
        }
        return components;
    }

    public void createTable(String tableName, String[] fields) throws IOException {
        TableName name = tableName(tableName);
        Set<String> families = new LinkedHashSet<>();
        for (String field : fields) families.add(parts(field)[0]);
        if (families.isEmpty()) throw new IllegalArgumentException("No column family");
        TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(name);
        for (String family : families) {
            builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
        }
        if (admin.tableExists(name)) {
            if (admin.isTableEnabled(name)) admin.disableTable(name);
            admin.deleteTable(name);
        }
        admin.createTable(builder.build());
    }

    public void addRecord(String tableName, String row, String[] fields, String[] values)
            throws IOException {
        if (fields.length == 0 || fields.length != values.length) {
            throw new IllegalArgumentException("Mismatched fields and values");
        }
        Put put = new Put(Bytes.toBytes(row));
        for (int i = 0; i < fields.length; i++) {
            String[] field = parts(fields[i]);
            String qualifier = field.length == 2 ? field[1] : "value";
            put.addColumn(Bytes.toBytes(field[0]), Bytes.toBytes(qualifier), Bytes.toBytes(values[i]));
        }
        try (Table table = connection.getTable(tableName(tableName))) {
            table.put(put);
        }
    }

    /** Scan ALL rows so that a row lacking the requested column is still reported. */
    public Map<String, Map<String, String>> scanColumn(String tableName, String column)
            throws IOException {
        String[] field = parts(column);
        byte[] family = Bytes.toBytes(field[0]);
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        try (Table table = connection.getTable(tableName(tableName));
             ResultScanner scan = table.getScanner(new Scan())) {
            for (Result record : scan) {
                Map<String, String> cells = new LinkedHashMap<>();
                if (field.length == 2) {
                    byte[] value = record.getValue(family, Bytes.toBytes(field[1]));
                    if (value != null) cells.put(column, Bytes.toString(value));
                } else {
                    Map<byte[], byte[]> values = record.getFamilyMap(family);
                    if (values != null) {
                        for (Map.Entry<byte[], byte[]> entry : values.entrySet()) {
                            cells.put(field[0] + ":" + Bytes.toString(entry.getKey()),
                                    Bytes.toString(entry.getValue()));
                        }
                    }
                }
                result.put(Bytes.toString(record.getRow()), cells.isEmpty() ? null : cells);
            }
        }
        result.forEach((row, cells) -> System.out.println(row + " => " + cells));
        return result;
    }

    /** The assignment's three-argument form reads the new value from stdin. */
    public void modifyData(String tableName, String row, String column) throws IOException {
        System.out.print("New cell value: ");
        modifyData(tableName, row, column, new Scanner(System.in, "UTF-8").nextLine());
    }

    public void modifyData(String tableName, String row, String column, String value)
            throws IOException {
        if (parts(column).length != 2) throw new IllegalArgumentException("Use family:qualifier");
        addRecord(tableName, row, new String[]{column}, new String[]{value});
    }

    public void deleteRow(String tableName, String row) throws IOException {
        try (Table table = connection.getTable(tableName(tableName))) {
            table.delete(new Delete(Bytes.toBytes(row)));
        }
    }

    private void deleteColumn(String tableName, String row, String column) throws IOException {
        String[] field = parts(column);
        if (field.length != 2) throw new IllegalArgumentException("Use family:qualifier");
        try (Table table = connection.getTable(tableName(tableName))) {
            table.delete(new Delete(Bytes.toBytes(row))
                    .addColumns(Bytes.toBytes(field[0]), Bytes.toBytes(field[1])));
        }
    }

    private long count(String name) throws IOException {
        long rows = 0;
        try (Table table = connection.getTable(tableName(name));
             ResultScanner scan = table.getScanner(new Scan().setFilter(new FirstKeyOnlyFilter()))) {
            for (Result ignored : scan) rows++;
        }
        return rows;
    }

    private void show(String name) throws IOException {
        System.out.println("FULL SCAN " + name);
        try (Table table = connection.getTable(tableName(name));
             ResultScanner scan = table.getScanner(new Scan())) {
            for (Result row : scan) {
                Map<String, String> cells = new LinkedHashMap<>();
                row.getNoVersionMap().forEach((family, columns) ->
                    columns.forEach((qualifier, value) ->
                        cells.put(Bytes.toString(family) + ":" + Bytes.toString(qualifier),
                                  Bytes.toString(value))));
                System.out.println(Bytes.toString(row.getRow()) + " " + cells);
            }
        }
    }

    private String get(String name, String row, String field) throws IOException {
        String[] column = parts(field);
        try (Table table = connection.getTable(tableName(name))) {
            byte[] value = table.get(new Get(Bytes.toBytes(row)))
                    .getValue(Bytes.toBytes(column[0]), Bytes.toBytes(column[1]));
            return value == null ? null : Bytes.toString(value);
        }
    }

    private void importRows(Path data, String name, String[] fields, int expected) throws IOException {
        createTable(name, fields);
        for (String line : Files.readAllLines(data.resolve(name + ".tsv"), StandardCharsets.UTF_8)) {
            String[] values = line.split("\t", -1);
            String key = "Student".equals(name) ? values[1]
                    : "SC".equals(name) ? values[0] + "#" + values[1] : values[0];
            addRecord(name, key, fields, values);
        }
        require(count(name) == expected, "Wrong row count: " + name);
        show(name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private void run(Path data) throws IOException {
        importRows(data, "Student",
                new String[]{"info:S_No", "info:S_Name", "info:S_Sex", "info:S_Age"}, 3);
        importRows(data, "Course", new String[]{"info:C_No", "info:C_Name", "info:C_Credit"}, 3);
        importRows(data, "SC", new String[]{"info:SC_Sno", "info:SC_Cno", "Score:SC_Score"}, 6);
        require(Objects.equals(get("Student", "Mary", "info:S_No"), "2015003"), "Mary missing");
        require(Objects.equals(get("Student", "Lisi", "info:S_No"), "2015003"), "Lisi missing");
        require(Objects.equals(get("SC", "2015002#123002", "Score:SC_Score"), "77"), "SC row missing");

        String[] fields = {"info:name", "Score:Math", "Score:English"};
        createTable("ApiDemo", fields);
        addRecord("ApiDemo", "alice", fields, new String[]{"Alice", "86", "69"});
        addRecord("ApiDemo", "bob", new String[]{"info:name"}, new String[]{"Bob"});
        require(count("ApiDemo") == 2, "Demo should have two rows");
        show("ApiDemo");
        System.out.println("COLUMN FAMILY Score");
        Map<String, Map<String, String>> family = scanColumn("ApiDemo", "Score");
        require(family.get("alice").size() == 2, "Family scan did not return both columns");
        System.out.println("COLUMN Score:Math");
        Map<String, Map<String, String>> math = scanColumn("ApiDemo", "Score:Math");
        require(math.get("alice").size() == 1 && "86".equals(math.get("alice").get("Score:Math"))
                && math.containsKey("bob") && math.get("bob") == null, "Column scan incorrect");

        TableName demo = tableName("ApiDemo");
        admin.addColumnFamily(demo, ColumnFamilyDescriptorBuilder.of("extra"));
        require(admin.getDescriptor(demo).hasColumnFamily(Bytes.toBytes("extra")), "Family add failed");
        addRecord("ApiDemo", "alice", new String[]{"extra:note"}, new String[]{"temporary"});
        deleteColumn("ApiDemo", "alice", "extra:note");
        require(get("ApiDemo", "alice", "extra:note") == null, "Cell delete failed");
        admin.deleteColumnFamily(demo, Bytes.toBytes("extra"));
        require(!admin.getDescriptor(demo).hasColumnFamily(Bytes.toBytes("extra")), "Family delete failed");
        System.out.println("PASS add/delete column and column family");

        modifyData("ApiDemo", "alice", "Score:Math", "90");
        require("90".equals(get("ApiDemo", "alice", "Score:Math")), "Modify failed");
        deleteRow("ApiDemo", "bob");
        require(count("ApiDemo") == 1 && get("ApiDemo", "bob", "info:name") == null, "Delete row failed");
        System.out.println("PASS modify cell and delete row");

        admin.disableTable(demo);
        admin.truncateTable(demo, true);
        require(count("ApiDemo") == 0 && admin.isTableEnabled(demo), "Truncate failed");
        System.out.println("PASS truncate: ApiDemo=0");
        addRecord("ApiDemo", "scratch", new String[]{"info:name"}, new String[]{"before recreate"});
        createTable("ApiDemo", fields);
        require(count("ApiDemo") == 0, "Recreate failed");
        System.out.println("PASS recreate: ApiDemo=0");

        TableName[] names = admin.listTableNamesByNamespace(namespace);
        Arrays.sort(names);
        require(names.length == 4, "Expected four experiment tables");
        System.out.println("TABLES " + Arrays.toString(names));
        System.out.println("COUNTS Student=" + count("Student") + ", Course=" + count("Course")
                + ", SC=" + count("SC") + ", ApiDemo=" + count("ApiDemo"));
        System.out.println("HBASE JAVA PASS");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !args[0].matches("exp3_java_[0-9]{14}_[A-Za-z0-9]{6}")) {
            throw new IllegalArgumentException("Use run.sh to create a fresh lab namespace");
        }
        Configuration conf = HBaseConfiguration.create();
        conf.setInt("hbase.client.retries.number", 5);
        try (Connection connection = ConnectionFactory.createConnection(conf);
             Admin admin = connection.getAdmin()) {
            // Creating a brand new namespace is mandatory; existing namespaces are never reused.
            admin.createNamespace(NamespaceDescriptor.create(args[0]).build());
            System.out.println("NAMESPACE " + args[0]);
            new HBaseLab(connection, admin, args[0]).run(Paths.get(args[1]));
        }
    }
}
