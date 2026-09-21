package com.cloudmeal.properties;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cloudmeal.jwt")
@Data
public class JwtProperties implements InitializingBean {

    /**
     * 管理端员工生成jwt令牌相关配置
     */
    private String adminSecretKey;
    private long adminTtl;
    private String adminTokenName;

    /**
     * 用户端微信用户生成jwt令牌相关配置
     */
    private String userSecretKey;
    private long userTtl;
    private String userTokenName;

    @Override
    public void afterPropertiesSet() {
        requireConfiguredSecret(adminSecretKey, "admin-secret-key");
        requireConfiguredSecret(userSecretKey, "user-secret-key");
    }

    private static void requireConfiguredSecret(String secret, String name) {
        if (secret == null || secret.trim().isEmpty() || secret.contains("${")) {
            throw new IllegalStateException("cloudmeal.jwt." + name + " must be configured with a resolved, non-blank value");
        }
    }
}
