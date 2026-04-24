package org.peoplemesh.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "peoplemesh")
public interface AppConfig {

    ProblemsConfig problems();
    ConsentTokenConfig consentToken();
    SessionConfig session();
    OAuthConfig oauth();
    OidcProviders oidc();
    MatchingConfig matching();
    SearchConfig search();
    RetentionConfig retention();
    CvImportConfig cvImport();
    NotificationConfig notification();
    ClusteringConfig clustering();
    MaintenanceConfig maintenance();
    EntitlementsConfig entitlements();
    SkillsConfig skills();
    FrontendConfig frontend();
    OrganizationConfig organization();

    interface ProblemsConfig {
        @WithDefault("about:blank")
        String baseUri();
    }

    interface MaintenanceConfig {
        java.util.Optional<String> apiKey();

        java.util.Optional<String> allowedCidrs();
    }

    interface EntitlementsConfig {
        java.util.Optional<java.util.List<String>> isAdmin();
    }

    interface CvImportConfig {
        @WithDefault("5242880")
        long maxFileSize();
    }

    interface ConsentTokenConfig {
        String secret();

        @WithDefault("300")
        int ttlSeconds();
    }

    interface SessionConfig {
        String secret();
    }

    interface OAuthConfig {
        String stateSecret();
    }

    interface OidcProviders {
        OidcProviderCreds google();
        OidcProviderCreds microsoft();
        OidcProviderCreds github();
    }

    interface OidcProviderCreds {
        @WithDefault("none")
        String clientId();

        @WithDefault("none")
        String clientSecret();
    }

    interface MatchingConfig {
        @WithDefault("50")
        int candidatePoolSize();
    }

    interface RetentionConfig {
        @WithDefault("12")
        int inactiveMonths();
    }

    interface NotificationConfig {
        @WithDefault("true")
        boolean enabled();

        @WithDefault("true")
        boolean dryRun();

        @WithDefault("[PeopleMesh]")
        String subjectPrefix();
    }

    interface ClusteringConfig {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("20")
        int k();

        @WithDefault("5")
        int minClusterSize();

        @WithDefault("0.4")
        double maxCentroidDistance();
    }

    interface SkillsConfig {
        @WithDefault("0.80")
        double matchThreshold();

        @WithDefault("0.75")
        double reconciliationThreshold();
    }

    interface SearchConfig {
        @WithDefault("100")
        int candidatePoolSize();
    }

    interface FrontendConfig {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("src/main/web")
        String dir();

        java.util.Optional<String> origin();
    }

    interface OrganizationConfig {
        java.util.Optional<String> name();
        java.util.Optional<String> contactEmail();
        java.util.Optional<String> dpoName();
        java.util.Optional<String> dpoEmail();
        java.util.Optional<String> dataLocation();
        java.util.Optional<String> governingLaw();
    }

}
