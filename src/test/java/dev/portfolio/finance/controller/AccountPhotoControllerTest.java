package dev.portfolio.finance.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.GlobalExceptionHandler;
import dev.portfolio.finance.exception.account.*;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.service.AccountPhotoService;
import dev.portfolio.finance.support.ProfilePhotoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountPhotoControllerTest {
    private final AccountPhotoService photos = mock(AccountPhotoService.class);
    private final TestingAuthenticationToken principal = new TestingAuthenticationToken("a@example.com", null);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AccountPhotoController(photos))
                .setControllerAdvice(new ProfilePhotoExceptionHandler(), new AccountExceptionHandler(new GlobalExceptionHandler())).build();
    }

    @Test
    void uploadUsesPrincipalAndReturnsOnlyCanonicalResponse() throws Exception {
        var user = new User("A", "B", "a@example.com", "PRIVATE_HASH");
        user.changeProfilePhotoKey(ProfilePhotoTestSupport.KEY);
        when(photos.upload(eq("a@example.com"), any())).thenReturn(ProfilePhotoTestSupport.mapper().toResponse(user));
        String body = mvc.perform(multipart(HttpMethod.PUT, "/api/account/photo")
                .file(new MockMultipartFile("photo", new byte[]{1})).principal(principal))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profilePhotoUrl").value(ProfilePhotoTestSupport.URL))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("profilePhotoKey", "passwordHash", "PRIVATE_HASH");
    }

    @ParameterizedTest
    @CsvSource({"EMPTY,400", "TOO_LARGE,413", "UNSUPPORTED,415", "INVALID,400", "DIMENSIONS,400", "ANIMATION,400"})
    void mapsProcessorErrors(InvalidProfilePhotoException.Reason reason, int code) throws Exception {
        when(photos.upload(any(), any())).thenThrow(new InvalidProfilePhotoException(reason));
        mvc.perform(multipart(HttpMethod.PUT, "/api/account/photo")
                .file(new MockMultipartFile("photo", new byte[]{1})).principal(principal))
                .andExpect(status().is(code)).andExpect(jsonPath("$.status").value(code));
    }

    @Test
    void mapsDisabledStorageMissingAccountAndUnexpectedErrorsSafely() throws Exception {
        when(photos.remove(any())).thenThrow(new ProfilePhotoStorageException(ProfilePhotoStorageException.Reason.DISABLED));
        mvc.perform(delete("/api/account/photo").principal(principal)).andExpect(status().isServiceUnavailable());
        doThrow(new InvalidCredentialsException("Authentication is required to access this resource")).when(photos).remove(any());
        mvc.perform(delete("/api/account/photo").principal(principal)).andExpect(status().isUnauthorized());
        doThrow(new IllegalStateException("PRIVATE_SQL")).when(photos).remove(any());
        String body = mvc.perform(delete("/api/account/photo").principal(principal)).andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("PRIVATE_SQL", "IllegalStateException");
    }
}
