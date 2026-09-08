package dev.portfolio.finance.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import dev.portfolio.finance.service.DashboardService;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    private TestingAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        DashboardController controller =
                new DashboardController(
                        dashboardService
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(controller)
                        .build();

        authentication =
                new TestingAuthenticationToken(
                        TEST_EMAIL,
                        null
                );
    }

    @Test
    void shouldReturnDashboardForAuthenticatedUser()
            throws Exception {

        // Arrange
        when(dashboardService.getDashboard(
                TEST_EMAIL
        )).thenReturn(null);

        // Act + Assert
        mockMvc.perform(
                        get("/api/dashboard")
                                .principal(authentication)
                )
                .andExpect(
                        status().isOk()
                );

        verify(dashboardService)
                .getDashboard(
                        TEST_EMAIL
                );
    }
}