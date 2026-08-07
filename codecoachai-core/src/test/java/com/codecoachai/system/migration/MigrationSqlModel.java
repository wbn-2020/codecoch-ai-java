package com.codecoachai.system.migration;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class MigrationSqlModel {

    private MigrationSqlModel() {
    }

    static Script parse(String sql) {
        return new Script(new Lexer(sql).tokenize());
    }

    static final class Script {
        private final List<Token> tokens;

        private Script(List<Token> tokens) {
            this.tokens = List.copyOf(tokens);
        }

        Set<String> insertTargets() {
            Set<String> targets = new LinkedHashSet<>();
            for (int index = 0; index + 2 < tokens.size(); index++) {
                if (tokens.get(index).isWord("insert")
                        && tokens.get(index + 1).isWord("into")
                        && tokens.get(index + 2).kind == TokenKind.WORD) {
                    targets.add(tokens.get(index + 2).text);
                }
            }
            return targets;
        }

        InsertModel singleInsertInto(String tableName) {
            List<InsertModel> matches = insertsInto(tableName);
            if (matches.size() != 1) {
                throw new IllegalArgumentException(
                        "Expected one INSERT into " + tableName + " but found " + matches.size());
            }
            return matches.get(0);
        }

        List<InsertModel> insertsInto(String tableName) {
            String canonicalTable = canonical(tableName);
            List<InsertModel> matches = new ArrayList<>();
            for (int index = 0; index + 2 < tokens.size(); index++) {
                if (!tokens.get(index).isWord("insert")
                        || !tokens.get(index + 1).isWord("into")
                        || !tokens.get(index + 2).isWord(canonicalTable)) {
                    continue;
                }
                matches.add(parseInsert(index));
            }
            return matches;
        }

        UpdateModel singleUpdate(String tableName) {
            String canonicalTable = canonical(tableName);
            List<UpdateModel> matches = new ArrayList<>();
            for (int index = 0; index + 1 < tokens.size(); index++) {
                if (tokens.get(index).isWord("update")
                        && tokens.get(index + 1).isWord(canonicalTable)) {
                    matches.add(parseUpdate(index));
                }
            }
            if (matches.size() != 1) {
                throw new IllegalArgumentException(
                        "Expected one UPDATE of " + tableName + " but found " + matches.size());
            }
            return matches.get(0);
        }

        List<String> stringLiterals() {
            return tokens.stream()
                    .filter(token -> token.kind == TokenKind.STRING)
                    .map(Token::text)
                    .toList();
        }

        boolean containsSequence(String... expected) {
            List<String> sequence = new ArrayList<>(expected.length);
            for (String token : expected) {
                sequence.add(canonical(token));
            }
            for (int start = 0; start + sequence.size() <= tokens.size(); start++) {
                boolean match = true;
                for (int offset = 0; offset < sequence.size(); offset++) {
                    if (!tokens.get(start + offset).text.equals(sequence.get(offset))) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return true;
                }
            }
            return false;
        }

        List<AlterTableModel> dynamicAlterTables() {
            List<AlterTableModel> alters = new ArrayList<>();
            for (String literal : stringLiterals()) {
                Script nested = MigrationSqlModel.parse(literal);
                if (nested.tokens.size() >= 3
                        && nested.tokens.get(0).isWord("alter")
                        && nested.tokens.get(1).isWord("table")) {
                    alters.add(parseAlterTable(nested.tokens));
                }
            }
            return alters;
        }

        private InsertModel parseInsert(int insertIndex) {
            int cursor = insertIndex + 3;
            String tableName = tokens.get(insertIndex + 2).text;
            if (!tokens.get(cursor).isSymbol("(")) {
                throw new IllegalArgumentException("INSERT column list is required for " + tableName);
            }
            int columnEnd = matchingParenthesis(tokens, cursor);
            List<String> columns = tokens.subList(cursor + 1, columnEnd).stream()
                    .filter(token -> token.kind == TokenKind.WORD)
                    .map(Token::text)
                    .toList();

            cursor = columnEnd + 1;
            while (cursor < tokens.size() && !tokens.get(cursor).isWord("values")) {
                cursor++;
            }
            if (cursor + 1 >= tokens.size() || !tokens.get(cursor + 1).isSymbol("(")) {
                throw new IllegalArgumentException("INSERT VALUES list is required for " + tableName);
            }
            int valueStart = cursor + 1;
            int valueEnd = matchingParenthesis(tokens, valueStart);
            List<List<Token>> expressions = splitTopLevel(
                    tokens.subList(valueStart + 1, valueEnd), ",");
            if (columns.size() != expressions.size()) {
                throw new IllegalArgumentException(
                        "INSERT column/value count mismatch for " + tableName);
            }

            Map<String, Expression> values = new LinkedHashMap<>();
            for (int index = 0; index < columns.size(); index++) {
                values.put(columns.get(index), new Expression(expressions.get(index)));
            }
            return new InsertModel(tableName, values);
        }

        private UpdateModel parseUpdate(int updateIndex) {
            String tableName = tokens.get(updateIndex + 1).text;
            int setIndex = findWord(tokens, updateIndex + 2, "set");
            int whereIndex = findWord(tokens, setIndex + 1, "where");
            int statementEnd = findSymbol(tokens, whereIndex + 1, ";");
            if (statementEnd < 0) {
                statementEnd = tokens.size();
            }

            Map<String, Expression> assignments = new LinkedHashMap<>();
            for (List<Token> assignment :
                    splitTopLevel(tokens.subList(setIndex + 1, whereIndex), ",")) {
                int equalsIndex = findSymbol(assignment, 0, "=");
                if (equalsIndex <= 0) {
                    throw new IllegalArgumentException("Invalid UPDATE assignment: " + assignment);
                }
                String column = lastWord(assignment.subList(0, equalsIndex));
                assignments.put(
                        column,
                        new Expression(assignment.subList(equalsIndex + 1, assignment.size())));
            }

            List<Condition> conditions = new ArrayList<>();
            for (List<Token> condition :
                    splitTopLevelByWord(tokens.subList(whereIndex + 1, statementEnd), "and")) {
                conditions.add(parseEqualityCondition(condition));
            }
            return new UpdateModel(tableName, assignments, conditions);
        }
    }

    record InsertModel(String tableName, Map<String, Expression> values) {

        Expression value(String columnName) {
            return requireNonNull(values.get(canonical(columnName)), columnName);
        }
    }

    record UpdateModel(
            String tableName,
            Map<String, Expression> assignments,
            List<Condition> conditions) {

        Expression assignment(String columnName) {
            return requireNonNull(assignments.get(canonical(columnName)), columnName);
        }

        Condition condition(String columnName) {
            String canonicalColumn = canonical(columnName);
            return conditions.stream()
                    .filter(condition -> condition.column.equals(canonicalColumn))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Missing condition for " + columnName));
        }

        boolean matches(Map<String, Object> row) {
            return conditions.stream().allMatch(condition -> condition.matches(row));
        }

        Map<String, Object> apply(Map<String, Object> row) {
            Map<String, Object> result = new LinkedHashMap<>(row);
            if (!matches(row)) {
                return result;
            }
            assignments.forEach((column, expression) -> {
                if (expression.isLiteral()) {
                    result.put(column, expression.literalValue());
                }
            });
            return result;
        }
    }

    record Condition(
            String column,
            String operator,
            Object expectedValue,
            boolean binaryComparison) {

        boolean matches(Map<String, Object> row) {
            Object actualValue = row.get(column);
            if (!"=".equals(operator)) {
                throw new IllegalStateException("Unsupported operator " + operator);
            }
            if (actualValue instanceof String actual && expectedValue instanceof String expected) {
                return binaryComparison
                        ? actual.equals(expected)
                        : actual.equalsIgnoreCase(expected);
            }
            return java.util.Objects.equals(actualValue, expectedValue);
        }
    }

    record Expression(List<Token> tokens) {

        Expression {
            tokens = List.copyOf(tokens);
        }

        boolean isLiteral() {
            return tokens.size() == 1
                    && (tokens.get(0).kind == TokenKind.STRING
                    || tokens.get(0).kind == TokenKind.NUMBER);
        }

        Object literalValue() {
            if (!isLiteral()) {
                throw new IllegalStateException("Expression is not a literal: " + tokens);
            }
            Token token = tokens.get(0);
            return token.kind == TokenKind.NUMBER
                    ? Long.parseLong(token.text)
                    : token.text;
        }

        boolean isVariable(String variableName) {
            return tokens.size() == 1
                    && tokens.get(0).isWord(canonical(variableName));
        }
    }

    record AlterTableModel(
            String tableName,
            List<GeneratedColumn> generatedColumns,
            List<IndexAddition> addedIndexes,
            List<String> droppedIndexes) {
    }

    record GeneratedColumn(
            String name,
            String sqlType,
            GeneratedCaseExpression expression,
            boolean stored) {
    }

    record GeneratedCaseExpression(
            String conditionColumn,
            long activeValue,
            String valueColumn) {

        Object evaluate(Map<String, Object> row) {
            Object conditionValue = row.get(conditionColumn);
            if (conditionValue instanceof Number number
                    && number.longValue() == activeValue) {
                return row.get(valueColumn);
            }
            return null;
        }
    }

    record IndexAddition(String name, boolean unique, List<String> columns) {
    }

    private static AlterTableModel parseAlterTable(List<Token> tokens) {
        if (tokens.size() < 3
                || !tokens.get(0).isWord("alter")
                || !tokens.get(1).isWord("table")) {
            throw new IllegalArgumentException("Not an ALTER TABLE statement");
        }
        String tableName = tokens.get(2).text;
        int end = tokens.size();
        if (tokens.get(end - 1).isSymbol(";")) {
            end--;
        }

        List<GeneratedColumn> generatedColumns = new ArrayList<>();
        List<IndexAddition> indexes = new ArrayList<>();
        List<String> droppedIndexes = new ArrayList<>();
        for (List<Token> clause : splitTopLevel(tokens.subList(3, end), ",")) {
            if (clause.isEmpty()) {
                continue;
            }
            if (clause.get(0).isWord("drop")
                    && clause.size() >= 3
                    && clause.get(1).isWord("index")) {
                droppedIndexes.add(clause.get(2).text);
                continue;
            }
            if (!clause.get(0).isWord("add")) {
                continue;
            }

            int cursor = 1;
            if (clause.get(cursor).isWord("column")) {
                cursor++;
            }
            if (clause.get(cursor).isWord("unique")
                    || clause.get(cursor).isWord("key")
                    || clause.get(cursor).isWord("index")) {
                boolean unique = clause.get(cursor).isWord("unique");
                if (unique) {
                    cursor++;
                }
                if (clause.get(cursor).isWord("key")
                        || clause.get(cursor).isWord("index")) {
                    cursor++;
                }
                String indexName = clause.get(cursor++).text;
                while (cursor < clause.size() && !clause.get(cursor).isSymbol("(")) {
                    cursor++;
                }
                int close = matchingParenthesis(clause, cursor);
                List<String> columns = clause.subList(cursor + 1, close).stream()
                        .filter(token -> token.kind == TokenKind.WORD)
                        .map(Token::text)
                        .toList();
                indexes.add(new IndexAddition(indexName, unique, columns));
                continue;
            }

            String columnName = clause.get(cursor++).text;
            String sqlType = clause.get(cursor++).text;
            int asIndex = findWord(clause, cursor, "as");
            if (asIndex < 0 || asIndex + 1 >= clause.size()
                    || !clause.get(asIndex + 1).isSymbol("(")) {
                continue;
            }
            int expressionEnd = matchingParenthesis(clause, asIndex + 1);
            GeneratedCaseExpression expression = parseGeneratedCaseExpression(
                    clause.subList(asIndex + 2, expressionEnd));
            boolean stored = clause.stream().anyMatch(token -> token.isWord("stored"));
            generatedColumns.add(new GeneratedColumn(
                    columnName, sqlType, expression, stored));
        }
        return new AlterTableModel(
                tableName,
                List.copyOf(generatedColumns),
                List.copyOf(indexes),
                List.copyOf(droppedIndexes));
    }

    private static GeneratedCaseExpression parseGeneratedCaseExpression(
            List<Token> expression) {
        List<String> values = expression.stream().map(Token::text).toList();
        if (values.size() != 10
                || !"case".equals(values.get(0))
                || !"when".equals(values.get(1))
                || !"=".equals(values.get(3))
                || !"then".equals(values.get(5))
                || !"else".equals(values.get(7))
                || !"null".equals(values.get(8))
                || !"end".equals(values.get(9))) {
            throw new IllegalArgumentException(
                    "Unsupported generated column expression: " + values);
        }
        return new GeneratedCaseExpression(
                values.get(2),
                Long.parseLong(values.get(4)),
                values.get(6));
    }

    private static Condition parseEqualityCondition(List<Token> condition) {
        int equalsIndex = findSymbol(condition, 0, "=");
        if (equalsIndex <= 0 || equalsIndex + 1 >= condition.size()) {
            throw new IllegalArgumentException("Expected equality condition: " + condition);
        }
        List<Token> left = condition.subList(0, equalsIndex);
        List<Token> right = condition.subList(equalsIndex + 1, condition.size());
        String column = lastWord(left);
        boolean binary = left.stream().anyMatch(token -> token.isWord("binary"))
                || right.stream().anyMatch(token -> token.isWord("binary"));
        Token valueToken = right.stream()
                .filter(token -> token.kind == TokenKind.STRING
                        || token.kind == TokenKind.NUMBER)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Expected literal equality value: " + condition));
        Object value = valueToken.kind == TokenKind.NUMBER
                ? Long.parseLong(valueToken.text)
                : valueToken.text;
        return new Condition(column, "=", value, binary);
    }

    private static String lastWord(List<Token> tokens) {
        for (int index = tokens.size() - 1; index >= 0; index--) {
            Token token = tokens.get(index);
            if (token.kind == TokenKind.WORD && !token.isWord("binary")) {
                return token.text;
            }
        }
        throw new IllegalArgumentException("Expected identifier in " + tokens);
    }

    private static int findWord(List<Token> tokens, int start, String value) {
        for (int index = start; index < tokens.size(); index++) {
            if (tokens.get(index).isWord(value)) {
                return index;
            }
        }
        return -1;
    }

    private static int findSymbol(List<Token> tokens, int start, String value) {
        for (int index = start; index < tokens.size(); index++) {
            if (tokens.get(index).isSymbol(value)) {
                return index;
            }
        }
        return -1;
    }

    private static int matchingParenthesis(List<Token> tokens, int openingIndex) {
        if (openingIndex < 0 || !tokens.get(openingIndex).isSymbol("(")) {
            throw new IllegalArgumentException("Expected opening parenthesis");
        }
        int depth = 0;
        for (int index = openingIndex; index < tokens.size(); index++) {
            if (tokens.get(index).isSymbol("(")) {
                depth++;
            } else if (tokens.get(index).isSymbol(")") && --depth == 0) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unclosed parenthesis");
    }

    private static List<List<Token>> splitTopLevel(
            List<Token> tokens, String separatorSymbol) {
        List<List<Token>> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (token.isSymbol("(")) {
                depth++;
            } else if (token.isSymbol(")")) {
                depth--;
            } else if (depth == 0 && token.isSymbol(separatorSymbol)) {
                parts.add(List.copyOf(tokens.subList(start, index)));
                start = index + 1;
            }
        }
        parts.add(List.copyOf(tokens.subList(start, tokens.size())));
        return parts;
    }

    private static List<List<Token>> splitTopLevelByWord(
            List<Token> tokens, String separatorWord) {
        List<List<Token>> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int index = 0; index < tokens.size(); index++) {
            Token token = tokens.get(index);
            if (token.isSymbol("(")) {
                depth++;
            } else if (token.isSymbol(")")) {
                depth--;
            } else if (depth == 0 && token.isWord(separatorWord)) {
                parts.add(List.copyOf(tokens.subList(start, index)));
                start = index + 1;
            }
        }
        parts.add(List.copyOf(tokens.subList(start, tokens.size())));
        return parts;
    }

    private static String canonical(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private enum TokenKind {
        WORD,
        STRING,
        NUMBER,
        SYMBOL
    }

    record Token(TokenKind kind, String text) {

        boolean isWord(String value) {
            return kind == TokenKind.WORD && text.equals(canonical(value));
        }

        boolean isSymbol(String value) {
            return kind == TokenKind.SYMBOL && text.equals(value);
        }
    }

    private static final class Lexer {
        private final String sql;
        private int index;

        private Lexer(String sql) {
            this.sql = sql;
        }

        private List<Token> tokenize() {
            List<Token> tokens = new ArrayList<>();
            while (index < sql.length()) {
                char current = sql.charAt(index);
                char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
                if (Character.isWhitespace(current)) {
                    index++;
                } else if (current == '-' && next == '-') {
                    skipLineComment();
                } else if (current == '#') {
                    skipLineComment();
                } else if (current == '/' && next == '*') {
                    skipBlockComment();
                } else if (current == '\'') {
                    tokens.add(new Token(TokenKind.STRING, readString()));
                } else if (current == '`') {
                    tokens.add(new Token(TokenKind.WORD, canonical(readBacktickIdentifier())));
                } else if (current == '@') {
                    tokens.add(new Token(TokenKind.WORD, canonical(readVariable())));
                } else if (Character.isLetter(current) || current == '_') {
                    tokens.add(new Token(TokenKind.WORD, canonical(readWord())));
                } else if (Character.isDigit(current)) {
                    tokens.add(new Token(TokenKind.NUMBER, readNumber()));
                } else {
                    tokens.add(new Token(TokenKind.SYMBOL, readSymbol()));
                }
            }
            return Collections.unmodifiableList(tokens);
        }

        private void skipLineComment() {
            while (index < sql.length() && sql.charAt(index) != '\n') {
                index++;
            }
        }

        private void skipBlockComment() {
            index += 2;
            while (index + 1 < sql.length()
                    && !(sql.charAt(index) == '*' && sql.charAt(index + 1) == '/')) {
                index++;
            }
            if (index + 1 >= sql.length()) {
                throw new IllegalArgumentException("Unclosed SQL block comment");
            }
            index += 2;
        }

        private String readString() {
            index++;
            StringBuilder value = new StringBuilder();
            while (index < sql.length()) {
                char current = sql.charAt(index++);
                if (current == '\\' && index < sql.length()) {
                    value.append(sql.charAt(index++));
                } else if (current == '\'' && index < sql.length()
                        && sql.charAt(index) == '\'') {
                    value.append('\'');
                    index++;
                } else if (current == '\'') {
                    return value.toString();
                } else {
                    value.append(current);
                }
            }
            throw new IllegalArgumentException("Unclosed SQL string literal");
        }

        private String readBacktickIdentifier() {
            index++;
            StringBuilder value = new StringBuilder();
            while (index < sql.length()) {
                char current = sql.charAt(index++);
                if (current == '`') {
                    if (index < sql.length() && sql.charAt(index) == '`') {
                        value.append('`');
                        index++;
                    } else {
                        return value.toString();
                    }
                } else {
                    value.append(current);
                }
            }
            throw new IllegalArgumentException("Unclosed backtick identifier");
        }

        private String readVariable() {
            int start = index++;
            while (index < sql.length()) {
                char current = sql.charAt(index);
                if (!Character.isLetterOrDigit(current) && current != '_') {
                    break;
                }
                index++;
            }
            return sql.substring(start, index);
        }

        private String readWord() {
            int start = index++;
            while (index < sql.length()) {
                char current = sql.charAt(index);
                if (!Character.isLetterOrDigit(current)
                        && current != '_'
                        && current != '$') {
                    break;
                }
                index++;
            }
            return sql.substring(start, index);
        }

        private String readNumber() {
            int start = index++;
            while (index < sql.length() && Character.isDigit(sql.charAt(index))) {
                index++;
            }
            return sql.substring(start, index);
        }

        private String readSymbol() {
            char current = sql.charAt(index++);
            if (index < sql.length()) {
                char next = sql.charAt(index);
                if ((current == '<' || current == '>' || current == '!')
                        && next == '=') {
                    index++;
                    return "" + current + next;
                }
                if (current == '<' && next == '>') {
                    index++;
                    return "<>";
                }
            }
            return String.valueOf(current);
        }
    }
}
