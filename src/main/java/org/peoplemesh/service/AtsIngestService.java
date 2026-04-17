package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.AtsIngestRequestDto;
import org.peoplemesh.domain.dto.AtsIngestResultDto;
import org.peoplemesh.domain.dto.AtsJobEntryDto;
import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.service.JobService.AtsJobPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AtsIngestService {

    private static final Logger LOG = Logger.getLogger(AtsIngestService.class);
    private static final int MAX_BATCH_SIZE = 100;

    @Inject
    JobService jobService;

    public AtsIngestResultDto ingestJobs(AtsIngestRequestDto request) {
        validateRequest(request);
        List<JobPostingDto> upserted = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();
        for (AtsJobEntryDto entry : request.jobs) {
            try {
                AtsJobPayload payload = toPayload(entry);
                JobPostingDto result = jobService.upsertFromAts(request.ownerUserId, entry.externalId, payload);
                upserted.add(result);
            } catch (Exception e) {
                LOG.warnf("ATS ingest failed for external_id=%s", entry.externalId);
                errors.add(Map.of(
                        "external_id", entry.externalId != null ? entry.externalId : "",
                        "error", "Failed to upsert job"
                ));
            }
        }
        LOG.infof("ATS ingest: %d upserted, %d failed", upserted.size(), errors.size());
        return new AtsIngestResultDto(upserted.size(), errors.size(), errors);
    }

    private void validateRequest(AtsIngestRequestDto request) {
        if (request == null) {
            throw new ValidationBusinessException("Request body is required");
        }
        if (request.jobs == null || request.jobs.isEmpty()) {
            throw new ValidationBusinessException("jobs array is required");
        }
        if (request.jobs.size() > MAX_BATCH_SIZE) {
            throw new ValidationBusinessException("batch size exceeds maximum of " + MAX_BATCH_SIZE);
        }
        if (request.ownerUserId == null) {
            throw new ValidationBusinessException("owner_user_id is required");
        }
    }

    private static AtsJobPayload toPayload(AtsJobEntryDto entry) {
        return new AtsJobPayload(
                entry.title,
                entry.description,
                entry.requirementsText,
                entry.skillsRequired,
                entry.workMode,
                entry.employmentType,
                entry.country,
                entry.status,
                entry.externalUrl
        );
    }
}
