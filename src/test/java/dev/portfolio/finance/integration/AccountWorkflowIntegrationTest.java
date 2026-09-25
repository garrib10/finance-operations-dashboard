package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = "logging.level.org.springframework.web=DEBUG")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountWorkflowIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwt;
    @Autowired private JsonMapper mapper;
    @Value("${app.jwt.secret}") private String secret;
    private User a;
    private User b;
    private String token;
    private static final String OLD = " River meadow lantern 42! ";
    private static final String NEXT = " Another quiet forest 73! ";

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        a = users.saveAndFlush(new User("First", "User", "a-" + suffix + "@example.com", encoder.encode(OLD)));
        b = users.saveAndFlush(new User("Other", "User", "b-" + suffix + "@example.com", encoder.encode(OLD)));
        token = jwt.generateToken(a);
    }

    private MockHttpServletRequestBuilder accountRequest(String operation) {
        return (operation.equals("password") ? post("/api/account/" + operation) : put("/api/account/" + operation))
                .contentType("application/json");
    }

    private String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    private String login(String email, String password, int status) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"profile", "preferences", "password"})
    void rejectsAllUnauthenticatedAndInvalidTokenVariants(String operation) throws Exception {
        String expired = Jwts.builder().subject(a.getEmail()).expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
        String wrongSignature = new JwtService("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", 60000).generateToken(a);
        for (String header : new String[]{"", "Basic abc", "Bearer malformed", "Bearer " + expired, "Bearer " + wrongSignature}) {
            var request = accountRequest(operation).content("{}");
            if (!header.isEmpty()) request.header("Authorization", header);
            String body = mvc.perform(request).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(expired, wrongSignature, "passwordHash", "stackTrace");
        }
    }

    @Test
    void mutationsAreSelfScopedAndPersistAfterNewLogin() throws Exception {
        String hash = a.getPasswordHash();
        var createdAt = users.findByEmail(a.getEmail()).orElseThrow().getCreatedAt();
        var profile = Map.of("firstName", " New ", "lastName", " Name ", "displayName", " Display ",
                "id", b.getId(), "email", b.getEmail(), "role", "ADMIN", "createdAt", "2000-01-01", "passwordHash", "attacker-hash");
        String response = mvc.perform(accountRequest("profile").header("Authorization", "Bearer " + token)
                .header("X-User-Id", b.getId()).queryParam("userId", b.getId().toString()).content(json(profile)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(a.getId()))
                .andExpect(jsonPath("$.email").value(a.getEmail())).andExpect(jsonPath("$.displayName").value("Display"))
                .andExpect(jsonPath("$.firstName").value("New")).andExpect(jsonPath("$.lastName").value("Name"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(hash, "passwordHash", "attacker-hash");
        mvc.perform(accountRequest("preferences").header("Authorization", "Bearer " + token)
                .content(json(Map.of("dateFormat", "ISO", "transactionPageSize", 25, "id", b.getId(), "email", b.getEmail(), "updatedAt", "2000-01-01"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferences.dateFormat").value("ISO"));
        User saved = users.findByEmail(a.getEmail()).orElseThrow();
        assertThat(saved.getPasswordHash()).isEqualTo(hash);
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getTransactionPageSize()).isEqualTo(25);
        for (String accessToken : new String[]{token, mapper.readTree(login(a.getEmail(), OLD, 200)).get("accessToken").stringValue()}) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Display"))
                    .andExpect(jsonPath("$.preferences.transactionPageSize").value(25))
                    .andExpect(jsonPath("$.preferences.dateFormat").value("ISO"))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }
        User other = users.findByEmail(b.getEmail()).orElseThrow();
        assertThat(other.getDisplayName()).isEqualTo("Other User");
        assertThat(other.getTransactionPageSize()).isEqualTo(10);
        assertThat(other.getPasswordHash()).isEqualTo(b.getPasswordHash());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt.generateToken(b)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(b.getId()))
                .andExpect(jsonPath("$.displayName").value("Other User"));
    }

    @Test
    void passwordErrorsPreserveCredentialsAndSuccessfulChangeIsSelfScoped(CapturedOutput output) throws Exception {
        mvc.perform(accountRequest("password").header("Authorization", "Bearer " + token)
                .content(json(Map.of("currentPassword", "wrong secret", "newPassword", NEXT))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.currentPassword").value("Current password is incorrect"));
        mvc.perform(accountRequest("password").header("Authorization", "Bearer " + token)
                .content(json(Map.of("currentPassword", OLD, "newPassword", OLD))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.newPassword").isString());
        login(a.getEmail(), OLD, 200);
        assertThat(users.findByEmail(a.getEmail()).orElseThrow().getPasswordHash()).isEqualTo(a.getPasswordHash());
        mvc.perform(accountRequest("password").header("Authorization", "Bearer " + token)
                .content(json(Map.of("currentPassword", OLD, "newPassword", NEXT, "id", b.getId(), "email", b.getEmail()))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        login(a.getEmail(), OLD, 401);
        String newLogin = login(a.getEmail(), NEXT, 200);
        String newToken = mapper.readTree(newLogin).get("accessToken").stringValue();
        login(b.getEmail(), OLD, 200);
        assertThat(users.findByEmail(b.getEmail()).orElseThrow().getPasswordHash()).isEqualTo(b.getPasswordHash());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertThat(output.getAll()).doesNotContain(OLD, NEXT, "wrong secret", token, newToken, a.getPasswordHash(), b.getPasswordHash());
    }

    @Test
    void malformedAndInvalidPasswordBodiesDoNotLeakValues(CapturedOutput output) throws Exception {
        String secretValue = "tiny-secret";
        String result = mvc.perform(accountRequest("password").header("Authorization", "Bearer " + token)
                .content(json(Map.of("currentPassword", OLD, "newPassword", secretValue))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.newPassword").isString())
                .andReturn().getResponse().getContentAsString();
        String malformed = mvc.perform(accountRequest("password").header("Authorization", "Bearer " + token)
                .content("{\"newPassword\":\"malformed-secret\","))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(result + malformed + output.getAll()).doesNotContain(secretValue, "malformed-secret", OLD);
    }

    @Test
    void tokenForUnknownUserReturnsNormal401() throws Exception {
        String unknownToken = jwt.generateToken(new User("Missing", "User", "missing@example.com", "hash"));
        mvc.perform(accountRequest("profile").header("Authorization", "Bearer " + unknownToken).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
    }
}
