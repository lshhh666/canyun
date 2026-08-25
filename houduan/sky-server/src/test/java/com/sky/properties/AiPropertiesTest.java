package com.sky.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class AiPropertiesTest {

    @Test
    void shouldBindAiConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sky.ai.base-url", "https://example.com/v1")
                .withProperty("sky.ai.model", "test-model")
                .withProperty("sky.ai.api-key", "test-secret-key");

        AiProperties properties = Binder.get(environment)
                .bind("sky.ai", Bindable.of(AiProperties.class))
                .orElseThrow(IllegalStateException::new);

        assertThat(properties.getBaseUrl()).isEqualTo("https://example.com/v1");
        assertThat(properties.getModel()).isEqualTo("test-model");
        assertThat(properties.getApiKey()).isEqualTo("test-secret-key");
        assertThat(properties.toString()).doesNotContain("test-secret-key");
    }
}
