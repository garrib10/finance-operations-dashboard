package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import dev.portfolio.finance.support.AuthRequests;

/**
 * Phase 3 of #19 end to end on H2: category selection in transaction and budget writes,
 * atomic rollback (asserted on final database state), the transaction category filter, and
 * category icons in financial and dashboard responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinancialCategoryIntegrationTest {

    private static final String PASSWORD = "River meadow lantern 42!";
    private static final String TODAY = LocalDate.now().toString();

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbc;

    private String owner;
    private long ownerId;
    private String other;

    @BeforeEach
    void users() throws Exception {
        owner = login("owner");
        ownerId = jdbc.queryForObject("SELECT MAX(id) FROM users", Long.class);
        other = login("other");
    }

    // ------------------------------------------------------------------ transactions

    @Test
    void transactionWritesAcceptAnExistingCategoryOrCreateOneAtomically() throws Exception {
        long groceries = categoryId(owner, "Groceries");

        JsonNode existing = json(send(post("/api/transactions"), owner, transactionBody(
                "\"categoryId\": " + groceries, "EXPENSE", "12.50", "Market"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName").value("Groceries"))
                .andExpect(jsonPath("$.categoryIconKey").value("shopping-cart")));

        JsonNode created = json(send(post("/api/transactions"), owner, transactionBody(
                "\"newCategory\": {\"name\": \"  Pet   Care \", \"iconKey\": \"paw-print\", \"builtIn\": true}",
                "EXPENSE", "40.00", "Vet"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName").value("Pet Care"))
                .andExpect(jsonPath("$.categoryIconKey").value("paw-print")));
        long pets = created.get("categoryId").asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ? AND user_id = ? "
                + "AND built_in = FALSE AND budget_enabled = TRUE AND icon_key = 'paw-print' "
                + "AND normalized_name = 'pet care'", Long.class, pets, ownerId)).isEqualTo(1);

        // Update to a brand-new category keeps the transaction ID; the category is reusable.
        long transactionId = existing.get("id").asLong();
        JsonNode updated = json(send(put("/api/transactions/" + transactionId), owner, transactionBody(
                "\"newCategory\": {\"name\": \"Farmers Market\"}", "EXPENSE", "13.00", "Market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.categoryName").value("Farmers Market"))
                .andExpect(jsonPath("$.categoryIconKey").value("tag")));
        send(put("/api/transactions/" + transactionId), owner, transactionBody(
                "\"categoryId\": " + pets, "EXPENSE", "13.00", "Market"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(pets));
        send(post("/api/budgets"), owner, budgetBody("\"categoryId\": " + updated.get("categoryId").asLong(), 9, 2026))
                .andExpect(status().isCreated());
    }

    @Test
    void failedTransactionWritesLeaveNoCategoryBehind() throws Exception {
        long before = categoryCount();
        long transactionsBefore = transactionCount();

        // Invalid financial fields: rejected by validation before any category work.
        send(post("/api/transactions"), owner, transactionBody(
                "\"newCategory\": {\"name\": \"Never Saved\"}", "EXPENSE", "0", "Bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").exists());
        // Too large or too precise for the DECIMAL(12,2) column: a field error, nothing written.
        for (String amount : List.of("99999999999999.00", "10000000000.00", "12.345")) {
            send(post("/api/transactions"), owner, transactionBody(
                    "\"newCategory\": {\"name\": \"Also Never Saved\"}", "EXPENSE", amount, "Too big"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.amount").value(
                            "Amount can have at most 10 whole digits and 2 decimal places"));
        }
        // Duplicate new category (including a built-in name): 409, no transaction.
        for (String name : List.of("groceries", "HOUSING")) {
            send(post("/api/transactions"), owner, transactionBody(
                    "\"newCategory\": {\"name\": \"" + name + "\"}", "EXPENSE", "5.00", "Dup"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CATEGORY_DUPLICATE"));
        }
        // Updating a missing transaction never creates the requested category.
        send(put("/api/transactions/987654321"), owner, transactionBody(
                "\"newCategory\": {\"name\": \"Orphan\"}", "EXPENSE", "5.00", "Missing"))
                .andExpect(status().isNotFound());

        assertThat(categoryCount()).isEqualTo(before);
        assertThat(transactionCount()).isEqualTo(transactionsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE normalized_name IN "
                + "('never saved', 'also never saved', 'orphan')", Long.class)).isZero();
    }

    @Test
    void selectionErrorsUseTheStandardFieldValidationShape() throws Exception {
        long groceries = categoryId(owner, "Groceries");
        for (String path : List.of("/api/transactions", "/api/budgets")) {
            boolean transaction = path.contains("transactions");
            String both = "\"categoryId\": " + groceries + ", \"newCategory\": {\"name\": \"Pets\"}";
            send(post(path), owner, transaction ? transactionBody(both, "EXPENSE", "5.00", "x") : budgetBody(both, 9, 2026))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.newCategory").value(
                            "Choose an existing category or a new category, not both"));
            send(post(path), owner, transaction ? transactionBody("\"type\": \"EXPENSE\"", "EXPENSE", "5.00", "x")
                    : budgetBody("\"month\": 9", 9, 2026))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.categoryId").value("Choose an existing category or create a new one"));
            String badIcon = "\"newCategory\": {\"name\": \"Pets\", \"iconKey\": \"<svg/>\"}";
            send(post(path), owner, transaction ? transactionBody(badIcon, "EXPENSE", "5.00", "x")
                    : budgetBody(badIcon, 9, 2026))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['newCategory.iconKey']").value(
                            "Icon must be one of the approved category icons"));
            String badName = "\"newCategory\": {\"name\": \"Bad\\u0000Name\"}";
            send(post(path), owner, transaction ? transactionBody(badName, "EXPENSE", "5.00", "x")
                    : budgetBody(badName, 9, 2026))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields['newCategory.name']").value(
                            "Category name contains unsupported characters"));
        }
    }

    @Test
    void anotherUsersCategoryCannotBeUsedOrDetected() throws Exception {
        long foreignCustom = json(send(post("/api/categories"), owner,
                "{\"name\": \"Private\", \"budgetEnabled\": true}").andExpect(status().isCreated()))
                .get("id").asLong();
        long foreignBuiltIn = categoryId(owner, "Housing");
        long otherTransactions = transactionCount();

        for (long id : new long[] {foreignCustom, foreignBuiltIn, 987_654_321L}) {
            send(post("/api/transactions"), other, transactionBody("\"categoryId\": " + id, "EXPENSE", "5.00", "x"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Category not found"));
            send(post("/api/budgets"), other, budgetBody("\"categoryId\": " + id, 9, 2026))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
            send(get("/api/transactions?categoryId=" + id), other, null)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        }
        assertThat(transactionCount()).isEqualTo(otherTransactions);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM budgets b JOIN users u ON u.id = b.user_id "
                + "WHERE u.id <> ?", Long.class, ownerId)).isZero();
    }

    // ------------------------------------------------------------------ budgets

    @Test
    void budgetWritesAcceptAnExistingCategoryOrCreateOneAtomically() throws Exception {
        long dining = categoryId(owner, "Dining");
        JsonNode budget = json(send(post("/api/budgets"), owner, budgetBody("\"categoryId\": " + dining, 9, 2026))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryIconKey").value("utensils")));

        JsonNode created = json(send(post("/api/budgets"), owner, budgetBody(
                "\"newCategory\": {\"name\": \"Gym\", \"iconKey\": \"dumbbell\"}", 9, 2026))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName").value("Gym"))
                .andExpect(jsonPath("$.categoryIconKey").value("dumbbell")));
        long gym = created.get("categoryId").asLong();
        // A category created from a budget is reusable by transactions.
        send(post("/api/transactions"), owner, transactionBody("\"categoryId\": " + gym, "EXPENSE", "30.00", "Gym"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryIconKey").value("dumbbell"));

        long budgetId = budget.get("id").asLong();
        send(put("/api/budgets/" + budgetId), owner, budgetBody("\"newCategory\": {\"name\": \"Date Night\"}", 9, 2026))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(budgetId))
                .andExpect(jsonPath("$.categoryName").value("Date Night"))
                .andExpect(jsonPath("$.categoryIconKey").value("tag"));
        send(get("/api/budgets/" + budgetId + "/analytics"), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIconKey").value("tag"))
                .andExpect(jsonPath("$.status").value("ON_TRACK"));
    }

    @Test
    void failedBudgetWritesLeaveNoCategoryBehind() throws Exception {
        long groceries = categoryId(owner, "Groceries");
        send(post("/api/budgets"), owner, budgetBody("\"categoryId\": " + groceries, 9, 2026))
                .andExpect(status().isCreated());
        long categories = categoryCount();
        long budgets = budgetCount();

        send(post("/api/budgets"), owner, budgetBody("\"newCategory\": {\"name\": \"Never Saved\"}", 13, 2026))
                .andExpect(status().isBadRequest());
        send(post("/api/budgets"), owner, budgetBody("\"newCategory\": {\"name\": \"Too Big\"}", 9, 2026)
                .replace("\"monthlyLimit\": 100.00", "\"monthlyLimit\": 99999999999999.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.monthlyLimit").value(
                        "Monthly limit can have at most 10 whole digits and 2 decimal places"));
        send(post("/api/budgets"), owner, budgetBody("\"newCategory\": {\"name\": \"GROCERIES\"}", 10, 2026))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_DUPLICATE"));
        send(post("/api/budgets"), owner, budgetBody("\"categoryId\": " + groceries, 9, 2026))
                .andExpect(status().isConflict());
        send(put("/api/budgets/987654321"), owner, budgetBody("\"newCategory\": {\"name\": \"Orphan\"}", 9, 2026))
                .andExpect(status().isNotFound());

        assertThat(categoryCount()).isEqualTo(categories);
        assertThat(budgetCount()).isEqualTo(budgets);
    }

    // ------------------------------------------------------------------ filtering

    @Test
    void categoryFilterCombinesWithEveryOtherFilterSortAndPage() throws Exception {
        long groceries = categoryId(owner, "Groceries");
        long dining = categoryId(owner, "Dining");
        long pets = json(send(post("/api/transactions"), owner, transactionBody(
                "\"newCategory\": {\"name\": \"Pets\"}", "EXPENSE", "5.00", "Treats", "2026-01-05"))
                .andExpect(status().isCreated())).get("categoryId").asLong();
        String[][] rows = {
                {"" + groceries, "EXPENSE", "10.00", "Market run", "2026-01-10"},
                {"" + groceries, "EXPENSE", "20.00", "Market run", "2026-01-20"},
                {"" + groceries, "EXPENSE", "20.00", "Corner shop", "2026-02-01"},
                {"" + groceries, "INCOME", "15.00", "Refund", "2026-01-15"},
                {"" + dining, "EXPENSE", "20.00", "Market cafe", "2026-01-12"},
                {"" + pets, "EXPENSE", "50.00", "Vet", "2026-01-18"}};
        for (String[] row : rows) {
            send(post("/api/transactions"), owner, transactionBody("\"categoryId\": " + row[0], row[1], row[2], row[3],
                    row[4])).andExpect(status().isCreated());
        }
        // The other user's identical data must never appear.
        send(post("/api/transactions"), other, transactionBody("\"categoryId\": " + categoryId(other, "Groceries"),
                "EXPENSE", "10.00", "Market run", "2026-01-10")).andExpect(status().isCreated());

        assertThat(descriptions("")).hasSize(7);
        assertThat(descriptions("categoryId=" + groceries)).hasSize(4);
        assertThat(descriptions("categoryId=" + pets)).containsExactly("Vet", "Treats");
        assertThat(descriptions("categoryId=" + groceries + "&type=EXPENSE")).hasSize(3);
        assertThat(descriptions("categoryId=" + groceries + "&search=market")).hasSize(2);
        assertThat(descriptions("categoryId=" + groceries + "&startDate=2026-01-11&endDate=2026-01-31"))
                .containsExactly("Market run", "Refund");
        assertThat(descriptions("categoryId=" + groceries + "&minAmount=15&maxAmount=20&type=EXPENSE"))
                .containsExactly("Corner shop", "Market run");
        assertThat(descriptions("categoryId=" + groceries + "&sortBy=amount&sortDirection=asc"))
                .containsExactly("Market run", "Refund", "Market run", "Corner shop");

        JsonNode page = json(send(get("/api/transactions?categoryId=" + groceries + "&size=3&page=1"), owner, null)
                .andExpect(status().isOk()));
        assertThat(page.get("totalElements").asLong()).isEqualTo(4);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("transactions")).hasSize(1);
        assertThat(page.get("transactions").get(0).get("categoryIconKey").asString()).isEqualTo("shopping-cart");

        send(get("/api/transactions?categoryId=0"), owner, null).andExpect(status().isBadRequest());
        send(get("/api/transactions?categoryId=abc"), owner, null).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ dashboard

    @Test
    void dashboardTreatsCustomCategoriesLikeAnyOtherAndKeepsTotalsAcrossRenames() throws Exception {
        String longName = "Ünïcödé Café ☕ " + "x".repeat(84);
        long pets = json(send(post("/api/transactions"), owner, transactionBody(
                "\"newCategory\": {\"name\": \"Pets\", \"iconKey\": \"paw-print\"}", "EXPENSE", "60.00", "Vet", TODAY))
                .andExpect(status().isCreated())).get("categoryId").asLong();
        long unicode = json(send(post("/api/transactions"), owner, transactionBody(
                "\"newCategory\": {\"name\": \"" + longName + "\"}", "EXPENSE", "5.00", "Latte", TODAY))
                .andExpect(status().isCreated())).get("categoryId").asLong();
        long sideGig = json(send(post("/api/transactions"), owner, transactionBody(
                "\"newCategory\": {\"name\": \"Side Gig\", \"iconKey\": \"wallet\"}", "INCOME", "200.00", "Gig", TODAY))
                .andExpect(status().isCreated())).get("categoryId").asLong();
        send(post("/api/budgets"), owner, budgetBody("\"categoryId\": " + pets, LocalDate.now().getMonthValue(),
                LocalDate.now().getYear())).andExpect(status().isCreated());
        // Same name for another user: separate identity and totals.
        send(post("/api/transactions"), other, transactionBody(
                "\"newCategory\": {\"name\": \"Pets\"}", "EXPENSE", "999.00", "Other vet", TODAY))
                .andExpect(status().isCreated());

        String before = dashboardTotals();
        assertDashboard("Pets", "paw-print", pets);

        send(put("/api/categories/" + pets), owner, "{\"name\": \"Pet Care\", \"budgetEnabled\": true, "
                + "\"iconKey\": \"heart-pulse\"}").andExpect(status().isOk());
        // A row whose stored key is outside the catalog is shown as "tag" and left unchanged.
        jdbc.update("UPDATE categories SET icon_key = 'retired-icon' WHERE id = ?", unicode);

        assertThat(dashboardTotals()).isEqualTo(before);
        assertDashboard("Pet Care", "heart-pulse", pets);
        JsonNode dashboard = json(send(get("/api/dashboard"), owner, null).andExpect(status().isOk()));
        assertThat(find(dashboard.get("categorySpending"), unicode).get("categoryName").asString()).isEqualTo(longName);
        assertThat(find(dashboard.get("categorySpending"), unicode).get("categoryIconKey").asString()).isEqualTo("tag");
        assertThat(find(dashboard.get("recentTransactions"), sideGig).get("categoryIconKey").asString())
                .isEqualTo("wallet");
        assertThat(find(dashboard.get("categorySpending"), sideGig)).isNull();
        assertThat(jdbc.queryForObject("SELECT icon_key FROM categories WHERE id = ?", String.class, unicode))
                .isEqualTo("retired-icon");
    }

    private void assertDashboard(String name, String icon, long categoryId) throws Exception {
        JsonNode dashboard = json(send(get("/api/dashboard"), owner, null).andExpect(status().isOk()));
        JsonNode spending = find(dashboard.get("categorySpending"), categoryId);
        assertThat(spending.get("categoryName").asString()).isEqualTo(name);
        assertThat(spending.get("categoryIconKey").asString()).isEqualTo(icon);
        assertThat(spending.get("amountSpent").decimalValue()).isEqualByComparingTo("60.00");
        JsonNode summary = find(dashboard.get("budgetSummaries"), categoryId);
        assertThat(summary.get("categoryName").asString()).isEqualTo(name);
        assertThat(summary.get("categoryIconKey").asString()).isEqualTo(icon);
        assertThat(summary.get("status").asString()).isEqualTo("CAUTION");
        assertThat(summary.get("percentageUsed").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(find(dashboard.get("recentTransactions"), categoryId).get("categoryIconKey").asString())
                .isEqualTo(icon);
    }

    private String dashboardTotals() throws Exception {
        JsonNode dashboard = json(send(get("/api/dashboard"), owner, null).andExpect(status().isOk()));
        return dashboard.get("totalIncome").decimalValue().toPlainString() + "|"
                + dashboard.get("totalExpenses").decimalValue().toPlainString() + "|"
                + dashboard.get("monthlyExpenses").decimalValue().toPlainString() + "|"
                + dashboard.get("currentBalance").decimalValue().toPlainString();
    }

    // ------------------------------------------------------------------ helpers

    private static JsonNode find(JsonNode items, long categoryId) {
        for (JsonNode item : items) {
            if (item.get("categoryId").asLong() == categoryId) {
                return item;
            }
        }
        return null;
    }

    private List<String> descriptions(String query) throws Exception {
        List<String> result = new ArrayList<>();
        for (JsonNode transaction : json(send(get("/api/transactions?size=50&" + query), owner, null)
                .andExpect(status().isOk())).get("transactions")) {
            result.add(transaction.get("description").asString());
        }
        return result;
    }

    private static String transactionBody(String selection, String type, String amount, String description) {
        return transactionBody(selection, type, amount, description, "2026-09-01");
    }

    private static String transactionBody(String selection, String type, String amount, String description,
                                          String date) {
        return "{" + selection + ", \"type\": \"" + type + "\", \"amount\": " + amount + ", \"description\": \""
                + description + "\", \"transactionDate\": \"" + date + "\"}";
    }

    private static String budgetBody(String selection, int month, int year) {
        return "{" + selection + ", \"monthlyLimit\": 100.00, \"month\": " + month + ", \"year\": " + year + "}";
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String bearer, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, bearer);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long categoryId(String bearer, String name) throws Exception {
        for (JsonNode category : json(send(get("/api/categories"), bearer, null))) {
            if (category.get("name").asString().equals(name)) {
                return category.get("id").asLong();
            }
        }
        throw new AssertionError("No category " + name);
    }

    private long categoryCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM categories", Long.class);
    }

    private long transactionCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long.class);
    }

    private long budgetCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM budgets", Long.class);
    }

    private String login(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName": "Fin", "lastName": "Tester", "email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(AuthRequests.protectedAuth(post("/api/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + jsonMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
    }
}
