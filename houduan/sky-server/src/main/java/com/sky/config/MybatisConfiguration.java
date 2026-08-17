package com.sky.config;

import com.github.pagehelper.PageInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 插件配置。
 */
@Configuration
public class MybatisConfiguration {

    /**
     * 保留项目原有的 PageHelper.startPage() 分页能力。
     */
    @Bean
    public PageInterceptor pageInterceptor() {
        return new PageInterceptor();
    }
}
