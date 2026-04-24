package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.IngestResultDto;
import org.peoplemesh.domain.dto.NodeIngestEntryDto;
import org.peoplemesh.domain.dto.NodesIngestRequestDto;
import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.repository.NodeRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock
    JobService jobService;

    @Mock
    NodeRepository nodeRepository;

    @Mock
    EmbeddingService embeddingService;

    @InjectMocks
    IngestService service;

    @Test
    void ingestNodes_nullRequest_throwsValidationError() {
        assertThrows(ValidationBusinessException.class, () -> service.ingestNodes(null));
    }

    @Test
    void ingestNodes_emptyNodes_throwsValidationError() {
        NodesIngestRequestDto request = new NodesIngestRequestDto();
        request.nodes = List.of();
        assertThrows(ValidationBusinessException.class, () -> service.ingestNodes(request));
    }

    @Test
    void ingestNodes_validRequest_returnsUpsertCounters() {
        NodesIngestRequestDto request = new NodesIngestRequestDto();
        request.nodes = List.of(job("ext-1"));

        when(jobService.upsertFromIngest(eq("workday"), eq("ext-1"), any())).thenReturn(mockPosting());

        IngestResultDto result = service.ingestNodes(request);
        assertEquals(1, result.upserted());
        assertEquals(0, result.failed());
    }

    @Test
    void ingestNodes_whenUpsertThrows_recordsFailedEntry() {
        NodesIngestRequestDto request = new NodesIngestRequestDto();
        request.nodes = List.of(job("ext-fail"));
        when(jobService.upsertFromIngest(eq("workday"), eq("ext-fail"), any())).thenThrow(new RuntimeException("boom"));

        IngestResultDto result = service.ingestNodes(request);
        assertEquals(0, result.upserted());
        assertEquals(1, result.failed());
    }

    private static NodeIngestEntryDto job(String externalId) {
        NodeIngestEntryDto entry = new NodeIngestEntryDto();
        entry.nodeType = "JOB";
        entry.source = "workday";
        entry.externalId = externalId;
        entry.title = "Engineer";
        entry.description = "Desc";
        entry.requirementsText = "Req";
        entry.skillsRequired = List.of("java");
        entry.workMode = "REMOTE";
        entry.employmentType = "EMPLOYED";
        entry.country = "IT";
        entry.status = "OPEN";
        return entry;
    }

    private static JobPostingDto mockPosting() {
        return new JobPostingDto(
                UUID.randomUUID(),
                "Engineer",
                "Desc",
                "Req",
                List.of("java"),
                WorkMode.REMOTE,
                EmploymentType.EMPLOYED,
                "IT",
                null,
                null,
                null,
                null
        );
    }
}
