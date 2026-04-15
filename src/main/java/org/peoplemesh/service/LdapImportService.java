package org.peoplemesh.service;

import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.ldap.sdk.*;
import com.unboundid.ldap.sdk.controls.SimplePagedResultsControl;
import com.unboundid.util.ssl.SSLUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.LdapImportResult;
import org.peoplemesh.domain.dto.LdapUserPreview;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;

import javax.net.ssl.SSLSocketFactory;
import java.security.GeneralSecurityException;
import java.util.*;

@ApplicationScoped
public class LdapImportService {

    private static final Logger LOG = Logger.getLogger(LdapImportService.class);
    private static final String PROVIDER = "ldap-ipa";
    private static final String[] SEARCH_ATTRS = {
            "uid", "cn", "displayName", "givenName", "sn", "mail",
            "title", "ou", "l", "co"
    };

    @Inject
    AppConfig appConfig;

    @Inject
    ProfileService profileService;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AuditService auditService;

    public List<LdapUserPreview> preview(int limit) {
        validateConfig();
        List<LdapUserPreview> result = new ArrayList<>();
        try (LDAPConnection conn = connect()) {
            ASN1OctetString resumeCookie = null;
            outer:
            while (true) {
                SearchRequest req = buildSearchRequest(
                        new Control[]{new SimplePagedResultsControl(Math.min(limit, appConfig.ldap().pageSize()), resumeCookie)});
                SearchResult searchResult = conn.search(req);
                for (SearchResultEntry entry : searchResult.getSearchEntries()) {
                    if (result.size() >= limit) break outer;
                    LdapUserPreview preview = entryToPreview(entry);
                    if (preview != null) result.add(preview);
                }
                resumeCookie = SimplePagedResultsControl.get(searchResult) != null
                        ? SimplePagedResultsControl.get(searchResult).getCookie()
                        : null;
                if (resumeCookie == null || resumeCookie.getValueLength() == 0) break;
            }
        } catch (LDAPException e) {
            throw new IllegalStateException("LDAP preview failed: " + e.getMessage(), e);
        }
        return result;
    }

