package org.peoplemesh.service;

import org.peoplemesh.domain.dto.ProfileSchema;

import java.io.InputStream;
import java.util.UUID;

public interface CvImportProvider {

    String key();

    String source();

    ProfileSchema extractProfile(InputStream content, String fileName, UUID userId);
}
