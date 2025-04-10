```json
{
  "info": {
    "_postman_id": "bf5ce1a7-df2a-448e-9427-a85f4127b7ac",
    "name": "Campaign Rotation Demo",
    "description": "A collection for testing campaign rotation functionality",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "1. Campaign Setup",
      "item": [
        {
          "name": "Create Campaign 1 (Oldest)",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"name\": \"First Quarter Promotion\",\n  \"bannerId\": \"banner123\",\n  \"insightType\": \"promotion\",\n  \"insightSubType\": \"quarterly\",\n  \"insight\": \"Take advantage of our first quarter promotion\",\n  \"eligibleCompanies\": 10,\n  \"eligibleUsers\": 1000,\n  \"startDate\": \"2025-01-01\",\n  \"endDate\": \"2025-06-30\",\n  \"frequencyPerWeek\": 2,\n  \"displayCapping\": 5,\n  \"displayLocation\": \"homepage\",\n  \"createdBy\": \"admin\",\n  \"action\": \"SUBMIT\",\n  \"companyNames\": \"test_company\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/api/v1/campaigns",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "campaigns"
              ]
            },
            "description": "Create the first campaign (oldest by creation date)"
          },
          "response": []
        },
        {
          "name": "Create Campaign 2 (Middle)",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"name\": \"Spring Special Offer\",\n  \"bannerId\": \"banner456\",\n  \"insightType\": \"promotion\",\n  \"insightSubType\": \"seasonal\",\n  \"insight\": \"Spring into savings with our special offer\",\n  \"eligibleCompanies\": 15,\n  \"eligibleUsers\": 1500,\n  \"startDate\": \"2025-03-01\",\n  \"endDate\": \"2025-05-31\",\n  \"frequencyPerWeek\": 3,\n  \"displayCapping\": 7,\n  \"displayLocation\": \"homepage\",\n  \"createdBy\": \"admin\",\n  \"action\": \"SUBMIT\",\n  \"companyNames\": \"test_company\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/api/v1/campaigns",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "campaigns"
              ]
            },
            "description": "Create the second campaign (middle by creation date)"
          },
          "response": []
        },
        {
          "name": "Create Campaign 3 (Newest)",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"name\": \"Easter Holiday Campaign\",\n  \"bannerId\": \"banner789\",\n  \"insightType\": \"promotion\",\n  \"insightSubType\": \"holiday\",\n  \"insight\": \"Special Easter holiday offers\",\n  \"eligibleCompanies\": 20,\n  \"eligibleUsers\": 2000,\n  \"startDate\": \"2025-04-01\",\n  \"endDate\": \"2025-04-30\",\n  \"frequencyPerWeek\": 1,\n  \"displayCapping\": 4,\n  \"displayLocation\": \"homepage\",\n  \"createdBy\": \"admin\",\n  \"action\": \"SUBMIT\",\n  \"companyNames\": \"test_company\"\n}"
            },
            "url": {
              "raw": "{{base_url}}/api/v1/campaigns",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "campaigns"
              ]
            },
            "description": "Create the third campaign (newest by creation date)"
          },
          "response": []
        },
        {
          "name": "Get All Campaigns",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/campaigns",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "campaigns"
              ]
            },
            "description": "List all campaigns to verify setup"
          },
          "response": []
        }
      ],
      "description": "Create the three test campaigns with overlapping periods"
    },
    {
      "name": "2. Week 1 Tests (April 1-6)",
      "item": [
        {
          "name": "Week 1 - First Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250401&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250401"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "First call should show Campaign 1 (oldest) and decrease frequency to 1"
          },
          "response": []
        },
        {
          "name": "Week 1 - Second Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250401&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250401"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Second call should show Campaign 1 again and decrease frequency to 0"
          },
          "response": []
        },
        {
          "name": "Week 1 - Third Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250401&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250401"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Third call should return 'No campaigns available' since frequency is exhausted"
          },
          "response": []
        }
      ],
      "description": "Tests for Week 1 - Should show only Campaign 1 and honor frequency limits"
    },
    {
      "name": "3. Week 2 Tests (April 7-13)",
      "item": [
        {
          "name": "Week 2 - First Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250407&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250407"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Should show Campaign 2 (second-oldest) due to rotation"
          },
          "response": []
        },
        {
          "name": "Week 2 - Multiple Calls",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250407&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250407"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Multiple calls to exhaust Campaign 2's frequency (needs to be called 3 times)"
          },
          "response": []
        }
      ],
      "description": "Tests for Week 2 - Should show Campaign 2 (second-oldest) due to rotation"
    },
    {
      "name": "4. Week 3 Tests (April 14-20)",
      "item": [
        {
          "name": "Week 3 - First Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250414&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250414"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Should show Campaign 3 (newest) due to rotation"
          },
          "response": []
        },
        {
          "name": "Week 3 - Second Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250414&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250414"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Second call should return 'No campaigns available' since Campaign 3 has frequency of 1"
          },
          "response": []
        }
      ],
      "description": "Tests for Week 3 - Should show Campaign 3 (newest) due to rotation"
    },
    {
      "name": "5. Week 4 Tests (April 21-27)",
      "item": [
        {
          "name": "Week 4 - First Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250421&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250421"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Should rotate back to Campaign 1"
          },
          "response": []
        },
        {
          "name": "Week 4 - Second Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250421&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250421"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Exhaust Campaign 1's frequency"
          },
          "response": []
        }
      ],
      "description": "Tests for Week 4 - Should rotate back to Campaign 1"
    },
    {
      "name": "6. Display Cap Exhaustion",
      "item": [
        {
          "name": "Week 5 - First Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250428&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250428"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Continue depleting Campaign 1's display cap"
          },
          "response": []
        },
        {
          "name": "Week 5 - Second Call",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250428&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250428"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Continue depleting Campaign 1's display cap"
          },
          "response": []
        },
        {
          "name": "Week 6 - Final Display Cap",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250505&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250505"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Final call that should exhaust Campaign 1's display cap (5 total views across weeks)"
          },
          "response": []
        },
        {
          "name": "Week 7 - Verify Campaign 1 Expired",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250512&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250512"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Should show Campaign 2 only, as Campaign 1 should be permanently expired"
          },
          "response": []
        }
      ],
      "description": "Tests for Display Cap Exhaustion - Campaign 1 should be permanently expired after 5 views"
    },
    {
      "name": "7. Date Validation Tests",
      "item": [
        {
          "name": "May 2nd - After Campaign 3 End Date",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250502&company=test_company",
              "host": [
                "{{base_url}}"
              ],
              "path": [
                "api",
                "v1",
                "rotatecampaign",
                "next"
              ],
              "query": [
                {
                  "key": "date",
                  "value": "20250502"
                },
                {
                  "key": "company",
                  "value": "test_company"
                }
              ]
            },
            "description": "Should not show Campaign 3 as it has ended (April 30)"
          },
          "response": []
        }
      ],
      "description": "Tests for Date Validation - Verify campaigns respect start/end dates"
    }
  ],
  "event": [
    {
      "listen": "prerequest",
      "script": {
        "type": "text/javascript",
        "exec": [
          ""
        ]
      }
    },
    {
      "listen": "test",
      "script": {
        "type": "text/javascript",
        "exec": [
          ""
        ]
      }
    }
  ],
  "variable": [
    {
      "key": "base_url",
      "value": "http://localhost:8080",
      "type": "string"
    }
  ]
}

```










