package com.codecoachai.system.migration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.codecoachai.common.core.domain.BaseEntity;
import com.codecoachai.system.domain.entity.SysRoleMenu;
import com.codecoachai.system.migration.MigrationSqlModel.AlterTableModel;
import com.codecoachai.system.migration.MigrationSqlModel.GeneratedColumn;
import com.codecoachai.system.migration.MigrationSqlModel.IndexAddition;
import com.codecoachai.system.migration.MigrationSqlModel.Script;
import com.codecoachai.system.migration.MigrationSqlModel.UpdateModel;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.crypto.bcrypt.BCrypt;

class AdminBootstrapAndRoleMenuMigrationContractTest {

    private static final Path SQL_DIR = Path.of("..", "sql");
    private static final Path MIGRATION_DIR = SQL_DIR.resolve("migration");
    private static final Path COMMON_NACOS_CONFIG =
            Path.of("..", "docs", "nacos", "codecoachai-common-dev.yml");
    private static final String LEGACY_PASSWORD_FINGERPRINT =
            "$2a$10$OuTN8naVk6kfkcyMNiSf.eO3rCVpGr2j7RL.iQvHkM6H/AJoFVtHG";
    private static final String ROLE_MENU_MIGRATION =
            "V4_097__role_menu_active_uniqueness.sql";
    private static final Pattern BCRYPT_HASH =
            Pattern.compile("^\\$2[aby]\\$(\\d{2})\\$[./A-Za-z0-9]{53}$");

    @Test
    void baselineHasNoLoginAccountAndBootstrapConsumesOnlyExplicitInputs()
            throws Exception {
        Script baseline = MigrationSqlModel.parse(
                Files.readString(SQL_DIR.resolve("init.sql")));
        assertFalse(baseline.insertTargets().contains("sys_user"));
        assertFalse(baseline.insertTargets().contains("sys_user_role"));
        assertTrue(baseline.stringLiterals().stream()
                .noneMatch(BCRYPT_HASH.asMatchPredicate()));

        Script bootstrap = MigrationSqlModel.parse(Files.readString(
                SQL_DIR.resolve("bootstrap").resolve("bootstrap_admin.sql")));
        var userInsert = bootstrap.singleInsertInto("sys_user");
        assertTrue(userInsert.value("username")
                .isVariable("@bootstrap_admin_username"));
        assertTrue(userInsert.value("password")
                .isVariable("@bootstrap_admin_password_hash"));
        assertTrue(userInsert.value("nickname")
                .isVariable("@bootstrap_admin_nickname"));
        assertTrue(userInsert.value("email")
                .isVariable("@bootstrap_admin_email"));
        bootstrap.singleInsertInto("sys_user_role");

        assertTrue(bootstrap.stringLiterals().stream()
                .noneMatch(BCRYPT_HASH.asMatchPredicate()),
                "bootstrap must not embed a reusable password hash");
        assertTrue(bootstrap.stringLiterals().stream()
                .noneMatch(value -> value.equalsIgnoreCase("admin123")));
        assertTrue(bootstrap.containsSequence(
                "char_length", "(", "@bootstrap_admin_password_hash", ")", "<>", "60"));
        assertTrue(bootstrap.containsSequence(
                "bcrypt_cost", "<", "12", "or", "bcrypt_cost", ">", "31"));
        assertTrue(bootstrap.containsSequence(
                "active_admin_count", ">", "0", "then"));
        assertTrue(bootstrap.containsSequence(
                "username_count", ">", "0", "then"));
        assertTrue(bootstrap.containsSequence(
                "select", "get_lock", "("));
        assertTrue(bootstrap.containsSequence(
                "do", "release_lock", "("));
        assertTrue(bootstrap.containsSequence(
                "declare", "exit", "handler", "for", "sqlexception"));
        assertTrue(bootstrap.containsSequence("start", "transaction"));
        assertTrue(bootstrap.containsSequence("rollback"));
        assertTrue(bootstrap.containsSequence("commit"));
    }

