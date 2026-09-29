package dev.portfolio.finance.dto.auth;

import dev.portfolio.finance.dto.account.AccountPreferencesResponse;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.storage.ProfilePhotoUrlResolver;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {
    private final ProfilePhotoUrlResolver photoUrls;

    public UserResponseMapper(ProfilePhotoUrlResolver photoUrls) {
        this.photoUrls = photoUrls;
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getFirstName(), user.getLastName(), user.getDisplayName(),
                user.getEmail(), user.getCreatedAt(),
                new AccountPreferencesResponse(user.getDateFormat(), user.getTransactionPageSize()),
                photoUrls.resolveDeliveryUrl(user.getProfilePhotoKey()));
    }
}
