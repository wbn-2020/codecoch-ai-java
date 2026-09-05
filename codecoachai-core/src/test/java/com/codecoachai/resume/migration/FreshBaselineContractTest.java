package com.codecoachai.resume.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FreshBaselineContractTest {

    private static final Path SQL_DIR = Path.of("..", "sql");
    private static final Path MIGRATION_DIR = SQL_DIR.resolve("migration");
    private static final String SQL_TYPE =
            "(?:bigint|int|integer|smallint|mediumint|tinyint|decimal|numeric|"
                    + "double|float|boolean|bit|varchar|char|text|tinytext|mediumtext|"
                    + "longtext|datetime|timestamp|date|time|year|json|blob|tinyblob|"
                    + "mediumblob|longblob|binary|varbinary|enum|set)";
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?`?([a-z0-9_]+)`?\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_COLUMN = Pattern.compile(
            "(?im)^\\s*`?([a-z][a-z0-9_]*)`?\\s+" + SQL_TYPE + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_INDEX = Pattern.compile(
            "(?im)^\\s*(unique\\s+)?(?:key|index)\\s+`?([a-z][a-z0-9_]*)`?"
                    + "\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALTER_TABLE = Pattern.compile(
            "alter\\s+table\\s+`?([a-z0-9_]+)`?\\s+(.*?);",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ADD_COLUMN = Pattern.compile(
            "\\badd\\s+(?:column\\s+)?`?([a-z][a-z0-9_]*)`?\\s+" + SQL_TYPE + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_INDEX = Pattern.compile(
            "\\badd\\s+(unique\\s+)?(?:key|index)\\s+`?([a-z][a-z0-9_]*)`?"
                    + "\\s*\\(([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);

    @Test
    void freshBaselineAcceptsV4068ExactlyOnceInTheFlywaySequence() throws Exception {
        Map<String, TableDefinition> baseline = parseBaseline(
                Files.readString(SQL_DIR.resolve("init.sql")));
        TableDefinition jobApplication = baseline.get("job_application");
        assertNotNull(jobApplication, "fresh baseline must define job_application");
        assertFalse(jobApplication.columns.contains("import_fingerprint"));
        assertFalse(jobApplication.indexes.containsKey(
                "uk_job_application_import_fingerprint"));

        List<AlterAddition> additions = parseDirectAlterAdditions(
                Files.readString(MIGRATION_DIR.resolve(
                        "V4_068__career_import_application_fingerprint.sql")));
        assertEquals(2, additions.size(), "V4_068 must add one column and one index");

        TableDefinition migrated = jobApplication.copy();
        List<String> collisions = apply(additions, migrated);
        assertTrue(collisions.isEmpty(),
                () -> "V4_068 collides with the fresh baseline: " + collisions);
        assertTrue(migrated.columns.contains("import_fingerprint"));

        IndexDefinition fingerprintIndex =
                migrated.indexes.get("uk_job_application_import_fingerprint");
        assertNotNull(fingerprintIndex);
        assertTrue(fingerprintIndex.unique);
        assertEquals(List.of("user_id", "import_fingerprint", "deleted"),
                fingerprintIndex.columns);
    }

    @Test
    void noPostBaselineDirectAddRecreatesABaselineColumnOrIndex() throws Exception {
        Map<String, TableDefinition> baseline = parseBaseline(
                Files.readString(SQL_DIR.resolve("init.sql")));
        List<String> collisions = new ArrayList<>();

        try (Stream<Path> paths = Files.list(MIGRATION_DIR)) {
            for (Path migration : paths
                    .filter(Files::isRegularFile)
                    .filter(FreshBaselineContractTest::isPostBaselineMigration)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                for (AlterAddition addition :
                        parseDirectAlterAdditions(Files.readString(migration))) {
                    TableDefinition table = baseline.get(addition.tableName);
                    if (table == null) {
                        continue;
                    }
                    if (addition.kind == AdditionKind.COLUMN
                            && table.columns.contains(addition.objectName)) {
                        collisions.add(migration.getFileName() + ": "
                                + addition.tableName + "." + addition.objectName);
                    }
                    if (addition.kind == AdditionKind.INDEX
                            && table.indexes.containsKey(addition.objectName)) {
                        collisions.add(migration.getFileName() + ": "
                                + addition.tableName + "." + addition.objectName);
                    }
                }
            }
        }

        assertTrue(collisions.isEmpty(),
                () -> "Fresh baseline conflicts with direct migration DDL: " + collisions);
    }

    private static boolean isPostBaselineMigration(Path path) {
        return path.getFileName().toString().matches("V[34]_\\d+__.+\\.sql");
    }

    private static Map<String, TableDefinition> parseBaseline(String sql) {
        Map<String, TableDefinition> tables = new HashMap<>();
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            int openingParenthesis = matcher.end() - 1;
            int closingParenthesis = findMatchingParenthesis(sql, openingParenthesis);
            String tableName = canonical(matcher.group(1));
            String body = sql.substring(openingParenthesis + 1, closingParenthesis);
            tables.putIfAbsent(tableName, parseTableDefinition(body));
        }
        return tables;
    }

    private static TableDefinition parseTableDefinition(String body) {
        Set<String> columns = new LinkedHashSet<>();
        Matcher columnMatcher = TABLE_COLUMN.matcher(body);
        while (columnMatcher.find()) {
            columns.add(canonical(columnMatcher.group(1)));
        }

        Map<String, IndexDefinition> indexes = new LinkedHashMap<>();
        Matcher indexMatcher = TABLE_INDEX.matcher(body);
        while (indexMatcher.find()) {
            indexes.put(
                    canonical(indexMatcher.group(2)),
                    new IndexDefinition(
                            indexMatcher.group(1) != null,
                            parseIndexColumns(indexMatcher.group(3))));
        }
        return new TableDefinition(columns, indexes);
    }

    private static List<AlterAddition> parseDirectAlterAdditions(String sql) {
        String executableSql = stripCommentsAndStringLiterals(sql);
        List<AlterAddition> additions = new ArrayList<>();
        Matcher alterMatcher = ALTER_TABLE.matcher(executableSql);
        while (alterMatcher.find()) {
            String tableName = canonical(alterMatcher.group(1));
            String alterBody = alterMatcher.group(2);

            Matcher columnMatcher = ADD_COLUMN.matcher(alterBody);
            while (columnMatcher.find()) {
                additions.add(AlterAddition.column(
                        tableName, canonical(columnMatcher.group(1))));
            }

            Matcher indexMatcher = ADD_INDEX.matcher(alterBody);
            while (indexMatcher.find()) {
                additions.add(AlterAddition.index(
                        tableName,
                        canonical(indexMatcher.group(2)),
                        indexMatcher.group(1) != null,
                        parseIndexColumns(indexMatcher.group(3))));
            }
        }
        return additions;
    }

    private static List<String> apply(
            List<AlterAddition> additions, TableDefinition table) {
        List<String> collisions = new ArrayList<>();
        for (AlterAddition addition : additions) {
            if (addition.kind == AdditionKind.COLUMN) {
                if (!table.columns.add(addition.objectName)) {
                    collisions.add("column " + addition.objectName);
                }
            } else {
                IndexDefinition previous = table.indexes.putIfAbsent(
                        addition.objectName,
                        new IndexDefinition(addition.unique, addition.columns));
                if (previous != null) {
                    collisions.add("index " + addition.objectName);
                }
            }
        }
        return collisions;
    }

    private static List<String> parseIndexColumns(String value) {
        List<String> columns = new ArrayList<>();
        for (String rawColumn : value.split(",")) {
            String normalized = rawColumn
                    .trim()
                    .replace("`", "")
                    .replaceAll("\\s+(?:asc|desc)$", "")
                    .replaceAll("\\(\\d+\\)$", "");
            columns.add(canonical(normalized));
        }
        return List.copyOf(columns);
    }

    private static int findMatchingParenthesis(String sql, int openingParenthesis) {
        int depth = 0;
        boolean inString = false;
        boolean inBacktick = false;
        for (int index = openingParenthesis; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            if (inString) {
                if (current == '\\') {
                    index++;
                } else if (current == '\'' && next == '\'') {
                    index++;
                } else if (current == '\'') {
                    inString = false;
                }
                continue;
            }
            if (inBacktick) {
                if (current == '`') {
                    inBacktick = false;
                }
                continue;
            }
            if (current == '\'') {
                inString = true;
            } else if (current == '`') {
                inBacktick = true;
            } else if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unclosed CREATE TABLE definition");
    }

    private static String stripCommentsAndStringLiterals(String sql) {
        StringBuilder stripped = new StringBuilder(sql.length());
        boolean inString = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                    stripped.append('\n');
                } else {
                    stripped.append(' ');
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    stripped.append("  ");
                    index++;
                    inBlockComment = false;
                } else {
                    stripped.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (inString) {
                if (current == '\\') {
                    stripped.append("  ");
                    index++;
                } else if (current == '\'' && next == '\'') {
                    stripped.append("  ");
                    index++;
                } else if (current == '\'') {
                    stripped.append(' ');
                    inString = false;
                } else {
                    stripped.append(current == '\n' ? '\n' : ' ');
                }
                continue;
            }

            if (current == '-' && next == '-') {
                stripped.append("  ");
                index++;
                inLineComment = true;
            } else if (current == '#') {
                stripped.append(' ');
                inLineComment = true;
            } else if (current == '/' && next == '*') {
                stripped.append("  ");
                index++;
                inBlockComment = true;
            } else if (current == '\'') {
                stripped.append(' ');
                inString = true;
            } else {
                stripped.append(current);
            }
        }
        return stripped.toString();
    }

    private static String canonical(String value) {
        return value.toLowerCase(Locale.ROOT).trim();
    }

    private enum AdditionKind {
        COLUMN,
        INDEX
    }

    private record IndexDefinition(boolean unique, List<String> columns) {
    }

    private record AlterAddition(
            String tableName,
            AdditionKind kind,
            String objectName,
            boolean unique,
            List<String> columns) {

        private static AlterAddition column(String tableName, String columnName) {
            return new AlterAddition(
                    tableName, AdditionKind.COLUMN, columnName, false, List.of());
        }

        private static AlterAddition index(
                String tableName,
                String indexName,
                boolean unique,
                List<String> columns) {
            return new AlterAddition(
                    tableName, AdditionKind.INDEX, indexName, unique, columns);
        }
    }

    private static final class TableDefinition {
        private final Set<String> columns;
        private final Map<String, IndexDefinition> indexes;

        private TableDefinition(
                Set<String> columns, Map<String, IndexDefinition> indexes) {
            this.columns = new HashSet<>(columns);
            this.indexes = new HashMap<>(indexes);
        }

        private TableDefinition copy() {
            return new TableDefinition(columns, indexes);
        }
    }
}
