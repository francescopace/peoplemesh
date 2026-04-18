package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.AtsIngestRequestDto;
import org.peoplemesh.domain.dto.AtsIngestResultDto;
import org.peoplemesh.domain.dto.AtsJobEntryDto;
import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.exception.ValidationBusinessException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtsIngestServiceTest {

    @Mock
    JobService jobService;

    @InjectMocks
    AtsIngestService service;

    @Test
    void ingestJobs_nullRequest_throwsValidationError() {
        assertThrows(ValidationBusinessException.class, () -> service.ingestJobs(null));
    }

    @Test
    void ingestJobs_emptyJobs_throwsValidationError() {
        AtsIngestRequestDto request = new AtsIngestRequestDto();
        request.ownerUserId = UUID.randomUUID();
        request.jobs = List.of();
        assertThrows(ValidationBusinessException.class, () -> service.ingestJobs(request));
    }

    @Test
    void ingestJobs_happyPath_countsUpserts() {
        UUID owner = UUID.randomUUID();
        AtsIngestRequestDto request = new AtsIngestRequestDto();
        request.ownerUserId = owner;
        request.jobs = List.of(job("ext-1"));

        when(jobService.upsertFromAts(eq(owner), eq("ext-1"), any())).thenReturn(mockPosting());

        AtsIngestResultDto result = service.ingestJobs(request);
        assertEquals(1, result.upserted());
        assertEquals(0, result.failed());
    }

    @Test
    void ingestJobs_failedEntry_isReported() {
        UUID owner = UUID.randomUUID();
        AtsIngestRequestDto request = new AtsIngestRequestDto();
        request.ownerUserId = owner;
        request.jobs = List.of(job("ext-fail"));
        when(jobService.upsertFromAts(eq(owner), eq("ext-fail"), any())).thenThrow(new RuntimeException("boom"));

        AtsIngestResultDto result = service.ingestJobs(request);
        assertEquals(0, result.upserted());
        assertEquals(1, result.failed());
    }

    private static AtsJobEntryDto job(String externalId) {
        AtsJobEntryDto entry = new AtsJobEntryDto();
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
