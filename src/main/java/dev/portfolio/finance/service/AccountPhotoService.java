package dev.portfolio.finance.service;

import dev.portfolio.finance.config.ProfilePhotoProperties;
import dev.portfolio.finance.dto.auth.UserResponse;
import dev.portfolio.finance.dto.auth.UserResponseMapper;
import dev.portfolio.finance.exception.account.InvalidProfilePhotoException;
import dev.portfolio.finance.exception.account.ProfilePhotoStorageException;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.storage.ProfilePhotoKeyGenerator;
import dev.portfolio.finance.storage.ProfilePhotoStorage;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AccountPhotoService {
    private static final Logger log = LoggerFactory.getLogger(AccountPhotoService.class);
    private final ProfilePhotoProperties properties;
    private final ProfilePhotoProcessor processor;
    private final ProfilePhotoKeyGenerator keys;
    private final ProfilePhotoStorage storage;
    private final UserRepository users;
    private final UserResponseMapper mapper;
    private final TransactionTemplate transaction;

    public AccountPhotoService(ProfilePhotoProperties properties, ProfilePhotoProcessor processor,
            ProfilePhotoKeyGenerator keys, ProfilePhotoStorage storage, UserRepository users,
            UserResponseMapper mapper, PlatformTransactionManager transactions) {
        this.properties = properties;
        this.processor = processor;
        this.keys = keys;
        this.storage = storage;
        this.users = users;
        this.mapper = mapper;
        transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public UserResponse upload(String email, MultipartFile photo) {
        requireEnabled();
        Long userId = users.findByEmail(email).orElseThrow(AccountPhotoService::unauthenticated).getId();
        byte[] processed = processor.process(read(photo)).bytes();
        String key = keys.generate();
        try {
            storage.store(key, processed);
        } catch (RuntimeException ex) {
            log.warn("profile_photo operation=upload userId={} category=provider_unavailable", userId);
            // Do not delete on a collision: that object may belong to an earlier operation.
            // Ambiguous provider outcomes are documented as possible orphan objects.
            throw new ProfilePhotoStorageException(ProfilePhotoStorageException.Reason.UNAVAILABLE);
        }
        Change change;
        try {
            change = persist(email, key);
        } catch (RuntimeException ex) {
            log.warn("profile_photo operation=upload userId={} category=persistence_failure", userId);
            cleanup(key, userId);
            throw new IllegalStateException("Unable to persist the profile photo");
        }
        log.info("profile_photo operation={} userId={} category=success",
                change.previousKey() == null ? "upload" : "replacement", change.response().id());
        if (!key.equals(change.previousKey())) cleanup(change.previousKey(), change.response().id());
        return change.response();
    }

    public UserResponse remove(String email) {
        requireEnabled();
        Change change = persist(email, null);
        log.info("profile_photo operation=removal userId={} category=success", change.response().id());
        cleanup(change.previousKey(), change.response().id());
        return change.response();
    }

    private Change persist(String email, String key) {
        // Lock only during the short database update. Each concurrent operation observes
        // the most recently committed key, so cleanup cannot delete the active replacement.
        return transaction.execute(status -> {
            var user = users.findByEmailForPhotoUpdate(email).orElseThrow(AccountPhotoService::unauthenticated);
            String previous = user.getProfilePhotoKey();
            user.changeProfilePhotoKey(key);
            users.saveAndFlush(user);
            return new Change(previous, mapper.toResponse(user));
        }); // Includes commit; failures here are handled before cleanup of the old object.
    }

    private byte[] read(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) throw invalid(InvalidProfilePhotoException.Reason.EMPTY);
        if (photo.getSize() > properties.maxInputBytes()) throw invalid(InvalidProfilePhotoException.Reason.TOO_LARGE);
        try (var input = photo.getInputStream()) {
            byte[] bytes = input.readNBytes(Math.toIntExact(properties.maxInputBytes()) + 1);
            if (bytes.length > properties.maxInputBytes()) throw invalid(InvalidProfilePhotoException.Reason.TOO_LARGE);
            return bytes;
        } catch (IOException ex) {
            throw invalid(InvalidProfilePhotoException.Reason.INVALID);
        }
    }

    private void cleanup(String key, Long userId) {
        if (key == null) return;
        try { storage.delete(key); }
        catch (RuntimeException ex) {
            log.warn("profile_photo operation=cleanup userId={} category=provider_failure", userId);
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) throw new ProfilePhotoStorageException(ProfilePhotoStorageException.Reason.DISABLED);
    }

    private static InvalidProfilePhotoException invalid(InvalidProfilePhotoException.Reason reason) {
        return new InvalidProfilePhotoException(reason);
    }

    private static InvalidCredentialsException unauthenticated() {
        return new InvalidCredentialsException("Authentication is required to access this resource");
    }

    private record Change(String previousKey, UserResponse response) {}
}