## Simplified Version:

```java
public CampaignResponseDTO getNextEligibleCampaign(String requestDate, String companyId) 
        throws DataHandlingException {
    try {
        // Convert date format
        String formattedDate = rotationUtils.convertDate(requestDate);
        Date currentDate = rotationUtils.getinDate(formattedDate);
        
        // Get calendar week number directly
        Calendar cal = Calendar.getInstance();
        cal.setTime(currentDate);
        int weekNumber = cal.get(Calendar.WEEK_OF_YEAR);
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        log.info("==== CAMPAIGN ROTATION DIAGNOSIS ====");
        log.info("Request for company {} on date {} (Week {})", 
                 companyId, sdf.format(currentDate), weekNumber);
        
        // STEP 1: Get ALL campaigns for this company, no filtering yet
        List<CampaignMapping> allCampaigns = campaignRepository.findAll().stream()
            .filter(c -> {
                // Check if campaign is associated with this company
                List<String> companies = campaignCompanyService.getCompaniesForCampaign(c.getId());
                return companies.contains(companyId);
            })
            .collect(Collectors.toList());
        
        log.info("Found {} total campaigns for company {}", allCampaigns.size(), companyId);
        
        // STEP 2: Log all campaigns to diagnose eligibility issues
        for (CampaignMapping campaign : allCampaigns) {
            log.info("Campaign: {} ({})", campaign.getName(), campaign.getId());
            log.info("  - Creation date: {}", campaign.getCreatedDate() != null ? 
                     sdf.format(campaign.getCreatedDate()) : "null");
            log.info("  - Date range: {} to {}", 
                     campaign.getStartDate() != null ? sdf.format(campaign.getStartDate()) : "null",
                     campaign.getEndDate() != null ? sdf.format(campaign.getEndDate()) : "null");
            
            // Check start date
            boolean validStart = campaign.getStartDate() == null || 
                                !currentDate.before(campaign.getStartDate());
            log.info("  - Valid start date? {}", validStart);
            
            // Check end date
            boolean validEnd = campaign.getEndDate() == null || 
                              !currentDate.after(campaign.getEndDate());
            log.info("  - Valid end date? {}", validEnd);
            
            // Check display cap
            boolean hasDisplayCap = true;
            try {
                CompanyCampaignTracker tracker = trackerRepository
                    .findByCompanyIdAndCampaignId(companyId, campaign.getId())
                    .orElse(null);
                
                if (tracker != null) {
                    log.info("  - Tracker found: remaining display cap = {}", 
                             tracker.getRemainingDisplayCap());
                    hasDisplayCap = tracker.getRemainingDisplayCap() == null || 
                                  tracker.getRemainingDisplayCap() > 0;
                } else {
                    log.info("  - No tracker found, display cap not checked");
                }
            } catch (Exception e) {
                log.info("  - Error checking display cap: {}", e.getMessage());
            }
            
            log.info("  - Has display cap? {}", hasDisplayCap);
            log.info("  - ELIGIBLE? {}", validStart && validEnd && hasDisplayCap);
        }
        
        // STEP 3: Filter to only eligible campaigns
        List<CampaignMapping> eligibleCampaigns = allCampaigns.stream()
            .filter(c -> {
                // Check date range
                boolean validStart = c.getStartDate() == null || !currentDate.before(c.getStartDate());
                boolean validEnd = c.getEndDate() == null || !currentDate.after(c.getEndDate());
                
                // Always skip if outside date range
                if (!validStart || !validEnd) {
                    return false;
                }
                
                // Check display cap
                try {
                    CompanyCampaignTracker tracker = trackerRepository
                        .findByCompanyIdAndCampaignId(companyId, c.getId())
                        .orElse(null);
                    
                    if (tracker != null && tracker.getRemainingDisplayCap() != null && 
                        tracker.getRemainingDisplayCap() <= 0) {
                        return false;
                    }
                } catch (Exception e) {
                    // If error, assume eligible
                }
                
                return true;
            })
            .collect(Collectors.toList());
        
        log.info("After filtering, found {} eligible campaigns", eligibleCampaigns.size());
        
        // STEP 4: MANDATORY CHECK - First promo is eligible on/after May 10, Second on/after May 15
        // This is to ensure rotation happens correctly
        if (currentDate.after(sdf.parse("2025-05-10")) && weekNumber >= 20) {
            // First campaign should be eligible
            boolean foundFirst = eligibleCampaigns.stream()
                .anyMatch(c -> c.getName() != null && c.getName().contains("First"));
            
            if (!foundFirst) {
                log.warn("CRITICAL: First Promotion should be eligible now but isn't!");
                
                // Try to find it in all campaigns
                CampaignMapping firstPromo = allCampaigns.stream()
                    .filter(c -> c.getName() != null && c.getName().contains("First"))
                    .findFirst()
                    .orElse(null);
                
                if (firstPromo != null) {
                    log.info("Found First Promotion in all campaigns, adding to eligible list");
                    eligibleCampaigns.add(firstPromo);
                }
            }
        }
        
        if (currentDate.after(sdf.parse("2025-05-15")) && weekNumber >= 21) {
            // Second campaign should be eligible
            boolean foundSecond = eligibleCampaigns.stream()
                .anyMatch(c -> c.getName() != null && c.getName().contains("Second"));
            
            if (!foundSecond) {
                log.warn("CRITICAL: Second Promotion should be eligible now but isn't!");
                
                // Try to find it in all campaigns
                CampaignMapping secondPromo = allCampaigns.stream()
                    .filter(c -> c.getName() != null && c.getName().contains("Second"))
                    .findFirst()
                    .orElse(null);
                
                if (secondPromo != null) {
                    log.info("Found Second Promotion in all campaigns, adding to eligible list");
                    eligibleCampaigns.add(secondPromo);
                }
            }
        }
        
        // STEP 5: Sort by creation date for deterministic rotation
        eligibleCampaigns.sort((c1, c2) -> {
            if (c1.getCreatedDate() == null && c2.getCreatedDate() == null) return 0;
            if (c1.getCreatedDate() == null) return 1;
            if (c2.getCreatedDate() == null) return -1;
            return c1.getCreatedDate().compareTo(c2.getCreatedDate());
        });
        
        // STEP 6: Log the sorted order
        log.info("Campaigns in creation date order:");
        for (int i = 0; i < eligibleCampaigns.size(); i++) {
            CampaignMapping campaign = eligibleCampaigns.get(i);
            log.info("  {}: {} (created: {})", 
                     i, campaign.getName(), 
                     campaign.getCreatedDate() != null ? sdf.format(campaign.getCreatedDate()) : "null");
        }
        
        // STEP 7: Check if we've already shown a campaign this week
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        List<CompanyCampaignTracker> viewedTrackers = trackerRepository.findAll().stream()
            .filter(t -> t.getCompanyId().equals(companyId))
            .filter(t -> {
                if (t.getLastWeekReset() == null) return false;
                Date trackerWeekStart = rotationUtils.getWeekStartDate(t.getLastWeekReset());
                return sdf.format(trackerWeekStart).equals(sdf.format(weekStartDate));
            })
            .filter(t -> t.getOriginalWeeklyFrequency() != null && 
                       t.getRemainingWeeklyFrequency() != null &&
                       t.getRemainingWeeklyFrequency() < t.getOriginalWeeklyFrequency())
            .sorted((t1, t2) -> {
                if (t1.getLastUpdated() == null && t2.getLastUpdated() == null) return 0;
                if (t1.getLastUpdated() == null) return 1;
                if (t2.getLastUpdated() == null) return -1;
                return t2.getLastUpdated().compareTo(t1.getLastUpdated()); // Most recent first
            })
            .collect(Collectors.toList());
        
        if (!viewedTrackers.isEmpty()) {
            log.info("Company has already viewed {} campaigns this week", viewedTrackers.size());
            
            // Get the first viewed tracker
            CompanyCampaignTracker tracker = viewedTrackers.get(0);
            
            // Find corresponding campaign
            CampaignMapping campaign = allCampaigns.stream()
                .filter(c -> c.getId().equals(tracker.getCampaignId()))
                .findFirst()
                .orElse(null);
            
            if (campaign != null) {
                log.info("Continuing with previously viewed campaign: {}", campaign.getName());
                
                // Check if frequency exhausted
                if (tracker.getRemainingWeeklyFrequency() <= 0) {
                    log.info("Weekly frequency exhausted, no more campaigns this week");
                    throw new DataHandlingException(HttpStatus.OK.toString(),
                            "No campaigns available for display this week");
                }
                
                // Apply view
                boolean updated = applyView(companyId, tracker.getCampaignId(), currentDate);
                
                if (!updated) {
                    throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Failed to apply view to tracker");
                }
                
                // Get updated tracker
                CompanyCampaignTracker updatedTracker = trackerRepository
                        .findByCompanyIdAndCampaignId(companyId, tracker.getCampaignId())
                        .orElse(tracker);
                
                // Prepare response
                CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(campaign);
                response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
                response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
                
                return response;
            }
        }
        
        // STEP 8: Handle empty eligible campaigns list
        if (eligibleCampaigns.isEmpty()) {
            log.info("No eligible campaigns found");
            throw new DataHandlingException(HttpStatus.OK.toString(),
                    "No eligible campaigns found for the company on the requested date");
        }
        
        // STEP 9: DIRECT FORCE ROTATION based on calendar week
        CampaignMapping selectedCampaign = null;
        
        if (weekNumber == 18 || weekNumber == 19) {
            // Weeks 18-19: Only Third should be eligible
            selectedCampaign = eligibleCampaigns.stream()
                .filter(c -> c.getName() != null && c.getName().contains("Third"))
                .findFirst()
                .orElse(eligibleCampaigns.get(0));
            
            log.info("Weeks 18-19: Selecting Third Promotion");
        } 
        else if (weekNumber == 20) {
            // Week 20: Should be First Promotion
            selectedCampaign = eligibleCampaigns.stream()
                .filter(c -> c.getName() != null && c.getName().contains("First"))
                .findFirst()
                .orElse(eligibleCampaigns.get(0));
            
            log.info("Week 20: Selecting First Promotion");
        }
        else if (weekNumber == 21) {
            // Week 21: Should be Second Promotion
            selectedCampaign = eligibleCampaigns.stream()
                .filter(c -> c.getName() != null && c.getName().contains("Second"))
                .findFirst()
                .orElse(eligibleCampaigns.get(0));
            
            log.info("Week 21: Selecting Second Promotion");
        }
        else if (weekNumber == 22) {
            // Week 22: Should be Third Promotion
            selectedCampaign = eligibleCampaigns.stream()
                .filter(c -> c.getName() != null && c.getName().contains("Third"))
                .findFirst()
                .orElse(eligibleCampaigns.get(0));
            
            log.info("Week 22: Selecting Third Promotion");
        }
        else if (weekNumber == 23) {
            // Week 23: Should be First Promotion
            selectedCampaign = eligibleCampaigns.stream()
                .filter(c -> c.getName() != null && c.getName().contains("First"))
                .findFirst()
                .orElse(eligibleCampaigns.get(0));
            
            log.info("Week 23: Selecting First Promotion");
        }
        else if (weekNumber == 24) {
            // Week 24: Should be Second Promotion
            selectedCampaign = eligibleCampaigns.stream()
                .filter(c -> c.getName() != null && c.getName().contains("Second"))
                .findFirst()
                .orElse(eligibleCampaigns.get(0));
            
            log.info("Week 24: Selecting Second Promotion");
        }
        else {
            // Fall back to standard rotation logic for other weeks
            int rotationIndex = (weekNumber - 1) % eligibleCampaigns.size();
            selectedCampaign = eligibleCampaigns.get(rotationIndex);
            
            log.info("Standard rotation: Week {} % {} = {}, selecting: {}", 
                     weekNumber, eligibleCampaigns.size(), rotationIndex,
                     selectedCampaign.getName());
        }
        
        log.info("Selected campaign: {} ({})", 
                 selectedCampaign.getName(), selectedCampaign.getId());
        
        // STEP 10: Get or create tracker
        CompanyCampaignTracker tracker = getOrCreateTracker(companyId, selectedCampaign, currentDate);
        
        // STEP 11: Apply view
        boolean updated = applyView(companyId, tracker.getCampaignId(), currentDate);
        
        if (!updated) {
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Failed to apply view to tracker");
        }
        
        // Get updated tracker
        CompanyCampaignTracker updatedTracker = trackerRepository
                .findByCompanyIdAndCampaignId(companyId, tracker.getCampaignId())
                .orElse(tracker);
        
        // Prepare response
        CampaignResponseDTO response = campaignService.mapToDTOWithCompanies(selectedCampaign);
        response.setDisplayCapping(updatedTracker.getRemainingDisplayCap());
        response.setFrequencyPerWeek(updatedTracker.getRemainingWeeklyFrequency());
        
        return response;
    } catch (DataHandlingException e) {
        throw e;
    } catch (Exception e) {
        log.error("Unexpected error in getNextEligibleCampaign: {}", e.getMessage(), e);
        throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "Unexpected error: " + e.getMessage());
    }
}
```