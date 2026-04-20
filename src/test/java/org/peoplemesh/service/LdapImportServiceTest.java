package org.peoplemesh.service;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.SearchResultEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LdapImportServiceTest {

    @Mock
    AppConfig appConfig;
    @Mock
    AppConfig.LdapConfig ldapConfig;
    @Mock
    ProfileService profileService;
    @Mock
    EmbeddingService embeddingService;
    @Mock
    AuditService auditService;
    @Mock
    NodeRepository nodeRepository;
    @Mock
    UserIdentityRepository userIdentityRepository;

    @InjectMocks
    LdapImportService service;

    @Test
    void preview_whenLdapUrlMissing_failsFastWithoutConnection() {
        when(appConfig.ldap()).thenReturn(ldapConfig);
        when(ldapConfig.url()).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.preview(10));

        assertTrue(error.getMessage().contains("LDAP URL is not configured"));
    }

    @Test
    void importFromLdap_whenBindPasswordMissing_failsFastWithoutConnection() {
        when(appConfig.ldap()).thenReturn(ldapConfig);
        when(ldapConfig.url()).thenReturn(Optional.of("ldap://ldap.peoplemesh.test:389"));
        when(ldapConfig.bindDn()).thenReturn(Optional.of("cn=admin,dc=peoplemesh,dc=test"));
        when(ldapConfig.bindPassword()).thenReturn(Optional.empty());

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.importFromLdap(UUID.randomUUID()));

        assertTrue(error.getMessage().contains("LDAP bind password is not configured"));
    }

    @Test
    void generateEmbeddingBatch_whenNoNodes_returnsZero() {
        when(nodeRepository.findByIds(List.of())).thenReturn(List.of());

        int generated = service.generateEmbeddingBatch(List.of());

        assertEquals(0, generated);
        verify(embeddingService, times(0)).generateEmbeddings(any());
    }

    @Test
    void generateEmbeddingBatch_persistsOnlyNodesWithNonNullEmbeddings() {
        MeshNode first = new MeshNode();
        first.id = UUID.randomUUID();
        first.nodeType = NodeType.USER;
        first.title = "First";
        first.description = "First node";
        MeshNode second = new MeshNode();
        second.id = UUID.randomUUID();
        second.nodeType = NodeType.USER;
        second.title = "Second";
        second.description = "Second node";
        when(nodeRepository.findByIds(List.of(first.id, second.id))).thenReturn(List.of(first, second));
        when(embeddingService.generateEmbeddings(any())).thenReturn(Arrays.asList(new float[]{1f, 2f}, null));

        int generated = service.generateEmbeddingBatch(List.of(first.id, second.id));

        assertEquals(2, generated);
        assertEquals(2, first.embedding.length);
        assertTrue(first.searchable);
        assertTrue(second.searchable);
        assertNull(second.embedding);
        verify(nodeRepository).persist(first);
        verify(nodeRepository, times(1)).persist(any(MeshNode.class));
    }

    @Test
    void importSingleUser_existingEmail_createsIdentityAndUpdatesProfileData() {
        SearchResultEntry entry = new SearchResultEntry(
                1,
                "uid=u1,dc=peoplemesh,dc=test",
                List.of(
                        new Attribute("uid", "u1"),
                        new Attribute("mail", "u1@peoplemesh.test"),
                        new Attribute("displayName", "User One"),
                        new Attribute("givenName", "User"),
                        new Attribute("sn", "One"),
                        new Attribute("title", "Engineer"),
                        new Attribute("l", "Milan"),
                        new Attribute("co", "IT")
                ));

        MeshNode existingNode = new MeshNode();
        existingNode.id = UUID.randomUUID();
        existingNode.externalId = "u1@peoplemesh.test";

        when(userIdentityRepository.findByProviderAndSubject("ldap-ipa", "u1")).thenReturn(Optional.empty());
        when(nodeRepository.findUserByExternalId("u1@peoplemesh.test")).thenReturn(Optional.of(existingNode));
        when(nodeRepository.findById(existingNode.id)).thenReturn(Optional.of(existingNode));

        boolean isNew = service.importSingleUser(entry, "u1", "u1@peoplemesh.test");

        assertFalse(isNew);
        assertEquals("IT", existingNode.country);
        verify(userIdentityRepository).persist(any(UserIdentity.class));
        verify(profileService).upsertProfileFromProvider(
                eq(existingNode.id),
                eq("ldap-ipa"),
                eq("User One"),
                eq("User"),
                eq("One"),
                eq("u1@peoplemesh.test"),
                eq(null),
                eq(null),
                eq(null));
        verify(nodeRepository).persist(existingNode);
    }
}
