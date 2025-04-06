# Scheduler

# Corrected Test API Examples

## Create Campaign API Calls

### Create First Campaign:
```bash
curl -X POST "http://localhost:8080/api/v1/campaigns" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "First Campaign",
    "bannerId": "BANNER-001",
    "insightType": "PROMOTION",
    "insightSubType": "SEASONAL",
    "insight": "Spring promotion for all companies",
    "companyNames": "ABCCorp|XYZInc",
    "eligibleCompanies": 10,
    "eligibleUsers": 1000,
    "startDate": "2025-05-01",
    "endDate": "2025-05-31",
    "frequencyPerWeek": 2,
    "displayCapping": 8,
    "displayLocation": "HOME_PAGE",
    "createdBy": "admin",
    "action": "ACTIVE"
  }'
```

### Create Second Campaign:
```bash
curl -X POST "http://localhost:8080/api/v1/campaigns" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Second Campaign",
    "bannerId": "BANNER-002",
    "insightType": "DISCOUNT",
    "insightSubType": "PERCENTAGE",
    "insight": "Special 20% discount for selected companies",
    "companyNames": "ABCCorp|123Corp",
    "eligibleCompanies": 5,
    "eligibleUsers": 500,
    "startDate": "2025-05-01",
    "endDate": "2025-06-15",
    "frequencyPerWeek": 3,
    "displayCapping": 12,
    "displayLocation": "DASHBOARD",
    "createdBy": "admin",
    "action": "ACTIVE"
  }'
```

### Create Third Campaign:
```bash
curl -X POST "http://localhost:8080/api/v1/campaigns" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Third Campaign",
    "bannerId": "BANNER-003",
    "insightType": "AWARENESS",
    "insightSubType": "PRODUCT",
    "insight": "Introducing our new product line",
    "companyNames": "ABCCorp|XYZInc|123Corp",
    "eligibleCompanies": 8,
    "eligibleUsers": 800,
    "startDate": "2025-05-15",
    "endDate": "2025-06-30",
    "frequencyPerWeek": 1,
    "displayCapping": 6,
    "displayLocation": "SIDEBAR",
    "createdBy": "admin",
    "action": "ACTIVE"
  }'
```

## Notes on Schema

The campaign table should have a `company_names` column that stores pipe-separated company identifiers:

```sql
CREATE TABLE campaigns_dev_rotation1 (
    -- other columns...
    company_names VARCHAR(255) NOT NULL,
    -- other columns...
);
```

The repository query for finding eligible campaigns for a company should use a LIKE operator:

```sql
@Query(value = "SELECT * FROM [dbo].[campaigns_dev_rotation1] WHERE "
        + "([start_date] <= :current_date AND [end_date] >= :current_date) "
        + "AND (visibility is NULL OR visibility != 'COMPLETED') "
        + "AND company_names LIKE %:company% " -- This is the part that matters
        + "AND (status = 'ACTIVE' OR status = 'SCHEDULED') "
        + "ORDER BY created_date ASC", 
        nativeQuery = true)
List<CampaignMapping> getEligibleCampaignsBasedonRequestDate(
        @Param("current_date") String currentDate,
        @Param("company") String company);
```

The actual matching logic depends on how exact you want the matching to be. For example:
- `LIKE '%|ABCCorp|%'` would match only if the company is in the middle
- `LIKE '%ABCCorp%'` would match if the string exists anywhere
- `LIKE '%|ABCCorp|%' OR company_names = 'ABCCorp' OR company_names LIKE 'ABCCorp|%' OR company_names LIKE '%|ABCCorp'` would be most precise

For most accurate matching, consider using a separate join table that maps campaigns to companies in a many-to-many relationship.




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



### Create campaign_company_mapping table for many-to-many relationship

```sql
CREATE TABLE campaign_company_mapping (
    id VARCHAR(36) PRIMARY KEY,
    campaign_id VARCHAR(36) NOT NULL,
    company_id VARCHAR(100) NOT NULL,
    CONSTRAINT fk_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns_dev_rotation1(id) ON DELETE CASCADE,
    CONSTRAINT uk_campaign_company UNIQUE (campaign_id, company_id)
);

-- Create indexes for better performance
CREATE INDEX idx_campaign_id ON campaign_company_mapping (campaign_id);
CREATE INDEX idx_company_id ON campaign_company_mapping (company_id);

-- Note: Original campaigns_dev_rotation1 table already exists
-- You may want to keep the company_names column for backward compatibility
-- or implement a migration script to populate the new mapping table from existing data

-- Sample migration script to populate the mapping table from existing data
-- This can be run as a one-time operation during implementation
INSERT INTO campaign_company_mapping (id, campaign_id, company_id)
SELECT 
    NEWID(), -- Generate a new UUID for each mapping
    id as campaign_id,
    value as company_id
FROM 
    campaigns_dev_rotation1
CROSS APPLY STRING_SPLIT(company_names, '|')
WHERE 
    company_names IS NOT NULL AND company_names <> '';

-- If you decide to remove the company_names column later:
-- ALTER TABLE campaigns_dev_rotation1 DROP COLUMN company_names;

-- Sample Data - Companies
-- These would likely come from an existing company table
INSERT INTO companies (id, name, status) VALUES
('ABCCorp', 'ABC Corporation', 'ACTIVE'),
('XYZInc', 'XYZ Inc.', 'ACTIVE'),
('123Corp', '123 Corporation', 'ACTIVE');

-- Sample Data - Campaigns
-- These would be used for testing the rotation logic
INSERT INTO campaigns_dev_rotation1 
(id, name, banner_id, insight_type, insight_sub_type, insight, eligible_companies, 
eligible_users, start_date, end_date, frequency_per_week, display_capping, 
display_location, created_by, created_date, status)
VALUES
(
    NEWID(), 
    'First Campaign', 
    'BANNER-001', 
    'PROMOTION', 
    'SEASONAL', 
    'Spring promotion for all companies',
    10,
    1000,
    '2025-05-01',
    '2025-05-31',
    2,
    8,
    'HOME_PAGE',
    'admin',
    GETDATE(),
    'ACTIVE'
);

-- Sample Data - Campaign-Company Mappings
-- Associate the sample campaign with companies
INSERT INTO campaign_company_mapping (id, campaign_id, company_id)
SELECT NEWID(), id, 'ABCCorp' FROM campaigns_dev_rotation1 WHERE name = 'First Campaign'
UNION
SELECT NEWID(), id, 'XYZInc' FROM campaigns_dev_rotation1 WHERE name = 'First Campaign';
```