    public LdapImportResult importFromLdap(UUID adminUserId) {
        validateConfig();
        long start = System.currentTimeMillis();
        int created = 0, updated = 0, skipped = 0, errors = 0;
        List<String> errorDetails = new ArrayList<>();

        try (LDAPConnection conn = connect()) {
            ASN1OctetString resumeCookie = null;
            while (true) {
                SearchRequest req = buildSearchRequest(
                        new Control[]{new SimplePagedResultsControl(appConfig.ldap().pageSize(), resumeCookie)});
                SearchResult searchResult = conn.search(req);
                for (SearchResultEntry entry : searchResult.getSearchEntries()) {
                    String uid = entry.getAttributeValue("uid");
                    String mail = entry.getAttributeValue("mail");
                    if (uid == null || uid.isBlank() || mail == null || mail.isBlank()) {
                        skipped++;
                        continue;
                    }
                    try {
                        boolean isNew = importSingleUser(entry, uid, mail);
                        if (isNew) created++;
                        else updated++;
                    } catch (Exception e) {
                        errors++;
                        String msg = "uid=" + uid + ": " + e.getMessage();
                        errorDetails.add(msg);
                        LOG.warnf("LDAP import error for %s: %s", uid, e.getMessage());
                    }
                }
                resumeCookie = SimplePagedResultsControl.get(searchResult) != null
                        ? SimplePagedResultsControl.get(searchResult).getCookie()
                        : null;
                if (resumeCookie == null || resumeCookie.getValueLength() == 0) break;
            }
        } catch (LDAPException e) {
            throw new IllegalStateException("LDAP import failed: " + e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - start;
        LOG.infof("LDAP import completed: created=%d updated=%d skipped=%d errors=%d durationMs=%d",
                created, updated, skipped, errors, duration);
        auditService.log(adminUserId, "LDAP_IMPORT", "admin_ldap_import", null,
                "{\"created\":%d,\"updated\":%d,\"skipped\":%d,\"errors\":%d}".formatted(created, updated, skipped, errors));
        return new LdapImportResult(created, updated, skipped, errors, duration, errorDetails);
    }

    public int generateEmbeddings(UUID adminUserId) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) MeshNode.getEntityManager()
                .createNativeQuery("""
                        SELECT mn.id FROM mesh.mesh_node mn
                        JOIN identity.user_identity ui ON ui.node_id = mn.id
                        WHERE ui.oauth_provider = :provider
                          AND mn.node_type = 'USER'
                          AND mn.embedding IS NULL
                        """)
                .setParameter("provider", PROVIDER)
                .getResultList();

        List<UUID> nodeIds = rows.stream()
                .map(r -> (UUID) (r instanceof Object[] arr ? arr[0] : r))
                .toList();

        int count = 0;
        for (UUID nodeId : nodeIds) {
            try {
                generateSingleEmbedding(nodeId);
                count++;
            } catch (Exception e) {
                LOG.warnf("Embedding generation failed for nodeId=%s: %s", nodeId, e.getMessage());
            }
        }
        LOG.infof("LDAP embedding generation: %d of %d nodes processed", count, nodeIds.size());
        auditService.log(adminUserId, "LDAP_GENERATE_EMBEDDINGS", "admin_ldap_embeddings", null,
                "{\"generated\":%d}".formatted(count));
        return count;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    boolean importSingleUser(SearchResultEntry entry, String uid, String mail) {
        String cn = entry.getAttributeValue("cn");
        String displayName = entry.getAttributeValue("displayName");
        String givenName = entry.getAttributeValue("givenName");
        String sn = entry.getAttributeValue("sn");
        String title = entry.getAttributeValue("title");
        String city = entry.getAttributeValue("l");
        String country = entry.getAttributeValue("co");

        String effectiveDisplayName = displayName != null ? displayName : cn;

        Optional<UserIdentity> existing = UserIdentity.findByOauth(PROVIDER, uid);
        MeshNode node;
        boolean isNew;

        if (existing.isPresent()) {
            node = MeshNode.<MeshNode>findByIdOptional(existing.get().nodeId).orElse(null);
            if (node == null) {
                node = createUserNode(mail);
                existing.get().nodeId = node.id;
                existing.get().persist();
            }
            isNew = false;
        } else {
            MeshNode existingByEmail = MeshNode.findUserByExternalId(mail).orElse(null);
            if (existingByEmail != null) {
                node = existingByEmail;
            } else {
                node = createUserNode(mail);
            }

            UserIdentity identity = new UserIdentity();
            identity.nodeId = node.id;
            identity.oauthProvider = PROVIDER;
            identity.oauthSubject = uid;
            identity.persist();
            isNew = existingByEmail == null;
        }

        profileService.upsertProfileFromProvider(
                node.id, PROVIDER, effectiveDisplayName, givenName, sn, mail, null, null, null);

        if (title != null && !title.isBlank()) {
            node = MeshNode.<MeshNode>findByIdOptional(node.id).orElse(node);
            if (node.structuredData == null) node.structuredData = new LinkedHashMap<>();
            node.structuredData.put("job_title", title);
            if (city != null && !city.isBlank()) node.structuredData.put("city", city);
            if (country != null && !country.isBlank()) node.country = country;
            node.persist();
        }

        return isNew;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void generateSingleEmbedding(UUID nodeId) {
        MeshNode node = MeshNode.<MeshNode>findByIdOptional(nodeId).orElse(null);
        if (node == null) return;
        String text = EmbeddingTextBuilder.buildText(node);
        float[] embedding = embeddingService.generateEmbedding(text);
        if (embedding != null) {
            node.embedding = embedding;
            node.searchable = true;
            node.persist();
        }
    }

    private MeshNode createUserNode(String email) {
        MeshNode n = new MeshNode();
        n.id = UUID.randomUUID();
        n.createdBy = n.id;
        n.nodeType = NodeType.USER;
        n.title = "Anonymous";
        n.description = "";
        n.externalId = email;
        n.tags = new ArrayList<>();
        n.structuredData = new LinkedHashMap<>();
        n.searchable = true;
        n.persist();
        return n;
    }

    private LdapUserPreview entryToPreview(SearchResultEntry entry) {
        String uid = entry.getAttributeValue("uid");
        if (uid == null || uid.isBlank()) return null;
        String cn = entry.getAttributeValue("cn");
        String displayName = entry.getAttributeValue("displayName");
        return new LdapUserPreview(
                uid,
                displayName != null ? displayName : cn,
                entry.getAttributeValue("mail"),
                entry.getAttributeValue("givenName"),
                entry.getAttributeValue("sn"),
                entry.getAttributeValue("title"),
                entry.getAttributeValue("ou"),
                entry.getAttributeValue("co"),
                entry.getAttributeValue("l")
        );
    }

    private void validateConfig() {
        AppConfig.LdapConfig ldap = appConfig.ldap();
        if (ldap.url().isEmpty() || ldap.url().get().isBlank()) {
            throw new IllegalStateException("LDAP URL is not configured (peoplemesh.ldap.url)");
        }
        if (ldap.bindDn().isEmpty() || ldap.bindDn().get().isBlank()) {
            throw new IllegalStateException("LDAP bind DN is not configured (peoplemesh.ldap.bind-dn)");
        }
        if (ldap.bindPassword().isEmpty() || ldap.bindPassword().get().isBlank()) {
            throw new IllegalStateException("LDAP bind password is not configured (peoplemesh.ldap.bind-password)");
        }
    }

    private LDAPConnection connect() throws LDAPException {
        AppConfig.LdapConfig ldap = appConfig.ldap();
        String url = ldap.url().orElseThrow();
        boolean useSsl = url.startsWith("ldaps://");
        String host = url.replaceFirst("^ldaps?://", "").replaceFirst(":[0-9]+$", "");
        int defaultPort = useSsl ? 636 : 389;
        int port = defaultPort;
        String portStr = url.replaceFirst("^ldaps?://[^:]+:?", "");
        if (!portStr.isBlank()) {
            try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}
        }

        int timeoutMs = ldap.connectTimeoutSeconds() * 1000;
        LDAPConnectionOptions options = new LDAPConnectionOptions();
        options.setConnectTimeoutMillis(timeoutMs);
        options.setResponseTimeoutMillis(timeoutMs);

        LDAPConnection conn;
        if (useSsl) {
            try {
                SSLUtil sslUtil = new SSLUtil((javax.net.ssl.TrustManager) null);
                SSLSocketFactory sf = sslUtil.createSSLSocketFactory();
                conn = new LDAPConnection(sf, options, host, port);
            } catch (GeneralSecurityException e) {
                throw new LDAPException(ResultCode.CONNECT_ERROR, "SSL setup failed: " + e.getMessage(), e);
            }
        } else {
            conn = new LDAPConnection(options, host, port);
        }

        conn.bind(ldap.bindDn().orElseThrow(), ldap.bindPassword().orElseThrow());
        return conn;
    }

    private SearchRequest buildSearchRequest(Control[] controls) throws LDAPSearchException {
        AppConfig.LdapConfig ldap = appConfig.ldap();
        try {
            SearchRequest req = new SearchRequest(
                    ldap.userBase(),
                    SearchScope.SUB,
                    ldap.userFilter(),
                    SEARCH_ATTRS
            );
            req.setControls(controls);
            return req;
        } catch (LDAPException e) {
            throw new LDAPSearchException(e);
        }
    }
}
