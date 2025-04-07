1. Test Basic Select Query
First, check if you can retrieve the campaign that's being selected:
sqlCopy-- Basic query to test campaign retrieval
SELECT * FROM campaigns WHERE id = '<campaign_id>';

-- Replace <campaign_id> with one of your actual campaign IDs
2. Test the Join Query
Check if the join between campaigns and campaign_company_mapping works:
sqlCopy-- Test join query for company and date
SELECT c.* 
FROM campaigns c
INNER JOIN campaign_company_mapping m ON c.id = m.campaign_id
WHERE m.company_id = 'Meta'
  AND c.start_date <= '2025-05-15' 
  AND c.end_date >= '2025-05-15'
  AND (c.visibility IS NULL OR c.visibility != 'COMPLETED')
  AND (c.status = 'ACTIVE' OR c.status = 'SCHEDULED' OR c.status = 'INPROGRESS')
ORDER BY c.created_date ASC;
3. Test Basic Update Statement
Try a simple update to see if it works:
sqlCopy-- Test basic update
UPDATE campaigns
SET visibility = 'VISIBLE'
WHERE id = '<campaign_id>';

-- Replace <campaign_id> with one of your actual campaign IDs
4. Test Complete Update Statement
Test the full update that's failing:
sqlCopy-- Test complete update
UPDATE campaigns
SET visibility = 'VISIBLE',
    frequency_per_week = 2,
    display_capping = 7,
    updated_date = GETDATE(),
    requested_date = GETDATE(),
    start_week_of_requested_date = GETDATE(),
    rotation_status = NULL
WHERE id = '<campaign_id>';

-- Replace <campaign_id> with one of your actual campaign IDs
5. Check Table Structure
Verify the table structure to ensure all columns exist:
sqlCopy-- Check table structure
EXEC sp_columns 'campaigns';

-- Check constraints
SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
WHERE TABLE_NAME = 'campaigns';

-- Check foreign keys
SELECT * FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_CATALOG = DB_NAME()
  AND UNIQUE_CONSTRAINT_NAME IN (
      SELECT CONSTRAINT_NAME 
      FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
      WHERE TABLE_NAME = 'campaigns'
  );
6. Check for Triggers
Check if there are any triggers on the table that might be causing issues:
sqlCopy-- Check for triggers
SELECT * FROM sys.triggers
WHERE parent_id = OBJECT_ID('campaigns');
7. Check Transaction Isolation Level
Check the current transaction isolation level:
sqlCopy-- Check isolation level
DBCC USEROPTIONS;
8. Test Original Frequency Update
Test updating the original frequency separately:
sqlCopy-- Test original frequency update
UPDATE campaigns
SET original_frequency_per_week = 2
WHERE id = '<campaign_id>'
  AND original_frequency_per_week IS NULL;