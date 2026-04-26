package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.AuthProvidersDto;
import org.peoplemesh.domain.dto.InfoResponse;
import org.peoplemesh.service.OAuthLoginService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InfoResourceTest {

    @Mock
    AppConfig appConfig;

    @Mock
    AppConfig.OrganizationConfig organizationConfig;

    @Mock
    OAuthLoginService oAuthLoginService;

    @InjectMocks
    InfoResource resource;

    @Test
    void info_returnsOrganizationMetadataAndAuthProviders() {
        when(appConfig.organization()).thenReturn(organizationConfig);
        when(organizationConfig.name()).thenReturn(Optional.of("Acme Corp"));
        when(organizationConfig.contactEmail()).thenReturn(Optional.of("contact@acme.test"));
        when(organizationConfig.dpoName()).thenReturn(Optional.of("Jane DPO"));
        when(organizationConfig.dpoEmail()).thenReturn(Optional.of("dpo@acme.test"));
        when(organizationConfig.dataLocation()).thenReturn(Optional.of("EU"));
        when(organizationConfig.governingLaw()).thenReturn(Optional.of("Italian law"));
        AuthProvidersDto providers = new AuthProvidersDto(
                List.of("google"),
                List.of("google", "github"));
        when(oAuthLoginService.providers()).thenReturn(providers);

        Response response = resource.info();

        assertEquals(200, response.getStatus());
        InfoResponse body = (InfoResponse) response.getEntity();
        assertEquals("Acme Corp", body.organizationName());
        assertEquals("contact@acme.test", body.contactEmail());
        assertEquals("Jane DPO", body.dpoName());
        assertEquals("dpo@acme.test", body.dpoEmail());
        assertEquals("EU", body.dataLocation());
        assertEquals("Italian law", body.governingLaw());
        assertEquals(providers, body.authProviders());
    }
}
