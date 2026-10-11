package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

/**
 * GET /api/budgets/analytics end to end: one month's budgets with analytics, validation,
 * authentication, ownership, and parity with the single-budget, dashboard, and Categories figures.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetMonthAnalyticsIntegrationTest {

    private static final String PASSWORD = "River meadow lantern 42!";
    private static final LocalDate MARCH_2024 = LocalDate.of(2024, 3, 1);

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    private String owner;
    private String other;

    @BeforeEach
    void users() throws Exception {
        owner = login("month-owner");
        other = login("month-other");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/budgets/analytics").param("month", "3").param("year", "2024"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlyThatMonthsBudgetsWithExpenseOnlyAnalyticsInCategoryOrder() throws Exception {
        long pets = createCategory(owner, "Pet Care", "paw-print");
        long rent = createCategory(owner, "Apartment Rent", "house");
        long groceries = categoryId(owner, "Groceries");
        long petsMarch = budget(owner, pets, "80.00", MARCH_2024);
        long rentMarch = budget(owner, rent, "1200.00", MARCH_2024);
        long groceriesMarch = budget(owner, groceries, "400.00", MARCH_2024);
        budget(owner, pets, "60.00", LocalDate.of(2024, 2, 1));   // other months never appear
        budget(owner, pets, "90.00", LocalDate.of(2024, 4, 1));
        budget(owner, pets, "70.00", LocalDate.of(2023, 3, 1));   // same month, another year
        transaction(owner, pets, "EXPENSE", "20.00", LocalDate.of(2024, 3, 1));   // first day: in
        transaction(owner, pets, "EXPENSE", "30.00", LocalDate.of(2024, 3, 31));  // last day: in
        transaction(owner, pets, "EXPENSE", "70.00", LocalDate.of(2024, 2, 29));  // day before: out
        transaction(owner, pets, "EXPENSE", "9.00", LocalDate.of(2024, 4, 1));    // day after: out
        transaction(owner, pets, "INCOME", "500.00", LocalDate.of(2024, 3, 15));  // income: never spending
        transaction(owner, groceries, "INCOME", "100.00", LocalDate.of(2024, 3, 10));
        long travel = createCategory(owner, "Weekend Trips", "plane");
        transaction(owner, travel, "EXPENSE", "45.00", LocalDate.of(2024, 3, 5)); // spending, no budget

        MvcResult result = send(month(3, 2024), owner).andExpect(status().isOk()).andReturn();
        JsonNode body = json(result);

        assertThat(body.get("month").asInt()).isEqualTo(3);
        assertThat(body.get("year").asInt()).isEqualTo(2024);
        assertThat(names(body)).containsExactly("Apartment Rent", "Groceries", "Pet Care");
        assertThat(budgetIds(body)).containsExactly(rentMarch, groceriesMarch, petsMarch);

        JsonNode petRow = byBudget(body, petsMarch);
        assertThat(petRow.get("categoryId").asLong()).isEqualTo(pets);
        assertThat(petRow.get("categoryIconKey").asString()).isEqualTo("paw-print");
        assertThat(petRow.get("monthlyLimit").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(petRow.get("amountSpent").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(petRow.get("amountRemaining").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(petRow.get("percentageUsed").decimalValue()).isEqualByComparingTo("62.50");
        assertThat(petRow.get("status").asString()).isEqualTo("CAUTION");
        assertThat(petRow.get("month").asInt()).isEqualTo(3);
        assertThat(petRow.get("year").asInt()).isEqualTo(2024);

        // Income only, and no transactions at all: zero spending, not a missing row.
        for (long id : new long[] {groceriesMarch, rentMarch}) {
            JsonNode row = byBudget(body, id);
            assertThat(row.get("amountSpent").decimalValue()).isEqualByComparingTo("0.00");
            assertThat(row.get("percentageUsed").decimalValue()).isEqualByComparingTo("0.00");
            assertThat(row.get("status").asString()).isEqualTo("ON_TRACK");
        }
        // Money keeps two decimal places in the JSON itself.
        String raw = result.getResponse().getContentAsString();
        assertThat(raw).contains("\"amountSpent\":0.00", "\"monthlyLimit\":1200.00", "\"amountRemaining\":30.00");
        // Exactly these fields, the same as the single-budget analytics.
        List<String> fields = new ArrayList<>(petRow.propertyNames());
        assertThat(fields).containsExactly("budgetId", "categoryId", "categoryName", "categoryIconKey",
                "monthlyLimit", "amountSpent", "amountRemaining", "percentageUsed", "status", "month", "year");
    }

    @Test
    void returnsAnEmptyListForAMonthWithoutBudgets() throws Exception {
        budget(owner, categoryId(owner, "Groceries"), "100.00", MARCH_2024);

        send(month(1, 2001), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(1))
                .andExpect(jsonPath("$.year").value(2001))
                .andExpect(jsonPath("$.budgets").isEmpty());
    }

    @Test
    void plansFutureMonthsLikeCreatingABudgetDoes() throws Exception {
        long pets = createCategory(owner, "Pet Care", "paw-print");
        LocalDate february2028 = LocalDate.of(2028, 2, 1);
        long budgetId = budget(owner, pets, "100.00", february2028);
        transaction(owner, pets, "EXPENSE", "100.00", LocalDate.of(2028, 2, 29)); // leap day, last day

        JsonNode row = byBudget(json(send(month(2, 2028), owner).andExpect(status().isOk())), budgetId);

        assertThat(row.get("amountSpent").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(row.get("amountRemaining").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(row.get("percentageUsed").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(row.get("status").asString()).isEqualTo("OVER_BUDGET");
    }

    @Test
    void keepsEachUsersBudgetsAndSpendingSeparateDespiteMatchingCategoryNames() throws Exception {
        long ownerPets = createCategory(owner, "Pet Care", "paw-print");
        long otherPets = createCategory(other, "Pet Care", "paw-print");
        long ownerBudget = budget(owner, ownerPets, "80.00", MARCH_2024);
        long otherBudget = budget(other, otherPets, "900.00", MARCH_2024);
        transaction(owner, ownerPets, "EXPENSE", "10.00", MARCH_2024);
        transaction(other, otherPets, "EXPENSE", "850.00", MARCH_2024);
        transaction(other, otherPets, "INCOME", "50.00", MARCH_2024);

        JsonNode ownerBody = json(send(month(3, 2024), owner).andExpect(status().isOk()));
        JsonNode otherBody = json(send(month(3, 2024), other).andExpect(status().isOk()));

        assertThat(budgetIds(ownerBody)).containsExactly(ownerBudget);
        assertThat(budgetIds(otherBody)).containsExactly(otherBudget);
        JsonNode ownerRow = byBudget(ownerBody, ownerBudget);
        assertThat(ownerRow.get("categoryId").asLong()).isEqualTo(ownerPets);
        assertThat(ownerRow.get("monthlyLimit").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(ownerRow.get("amountSpent").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(ownerRow.get("amountRemaining").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(ownerRow.get("percentageUsed").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(ownerRow.get("status").asString()).isEqualTo("ON_TRACK");
        JsonNode otherRow = byBudget(otherBody, otherBudget);
        assertThat(otherRow.get("categoryId").asLong()).isEqualTo(otherPets);
        assertThat(otherRow.get("amountSpent").decimalValue()).isEqualByComparingTo("850.00");
        assertThat(otherRow.get("percentageUsed").decimalValue()).isEqualByComparingTo("94.44");
        assertThat(otherRow.get("status").asString()).isEqualTo("WARNING");
    }

    @Test
    void agreesWithSingleBudgetAnalyticsAndTheCategoriesSummary() throws Exception {
        long pets = createCategory(owner, "Pet Care", "paw-print");
        long travel = createCategory(owner, "Vacations", "plane");
        long groceries = categoryId(owner, "Groceries");
        budget(owner, pets, "80.00", MARCH_2024);       // 75.63% -> WARNING
        budget(owner, travel, "200.00", MARCH_2024);    // 125%   -> OVER_BUDGET
        budget(owner, groceries, "33.33", MARCH_2024);  // 30.00 of 33.33 -> rounding
        transaction(owner, pets, "EXPENSE", "45.00", MARCH_2024);
        transaction(owner, pets, "EXPENSE", "15.50", MARCH_2024);
        transaction(owner, travel, "EXPENSE", "250.00", MARCH_2024);
        transaction(owner, groceries, "EXPENSE", "30.00", MARCH_2024);
        transaction(owner, groceries, "INCOME", "100.00", MARCH_2024);

        JsonNode month = json(send(month(3, 2024), owner).andExpect(status().isOk()));
        JsonNode summary = json(send(get("/api/categories/summary").param("month", "3").param("year", "2024"), owner)
                .andExpect(status().isOk()));

        assertThat(month.get("budgets")).hasSize(3);
        for (JsonNode row : month.get("budgets")) {
            JsonNode single = json(send(get("/api/budgets/" + row.get("budgetId").asLong() + "/analytics"), owner)
                    .andExpect(status().isOk()));
            assertSameAnalytics(row, single);
            JsonNode summaryBudget = categoryRow(summary, row.get("categoryId").asLong()).get("currentMonthBudget");
            assertThat(summaryBudget.get("budgetId").asLong()).isEqualTo(row.get("budgetId").asLong());
            for (String field : new String[] {"monthlyLimit", "amountSpent", "amountRemaining", "percentageUsed"}) {
                assertThat(summaryBudget.get(field).decimalValue()).as(field)
                        .isEqualByComparingTo(row.get(field).decimalValue());
            }
            assertThat(summaryBudget.get("status").asString()).isEqualTo(row.get("status").asString());
        }
        assertThat(byCategory(month, pets).get("status").asString()).isEqualTo("WARNING");
        assertThat(byCategory(month, travel).get("status").asString()).isEqualTo("OVER_BUDGET");
        assertThat(byCategory(month, groceries).get("percentageUsed").decimalValue()).isEqualByComparingTo("90.01");
    }

    @Test
    void agreesWithTheDashboardForTheCurrentMonth() throws Exception {
        LocalDate today = LocalDate.now();
        long pets = createCategory(owner, "Pet Care", "paw-print");
        budget(owner, pets, "80.00", today);
        transaction(owner, pets, "EXPENSE", "61.25", today);

        JsonNode month = json(send(month(today.getMonthValue(), today.getYear()), owner).andExpect(status().isOk()));
        JsonNode dashboard = json(send(get("/api/dashboard"), owner).andExpect(status().isOk()));

        assertThat(dashboard.get("budgetSummaries")).hasSize(1);
        JsonNode dashboardBudget = dashboard.get("budgetSummaries").get(0);
        JsonNode row = month.get("budgets").get(0);
        assertThat(row.get("budgetId").asLong()).isEqualTo(dashboardBudget.get("budgetId").asLong());
        for (String field : new String[] {"monthlyLimit", "amountSpent", "amountRemaining", "percentageUsed"}) {
            assertThat(row.get(field).decimalValue()).as(field).isEqualByComparingTo(dashboardBudget.get(field).decimalValue());
        }
        assertThat(row.get("status").asString()).isEqualTo(dashboardBudget.get("status").asString());
    }

    @ParameterizedTest(name = "month={0}, year={1}")
    @CsvSource({"1, 2000", "12, 2000", "12, 9999", "6, 2100"})
    void acceptsMonthsOneToTwelveFromTheYear2000WithNoUpperYear(String month, String year) throws Exception {
        send(get("/api/budgets/analytics").param("month", month).param("year", year), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(Integer.parseInt(month)))
                .andExpect(jsonPath("$.budgets").isEmpty());
    }

    @ParameterizedTest(name = "month=[{0}], year=[{1}] -> {2}")
    @CsvSource(nullValues = "null", value = {
            "0, 2024, month, Month must be between 1 and 12",
            "13, 2024, month, Month must be between 1 and 12",
            "null, 2024, month, Month is required",
            "'', 2024, month, Month is required",
            "abc, 2024, month, Month must be a whole number between 1 and 12",
            "8.5, 2024, month, Month must be a whole number between 1 and 12",
            "-1, 2024, month, Month must be a whole number between 1 and 12",
            "+8, 2024, month, Month must be a whole number between 1 and 12",
            "' 8', 2024, month, Month must be a whole number between 1 and 12",
            "3, null, year, Year is required",
            "3, 1999, year, Year must be 2000 or later",
            "3, abcd, year, Year must be a whole number",
            "3, 2024.0, year, Year must be a whole number",
            "3, 99999999999, year, Year must be a whole number",
    })
    void rejectsAnInvalidMonthOrYearWithAFieldError(String month, String year, String field, String message)
            throws Exception {
        MockHttpServletRequestBuilder request = get("/api/budgets/analytics");
        if (month != null) request.param("month", month);
        if (year != null) request.param("year", year);

        send(request, owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fields." + field).value(message));
    }

    @Test
    void reportsBothMissingParametersAtOnce() throws Exception {
        send(get("/api/budgets/analytics"), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.month").value("Month is required"))
                .andExpect(jsonPath("$.fields.year").value("Year is required"));
    }

    @Test
    void leavesTheExistingBudgetEndpointsAsTheyWere() throws Exception {
        long groceries = categoryId(owner, "Groceries");
        long budgetId = budget(owner, groceries, "100.00", MARCH_2024);

        send(get("/api/budgets"), owner).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(budgetId));
        send(get("/api/budgets/" + budgetId), owner).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(budgetId));
        send(get("/api/budgets/" + budgetId + "/analytics"), owner)
                .andExpect(status().isOk()).andExpect(jsonPath("$.budgetId").value(budgetId));
        // Another user's budget is still simply not found.
        send(get("/api/budgets/" + budgetId + "/analytics"), other).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    private static void assertSameAnalytics(JsonNode row, JsonNode single) {
        for (String field : new String[] {"budgetId", "categoryId", "month", "year"}) {
            assertThat(row.get(field).asLong()).as(field).isEqualTo(single.get(field).asLong());
        }
        for (String field : new String[] {"categoryName", "categoryIconKey", "status"}) {
            assertThat(row.get(field).asString()).as(field).isEqualTo(single.get(field).asString());
        }
        for (String field : new String[] {"monthlyLimit", "amountSpent", "amountRemaining", "percentageUsed"}) {
            assertThat(row.get(field).decimalValue()).as(field).isEqualByComparingTo(single.get(field).decimalValue());
        }
    }

    private static MockHttpServletRequestBuilder month(int month, int year) {
        return get("/api/budgets/analytics").param("month", String.valueOf(month)).param("year", String.valueOf(year));
    }

    private static List<String> names(JsonNode body) {
        List<String> names = new ArrayList<>();
        body.get("budgets").forEach(row -> names.add(row.get("categoryName").asString()));
        return names;
    }

    private static List<Long> budgetIds(JsonNode body) {
        List<Long> ids = new ArrayList<>();
        body.get("budgets").forEach(row -> ids.add(row.get("budgetId").asLong()));
        return ids;
    }

    private static JsonNode byBudget(JsonNode body, long budgetId) {
        for (JsonNode row : body.get("budgets")) {
            if (row.get("budgetId").asLong() == budgetId) {
                return row;
            }
        }
        throw new AssertionError("No budget " + budgetId);
    }

    private static JsonNode byCategory(JsonNode body, long categoryId) {
        for (JsonNode row : body.get("budgets")) {
            if (row.get("categoryId").asLong() == categoryId) {
                return row;
            }
        }
        throw new AssertionError("No budget for category " + categoryId);
    }

    private static JsonNode categoryRow(JsonNode summary, long categoryId) {
        for (JsonNode row : summary.get("categories")) {
            if (row.get("id").asLong() == categoryId) {
                return row;
            }
        }
        throw new AssertionError("No summary row " + categoryId);
    }

    private long createCategory(String bearer, String name, String iconKey) throws Exception {
        return json(send(post("/api/categories"), bearer,
                "{\"name\": \"%s\", \"budgetEnabled\": true, \"iconKey\": \"%s\"}".formatted(name, iconKey))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long categoryId(String bearer, String name) throws Exception {
        for (JsonNode category : json(send(get("/api/categories"), bearer))) {
            if (category.get("name").asString().equals(name)) {
                return category.get("id").asLong();
            }
        }
        throw new AssertionError("No category " + name);
    }

    private void transaction(String bearer, long categoryId, String type, String amount, LocalDate date)
            throws Exception {
        send(post("/api/transactions"), bearer, """
                {"categoryId": %d, "type": "%s", "amount": %s, "description": "Month test", "transactionDate": "%s"}
                """.formatted(categoryId, type, amount, date)).andExpect(status().isCreated());
    }

    private long budget(String bearer, long categoryId, String limit, LocalDate date) throws Exception {
        return json(send(post("/api/budgets"), bearer, """
                {"categoryId": %d, "monthlyLimit": %s, "month": %d, "year": %d}
                """.formatted(categoryId, limit, date.getMonthValue(), date.getYear()))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String bearer) throws Exception {
        return send(request, bearer, null);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String bearer, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, bearer);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return json(result.andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
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
