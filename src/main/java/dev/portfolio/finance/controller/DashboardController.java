package dev.portfolio.finance.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.portfolio.finance.dto.dashboard.DashboardResponse;
import dev.portfolio.finance.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(
            DashboardService dashboardService
    ) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardResponse> getDashboard(
            Authentication authentication
    ) {
        DashboardResponse response =
                dashboardService.getDashboard(
                        authentication.getName()
                );

        return ResponseEntity.ok(response);
    }
}