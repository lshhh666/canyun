package com.cloudmeal.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AiPropertiesTest {

    @Test
    void shouldBindAiConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("cloudmeal.ai.base-url", "https://example.com/v1")
                .withProperty("cloudmeal.ai.model", "test-model")
                .withProperty("cloudmeal.ai.embedding-model", "embedding-3")
                .withProperty("cloudmeal.ai.embedding-dimensions", "256")
                .withProperty("cloudmeal.ai.retrieval-top-k", "3")
                .withProperty("cloudmeal.ai.retrieval-min-score", "0.70")
                .withProperty("cloudmeal.ai.api-key", "test-secret-key")
                .withProperty("cloudmeal.ai.connect-timeout", "3000")
                .withProperty("cloudmeal.ai.read-timeout", "60000")
                .withProperty("cloudmeal.ai.turn-timeout-seconds", "180")
                .withProperty("cloudmeal.ai.max-tokens", "1024");

        AiProperties properties = Binder.get(environment)
                .bind("cloudmeal.ai", Bindable.of(AiProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertThat(properties.getBaseUrl()).isEqualTo("https://example.com/v1");
        assertThat(properties.getModel()).isEqualTo("test-model");
        assertThat(properties.getEmbeddingModel()).isEqualTo("embedding-3");
        assertThat(properties.getEmbeddingDimensions()).isEqualTo(256);
        assertThat(properties.getRetrievalTopK()).isEqualTo(3);
        assertThat(properties.getRetrievalMinScore()).isEqualTo(0.70D);
        assertThat(properties.getApiKey()).isEqualTo("test-secret-key");
        assertThat(properties.getConnectTimeout()).isEqualTo(3000);
        assertThat(properties.getReadTimeout()).isEqualTo(60000);
        assertThat(properties.getTurnTimeoutSeconds()).isEqualTo(180);
        assertThat(properties.getMaxTokens()).isEqualTo(1024);
        assertThat(properties.toString()).doesNotContain("test-secret-key");
    }
}
