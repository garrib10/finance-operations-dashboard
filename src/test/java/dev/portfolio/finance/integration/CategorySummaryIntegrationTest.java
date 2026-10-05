package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import dev.portfolio.finance.support.AuthRequests;

/** GET /api/categories/summary end to end: contract, authentication, ownership, and dashboard parity. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategorySummaryIntegrationTest {

    private static final String PASSWORD = "River meadow lantern 42!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    private String owner;
    private String other;
    private LocalDate today;

    @BeforeEach
    void users() throws Exception {
        owner = login("summary-owner");
        other = login("summary-other");
        today = LocalDate.now();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/categories/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void describesEveryOwnedCategoryWithTheServerMonth() throws Exception {
        long pets = createCategory(owner, "Pet Care", "paw-print");
        createCategory(owner, "Unused Hobby", "tag");
        transaction(owner, pets, "EXPENSE", "12.50", today);
        transaction(owner, pets, "INCOME", "3.00", today);
        // Just outside the server's month on both sides: all time, but not this month.
        transaction(owner, pets, "EXPENSE", "7.00", today.withDayOfMonth(1).minusDays(1));
        transaction(owner, pets, "EXPENSE", "8.00", today.withDayOfMonth(1).plusMonths(1));

        JsonNode summary = summary(owner);

        assertThat(summary.get("month").asInt()).isEqualTo(today.getMonthValue());
        assertThat(summary.get("year").asInt()).isEqualTo(today.getYear());

        JsonNode petRow = row(summary, "Pet Care");
        assertThat(petRow.get("iconKey").asString()).isEqualTo("paw-print");
        assertThat(petRow.get("builtIn").asBoolean()).isFalse();
        assertThat(petRow.get("budgetEnabled").asBoolean()).isTrue();
        assertThat(petRow.get("transactionCount").asLong()).isEqualTo(4);
        assertThat(petRow.get("currentMonthTransactionCount").isIntegralNumber()).isTrue();
        assertThat(petRow.get("currentMonthTransactionCount").asLong()).isEqualTo(2);
        assertThat(petRow.get("budgetCount").asLong()).isZero();
        assertThat(petRow.get("lastTransactionDate").asString())
                .isEqualTo(today.withDayOfMonth(1).plusMonths(1).toString());
        assertThat(petRow.get("currentMonthSpent").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(petRow.get("allTimeSpent").decimalValue()).isEqualByComparingTo("27.50");
        assertThat(petRow.get("currentMonthBudget").isNull()).isTrue();
        assertThat(petRow.get("canDelete").asBoolean()).isFalse();

        JsonNode unused = row(summary, "Unused Hobby");
        assertThat(unused.get("currentMonthTransactionCount").asLong()).isZero();
        assertThat(unused.get("lastTransactionDate").isNull()).isTrue();
        assertThat(unused.get("canDelete").asBoolean()).isTrue();

        // Built-in categories from registration are listed but never deletable.
        JsonNode groceries = row(summary, "Groceries");
        assertThat(groceries.get("builtIn").asBoolean()).isTrue();
        assertThat(groceries.get("canDelete").asBoolean()).isFalse();
    }

    @Test
    void ordersRowsByNameThenId() throws Exception {
        String previous = null;
        for (JsonNode row : summary(owner).get("categories")) {
            String name = row.get("name").asString();
            if (previous != null) {
                assertThat(previous.compareToIgnoreCase(name)).isLessThanOrEqualTo(0);
            }
            previous = name;
        }
    }

    @Test
    void neverIncludesAnotherUsersCategoriesOrTotals() throws Exception {
        long ownerPets = createCategory(owner, "Pet Care", "paw-print");
        long otherPets = createCategory(other, "Pet Care", "paw-print");
        transaction(owner, ownerPets, "EXPENSE", "10.00", today);
        transaction(other, otherPets, "EXPENSE", "900.00", today);
        budget(other, otherPets, "1000.00", today);

        JsonNode ownerSummary = summary(owner);
        Set<Long> ownerIds = ids(ownerSummary);

        assertThat(ownerIds).doesNotContain(otherPets);
        assertThat(ids(summary(other))).doesNotContainAnyElementsOf(ownerIds);
        JsonNode petRow = row(ownerSummary, "Pet Care");
        assertThat(petRow.get("id").asLong()).isEqualTo(ownerPets);
        assertThat(petRow.get("allTimeSpent").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(petRow.get("currentMonthTransactionCount").asLong()).isEqualTo(1); // Not the other user's.
        assertThat(petRow.get("budgetCount").asLong()).isZero();
    }

    @Test
    void matchesTheDashboardSpendingAndBudgetMetrics() throws Exception {
        long pets = createCategory(owner, "Pet Care", "paw-print");
        long travel = createCategory(owner, "Vacations", "plane");
        long groceries = categoryId(owner, "Groceries");
        transaction(owner, pets, "EXPENSE", "45.00", today);
        transaction(owner, pets, "EXPENSE", "15.50", today);
        transaction(owner, travel, "EXPENSE", "250.00", today);
        transaction(owner, groceries, "EXPENSE", "30.00", today);
        transaction(owner, groceries, "INCOME", "100.00", today);
        budget(owner, pets, "80.00", today);      // 75.63% -> WARNING
        budget(owner, travel, "200.00", today);   // 125%   -> OVER_BUDGET
        budget(owner, groceries, "500.00", today);// 6%     -> ON_TRACK

        JsonNode summary = summary(owner);
        JsonNode dashboard = json(send(get("/api/dashboard"), owner, null).andExpect(status().isOk()));

        Map<Long, BigDecimal> dashboardSpending = new HashMap<>();
        for (JsonNode spending : dashboard.get("categorySpending")) {
            dashboardSpending.put(spending.get("categoryId").asLong(), spending.get("amountSpent").decimalValue());
        }
        for (JsonNode row : summary.get("categories")) {
            BigDecimal expected = dashboardSpending.getOrDefault(row.get("id").asLong(), BigDecimal.ZERO);
            assertThat(row.get("currentMonthSpent").decimalValue())
                    .as("current-month spending for %s", row.get("name").asString())
                    .isEqualByComparingTo(expected);
        }

        assertThat(dashboard.get("budgetSummaries")).hasSize(3);
        for (JsonNode dashboardBudget : dashboard.get("budgetSummaries")) {
            JsonNode budget = byId(summary, dashboardBudget.get("categoryId").asLong()).get("currentMonthBudget");
            assertThat(budget.get("budgetId").asLong()).isEqualTo(dashboardBudget.get("budgetId").asLong());
            for (String field : new String[] {"monthlyLimit", "amountSpent", "amountRemaining", "percentageUsed"}) {
                assertThat(budget.get(field).decimalValue()).as(field)
                        .isEqualByComparingTo(dashboardBudget.get(field).decimalValue());
            }
            assertThat(budget.get("status").asString()).isEqualTo(dashboardBudget.get("status").asString());
        }
        assertThat(byId(summary, pets).get("currentMonthBudget").get("status").asString()).isEqualTo("WARNING");
        assertThat(byId(summary, travel).get("currentMonthBudget").get("status").asString()).isEqualTo("OVER_BUDGET");
        assertThat(byId(summary, groceries).get("currentMonthBudget").get("status").asString()).isEqualTo("ON_TRACK");
    }

    // ------------------------------------------------------------------ helpers

    @Test
    void reportsTheSameSelectedAndServerMonthByDefault() throws Exception {
        JsonNode summary = summary(owner);

        assertThat(summary.get("month").asInt()).isEqualTo(today.getMonthValue());
        assertThat(summary.get("year").asInt()).isEqualTo(today.getYear());
        assertThat(summary.get("serverCurrentMonth").asInt()).isEqualTo(today.getMonthValue());
        assertThat(summary.get("serverCurrentYear").asInt()).isEqualTo(today.getYear());
    }

    @Test
    void describesAnEarlierMonthWhileKeepingAllTimeValues() throws Exception {
        LocalDate selected = today.withDayOfMonth(1).minusMonths(1);       // first day of last month
        LocalDate selectedEnd = selected.withDayOfMonth(selected.lengthOfMonth());
        long pets = createCategory(owner, "Pet Care", "paw-print");
        long quiet = createCategory(owner, "Quiet Month", "tag");
        transaction(owner, pets, "EXPENSE", "20.00", selected);                // first day: in
        transaction(owner, pets, "INCOME", "5.00", selectedEnd);               // last day: counted, not spending
        transaction(owner, pets, "EXPENSE", "70.00", selected.minusDays(1));   // the month before: out
        transaction(owner, pets, "EXPENSE", "9.00", today);                    // this month: out
        budget(owner, pets, "40.00", selected);
        budget(owner, pets, "500.00", today);
        transaction(owner, quiet, "EXPENSE", "3.00", today);

        JsonNode summary = summary(owner, selected);

        assertThat(summary.get("month").asInt()).isEqualTo(selected.getMonthValue());
        assertThat(summary.get("year").asInt()).isEqualTo(selected.getYear());
        assertThat(summary.get("serverCurrentMonth").asInt()).isEqualTo(today.getMonthValue());
        assertThat(summary.get("serverCurrentYear").asInt()).isEqualTo(today.getYear());

        JsonNode petRow = byId(summary, pets);
        assertThat(petRow.get("currentMonthTransactionCount").asLong()).isEqualTo(2);
        assertThat(petRow.get("currentMonthSpent").decimalValue()).isEqualByComparingTo("20.00");
        JsonNode budget = petRow.get("currentMonthBudget");
        assertThat(budget.get("monthlyLimit").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(budget.get("amountRemaining").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(budget.get("percentageUsed").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(budget.get("status").asString()).isEqualTo("CAUTION");
        // All-time values are the same whichever month is shown.
        assertThat(petRow.get("transactionCount").asLong()).isEqualTo(4);
        assertThat(petRow.get("budgetCount").asLong()).isEqualTo(2);
        assertThat(petRow.get("allTimeSpent").decimalValue()).isEqualByComparingTo("99.00");
        assertThat(petRow.get("lastTransactionDate").asString()).isEqualTo(today.toString());
        assertThat(petRow.get("canDelete").asBoolean()).isFalse();

        // No activity that month: still listed, with zeros.
        JsonNode quietRow = byId(summary, quiet);
        assertThat(quietRow.get("currentMonthTransactionCount").asLong()).isZero();
        assertThat(quietRow.get("currentMonthSpent").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(quietRow.get("currentMonthBudget").isNull()).isTrue();
    }

    @Test
    void keepsEachUsersEarlierMonthSeparate() throws Exception {
        LocalDate selected = today.withDayOfMonth(1).minusMonths(1);
        long ownerPets = createCategory(owner, "Pet Care", "paw-print");
        long otherPets = createCategory(other, "Pet Care", "paw-print");
        transaction(owner, ownerPets, "EXPENSE", "10.00", selected);
        transaction(other, otherPets, "EXPENSE", "800.00", selected);
        transaction(other, otherPets, "INCOME", "50.00", selected);
        budget(other, otherPets, "900.00", selected);

        JsonNode ownerSummary = summary(owner, selected);
        JsonNode otherSummary = summary(other, selected);

        assertThat(ids(ownerSummary)).doesNotContain(otherPets);
        assertThat(ids(otherSummary)).doesNotContain(ownerPets);
        JsonNode ownerRow = byId(ownerSummary, ownerPets);
        assertThat(ownerRow.get("currentMonthTransactionCount").asLong()).isEqualTo(1);
        assertThat(ownerRow.get("currentMonthSpent").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(ownerRow.get("currentMonthBudget").isNull()).isTrue();
        JsonNode otherRow = byId(otherSummary, otherPets);
        assertThat(otherRow.get("currentMonthTransactionCount").asLong()).isEqualTo(2);
        assertThat(otherRow.get("currentMonthSpent").decimalValue()).isEqualByComparingTo("800.00");
        assertThat(otherRow.get("currentMonthBudget").get("monthlyLimit").decimalValue()).isEqualByComparingTo("900.00");
    }

    @Test
    void rejectsInvalidPeriodsWithFieldErrors() throws Exception {
        LocalDate nextMonth = today.withDayOfMonth(1).plusMonths(1);
        String futureField = nextMonth.getYear() > today.getYear() ? "year" : "month";

        expectFieldError(Map.of("month", "8"), "year", "Month and year must be given together");
        expectFieldError(Map.of("year", "2026"), "month", "Month and year must be given together");
        expectFieldError(Map.of("month", "13", "year", "2026"), "month", "Month must be between 1 and 12");
        expectFieldError(Map.of("month", "5", "year", "1999"), "year", "Year must be 2000 or later");
        expectFieldError(Map.of("month", "abc", "year", "2026"), "month", "Month must be a whole number between 1 and 12");
        expectFieldError(Map.of("month", "8", "year", "abcd"), "year", "Year must be a whole number");
        send(get("/api/categories/summary")
                .param("month", String.valueOf(nextMonth.getMonthValue()))
                .param("year", String.valueOf(nextMonth.getYear())), owner, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields." + futureField).exists());
    }

    @Test
    void requiresAuthenticationForAnEarlierMonthToo() throws Exception {
        mockMvc.perform(get("/api/categories/summary").param("month", "1").param("year", "2026"))
                .andExpect(status().isUnauthorized());
    }

    private void expectFieldError(Map<String, String> params, String field, String message) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/categories/summary");
        params.forEach(request::param);
        send(request, owner, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fields." + field).value(message));
    }

    private JsonNode summary(String bearer) throws Exception {
        return json(send(get("/api/categories/summary"), bearer, null).andExpect(status().isOk()));
    }

    private JsonNode summary(String bearer, LocalDate month) throws Exception {
        return json(send(get("/api/categories/summary")
                .param("month", String.valueOf(month.getMonthValue()))
                .param("year", String.valueOf(month.getYear())), bearer, null).andExpect(status().isOk()));
    }

    private static JsonNode row(JsonNode summary, String name) {
        for (JsonNode row : summary.get("categories")) {
            if (row.get("name").asString().equals(name)) {
                return row;
            }
        }
        throw new AssertionError("No summary row named " + name);
    }

    private static JsonNode byId(JsonNode summary, long id) {
        for (JsonNode row : summary.get("categories")) {
            if (row.get("id").asLong() == id) {
                return row;
            }
        }
        throw new AssertionError("No summary row with ID " + id);
    }

    private static Set<Long> ids(JsonNode summary) {
        Set<Long> ids = new HashSet<>();
        summary.get("categories").forEach(row -> ids.add(row.get("id").asLong()));
        return ids;
    }

    private long createCategory(String bearer, String name, String iconKey) throws Exception {
        return json(send(post("/api/categories"), bearer,
                "{\"name\": \"%s\", \"budgetEnabled\": true, \"iconKey\": \"%s\"}".formatted(name, iconKey))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long categoryId(String bearer, String name) throws Exception {
        for (JsonNode category : json(send(get("/api/categories"), bearer, null))) {
            if (category.get("name").asString().equals(name)) {
                return category.get("id").asLong();
            }
        }
        throw new AssertionError("No category " + name);
    }

    private void transaction(String bearer, long categoryId, String type, String amount, LocalDate date)
            throws Exception {
        send(post("/api/transactions"), bearer, """
                {"categoryId": %d, "type": "%s", "amount": %s, "description": "Summary test", "transactionDate": "%s"}
                """.formatted(categoryId, type, amount, date)).andExpect(status().isCreated());
    }

    private void budget(String bearer, long categoryId, String limit, LocalDate date) throws Exception {
        send(post("/api/budgets"), bearer, """
                {"categoryId": %d, "monthlyLimit": %s, "month": %d, "year": %d}
                """.formatted(categoryId, limit, date.getMonthValue(), date.getYear())).andExpect(status().isCreated());
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
