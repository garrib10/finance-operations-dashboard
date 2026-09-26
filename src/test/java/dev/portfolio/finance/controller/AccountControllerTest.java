package dev.portfolio.finance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.portfolio.finance.dto.account.*;
import dev.portfolio.finance.dto.auth.UserResponse;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.GlobalExceptionHandler;
import dev.portfolio.finance.exception.account.*;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {
    @Mock private AccountService service;
    private MockMvc mvc;
    private final TestingAuthenticationToken principal = new TestingAuthenticationToken("a@example.com", null);
    private static final String PASSWORD_BODY = "{\"currentPassword\":\"old12345\",\"newPassword\":\"Another quiet forest 73!\"}";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AccountController(service))
                .setControllerAdvice(new AccountExceptionHandler(new GlobalExceptionHandler())).build();
    }

    @Test
    void returnsCanonicalProfileAndDelegatesWithPrincipal() throws Exception {
        when(service.updateProfile(eq("a@example.com"), any())).thenReturn(UserResponse.from(new User("New", "Name", "a@example.com", "secret-hash")));
        String body = mvc.perform(put("/api/account/profile").principal(principal).contentType("application/json")
                .content("{\"firstName\": \" New \", \"lastName\": \" Name \", \"displayName\": \" New Name \", \"email\": \"b@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.preferences.transactionPageSize").value(10))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password", "secret-hash");
        verify(service).updateProfile("a@example.com", new UpdateProfileRequest("New", "Name", "New Name"));
    }

    @Test
    void returnsCanonicalPreferences() throws Exception {
        User user = new User("A", "B", "a@example.com", "secret-hash");
        user.updatePreferences(dev.portfolio.finance.entity.DateFormatPreference.ISO, 25);
        when(service.updatePreferences(eq("a@example.com"), any())).thenReturn(UserResponse.from(user));
        mvc.perform(put("/api/account/preferences").principal(principal).contentType("application/json")
                .content("{\"dateFormat\":\"ISO\",\"transactionPageSize\":25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferences.dateFormat").value("ISO"))
                .andExpect(jsonPath("$.preferences.transactionPageSize").value(25))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(service).updatePreferences("a@example.com", new UpdatePreferencesRequest(dev.portfolio.finance.entity.DateFormatPreference.ISO, 25));
    }

    @Test
    void passwordChangeReturnsEmpty204() throws Exception {
        mvc.perform(post("/api/account/password").principal(principal).contentType("application/json").content(PASSWORD_BODY))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).changePassword("a@example.com", new ChangePasswordRequest("old12345", "Another quiet forest 73!"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"firstName\":null,\"lastName\":null,\"displayName\":null}", "{\"firstName\":\" \",\"lastName\":\" \",\"displayName\":\" \"}"})
    void rejectsInvalidProfile(String body) throws Exception {
        mvc.perform(put("/api/account/profile").principal(principal).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsOversizedProfileWithFieldErrors() throws Exception {
        mvc.perform(put("/api/account/profile").principal(principal).contentType("application/json")
                .content("{\"firstName\":\"" + "a".repeat(101) + "\",\"lastName\":\"B\",\"displayName\":\"C\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.firstName").isString());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"dateFormat\":null,\"transactionPageSize\":null}", "{\"dateFormat\":\"ISO\",\"transactionPageSize\":11}", "{\"dateFormat\":\"OTHER\",\"transactionPageSize\":10}", "{\"dateFormat\":{},\"transactionPageSize\":\"bad\"}"})
    void rejectsInvalidPreferences(String body) throws Exception {
        mvc.perform(put("/api/account/preferences").principal(principal).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"currentPassword\": null, \"newPassword\": null}", "{\"currentPassword\": \" \", \"newPassword\": \"short\"}"})
    void rejectsInvalidPasswords(String body) throws Exception {
        mvc.perform(post("/api/account/password").principal(principal).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.newPassword").isString());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"currentPassword", "newPassword"})
    void domainPasswordErrorsAre400FieldErrors(String field) throws Exception {
        doThrow(new AccountValidationException(field, "Password change rejected")).when(service).changePassword(anyString(), any());
        String body = mvc.perform(post("/api/account/password").principal(principal).contentType("application/json").content(PASSWORD_BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields." + field).value("Password change rejected"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("old12345", "Another quiet forest 73!");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"dateFormat\":1,\"transactionPageSize\":10}",
            "{\"dateFormat\":\"ISO\",\"transactionPageSize\":25.9}",
            "{\"dateFormat\":\"ISO\",\"transactionPageSize\":\"25\"}",
            "{\"dateFormat\":\"ISO\",\"transactionPageSize\":999999999999999999999}",
            "{\"dateFormat\":\"unknown-secret\",\"transactionPageSize\":10}"
    })
    void rejectsPreferenceCoercionWithSafeFieldErrors(String body) throws Exception {
        String response = mvc.perform(put("/api/account/preferences").principal(principal).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields").isMap())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("unknown-secret", "999999999999999999999");
        verifyNoInteractions(service);
    }

    @Test
    void acceptsMediumAndFiftyPreferences() throws Exception {
        when(service.updatePreferences(anyString(), any())).thenReturn(UserResponse.from(new User("A", "B", "a@example.com", "hash")));
        for (int size : new int[]{10, 50}) {
            mvc.perform(put("/api/account/preferences").principal(principal).contentType("application/json")
                    .content("{\"dateFormat\":\"MEDIUM\",\"transactionPageSize\":" + size + "}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void malformedJsonAndUnexpectedErrorsAreSafe() throws Exception {
        String body = mvc.perform(post("/api/account/password").principal(principal).contentType("application/json")
                .content("{\"newPassword\":\"secret malformed value\","))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret malformed value", "Exception", "stackTrace");
        doThrow(new IllegalStateException("SQL password_hash=secret internal-value")).when(service).changePassword(anyString(), any());
        String error = mvc.perform(post("/api/account/password").principal(principal).contentType("application/json").content(PASSWORD_BODY))
                .andExpect(status().isInternalServerError()).andReturn().getResponse().getContentAsString();
        assertThat(error).doesNotContain("SQL", "secret", "old12345", "internal-value", "password_hash");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]"})
    void rejectsMissingOrNonObjectBodies(String body) throws Exception {
        mvc.perform(post("/api/account/password").principal(principal).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void unknownPrincipalReturnsSafe401() throws Exception {
        doThrow(new InvalidCredentialsException("Authentication is required to access this resource"))
                .when(service).changePassword(anyString(), any());
        mvc.perform(post("/api/account/password").principal(principal).contentType("application/json").content(PASSWORD_BODY))
                .andExpect(status().isUnauthorized());
    }
}
