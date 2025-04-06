-- First drop the existing tables (in the right order to avoid constraint issues)
IF EXISTS (SELECT * FROM sys.foreign_keys WHERE name = 'FK_campaign')
    ALTER TABLE [dbo].[campaign_company_mapping] DROP CONSTRAINT [FK_campaign];

IF OBJECT_ID('[dbo].[campaign_company_mapping]', 'U') IS NOT NULL
    DROP TABLE [dbo].[campaign_company_mapping];

-- Now recreate the tables with proper constraints

-- 1. Make sure the campaigns table has the right primary key
IF EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
    WHERE CONSTRAINT_TYPE = 'PRIMARY KEY' AND TABLE_NAME = 'campaigns')
    ALTER TABLE [dbo].[campaigns] DROP CONSTRAINT PK_campaigns;

-- Add primary key if needed
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS 
    WHERE CONSTRAINT_TYPE = 'PRIMARY KEY' AND TABLE_NAME = 'campaigns')
    ALTER TABLE [dbo].[campaigns] 
    ADD CONSTRAINT PK_campaigns PRIMARY KEY (id);

-- 2. Create new mapping table with proper foreign key
CREATE TABLE [dbo].[campaign_company_mapping] (
    [id] VARCHAR(36) PRIMARY KEY,
    [campaign_id] VARCHAR(36) NOT NULL,
    [company_id] VARCHAR(100) NOT NULL,
    CONSTRAINT [uk_campaign_company] UNIQUE ([campaign_id], [company_id]),
    CONSTRAINT [FK_campaign] FOREIGN KEY ([campaign_id]) 
        REFERENCES [dbo].[campaigns] ([id]) 
        ON DELETE CASCADE
);

-- 3. Create indexes for performance
CREATE INDEX [idx_campaign_id] ON [dbo].[campaign_company_mapping] ([campaign_id]);
CREATE INDEX [idx_company_id] ON [dbo].[campaign_company_mapping] ([company_id]);