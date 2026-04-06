package org.peoplemesh.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "peoplemesh")
public interface AppConfig {

    ConsentTokenConfig consentToken();
    RateLimitConfig rateLimit();
    MatchingConfig matching();
    RetentionConfig retention();
    EmbeddingConfig embedding();

    interface ConsentTokenConfig {
        String secret();

        @WithDefault("300")
        int ttlSeconds();
    }

    interface RateLimitConfig {
        IpRateLimit ip();
        UserRateLimit user();
        ConnectionRateLimit connections();

        interface IpRateLimit {
            @WithDefault("100")
            int maxRequests();

            @WithDefault("60")
            int windowSeconds();
        }

        interface UserRateLimit {
            @WithDefault("30")
            int maxRequests();

            @WithDefault("60")
            int windowSeconds();
        }

        interface ConnectionRateLimit {
            @WithDefault("10")
            int maxPerDay();
        }
    }

    interface MatchingConfig {
        @WithDefault("50")
        int candidatePoolSize();

        @WithDefault("20")
        int resultLimit();

        @WithDefault("0.1")
        double decayLambda();
    }

    interface RetentionConfig {
        @WithDefault("12")
        int inactiveMonths();

        @WithDefault("30")
        int purgeDays();
    }

    interface EmbeddingConfig {
        @WithDefault("noop")
        String provider();

        @WithDefault("1536")
        int dimension();

        OpenAIConfig openai();
        CohereConfig cohere();

        interface OpenAIConfig {
            java.util.Optional<String> apiKey();

            @WithDefault("text-embedding-3-small")
            String model();
        }

        interface CohereConfig {
            java.util.Optional<String> apiKey();

            @WithDefault("embed-english-v3.0")
            String model();
        }
    }
}
