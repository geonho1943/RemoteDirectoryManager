package com.example.fileserver.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    public ApiKeyHasher apiKeyHasher() {
        return new ApiKeyHasher();
    }

    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
            SecurityProperties securityProperties,
            ApiKeyHasher apiKeyHasher,
            ObjectMapper objectMapper
    ) {
        return new ApiKeyAuthenticationFilter(securityProperties, apiKeyHasher, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilterRegistration(
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter
    ) {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(apiKeyAuthenticationFilter);
        registrationBean.addUrlPatterns("/api/v1", "/api/v1/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }
}
