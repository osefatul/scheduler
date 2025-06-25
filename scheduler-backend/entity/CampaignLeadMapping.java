package com.ubank.corp.dcr.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campaign_lead_mapping")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignLeadMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "lead_id", nullable = false)
    private String leadId;

    @Column(name = "campaign_id", nullable = false)
    private String campaignId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "email_address", nullable = false)
    private String emailAddress;

    @Column(name = "comments", nullable = true)
    private String comments;

    @Column(name = "marketing_relationship_type", nullable = false)
    private String marketingRelationshipType;

    @Column(name = "corporate_connection_insight", nullable = false)
    private String corporateConnectionInsight;
}