# Scheduler

# Campaign Rotation API Testing Examples

## 1. Get Next Eligible Campaign for a Company

### Example 1: Basic request
```bash
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250505&company=ABCCorp" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Example 2: Different company
```bash
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250505&company=XYZInc" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Example 3: Different week
```bash
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250512&company=ABCCorp" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## 2. Campaign Management APIs

### Get All Campaigns
```bash
curl -X GET "http://localhost:8080/api/v1/campaigns" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Get Campaign by ID
```bash
curl -X GET "http://localhost:8080/api/v1/campaigns/123e4567-e89b-12d3-a456-426614174000" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Create New Campaign
```bash
curl -X POST "http://localhost:8080/api/v1/campaigns" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -d '{
    "name": "Spring Promotion",
    "bannerId": "BANNER-001",
    "insightType": "DISCOUNT",
    "insightSubType": "SEASONAL",
    "insight": "Special spring discount for all customers!",
    "eligibleCompanies": 10,
    "eligibleUsers": 1000,
    "startDate": "2025-05-01",
    "endDate": "2025-05-31",
    "frequencyPerWeek": 3,
    "displayCapping": 15,
    "displayLocation": "HOME_PAGE",
    "createdBy": "admin",
    "action": "SCHEDULED"
  }'
```

### Update Campaign Status
```bash
curl -X PUT "http://localhost:8080/api/v1/campaigns/123e4567-e89b-12d3-a456-426614174000/status?status=ACTIVE" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## 3. Test Scenarios with Different Dates

### Test Scenario 1: Testing Week 18 (Campaign A should show)
```bash
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250428&company=ABCCorp" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Test Scenario 2: Testing Week 19 (Campaign B should show)
```bash
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250505&company=ABCCorp" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Test Scenario 3: Testing Week 20 (Campaign C should show)
```bash
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250512&company=ABCCorp" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Test Scenario 4: Testing Week 21 (Back to Campaign A)
```bash
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250519&company=ABCCorp" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## 4. Edge Case Testing

### No Eligible Campaigns
```bash
# Testing with a date before any campaign has started
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250101&company=ABCCorp" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Campaign Frequency Exhausted
```bash
# Call multiple times on same day to exhaust frequency
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250505&company=ABCCorp" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### Campaign After End Date
```bash
# Testing with a date after campaign has ended
curl -X GET "http://localhost:8080/api/v1/rotatecampaign/next?date=20250601&company=ABCCorp" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## 5. Testing with Postman

### Postman Collection (Import this to Postman)
```json
{
  "info": {
    "_postman_id": "123456-abcdef-123456",
    "name": "Campaign Rotation System",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "item": [
    {
      "name": "Get Next Campaign - Week 18",
      "request": {
        "method": "GET",
        "header": [],
        "url": {
          "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250428&company=ABCCorp",
          "host": ["{{base_url}}"],
          "path": ["api", "v1", "rotatecampaign", "next"],
          "query": [
            {
              "key": "date",
              "value": "20250428"
            },
            {
              "key": "company",
              "value": "ABCCorp"
            }
          ]
        }
      }
    },
    {
      "name": "Get Next Campaign - Week 19",
      "request": {
        "method": "GET",
        "header": [],
        "url": {
          "raw": "{{base_url}}/api/v1/rotatecampaign/next?date=20250505&company=ABCCorp",
          "host": ["{{base_url}}"],
          "path": ["api", "v1", "rotatecampaign", "next"],
          "query": [
            {
              "key": "date",
              "value": "20250505"
            },
            {
              "key": "company",
              "value": "ABCCorp"
            }
          ]
        }
      }
    },
    {
      "name": "Get All Campaigns",
      "request": {
        "method": "GET",
        "header": [],
        "url": {
          "raw": "{{base_url}}/api/v1/campaigns",
          "host": ["{{base_url}}"],
          "path": ["api", "v1", "campaigns"]
        }
      }
    },
    {
      "name": "Create New Campaign",
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
          "raw": "{\n    \"name\": \"Spring Promotion\",\n    \"bannerId\": \"BANNER-001\",\n    \"insightType\": \"DISCOUNT\",\n    \"insightSubType\": \"SEASONAL\",\n    \"insight\": \"Special spring discount for all customers!\",\n    \"eligibleCompanies\": 10,\n    \"eligibleUsers\": 1000,\n    \"startDate\": \"2025-05-01\",\n    \"endDate\": \"2025-05-31\",\n    \"frequencyPerWeek\": 3,\n    \"displayCapping\": 15,\n    \"displayLocation\": \"HOME_PAGE\",\n    \"createdBy\": \"admin\",\n    \"action\": \"SCHEDULED\"\n}"
        },
        "url": {
          "raw": "{{base_url}}/api/v1/campaigns",
          "host": ["{{base_url}}"],
          "path": ["api", "v1", "campaigns"]
        }
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

## 6. Test Cases with Expected Results

### Test Case 1: Basic Rotation
1. Create 3 campaigns with different creation dates
2. Test Week 18 (April 28-May 4) - Expect Campaign A (oldest)
3. Test Week 19 (May 5-11) - Expect Campaign B
4. Test Week 20 (May 12-18) - Expect Campaign C
5. Test Week 21 (May 19-25) - Expect Campaign A again

### Test Case 2: Frequency Limits
1. Set Campaign A with frequency_per_week = 2
2. Call API twice on same day in Week 18
3. Third call on same day should not return Campaign A
4. Call on Monday of Week 21 should return Campaign A again (frequency reset)

### Test Case 3: Campaign Eligibility
1. Create Campaign D with start_date = "2025-05-15"
2. Test on May 12 - Campaign D should not appear in rotation
3. Test on May 19 - Campaign D should now be included in rotation

### Test Case 4: Display Capping
1. Set Campaign B with display_capping = 3
2. Call API 3 times over different days
3. Campaign B should be marked as COMPLETED
4. After that, B should not appear in rotation