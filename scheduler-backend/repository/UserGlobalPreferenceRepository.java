package com.usbank.corp.dcr.api.repository;

import com.usbank.corp.dcr.api.entity.UserGlobalPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserGlobalPreferenceRepository extends JpaRepository<UserGlobalPreference, String> {
    
    /**
     * Find preference by user ID
     */
    Optional<UserGlobalPreference> findByUserId(String userId);
    
    /**
     * Check if user exists and has insights enabled
     */
    boolean existsByUserIdAndInsightsEnabled(String userId, Boolean insightsEnabled);
}