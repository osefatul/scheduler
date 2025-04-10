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