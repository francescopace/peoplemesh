package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.OperationTimingStatsDto;
import org.peoplemesh.domain.dto.SystemStatisticsDto;
import org.peoplemesh.domain.dto.TimingStatisticsDto;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.SystemStatisticsService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemResourceTest {

    @Mock
    SystemStatisticsService systemStatisticsService;
    @Mock
    CurrentUserService currentUserService;

    @InjectMocks
    SystemResource resource;

    @Test
    void getStatistics_nonAdmin_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(systemStatisticsService.loadStatisticsForUser(userId))
                .thenThrow(new ForbiddenBusinessException("Missing entitlement is_admin"));

        assertThrows(ForbiddenBusinessException.class, () -> resource.getStatistics());
    }

    @Test
    void getStatistics_admin_returns200() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        SystemStatisticsDto statistics = new SystemStatisticsDto(
                1L, 2L, 3L, 4L,
                new TimingStatisticsDto(
                        new OperationTimingStatsDto(0, 0, 0, 0),
                        new OperationTimingStatsDto(0, 0, 0, 0),
                        new OperationTimingStatsDto(0, 0, 0, 0),
                        new OperationTimingStatsDto(0, 0, 0, 0)
                )
        );
        when(systemStatisticsService.loadStatisticsForUser(userId)).thenReturn(statistics);

        Response response = resource.getStatistics();

        assertEquals(200, response.getStatus());
    }
}
