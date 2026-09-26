package dev.portfolio.finance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BaseEntityTest {
    @Test
    void lifecycleTimestampsMatchDatabasePrecision() {
        LocalDateTime creation = LocalDateTime.parse("2026-09-26T18:39:17.359101504");
        LocalDateTime update = LocalDateTime.parse("2026-09-26T18:40:18.123456789");
        LocalDateTime expectedCreation = LocalDateTime.parse("2026-09-26T18:39:17.359101");
        LocalDateTime expectedUpdate = LocalDateTime.parse("2026-09-26T18:40:18.123456");
        BaseEntity entity = new BaseEntity() {};

        try (var clock = mockStatic(LocalDateTime.class)) {
            clock.when(LocalDateTime::now).thenReturn(creation, update);
            entity.onCreate();
            assertThat(entity.getCreatedAt()).isEqualTo(expectedCreation);
            assertThat(entity.getUpdatedAt()).isEqualTo(expectedCreation);

            entity.onUpdate();
            assertThat(entity.getCreatedAt()).isEqualTo(expectedCreation);
            assertThat(entity.getUpdatedAt()).isEqualTo(expectedUpdate);
        }
    }
}
