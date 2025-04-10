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
        Date weekStartDate = rotationUtils.getWeekStartDate(currentDate);
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        log.info("Finding eligible campaign for company {} on date {}", 
                 companyId, sdf.format(currentDate));
        
        // Step 1: Check if the company has viewed any campaign this week
        List<CompanyCampaignTracker> viewedTrackers = findTrackersViewedThisWeek(companyId, weekStartDate);
        
        if (!viewedTrackers.isEmpty()) {
            log.info("Company has already viewed {} campaigns this week", viewedTrackers.size());
            
            // Get the first viewed tracker (most recently viewed)
            CompanyCampaignTracker tracker = viewedTrackers.get(0);
            
            log.info("Most recently viewed campaign: {}", tracker.getCampaignId());
            
            // If weekly frequency is exhausted, no more campaigns this week
            if (tracker.getRemainingWeeklyFrequency() <= 0) {
                log.info("Weekly frequency exhausted for company {} - no more campaigns this week", companyId);
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available for display this week");
            }
            
            // If display cap is exhausted, campaign is permanently expired
            if (tracker.getRemainingDisplayCap() <= 0) {
                log.info("Display cap exhausted for campaign {} - permanently expired", tracker.getCampaignId());
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available (display cap exhausted)");
            }
            
            // Get the campaign
            CampaignMapping campaign = campaignRepository.findById(tracker.getCampaignId())
                    .orElseThrow(() -> new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                            "Campaign not found"));
            
            // Verify it's still in date range
            if (campaign.getStartDate() != null && currentDate.before(campaign.getStartDate())) {
                log.info("Campaign {} not started yet", campaign.getId());
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available (campaign not started yet)");
            }
            
            if (campaign.getEndDate() != null && currentDate.after(campaign.getEndDate())) {
                log.info("Campaign {} has ended", campaign.getId());
                throw new DataHandlingException(HttpStatus.OK.toString(),
                        "No campaigns available (campaign has ended)");
            }
            
            // Apply view
            boolean updated = applyView(companyId, tracker.getCampaignId(), currentDate);
            
            if (!updated) {
                log.warn("Failed to apply view to tracker");
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
        
        // Step 2: If no campaign viewed this week, select based on rotation
        
        // Get all date-valid and eligible campaigns
        List<CampaignMapping> eligibleCampaigns = getCampaignsForDate(companyId, currentDate);
        
        if (eligibleCampaigns.isEmpty()) {
            log.info("No eligible campaigns found for company {} on date {}", 
                     companyId, sdf.format(currentDate));
            throw new DataHandlingException(HttpStatus.OK.toString(),
                    "No eligible campaigns found for the company on the requested date");
        }
        
        log.info("Found {} eligible campaigns for company {}", eligibleCampaigns.size(), companyId);
        
        // Select campaign based on rotation
        CampaignMapping selectedCampaign = selectCampaignForRotation(eligibleCampaigns, currentDate);
        
        if (selectedCampaign == null) {
            throw new DataHandlingException(HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                    "Failed to select campaign for rotation");
        }
        
        log.info("Selected campaign {} for company {}", selectedCampaign.getId(), companyId);
        
        // Get or create tracker
        CompanyCampaignTracker tracker = getOrCreateTracker(companyId, selectedCampaign, currentDate);
        
        // Apply view
        boolean updated = applyView(companyId, tracker.getCampaignId(), currentDate);
        
        if (!updated) {
            log.warn("Failed to apply view to tracker");
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

/**
 * Find all trackers that have been viewed this week
 */
private List<CompanyCampaignTracker> findTrackersViewedThisWeek(String companyId, Date weekStartDate) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    
    return trackerRepository.findAll().stream()
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
}

/**
 * Get all eligible campaigns for a company on a specific date
 */
private List<CampaignMapping> getCampaignsForDate(String companyId, Date currentDate) {
    // Get campaigns from repository
    String formattedDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
    List<CampaignMapping> campaigns = campaignRepository
            .getEligibleCampaignsForCompany(formattedDate, companyId);
    
    // Filter by date range
    return campaigns.stream()
            .filter(c -> {
                boolean validStart = c.getStartDate() == null || !currentDate.before(c.getStartDate());
                boolean validEnd = c.getEndDate() == null || !currentDate.after(c.getEndDate());
                return validStart && validEnd;
            })
            .filter(c -> {
                // Skip campaigns with exhausted display cap
                if (isDisplayCapExhausted(companyId, c.getId())) {
                    log.info("Skipping campaign {} - display cap exhausted", c.getId());
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());
}

```