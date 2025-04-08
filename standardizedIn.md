# Integration Instructions

## Backend Integration

1. Add the `StandardizedInsight` model class
2. Add the `StandardizedInsightService` class with the implementation
3. Enhance the `InsightRepository` with the additional methods
4. Add the `StandardizedInsightController` to expose the new endpoint

## Frontend Integration

1. Update your insightAPI.ts file with the new endpoint and types
2. Create the StandardizedInsights component
3. Integrate the component into your ManageCampaign flow

### Example Integration in ManageCampaign.tsx

Add the following import:
```typescript
import StandardizedInsights from '../StandardizedInsights/StandardizedInsights';
```

Then, in the Step 2 of your campaign setup, you can add the component:

```typescript
{currentStep === 1 && (
  <>
    
    
    {/* Add this conditional rendering for standardized insights */}
    {selectedInsightType && selectedSubType && (
      
    )}
  </>
)}
```

### Example Integration in Step2SetupCampaign.tsx

Alternatively, you can modify the Step2SetupCampaign component to include the StandardizedInsights:

```typescript
// At the end of the Step2SetupCampaign component, before the ButtonRow

  Insight Templates
  {selectedInsightType && selectedSubType && (
    
  )}

```

## Testing with Postman

1. Create a new request in Postman:
   - Method: GET
   - URL: http://localhost:8080/api/insights/standardized
   - Query parameters:
     - insightType: Money Movement
     - insightSubType: FX

2. Send the request to see the standardized insights with company counts and mappings.

## Expected Response Format

```json
[
  {
    "standardizedText": "[Company] could have used USB FX services amounting to [Amount] in [Countries] countries",
    "companyCount": 12,
    "companies": [
      "NATIONAL AUSTRALIA BANK LTD",
      "BRIDGEWAY TRADING CORPORATION",
      "BLACK HILLS SURGICAL HOSPITAL LLC",
      "..."
    ]
  },
  {
    "standardizedText": "[Company] has FX needs based on payment patterns",
    "companyCount": 8,
    "companies": [
      "AMERICAN FAMILY MUTUAL SUMMARY",
      "ACADEMY MORTGAGE CORPORATION",
      "..."
    ]
  }
]
```