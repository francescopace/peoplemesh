package org.peoplemesh.service;

import org.peoplemesh.domain.dto.ProfileSchema;
import java.util.Optional;

public interface ProfileStructuringLlm {
    
    /**
     * Parses the structured CV content (e.g. Markdown from Docling) 
     * and maps it to a ProfileSchema.
     *
     * @param cvContent The structured CV content
     * @return The extracted ProfileSchema, or empty if extraction failed
     */
    Optional<ProfileSchema> extractProfile(String cvContent);
}
