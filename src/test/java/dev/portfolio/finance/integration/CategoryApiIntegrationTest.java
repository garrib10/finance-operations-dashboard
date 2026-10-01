package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import dev.portfolio.finance.support.AuthRequests;

/** The hardened category API through the real security chain, services, and database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryApiIntegrationTest {

    private static final String PASSWORD = "River meadow lantern 42!";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;

    private Session owner;
    private Session other;

    private record Session(String bearer, String refreshCookie) {
    }

    @BeforeEach
    void registerTwoUsers() throws Exception {
        owner = registerAndLogin("owner");
        other = registerAndLogin("other");
    }

    @Test
    void listsOnlyTheUsersBuiltInCatalogWithIconsAndNoInternals() throws Exception {
        mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[?(@.builtIn == false)]").isEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Housing')].iconKey").value("house"))
                .andExpect(jsonPath("$[?(@.name == 'Income')].budgetEnabled").value(false))
                .andExpect(jsonPath("$[0].normalizedName").doesNotExist())
                .andExpect(jsonPath("$[0].userId").doesNotExist());

        long custom = create(owner, "{\"name\": \"Pets\", \"budgetEnabled\": true, \"iconKey\": \"heart-pulse\"}");
        mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, other.bearer()))
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[?(@.id == " + custom + ")]").isEmpty());
    }

    @Test
    void customCategoryLifecycleFollowsTheApprovedPolicies() throws Exception {
        String payload = "{\"name\": \"  Pet   Care \", \"budgetEnabled\": true, \"builtIn\": true, \"userId\": 1}";
        MvcResult created = mockMvc.perform(authorized(post("/api/categories"), owner)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pet Care"))
                .andExpect(jsonPath("$.builtIn").value(false))
                .andExpect(jsonPath("$.iconKey").value("tag"))
                .andReturn();
        long pets = json(created).get("id").asLong();

        // Equivalent name for the same user conflicts; another user may use it.
        mockMvc.perform(authorized(post("/api/categories"), owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"PET CARE\", \"budgetEnabled\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_DUPLICATE"));
        mockMvc.perform(authorized(post("/api/categories"), owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"housing\", \"budgetEnabled\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_DUPLICATE"));
        create(other, "{\"name\": \"Pet Care\", \"budgetEnabled\": true}");

        // Referenced by a transaction: rename keeps the ID and relabels the transaction.
        MvcResult transaction = mockMvc.perform(authorized(post("/api/transactions"), owner)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"categoryId": %d, "type": "EXPENSE", "amount": 12.50,
                                 "description": "Food", "transactionDate": "2026-09-01"}
                                """.formatted(pets)))
                .andExpect(status().isCreated())
                .andReturn();
        long transactionId = json(transaction).get("id").asLong();

        mockMvc.perform(authorized(put("/api/categories/" + pets), owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Pet Supplies\", \"budgetEnabled\": false, \"iconKey\": \"heart-pulse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pets))
                .andExpect(jsonPath("$.name").value("Pet Supplies"))
                .andExpect(jsonPath("$.budgetEnabled").value(false))
                .andExpect(jsonPath("$.iconKey").value("heart-pulse"));
        mockMvc.perform(authorized(put("/api/categories/" + pets), owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"pet supplies\", \"budgetEnabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("pet supplies"))
                .andExpect(jsonPath("$.iconKey").value("heart-pulse"));
        mockMvc.perform(authorized(get("/api/transactions/" + transactionId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(pets))
                .andExpect(jsonPath("$.categoryName").value("pet supplies"))
                .andExpect(jsonPath("$.amount").value(12.50));

        mockMvc.perform(authorized(delete("/api/categories/" + pets), owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        // Referenced only by a past-month budget: still in use.
        long travelFund = create(owner, "{\"name\": \"Travel Fund\", \"budgetEnabled\": true}");
        mockMvc.perform(authorized(post("/api/budgets"), owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\": %d, \"monthlyLimit\": 100.00, \"month\": 1, \"year\": 2020}"
                                .formatted(travelFund)))
                .andExpect(status().isCreated());
        mockMvc.perform(authorized(delete("/api/categories/" + travelFund), owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        // Unused custom: hard delete, then gone.
        long unused = create(owner, "{\"name\": \"Temporary\", \"budgetEnabled\": true}");
        mockMvc.perform(authorized(delete("/api/categories/" + unused), owner))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
        mockMvc.perform(authorized(get("/api/categories/" + unused), owner))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void builtInCategoriesCannotBeChangedOrDeleted() throws Exception {
        long housing = categoryId(owner, "Housing");

        mockMvc.perform(authorized(put("/api/categories/" + housing), owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Housing\", \"budgetEnabled\": true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_BUILT_IN"));
        mockMvc.perform(authorized(delete("/api/categories/" + housing), owner))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_BUILT_IN"));
        mockMvc.perform(authorized(get("/api/categories/" + housing), owner))
                .andExpect(jsonPath("$.name").value("Housing"))
                .andExpect(jsonPath("$.iconKey").value("house"));
    }

    @Test
    void anotherUsersCategoryIsIndistinguishableFromAMissingOne() throws Exception {
        long foreignCustom = create(owner, "{\"name\": \"Private\", \"budgetEnabled\": true}");
        long foreignBuiltIn = categoryId(owner, "Housing");
        long missing = 987_654_321L;

        String expectedGet = notFoundBody(get("/api/categories/" + missing));
        for (long id : new long[] {foreignCustom, foreignBuiltIn}) {
            assertThat(notFoundBody(get("/api/categories/" + id))).isEqualTo(expectedGet);
            assertThat(notFoundBody(put("/api/categories/" + id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Mine\", \"budgetEnabled\": true}")))
                    .isEqualTo(notFoundBody(put("/api/categories/" + missing).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"Mine\", \"budgetEnabled\": true}")));
            assertThat(notFoundBody(delete("/api/categories/" + id)))
                    .isEqualTo(notFoundBody(delete("/api/categories/" + missing)));
        }
        mockMvc.perform(authorized(get("/api/categories/" + foreignCustom), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Private"));
    }

    @Test
    void requiresABearerTokenAndIgnoresTheRefreshCookieAlone() throws Exception {
        assertThat(owner.refreshCookie()).contains("fintrack_refresh=");

        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/categories"),
                get("/api/categories").header(HttpHeaders.COOKIE, owner.refreshCookie()),
                get("/api/categories").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"),
                delete("/api/categories/1").header(HttpHeaders.COOKIE, owner.refreshCookie())}) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
        }
    }

    // ------------------------------------------------------------------ helpers

    private String notFoundBody(MockHttpServletRequestBuilder request) throws Exception {
        JsonNode body = json(mockMvc.perform(authorized(request, other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andReturn());
        return body.get("status") + " " + body.get("error") + " " + body.get("message") + " " + body.get("code");
    }

    private long create(Session session, String body) throws Exception {
        return json(mockMvc.perform(authorized(post("/api/categories"), session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }

    private long categoryId(Session session, String name) throws Exception {
        for (JsonNode category : json(mockMvc.perform(authorized(get("/api/categories"), session)).andReturn())) {
            if (category.get("name").asString().equals(name)) {
                return category.get("id").asLong();
            }
        }
        throw new AssertionError("No category " + name);
    }

    private static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, Session session) {
        return request.header(HttpHeaders.AUTHORIZATION, session.bearer());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private Session registerAndLogin(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"firstName": "Cat", "lastName": "Tester", "email": "%s", "password": "%s"}
                        """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(AuthRequests.protectedAuth(post("/api/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return new Session("Bearer " + json(login).get("accessToken").asString(), setCookie.split(";", 2)[0]);
    }
}
