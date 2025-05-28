package com.usbank.corp.dcr.api.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

@Service
@Slf4j
public class SessionIntegrationService implements HttpSessionListener {
    
    @Autowired
    private UserSessionService userSessionService;
    
    /**
     * Handle session destruction to ensure data persistence
     */
    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        HttpSession session = se.getSession();
        log.info("Session {} destroyed, applying pending views to database", session.getId());
        
        try {
            userSessionService.endSession(session);
        } catch (Exception e) {
            log.error("Error handling session destruction for session {}: {}", 
                    session.getId(), e.getMessage(), e);
        }
    }
    
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        log.info("New session created: {}", se.getSession().getId());
    }
}