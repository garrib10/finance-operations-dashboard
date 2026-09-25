package dev.portfolio.finance.controller;

import dev.portfolio.finance.dto.account.ChangePasswordRequest;
import dev.portfolio.finance.dto.account.UpdatePreferencesRequest;
import dev.portfolio.finance.dto.account.UpdateProfileRequest;
import dev.portfolio.finance.dto.auth.UserResponse;
import dev.portfolio.finance.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping("/profile")
    public ResponseEntity<UserResponse> updateProfile(
            Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(accountService.updateProfile(authentication.getName(), request));
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserResponse> updatePreferences(
            Authentication authentication, @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(accountService.updatePreferences(authentication.getName(), request));
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        accountService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
