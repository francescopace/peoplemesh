package org.peoplemesh.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.AtsIngestRequestDto;
import org.peoplemesh.domain.dto.AtsIngestResultDto;
import org.peoplemesh.service.AtsIngestService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtsIngestApplicationServiceTest {

    @Mock
    AtsIngestService atsIngestService;

    @InjectMocks
    AtsIngestApplicationService service;

    @Test
    void ingestJobs_delegatesToDomainService() {
        AtsIngestRequestDto request = new AtsIngestRequestDto();
        AtsIngestResultDto expected = new AtsIngestResultDto(2, 1, List.of());
        when(atsIngestService.ingestJobs(request)).thenReturn(expected);

        AtsIngestResultDto result = service.ingestJobs(request);

        assertSame(expected, result);
        verify(atsIngestService).ingestJobs(request);
    }
}
