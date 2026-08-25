package com.sky.config;

import com.sky.properties.AiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AiClientConfiguration {

    @Bean
    @Qualifier("aiRestTemplate")
    public RestTemplate aiRestTemplate(AiProperties aiProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(aiProperties.getConnectTimeout());
        requestFactory.setReadTimeout(aiProperties.getReadTimeout());
        return new RestTemplate(requestFactory);
    }
}
