```java
// ===== 7. Complete Testing Scripts =====

/* 
Complete API Testing Flow - Copy these curl commands to test the workflow:

=== PHASE 1: Campaign A First Closure ===
*/

// 1. First closure of Campaign A
curl -X POST "http://localhost:8080/api/v1/insights/closure/close" \
-H "Content-Type: application/json" \
-d '{
  "userId": "user123",
  "companyId": "comp456",
  "campaignId": "campaignA",
  "closureDate": "2025-06-01"
}'
// Expected: {"closureCount": 1, "action": "HIDDEN_UNTIL_NEXT_LOGIN", "message": "This insight will be hidden until your next login."}

// 2. Check closure status after first close
curl "http://localhost:8080/api/v1/insights/closure/debug/closure-status/user123/comp456/campaignA?date=2025-06-01"
// Expected: {"closureCount": 1, "isCampaignClosed": true, "isCampaignTemporarilyClosed": true, "isCampaignPermanentlyBlocked": false}

// 3. Try rotation immediately after first close (should be blocked)
curl "http://localhost:8080/api/v1/rotatecampaign/next?date=20250601&company=comp456&userId=user123"
// Expected: Error or different campaign (Campaign A filtered out)

// 4. Try rotation next day (Campaign A can appear again if eligible)
curl "http://localhost:8080/api/v1/rotatecampaign/next?date=20250602&company=comp456&userId=user123"
// Expected: Could return Campaign A again (if eligible by frequency/display/week rules)

/*
=== PHASE 2: Campaign A Second Closure ===
*/

// 5. Second closure of Campaign A
curl -X POST "http://localhost:8080/api/v1/insights/closure/close" \
-H "Content-Type: application/json" \
-d '{
  "userId": "user123",
  "companyId": "comp456",
  "campaignId": "campaignA",
  "closureDate": "2025-06-02"
}'
// Expected: {"closureCount": 2, "action": "PROMPT_CAMPAIGN_PREFERENCE", "isGlobalPrompt": false, "requiresUserInput": true}

// 6. User says "No, don't show me Campaign A again"
curl -X POST "http://localhost:8080/api/v1/insights/closure/preference" \
-H "Content-Type: application/json" \
-d '{
  "userId": "user123",
  "companyId": "comp456",
  "campaignId": "campaignA",
  "wantsToSee": false,
  "isGlobalResponse": false,
  "reason": "Not relevant to my business",
  "preferenceDate": "2025-06-02"
}'
// Expected: "Preference recorded successfully"

/*
=== PHASE 3: 1-Month Wait Period (NO CAMPAIGNS) ===
*/

// 7. Check wait period status
curl "http://localhost:8080/api/v1/insights/closure/check-wait-period/user123/comp456?date=2025-06-15"
// Expected: {"inWaitPeriod": true, "checkDate": "2025-06-15", "waitDetails": [...]}

// 8. Try rotation during wait period (Day 10)
curl "http://localhost:8080/api/v1/rotatecampaign/next?date=20250612&company=comp456&userId=user123"
// Expected: "You are in a waiting period. No campaigns available at this time."

// 9. Try rotation during wait period (Day 20)
curl "http://localhost:8080/api/v1/rotatecampaign/next?date=20250622&company=comp456&userId=user123"
// Expected: "You are in a waiting period. No campaigns available at this time."

// 10. Try rotation at end of wait period (still blocked)
curl "http://localhost:8080/api/v1/rotatecampaign/next?date=20250701&company=comp456&userId=user123"
// Expected: "You are in a waiting period. No campaigns available at this time."

/*
=== PHASE 4: After Wait Period (NEW CAMPAIGNS ONLY) ===
*/

// 11. Check wait period after 1 month
curl "http://localhost:8080/api/v1/insights/closure/check-wait-period/user123/comp456?date=2025-07-03"
// Expected: {"inWaitPeriod": false}

// 12. Check Campaign A status (should be permanently blocked)
curl "http://localhost:8080/api/v1/insights/closure/debug/closure-status/user123/comp456/campaignA?date=2025-07-03"
// Expected: {"isCampaignPermanentlyBlocked": true, "isCampaignClosed": true}

// 13. Try rotation after wait period (should get different campaign)
curl "http://localhost:8080/api/v1/rotatecampaign/next?date=20250703&company=comp456&userId=user123"
// Expected: Campaign B, C, or D (NEVER Campaign A)

/*
=== PHASE 5: Campaign B Lifecycle ===
*/

// 14. Close Campaign B first time
curl -X POST "http://localhost:8080/api/v1/insights/closure/close" \
-H "Content-Type: application/json" \
-d '{
  "userId": "user123",
  "companyId": "comp456",
  "campaignId": "campaignB",
  "closureDate": "2025-07-05"
}'
// Expected: {"closureCount": 1, "action": "HIDDEN_UNTIL_NEXT_LOGIN"}

// 15. Try rotation next day (Campaign B can appear again)
curl "http://localhost:8080/api/v1/rotatecampaign/next?date=20250706&company=comp456&userId=user123"
// Expected: Could return Campaign B again

// 16. Close Campaign B second time
curl -X POST "http://localhost:8080/api/v1/insights/closure/close" \
-H "Content-Type: application/json" \
-d '{
  "userId": "user123",
  "companyId": "comp456",
  "campaignId": "campaignB",
  "closureDate": "2025-07-06"
}'
// Expected: {"closureCount": 2, "action": "PROMPT_GLOBAL_PREFERENCE", "isGlobalPrompt": true, "requiresUserInput": true}

/*
=== PHASE 6A: User Wants Future Insights ===
*/

// 17A. User says "Yes, I want future insights"
curl -X POST "http://localhost:8080/api/v1/insights/closure/preference" \
-H "Content-Type: application/json" \
-d '{
  "userId": "user123",
  "companyId": "comp456",
  "campaignId": "campaignB",
  "wantsToSee": true,
  "isGlobalResponse": true,
  "reason": "I want to see insights but not this one",
  "preferenceDate": "2025-07-06"
}'
// Expected: "Preference recorded successfully"

// 18A. Check if back in wait period
curl "http://localhost:8080/api/v1/insights/closure/check-wait-period/user123/comp456?date=2025-07-15"
// Expected: {"inWaitPeriod": true} (another 1-month wait)

/*
=== PHASE 6B: User Doesn't Want Future Insights ===
*/

// 17B. User says "No, I don't want future insights" (GLOBAL OPT-OUT)
curl -X POST "http://localhost:8080/api/v1/insights/closure/preference" \
-H "Content-Type: application/json" \
-d '{
  "userId": "user123",
  "companyId": "comp456",
  "campaignId": "campaignB",
  "wantsToSee": false,
  "isGlobalResponse": true,
  "reason": "Too many notifications",
  "preferenceDate": "2025-07-06"
}'
// Expected: "Preference recorded successfully"

// 18B. Check global opt-out status
curl "http://localhost:8080/api/v1/insights/closure/check-optout/user123"
// Expected: true

// 19B. Try rotation after global opt-out
curl "http://localhost:8080/api/v1/rotatecampaign/next?date=20250707&company=comp456&userId=user123"
// Expected: "User has opted out of all insights"

/*
=== DEBUG AND ADMIN ENDPOINTS ===
*/

// Debug: Get all closures for user
curl "http://localhost:8080/api/v1/insights/closure/debug/all-closures/user123/comp456"

// Debug: Reset a specific campaign closure
curl -X POST "http://localhost:8080/api/v1/insights/closure/debug/reset-closure" \
-H "Content-Type: application/json" \
-d '{
  "userId": "user123",
  "companyId": "comp456",
  "campaignId": "campaignA"
}'

// Get closure statistics for a campaign
curl "http://localhost:8080/api/v1/insights/closure/statistics/campaignA"

// ===== 8. Database Verification Queries =====

/*
After running the tests above, verify database state with these SQL queries:
*/

-- Check closure records
SELECT 
    user_id,
    campaign_id, 
    closure_count,
    next_eligible_date,
    closure_reason,
    permanent_closed,
    created_date,
    updated_date
FROM user_insight_closure 
WHERE user_id = 'user123' 
ORDER BY updated_date DESC;

-- Expected after "don't see Campaign A again":
-- campaignA: closure_count=2, next_eligible_date='2025-07-02', closure_reason='Not relevant'

-- Check global preferences
SELECT 
    user_id,
    insights_enabled,
    opt_out_date,
    opt_out_reason
FROM user_global_preference 
WHERE user_id = 'user123';

-- Expected after global opt-out:
-- insights_enabled=false, opt_out_date='2025-07-06', opt_out_reason='Too many notifications'

-- Check campaign trackers
SELECT 
    user_id,
    campaign_id,
    remaining_weekly_frequency,
    remaining_display_cap,
    last_view_date,
    week_start_date
FROM user_campaign_tracker 
WHERE user_id = 'user123' 
ORDER BY last_view_date DESC;

// ===== 9. Expected Business Logic Summary =====

/*
COMPLETE USER JOURNEY SUMMARY:

Day 1 (2025-06-01):
├─ User sees Campaign A
├─ Close → closureCount=1 → Hide until next login
└─ Rotation blocked for Campaign A

Day 2 (2025-06-02):
├─ User can see Campaign A again (if eligible)
├─ Close Campaign A → closureCount=2 → Prompt user
└─ User says "don't see again" → Campaign A permanently blocked + 1-month wait

Wait Period (2025-06-02 to 2025-07-02):
├─ NO campaigns shown to user (not A, not B, NOTHING)
└─ All rotation calls return: "You are in a waiting period"

After Wait (2025-07-03+):
├─ Campaign A = Permanently blocked forever
├─ Campaign B/C/D = Can be shown
└─ Normal rotation resumes with OTHER campaigns

Campaign B Lifecycle:
├─ First close → Hide until next login
├─ Second close → "Want future insights?" (global question)
├─ No → Global opt-out (no campaigns ever)
└─ Yes → Another 1-month wait for this campaign only

KEY BUSINESS RULES:
✅ First closure = Hide until next login
✅ Second closure = Ask preference
✅ "Don't see again" = Campaign permanently blocked + 1-month wait period
✅ During wait period = NO campaigns at all
✅ After wait period = Different campaigns only, blocked campaign never returns
✅ Second closure of ANY campaign after previous closures = Global preference question
✅ Global opt-out = No campaigns ever again
*/

// ===== 10. Troubleshooting Guide =====

/*
If tests don't work as expected:

1. CHECK CLOSURE RECORD:
curl "http://localhost:8080/api/v1/insights/closure/debug/closure-status/user123/comp456/campaignA?date=2025-06-15"

2. CHECK WAIT PERIOD:
curl "http://localhost:8080/api/v1/insights/closure/check-wait-period/user123/comp456?date=2025-06-15"

3. CHECK ALL CLOSURES:
curl "http://localhost:8080/api/v1/insights/closure/debug/all-closures/user123/comp456"

4. RESET IF NEEDED:
curl -X POST "http://localhost:8080/api/v1/insights/closure/debug/reset-closure" \
-H "Content-Type: application/json" \
-d '{"userId": "user123", "companyId": "comp456", "campaignId": "campaignA"}'

5. CHECK APPLICATION LOGS:
Look for log entries with "ROTATION REQUEST START" and "User X is in 1-month wait period"

6. VERIFY DATE PARSING:
Make sure date formats are consistent (yyyy-MM-dd or yyyyMMdd)

COMMON ISSUES:
- Wrong campaign ID in tests
- Date format issues
- Cache problems (restart application if needed)
- Database transaction not committed
- Wrong company ID or user ID
*/

// ===== 11. Additional Repository Methods (if needed) =====

// Add to UserInsightClosureRepository.java if not already present:

@Query("SELECT c FROM UserInsightClosure c WHERE c.userId = :userId " +
       "AND c.companyId = :companyId " +
       "AND c.nextEligibleDate IS NOT NULL " +
       "AND c.nextEligibleDate > :currentDate")
List<UserInsightClosure> findCampaignsInWaitPeriod(
        @Param("userId") String userId, 
        @Param("companyId") String companyId,
        @Param("currentDate") Date currentDate);

@Query("SELECT c FROM UserInsightClosure c WHERE c.campaignId = :campaignId")
List<UserInsightClosure> findByCampaignId(@Param("campaignId") String campaignId);

@Query("SELECT COUNT(c) FROM UserInsightClosure c " +
       "WHERE c.campaignId = :campaignId " +
       "AND c.closureCount >= :minClosureCount")
long countClosuresByCampaign(
        @Param("campaignId") String campaignId,
        @Param("minClosureCount") Integer minClosureCount);

// ===== 12. Application Properties (if needed) =====

/*
Add to application.properties for better logging:

logging.level.com.usbank.corp.dcr.api.service.UserInsightClosureService=DEBUG
logging.level.com.usbank.corp.dcr.api.service.UserCampaignRotationService=DEBUG
logging.level.com.usbank.corp.dcr.api.controller.UserInsightClosureController=DEBUG

# For better date handling
spring.jackson.date-format=yyyy-MM-dd HH:mm:ss
spring.jackson.time-zone=UTC
*/
```