package org.peoplemesh.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.dto.AtsIngestRequestDto;
import org.peoplemesh.domain.dto.AtsIngestResultDto;
import org.peoplemesh.service.AtsIngestService;

@ApplicationScoped
public class AtsIngestApplicationService {

    @Inject
    AtsIngestService atsIngestService;

    public AtsIngestResultDto ingestJobs(AtsIngestRequestDto request) {
        return atsIngestService.ingestJobs(request);
    }
}
