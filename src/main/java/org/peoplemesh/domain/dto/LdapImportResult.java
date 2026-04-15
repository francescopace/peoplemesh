package org.peoplemesh.domain.dto;

import java.util.List;

public record LdapImportResult(
        int created, int updated, int skipped, int errors,
        long durationMs, List<String> errorDetails
) {}
