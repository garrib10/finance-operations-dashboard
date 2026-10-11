package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * V6: category name normalization, built-in metadata, icon keys, and ownership safeguards.
 *
 * <p>Java rather than SQL because the backfill must apply the exact same normalization on
 * MySQL and H2, which SQL functions cannot guarantee. Everything this migration depends on
 * lives in this file: it never calls application code, so later application changes cannot
 * alter it. <strong>Frozen once released</strong>: never edit this class; add V7+ instead.
 *
 * <p>Order: a read-only preflight (schema state, name validity, per-user collisions,
 * financial-record ownership), then DDL and the backfill. Unsafe legacy data fails the
 * preflight before any change, because MySQL DDL is not transactional. The migration never
 * merges, renames, reassigns, or inserts categories, and never rewrites financial records.
 */
public class V6__add_category_normalization_builtin_and_icons extends BaseJavaMigration {

    /** Explicit, stable checksum (Java migrations otherwise have none). Never change it. */
    private static final int CHECKSUM = 1_060_001;

    private static final int MAX_DISPLAY_LENGTH = 100;
    private static final int MAX_REPORTED_IDS = 10;
    private static final String GENERIC_ICON = "tag";

    /** Frozen canonical seeded names (normalized) and their icons at the time of V6. */
    private static final Map<String, String> BUILT_IN_ICONS = builtInIcons();

    private static final Set<String> NEW_CATEGORY_COLUMNS = Set.of("normalized_name", "built_in", "icon_key");
    private static final Set<String> NEW_CONSTRAINTS = Set.of(
            "uk_categories_user_normalized_name",
            "uk_categories_id_user",
            "fk_transactions_category_owner",
            "fk_budgets_category_owner",
            "ck_categories_icon_key_format");
    private static final Map<String, String> NEW_INDEXES = Map.of(
            "idx_transactions_category_user", "transactions",
            "idx_budgets_category_user", "budgets");
    private static final String LEGACY_NAME_CONSTRAINT = "uk_category_user_name";

    @Override
    public Integer getChecksum() {
        return CHECKSUM;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Dialect dialect = Dialect.detect(connection);

        // Preflight: read-only. Any problem stops the migration before the first DDL.
        List<String> problems = new ArrayList<>(inspectSchema(connection, dialect));
        if (!problems.isEmpty()) {
            throw preflightFailure(problems);
        }
        List<Backfill> backfill = planBackfill(connection, problems);
        problems.addAll(ownershipProblems(connection));
        if (!problems.isEmpty()) {
            throw preflightFailure(problems);
        }

        try (Statement statement = connection.createStatement()) {
            for (String sql : dialect.addNullableColumns()) {
                statement.execute(sql);
            }
            applyBackfill(connection, backfill);
            requireCompleteBackfill(statement);
            for (String sql : dialect.requireColumns()) {
                statement.execute(sql);
            }

            // Replace (user_id, name) with authoritative (user_id, normalized_name) uniqueness;
            // the replacement exists before the old key is dropped.
            statement.execute("ALTER TABLE categories ADD CONSTRAINT uk_categories_user_normalized_name "
                    + "UNIQUE (user_id, normalized_name)");
            statement.execute(dialect.dropLegacyNameConstraint());

            // Composite ownership: a transaction or budget can only reference its owner's category.
            // Existing single-column category foreign keys and indexes stay, so deletes remain restricted.
            statement.execute("ALTER TABLE categories ADD CONSTRAINT uk_categories_id_user UNIQUE (id, user_id)");
            statement.execute("CREATE INDEX idx_transactions_category_user ON transactions (category_id, user_id)");
            statement.execute("CREATE INDEX idx_budgets_category_user ON budgets (category_id, user_id)");
            statement.execute("ALTER TABLE transactions ADD CONSTRAINT fk_transactions_category_owner "
                    + "FOREIGN KEY (category_id, user_id) REFERENCES categories (id, user_id)");
            statement.execute("ALTER TABLE budgets ADD CONSTRAINT fk_budgets_category_owner "
                    + "FOREIGN KEY (category_id, user_id) REFERENCES categories (id, user_id)");

            // Icon keys are semantic slugs only; markup, URLs, and paths cannot match.
            statement.execute("ALTER TABLE categories ADD CONSTRAINT ck_categories_icon_key_format "
                    + "CHECK (REGEXP_LIKE(icon_key, '^[a-z0-9]+(-[a-z0-9]+)*$', 'c'))");
        }
    }