    @Test
    void V4096ChangesOnlyTheByteExactLegacyCredential() throws Exception {
        Script script = MigrationSqlModel.parse(Files.readString(MIGRATION_DIR.resolve(
                "V4_096__disable_legacy_default_admin.sql")));
        UpdateModel remediation = script.singleUpdate("sys_user");

        String replacementHash =
                (String) remediation.assignment("password").literalValue();
        var bcryptMatcher = BCRYPT_HASH.matcher(replacementHash);
        assertTrue(bcryptMatcher.matches(), "replacement must remain a valid BCrypt hash");
        assertTrue(Integer.parseInt(bcryptMatcher.group(1)) >= 12);
        assertNotEquals(LEGACY_PASSWORD_FINGERPRINT, replacementHash);
        assertDoesNotThrow(() -> BCrypt.checkpw(
                "a-value-that-was-not-used-to-create-the-sentinel", replacementHash));
        assertEquals(0L, remediation.assignment("status").literalValue());

        assertEquals("admin", remediation.condition("username").expectedValue());
        assertFalse(remediation.condition("username").binaryComparison());
        assertEquals(
                LEGACY_PASSWORD_FINGERPRINT,
                remediation.condition("password").expectedValue());
        assertTrue(remediation.condition("password").binaryComparison(),
                "password comparison must bypass the column's case-insensitive collation");
        assertEquals(0L, remediation.condition("deleted").expectedValue());

        Map<String, Object> legacy = adminRow(
                "admin", LEGACY_PASSWORD_FINGERPRINT, 1L, 0L);
        Map<String, Object> remediated = remediation.apply(legacy);
        assertEquals(replacementHash, remediated.get("password"));
        assertEquals(0L, remediated.get("status"));

        String rotatedHash =
                "$2a$12$Q4wV5O6qWlSfQnW9IYx.fO7B3p3bbx9HxkTHmMUdDr5.GvJVSvNwK";
        assertEquals(
                adminRow("admin", rotatedHash, 1L, 0L),
                remediation.apply(adminRow("admin", rotatedHash, 1L, 0L)));

        String caseChangedLegacyHash =
                LEGACY_PASSWORD_FINGERPRINT.replaceFirst("O", "o");
        assertNotEquals(LEGACY_PASSWORD_FINGERPRINT, caseChangedLegacyHash);
        assertTrue(LEGACY_PASSWORD_FINGERPRINT.equalsIgnoreCase(caseChangedLegacyHash));
        assertEquals(
                adminRow("admin", caseChangedLegacyHash, 1L, 0L),
                remediation.apply(adminRow("admin", caseChangedLegacyHash, 1L, 0L)),
                "case-insensitive SQL equality would incorrectly disable this rotated hash");

        assertEquals(
                adminRow("operator", LEGACY_PASSWORD_FINGERPRINT, 1L, 0L),
                remediation.apply(adminRow(
                        "operator", LEGACY_PASSWORD_FINGERPRINT, 1L, 0L)));
        assertEquals(
                adminRow("admin", LEGACY_PASSWORD_FINGERPRINT, 1L, 1L),
                remediation.apply(adminRow(
                        "admin", LEGACY_PASSWORD_FINGERPRINT, 1L, 1L)));
    }

    @Test
    void V4097DerivesAnActiveOnlyUniqueKeyCompatibleWithMyBatisSoftDelete()
            throws Exception {
        List<Path> versionMigrations;
        try (var paths = Files.list(MIGRATION_DIR)) {
            versionMigrations = paths
                    .filter(path -> path.getFileName().toString().startsWith("V4_097__"))
                    .toList();
        }
        assertEquals(1, versionMigrations.size(), "V4_097 must have one owner");
        assertEquals(ROLE_MENU_MIGRATION, versionMigrations.get(0).getFileName().toString());

        Script script = MigrationSqlModel.parse(
                Files.readString(MIGRATION_DIR.resolve(ROLE_MENU_MIGRATION)));
        assertTrue(script.containsSequence(
                "from", "sys_role_menu",
                "where", "deleted", "=", "0",
                "group", "by", "role_id", ",", "menu_id",
                "having", "count", "(", "1", ")", ">", "1"));
        assertTrue(script.containsSequence(
                "select", "max", "(", "non_unique", ")",
                "from", "information_schema", ".", "statistics"));
        assertTrue(script.containsSequence(
                "coalesce", "(", "@index_non_unique", ",", "1", ")", "<>", "0"));

        List<AlterTableModel> alters = script.dynamicAlterTables();
        assertEquals(3, alters.size(), "migration must cover add and partial-retry paths");
        assertTrue(alters.stream().allMatch(alter -> alter.tableName().equals("sys_role_menu")));

        List<GeneratedColumn> generatedColumns = alters.stream()
                .flatMap(alter -> alter.generatedColumns().stream())
                .toList();
        assertEquals(1, generatedColumns.size());
        GeneratedColumn activeMenu = generatedColumns.get(0);
        assertEquals("active_menu_id", activeMenu.name());
        assertEquals("bigint", activeMenu.sqlType());
        assertTrue(activeMenu.stored());
        assertEquals("deleted", activeMenu.expression().conditionColumn());
        assertEquals(0L, activeMenu.expression().activeValue());
        assertEquals("menu_id", activeMenu.expression().valueColumn());

        List<IndexAddition> indexes = alters.stream()
                .flatMap(alter -> alter.addedIndexes().stream())
                .toList();
        assertEquals(2, indexes.size(), "both normal and partial-retry paths add the key");
        for (IndexAddition index : indexes) {
            assertEquals("uk_role_menu", index.name());
            assertTrue(index.unique());
            assertEquals(List.of("role_id", "active_menu_id"), index.columns());
        }
        assertEquals(1, alters.stream()
                .filter(alter -> alter.droppedIndexes().contains("uk_role_menu"))
                .count());

        Field deletedField = BaseEntity.class.getDeclaredField("deleted");
        assertNotNull(deletedField.getAnnotation(TableLogic.class));
        assertTrue(BaseEntity.class.isAssignableFrom(SysRoleMenu.class));

        long logicDeleteValue = myBatisLogicValue("logic-delete-value");
        long logicNotDeleteValue = myBatisLogicValue("logic-not-delete-value");
        assertEquals(1L, logicDeleteValue);
        assertEquals(0L, logicNotDeleteValue);
        assertEquals(41L, activeMenu.expression().evaluate(roleMenuValues(
                7L, 41L, logicNotDeleteValue, activeMenu)));
        assertNull(activeMenu.expression().evaluate(roleMenuValues(
                7L, 41L, logicDeleteValue, activeMenu)));

        RoleMenuUniqueIndexSimulator simulator = new RoleMenuUniqueIndexSimulator(
                activeMenu, indexes.get(0), logicNotDeleteValue, logicDeleteValue);
        assertTrue(simulator.insertActive(7L, 41L));
        assertFalse(simulator.insertActive(7L, 41L),
                "the unique key must reject a second active relation");

        simulator.softDeleteActive(7L);
        assertEquals(1, simulator.tombstoneCount(7L, 41L));
        assertTrue(simulator.insertActive(7L, 41L));

        simulator.softDeleteActive(7L);
        assertEquals(2, simulator.tombstoneCount(7L, 41L),
                "multiple NULL active keys must preserve historical tombstones");
        assertTrue(simulator.insertActive(7L, 41L),
                "a third active relation must be creatable after two soft deletes");
    }

