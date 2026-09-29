package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static dev.portfolio.finance.support.ProfilePhotoTestSupport.*;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.storage.ProfilePhotoStorage;
import dev.portfolio.finance.storage.ProfilePhotoKeyGenerator;
import dev.portfolio.finance.exception.account.*;
import dev.portfolio.finance.support.ProfilePhotoImages;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(OutputCaptureExtension.class)
class AccountPhotoServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final ProfilePhotoStorage storage = mock(ProfilePhotoStorage.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ProfilePhotoKeyGenerator keys = mock(ProfilePhotoKeyGenerator.class);
    private final User user = new User("A", "B", "a@example.com", "hash");
    private AccountPhotoService service;
    private static final String NEW = "fintrack/test/profile-photos/22345678-1234-4123-8123-123456789abc";

    @BeforeEach
    void setup() {
        service = new AccountPhotoService(properties(true), new ProfilePhotoProcessor(properties(true)),
                keys, storage, users, mapper(), transactions);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findByEmailForPhotoUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(keys.generate()).thenReturn(NEW);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    private MockMultipartFile photo() throws Exception {
        return new MockMultipartFile("photo", "PRIVATE_FILENAME", "text/plain", ProfilePhotoImages.image("png", 20, 20));
    }

    @Test
    void firstUploadStoresFreshJpegBeforePersistenceAndReturnsCanonicalUrl() throws Exception {
        var response = service.upload(user.getEmail(), photo());
        assertThat(user.getProfilePhotoKey()).isEqualTo(NEW);
        assertThat(response.profilePhotoUrl()).isEqualTo("https://res.cloudinary.com/test-cloud/image/upload/v1/" + NEW + ".jpg");
        var order = inOrder(storage, users, transactions);
        order.verify(storage).store(eq(NEW), argThat(bytes -> (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216));
        order.verify(users).saveAndFlush(user);
        order.verify(transactions).commit(any());
        verify(storage, never()).delete(any());
    }

    @Test
    void replacementDeletesOldObjectOnlyAfterCommit() throws Exception {
        user.changeProfilePhotoKey(KEY);
        service.upload(user.getEmail(), photo());
        var order = inOrder(storage, users, transactions);
        order.verify(storage).store(eq(NEW), any());
        order.verify(users).saveAndFlush(user);
        order.verify(transactions).commit(any());
        order.verify(storage).delete(KEY);
        assertThat(user.getProfilePhotoKey()).isEqualTo(NEW);
    }

    @Test
    void corruptImagePreservesPreviousPhoto() {
        user.changeProfilePhotoKey(KEY);
        assertThatThrownBy(() -> service.upload(user.getEmail(), new MockMultipartFile("photo", new byte[]{1,2,3})))
                .isInstanceOf(InvalidProfilePhotoException.class);
        assertThat(user.getProfilePhotoKey()).isEqualTo(KEY);
        verifyNoInteractions(storage, transactions, keys);
    }

    @Test
    void providerFailurePreservesPreviousKeyAndSanitizesLogs(CapturedOutput output) throws Exception {
        user.changeProfilePhotoKey(KEY);
        doThrow(new IllegalStateException("PRIVATE_PROVIDER_SECRET")).when(storage).store(any(), any());
        var file = photo();
        assertThatThrownBy(() -> service.upload(user.getEmail(), file)).isInstanceOf(ProfilePhotoStorageException.class)
                .hasNoCause().hasMessageNotContaining("PRIVATE_PROVIDER_SECRET");
        assertThat(user.getProfilePhotoKey()).isEqualTo(KEY);
        verifyNoInteractions(transactions);
        verify(storage, never()).delete(any());
        assertThat(output).contains("category=provider_unavailable").doesNotContain("PRIVATE_PROVIDER_SECRET", "PRIVATE_FILENAME");
    }

    @Test
    void commitFailureDeletesNewObjectButNeverOldObject(CapturedOutput output) throws Exception {
        user.changeProfilePhotoKey(KEY);
        doThrow(new IllegalStateException("PRIVATE_SQL")).when(transactions).commit(any());
        doThrow(new IllegalStateException("PRIVATE_CLEANUP")).when(storage).delete(NEW);
        var file = photo();
        assertThatThrownBy(() -> service.upload(user.getEmail(), file)).isInstanceOf(IllegalStateException.class).hasNoCause();
        verify(storage).delete(NEW);
        verify(storage, never()).delete(KEY);
        assertThat(output).contains("category=persistence_failure", "category=provider_failure")
                .doesNotContain("PRIVATE_SQL", "PRIVATE_CLEANUP");
    }

    @Test
    void cleanupFailureKeepsSuccessfulReplacement(CapturedOutput output) throws Exception {
        user.changeProfilePhotoKey(KEY);
        doThrow(new IllegalStateException("PRIVATE_CLEANUP")).when(storage).delete(KEY);
        assertThat(service.upload(user.getEmail(), photo()).profilePhotoUrl()).endsWith(NEW + ".jpg");
        assertThat(user.getProfilePhotoKey()).isEqualTo(NEW);
        assertThat(output).contains("operation=replacement", "operation=cleanup").doesNotContain("PRIVATE_CLEANUP");
    }

    @Test
    void removalIsIdempotentAndCleanupFailureDoesNotRestoreKey() {
        user.changeProfilePhotoKey(KEY);
        doThrow(new IllegalStateException("PRIVATE_CLEANUP")).when(storage).delete(KEY);
        assertThat(service.remove(user.getEmail()).profilePhotoUrl()).isNull();
        assertThat(service.remove(user.getEmail()).profilePhotoUrl()).isNull();
        assertThat(user.getProfilePhotoKey()).isNull();
        verify(storage, times(1)).delete(KEY);
        var order = inOrder(transactions, storage);
        order.verify(transactions).commit(any());
        order.verify(storage).delete(KEY);
    }

    @Test
    void disabledMutationsNeverTouchUserOrProvider() throws Exception {
        var disabled = new AccountPhotoService(properties(false), new ProfilePhotoProcessor(properties(false)),
                keys, storage, users, mapper(), transactions);
        var file = photo();
        assertThatThrownBy(() -> disabled.upload(user.getEmail(), file)).isInstanceOf(ProfilePhotoStorageException.class);
        assertThatThrownBy(() -> disabled.remove(user.getEmail())).isInstanceOf(ProfilePhotoStorageException.class);
        verifyNoInteractions(users, storage, transactions);
    }

    @Test
    void boundedReadRejectsDishonestSizeAndClosesStream() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        var stream = spy(new ByteArrayInputStream(new byte[2097153]));
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(stream);
        assertThatThrownBy(() -> service.upload(user.getEmail(), file))
                .isInstanceOfSatisfying(InvalidProfilePhotoException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(InvalidProfilePhotoException.Reason.TOO_LARGE));
        verify(stream).close();
        verifyNoInteractions(storage, transactions);
    }

    @Test
    void missingPrincipalAccountCannotUploadOrRemove() throws Exception {
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.empty());
        when(users.findByEmailForPhotoUpdate(user.getEmail())).thenReturn(Optional.empty());
        var file = photo();
        assertThatThrownBy(() -> service.upload(user.getEmail(), file))
                .isInstanceOf(dev.portfolio.finance.exception.auth.InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.remove(user.getEmail()))
                .isInstanceOf(dev.portfolio.finance.exception.auth.InvalidCredentialsException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsMissingEmptyOversizedAndUnreadablePart() throws Exception {
        assertThatThrownBy(() -> service.upload(user.getEmail(), null)).isInstanceOf(InvalidProfilePhotoException.class);
        assertThatThrownBy(() -> service.upload(user.getEmail(), new MockMultipartFile("photo", new byte[0])))
                .isInstanceOf(InvalidProfilePhotoException.class);
        var file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(2097153L);
        assertThatThrownBy(() -> service.upload(user.getEmail(), file)).isInstanceOf(InvalidProfilePhotoException.class);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenThrow(new IOException("PRIVATE_PATH"));
        assertThatThrownBy(() -> service.upload(user.getEmail(), file)).isInstanceOf(InvalidProfilePhotoException.class).hasNoCause();
        verifyNoInteractions(storage, transactions);
    }
}
