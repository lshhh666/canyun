package com.cloudmeal.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigurationTest {

    @Test
    void developmentProfileKeepsLocalFallbacks() {
        try (ConfigurableApplicationContext context = start("dev")) {
            Environment environment = context.getEnvironment();
            assertThat(((DefaultListableBeanFactory) context.getBeanFactory()).isAllowCircularReferences()).isTrue();
            assertThat(environment.getProperty("cloudmeal.jwt.admin-secret-key")).isNotBlank();
            assertThat(environment.getProperty("cloudmeal.jwt.user-secret-key")).isNotBlank();
            assertThat(environment.getProperty("cloudmeal.jwt.admin-secret-key"))
                    .isNotEqualTo(environment.getProperty("cloudmeal.jwt.user-secret-key"));
        }
    }

    @Test
    void productionProfileUsesExternalSecretsAndDisablesCircularReferences() {
        try (ConfigurableApplicationContext context = start("prod",
                "--CLOUDMEAL_JWT_ADMIN_SECRET=test-admin-secret",
                "--CLOUDMEAL_JWT_USER_SECRET=test-user-secret")) {
            Environment environment = context.getEnvironment();
            assertThat(environment.acceptsProfiles("dev")).isFalse();
            assertThat(((DefaultListableBeanFactory) context.getBeanFactory()).isAllowCircularReferences()).isFalse();
            assertThat(environment.getProperty("cloudmeal.jwt.admin-secret-key"))
                    .isEqualTo("test-admin-secret");
            assertThat(environment.getProperty("cloudmeal.jwt.user-secret-key"))
                    .isEqualTo("test-user-secret");
        }
    }

    private ConfigurableApplicationContext start(String profile, String... extraArguments) {
        SpringApplication application = new SpringApplication(EmptyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        String[] arguments = new String[extraArguments.length + 2];
        arguments[0] = "--spring.profiles.active=" + profile;
        arguments[1] = "--spring.config.location=classpath:/application.yml";
        System.arraycopy(extraArguments, 0, arguments, 2, extraArguments.length);
        return application.run(arguments);
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }
}
