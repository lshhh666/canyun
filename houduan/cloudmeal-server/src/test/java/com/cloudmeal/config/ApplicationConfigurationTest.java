package com.cloudmeal.config;

import com.cloudmeal.properties.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationConfigurationTest {

    @Test
    void developmentProfileKeepsLocalFallbacks() {
        String firstAdminSecret;
        String firstUserSecret;
        try (ConfigurableApplicationContext context = start("dev")) {
            JwtProperties jwt = context.getBean(JwtProperties.class);
            assertThat(((DefaultListableBeanFactory) context.getBeanFactory()).isAllowCircularReferences()).isTrue();
            assertThat(jwt.getAdminSecretKey()).isNotBlank();
            assertThat(jwt.getUserSecretKey()).isNotBlank();
            assertThat(jwt.getAdminSecretKey()).isNotEqualTo(jwt.getUserSecretKey());
            firstAdminSecret = jwt.getAdminSecretKey();
            firstUserSecret = jwt.getUserSecretKey();
        }
        try (ConfigurableApplicationContext context = start("dev")) {
            JwtProperties jwt = context.getBean(JwtProperties.class);
            assertThat(jwt.getAdminSecretKey()).isNotEqualTo(firstAdminSecret);
            assertThat(jwt.getUserSecretKey()).isNotEqualTo(firstUserSecret);
        }
    }

    @Test
    void productionProfileUsesExternalSecretsAndDisablesCircularReferences() {
        try (ConfigurableApplicationContext context = start("prod",
                "--CLOUDMEAL_JWT_ADMIN_SECRET=test-admin-secret",
                "--CLOUDMEAL_JWT_USER_SECRET=test-user-secret")) {
            Environment environment = context.getEnvironment();
            JwtProperties jwt = context.getBean(JwtProperties.class);
            assertThat(environment.acceptsProfiles("dev")).isFalse();
            assertThat(((DefaultListableBeanFactory) context.getBeanFactory()).isAllowCircularReferences()).isFalse();
            assertThat(jwt.getAdminSecretKey()).isEqualTo("test-admin-secret");
            assertThat(jwt.getUserSecretKey()).isEqualTo("test-user-secret");
        }
    }

    @Test
    void productionProfileFailsWhenEitherSecretIsMissing() {
        assertThatThrownBy(() -> start("prod", "--CLOUDMEAL_JWT_USER_SECRET=test-user-secret"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("cloudmeal.jwt.admin-secret-key must be configured with a resolved, non-blank value");
        assertThatThrownBy(() -> start("prod", "--CLOUDMEAL_JWT_ADMIN_SECRET=test-admin-secret"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("cloudmeal.jwt.user-secret-key must be configured with a resolved, non-blank value");
    }

    @Test
    void productionProfileFailsForBlankOrUnresolvedSecrets() {
        assertThatThrownBy(() -> start("prod",
                "--CLOUDMEAL_JWT_ADMIN_SECRET=  ",
                "--CLOUDMEAL_JWT_USER_SECRET=test-user-secret"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("cloudmeal.jwt.admin-secret-key must be configured with a resolved, non-blank value");
        assertThatThrownBy(() -> start("prod",
                "--CLOUDMEAL_JWT_ADMIN_SECRET=test-admin-secret",
                "--CLOUDMEAL_JWT_USER_SECRET=${MISSING_JWT_SECRET}"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("cloudmeal.jwt.user-secret-key must be configured with a resolved, non-blank value");
    }

    @Test
    void mixedProfilesKeepProductionSettings() {
        try (ConfigurableApplicationContext context = start("dev,prod",
                "--CLOUDMEAL_JWT_ADMIN_SECRET=test-admin-secret",
                "--CLOUDMEAL_JWT_USER_SECRET=test-user-secret")) {
            JwtProperties jwt = context.getBean(JwtProperties.class);
            assertThat(((DefaultListableBeanFactory) context.getBeanFactory()).isAllowCircularReferences()).isFalse();
            assertThat(jwt.getAdminSecretKey()).isEqualTo("test-admin-secret");
            assertThat(jwt.getUserSecretKey()).isEqualTo("test-user-secret");
        }
        assertThatThrownBy(() -> start("dev,prod"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("cloudmeal.jwt.admin-secret-key must be configured with a resolved, non-blank value");
    }

    private ConfigurableApplicationContext start(String profile, String... extraArguments) {
        SpringApplication application = new SpringApplication(EmptyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        application.setEnvironment(environment);
        String[] arguments = new String[extraArguments.length + 2];
        arguments[0] = "--spring.profiles.active=" + profile;
        arguments[1] = "--spring.config.location=classpath:/application.yml";
        System.arraycopy(extraArguments, 0, arguments, 2, extraArguments.length);
        return application.run(arguments);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class EmptyConfiguration {
    }
}