    private static Map<String, Object> adminRow(
            String username, String password, long status, long deleted) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("username", username);
        row.put("password", password);
        row.put("status", status);
        row.put("deleted", deleted);
        return row;
    }

    private static long myBatisLogicValue(String propertyName) throws Exception {
        var resource = new FileSystemResource(COMMON_NACOS_CONFIG);
        List<PropertySource<?>> propertySources =
                new YamlPropertySourceLoader().load("codecoachai-common", resource);
        String propertyPath =
                "mybatis-plus.global-config.db-config." + propertyName;
        Object value = propertySources.stream()
                .map(source -> source.getProperty(propertyPath))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing MyBatis property " + propertyPath));
        return Long.parseLong(String.valueOf(value));
    }

    private static Map<String, Object> roleMenuValues(
            long roleId,
            long menuId,
            long deleted,
            GeneratedColumn generatedColumn) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("role_id", roleId);
        values.put("menu_id", menuId);
        values.put("deleted", deleted);
        values.put(
                generatedColumn.name(),
                generatedColumn.expression().evaluate(values));
        return values;
    }

    private static final class RoleMenuUniqueIndexSimulator {
        private final GeneratedColumn generatedColumn;
        private final IndexAddition uniqueIndex;
        private final long activeValue;
        private final long deletedValue;
        private final List<RoleMenuRow> rows = new ArrayList<>();

        private RoleMenuUniqueIndexSimulator(
                GeneratedColumn generatedColumn,
                IndexAddition uniqueIndex,
                long activeValue,
                long deletedValue) {
            this.generatedColumn = generatedColumn;
            this.uniqueIndex = uniqueIndex;
            this.activeValue = activeValue;
            this.deletedValue = deletedValue;
        }

        private boolean insertActive(long roleId, long menuId) {
            RoleMenuRow candidate = new RoleMenuRow(roleId, menuId, activeValue);
            List<Object> candidateKey = uniqueKey(candidate);
            boolean collision = rows.stream()
                    .map(this::uniqueKey)
                    .filter(key -> key != null)
                    .anyMatch(candidateKey::equals);
            if (collision) {
                return false;
            }
            rows.add(candidate);
            return true;
        }

        private void softDeleteActive(long roleId) {
            rows.stream()
                    .filter(row -> row.roleId == roleId && row.deleted == activeValue)
                    .forEach(row -> row.deleted = deletedValue);
            assertNoUniqueCollision();
        }

        private long tombstoneCount(long roleId, long menuId) {
            return rows.stream()
                    .filter(row -> row.roleId == roleId
                            && row.menuId == menuId
                            && row.deleted == deletedValue)
                    .count();
        }

        private void assertNoUniqueCollision() {
            List<List<Object>> keys = rows.stream()
                    .map(this::uniqueKey)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            assertEquals(keys.size(), keys.stream().distinct().count());
        }

        private List<Object> uniqueKey(RoleMenuRow row) {
            Map<String, Object> values = roleMenuValues(
                    row.roleId, row.menuId, row.deleted, generatedColumn);
            List<Object> key = uniqueIndex.columns().stream()
                    .map(values::get)
                    .toList();
            return key.stream().anyMatch(java.util.Objects::isNull) ? null : key;
        }
    }

    private static final class RoleMenuRow {
        private final long roleId;
        private final long menuId;
        private long deleted;

        private RoleMenuRow(long roleId, long menuId, long deleted) {
            this.roleId = roleId;
            this.menuId = menuId;
            this.deleted = deleted;
        }
    }
}