    // ---------------------------------------------------------------- preflight: schema

    private static List<String> inspectSchema(Connection connection, Dialect dialect) throws SQLException {
        List<String> problems = new ArrayList<>();
        String schema = dialect.schemaName(connection);

        Set<String> existingColumns = new HashSet<>();
        try (ResultSet rows = connection.getMetaData().getColumns(
                connection.getCatalog(), dialect.metadataSchema(connection), tableName(connection, "categories"), null)) {
            while (rows.next()) {
                existingColumns.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        for (String column : NEW_CATEGORY_COLUMNS) {
            if (existingColumns.contains(column)) {
                problems.add("categories." + column + " already exists (partially applied V6?)");
            }
        }

        Map<String, List<String>> constraints = new LinkedHashMap<>();
        Map<String, String> constraintTypes = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT tc.TABLE_NAME, tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE, kcu.COLUMN_NAME "
                        + "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                        + "LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu "
                        + "ON kcu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA "
                        + "AND kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME "
                        + "AND kcu.TABLE_NAME = tc.TABLE_NAME "
                        + "WHERE LOWER(tc.TABLE_SCHEMA) = LOWER(?) "
                        + "AND LOWER(tc.TABLE_NAME) IN ('categories', 'transactions', 'budgets') "
                        + "ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION")) {
            query.setString(1, schema);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String key = rows.getString(1).toLowerCase(Locale.ROOT) + "."
                            + rows.getString(2).toLowerCase(Locale.ROOT);
                    constraintTypes.put(key, rows.getString(3).toUpperCase(Locale.ROOT));
                    List<String> columns = constraints.computeIfAbsent(key, ignored -> new ArrayList<>());
                    if (rows.getString(4) != null) {
                        columns.add(rows.getString(4).toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        String legacy = "categories." + LEGACY_NAME_CONSTRAINT;
        if (!"UNIQUE".equals(constraintTypes.get(legacy))
                || !List.of("user_id", "name").equals(constraints.get(legacy))) {
            problems.add("expected unique constraint " + LEGACY_NAME_CONSTRAINT
                    + " on categories (user_id, name) was not found");
        }
        for (String key : constraintTypes.keySet()) {
            if (NEW_CONSTRAINTS.contains(key.substring(key.indexOf('.') + 1))) {
                problems.add("constraint " + key + " already exists");
            }
        }

        for (Map.Entry<String, String> index : NEW_INDEXES.entrySet()) {
            try (ResultSet rows = connection.getMetaData().getIndexInfo(connection.getCatalog(),
                    dialect.metadataSchema(connection), tableName(connection, index.getValue()), false, false)) {
                while (rows.next()) {
                    String name = rows.getString("INDEX_NAME");
                    if (name != null && name.equalsIgnoreCase(index.getKey())) {
                        problems.add("index " + index.getKey() + " already exists");
                        break;
                    }
                }
            }
        }
        return problems;
    }

    /** The table's name as the database stores it (MySQL: lowercase; H2: either case). */
    private static String tableName(Connection connection, String lowerCaseName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        return metadata.storesUpperCaseIdentifiers() ? lowerCaseName.toUpperCase(Locale.ROOT) : lowerCaseName;
    }

    // ---------------------------------------------------------------- preflight: data

    private record Backfill(long id, String normalizedName, boolean builtIn, String iconKey) {
    }

    private static List<Backfill> planBackfill(Connection connection, List<String> problems) throws SQLException {
        List<Backfill> plan = new ArrayList<>();
        Map<String, List<Long>> invalidByReason = new LinkedHashMap<>();
        Map<String, List<Long>> idsByUserAndName = new LinkedHashMap<>();
        Map<String, Long> userByKey = new LinkedHashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, user_id, name FROM categories ORDER BY id")) {
            while (rows.next()) {
                long id = rows.getLong(1);
                long userId = rows.getLong(2);
                String normalized;
                try {
                    normalized = FrozenNameNormalizer.comparisonName(rows.getString(3));
                } catch (InvalidName invalid) {
                    invalidByReason.computeIfAbsent(invalid.reason, ignored -> new ArrayList<>()).add(id);
                    continue;
                }
                String key = userId + "\u0000" + normalized;
                idsByUserAndName.computeIfAbsent(key, ignored -> new ArrayList<>()).add(id);
                userByKey.put(key, userId);
                String icon = BUILT_IN_ICONS.get(normalized);
                plan.add(new Backfill(id, normalized, icon != null, icon != null ? icon : GENERIC_ICON));
            }
        }

        invalidByReason.forEach((reason, ids) -> problems.add(
                ids.size() + " categor" + (ids.size() == 1 ? "y has" : "ies have") + " an invalid name ("
                        + reason + "): ids " + firstIds(ids)));
        idsByUserAndName.forEach((key, ids) -> {
            if (ids.size() > 1) {
                problems.add("user " + userByKey.get(key) + " has categories whose names normalize to the"
                        + " same value: ids " + firstIds(ids));
            }
        });
        return plan;
    }

    private static List<String> ownershipProblems(Connection connection) throws SQLException {
        List<String> problems = new ArrayList<>();
        for (String table : List.of("transactions", "budgets")) {
            List<Long> crossOwner = ids(connection, "SELECT f.id FROM " + table + " f "
                    + "JOIN categories c ON c.id = f.category_id WHERE c.user_id <> f.user_id ORDER BY f.id");
            if (!crossOwner.isEmpty()) {
                problems.add(crossOwner.size() + " " + table + " reference a category owned by another user: ids "
                        + firstIds(crossOwner));
            }
            List<Long> missing = ids(connection, "SELECT f.id FROM " + table + " f "
                    + "LEFT JOIN categories c ON c.id = f.category_id WHERE c.id IS NULL ORDER BY f.id");
            if (!missing.isEmpty()) {
                problems.add(missing.size() + " " + table + " reference a missing category: ids " + firstIds(missing));
            }
        }
        return problems;
    }

    private static List<Long> ids(Connection connection, String sql) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                ids.add(rows.getLong(1));
            }
        }
        return ids;
    }

    private static String firstIds(List<Long> ids) {
        List<Long> shown = ids.subList(0, Math.min(ids.size(), MAX_REPORTED_IDS));
        return shown + (ids.size() > shown.size() ? " (+" + (ids.size() - shown.size()) + " more)" : "");
    }

    /** Messages contain only row IDs and fixed reasons: never names, amounts, or credentials. */
    private static IllegalStateException preflightFailure(List<String> problems) {
        return new IllegalStateException("V6 preflight failed; no schema or data changes were made. "
                + "Resolve these rows manually (V6 never merges, renames, or reassigns data), then rerun: "
                + String.join("; ", problems));
    }

    // ---------------------------------------------------------------- backfill

    private static void applyBackfill(Connection connection, List<Backfill> plan) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE categories SET normalized_name = ?, built_in = ?, icon_key = ? WHERE id = ?")) {
            for (Backfill row : plan) {
                update.setString(1, row.normalizedName());
                update.setBoolean(2, row.builtIn());
                update.setString(3, row.iconKey());
                update.setLong(4, row.id());
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private static void requireCompleteBackfill(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM categories "
                + "WHERE normalized_name IS NULL OR built_in IS NULL OR icon_key IS NULL")) {
            rows.next();
            if (rows.getLong(1) != 0) {
                throw new IllegalStateException("V6 failed: " + rows.getLong(1)
                        + " categories were created while V6 ran and were not backfilled; stop the application"
                        + " and rerun the migration");
            }
        }
    }

    // ---------------------------------------------------------------- dialects

    private enum Dialect {
        MYSQL {
            @Override
            String schemaName(Connection connection) throws SQLException {
                return connection.getCatalog();
            }

            @Override
            String metadataSchema(Connection connection) {
                return null;
            }

            /** utf8mb4_0900_bin: exact, case- and accent-sensitive, NO PAD comparison. */
            @Override
            List<String> addNullableColumns() {
                return List.of("ALTER TABLE categories "
                        + "ADD COLUMN normalized_name VARCHAR(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL, "
                        + "ADD COLUMN built_in BIT(1) NULL, "
                        + "ADD COLUMN icon_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL");
            }

            @Override
            List<String> requireColumns() {
                return List.of("ALTER TABLE categories "
                        + "MODIFY COLUMN normalized_name VARCHAR(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL, "
                        + "MODIFY COLUMN built_in BIT(1) NOT NULL, "
                        + "MODIFY COLUMN icon_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL");
            }

            @Override
            String dropLegacyNameConstraint() {
                return "ALTER TABLE categories DROP INDEX " + LEGACY_NAME_CONSTRAINT;
            }
        },
        /** H2 compares VARCHAR exactly (case- and accent-sensitive) by default. */
        H2 {
            @Override
            String schemaName(Connection connection) throws SQLException {
                return connection.getSchema();
            }

            @Override
            String metadataSchema(Connection connection) throws SQLException {
                return connection.getSchema();
            }

            @Override
            List<String> addNullableColumns() {
                return List.of(
                        "ALTER TABLE categories ADD COLUMN normalized_name VARCHAR(300) NULL",
                        "ALTER TABLE categories ADD COLUMN built_in BIT(1) NULL",
                        "ALTER TABLE categories ADD COLUMN icon_key VARCHAR(64) NULL");
            }

            @Override
            List<String> requireColumns() {
                return List.of(
                        "ALTER TABLE categories ALTER COLUMN normalized_name SET NOT NULL",
                        "ALTER TABLE categories ALTER COLUMN built_in SET NOT NULL",
                        "ALTER TABLE categories ALTER COLUMN icon_key SET NOT NULL");
            }

            @Override
            String dropLegacyNameConstraint() {
                return "ALTER TABLE categories DROP CONSTRAINT " + LEGACY_NAME_CONSTRAINT;
            }
        };

        abstract String schemaName(Connection connection) throws SQLException;

        abstract String metadataSchema(Connection connection) throws SQLException;

        abstract List<String> addNullableColumns();

        abstract List<String> requireColumns();

        abstract String dropLegacyNameConstraint();

        static Dialect detect(Connection connection) throws SQLException {
            String product = connection.getMetaData().getDatabaseProductName();
            if ("MySQL".equalsIgnoreCase(product)) {
                return MYSQL;
            }
            if ("H2".equalsIgnoreCase(product)) {
                return H2;
            }
            throw new IllegalStateException("V6 supports MySQL and H2 only, not " + product);
        }
    }

    // ---------------------------------------------------------------- frozen normalization

    private static Map<String, String> builtInIcons() {
        Map<String, String> icons = new LinkedHashMap<>();
        icons.put("housing", "house");
        icons.put("groceries", "shopping-cart");
        icons.put("dining", "utensils");
        icons.put("transportation", "car");
        icons.put("utilities", "lightbulb");
        icons.put("insurance", "shield");
        icons.put("healthcare", "heart-pulse");
        icons.put("entertainment", "clapperboard");
        icons.put("shopping", "shopping-bag");
        icons.put("travel", "plane");
        icons.put("income", "circle-dollar-sign");
        icons.put("savings", "piggy-bank");
        icons.put("other", "tag");
        return Collections.unmodifiableMap(icons);
    }

    private static final class InvalidName extends Exception {
        private final String reason;

        InvalidName(String reason) {
            super(reason, null, false, false);
            this.reason = reason;
        }
    }

    /**
     * The V6 normalization policy, frozen. It intentionally duplicates the application's
     * CategoryNameNormalizer as of V6 and must never be changed or replaced by a call to it.
     */
    private static final class FrozenNameNormalizer {

        private FrozenNameNormalizer() {
        }

        static String comparisonName(String raw) throws InvalidName {
            if (raw == null) {
                throw new InvalidName("missing");
            }
            for (int i = 0; i < raw.length(); i++) {
                char current = raw.charAt(i);
                if (Character.isHighSurrogate(current) && i + 1 < raw.length()
                        && Character.isLowSurrogate(raw.charAt(i + 1))) {
                    i++;
                } else if (Character.isSurrogate(current)) {
                    throw new InvalidName("malformed");
                }
            }

            String composed = Normalizer.normalize(raw, Normalizer.Form.NFC);
            StringBuilder display = new StringBuilder(composed.length());
            boolean pendingSpace = false;
            for (int i = 0; i < composed.length(); ) {
                int codePoint = composed.codePointAt(i);
                i += Character.charCount(codePoint);
                if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                    pendingSpace = !display.isEmpty();
                    continue;
                }
                if (Character.isISOControl(codePoint)) {
                    throw new InvalidName("control character");
                }
                if (pendingSpace) {
                    display.append(' ');
                    pendingSpace = false;
                }
                display.appendCodePoint(codePoint);
            }
            if (display.isEmpty()) {
                throw new InvalidName("blank");
            }
            if (display.length() > MAX_DISPLAY_LENGTH) {
                throw new InvalidName("too long");
            }
            return Normalizer.normalize(display.toString().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        }
    }
}
