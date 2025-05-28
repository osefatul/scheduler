package com.usbank.corp.dcr.api.config;

import com.usbank.corp.dcr.api.service.SessionIntegrationService;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.HeaderHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

@Configuration
public class SessionConfig {
    
    /**
     * Register session listener for cleanup
     */
    @Bean
    public ServletListenerRegistrationBean<SessionIntegrationService> sessionListener() {
        return new ServletListenerRegistrationBean<>(new SessionIntegrationService());
    }
    
    /**
     * Configure session ID resolution (optional - for API clients)
     */
    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        return HeaderHttpSessionIdResolver.xAuthToken();
    }
}