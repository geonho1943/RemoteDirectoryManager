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

    // API 키 인증 필터 빈을 생성한다.
    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(
            SecurityProperties securityProperties,
            ObjectMapper objectMapper
    ) {
        return new ApiKeyAuthenticationFilter(securityProperties, objectMapper);
    }

    // API 경로에 인증 필터를 가장 높은 우선순위로 등록한다.
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
