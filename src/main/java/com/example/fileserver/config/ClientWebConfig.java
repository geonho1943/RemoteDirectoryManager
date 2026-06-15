package com.example.fileserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ClientWebConfig implements WebMvcConfigurer {

    // 임시 브라우저 클라이언트 정적 파일 경로를 등록한다.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/client/**")
                .addResourceLocations("file:client/");
    }

    // 루트와 클라이언트 경로를 임시 브라우저 클라이언트 진입점으로 연결한다.
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/client/");
        registry.addRedirectViewController("/client", "/client/");
        registry.addViewController("/client/").setViewName("forward:/client/index.html");
    }
}
