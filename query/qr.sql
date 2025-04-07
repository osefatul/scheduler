SELECT
    [Insight Type],
    [Insight Sub Type],
    CASE 
        WHEN [Insight] LIKE 'While %' THEN
            REPLACE(
                REPLACE(
                    REPLACE(
                        REPLACE(
                            REPLACE(
                                [Insight],
                                SUBSTRING([Insight], 1, CHARINDEX(' already uses FX', [Insight]) - 1),
                                '[Company]'
                            ),
                            SUBSTRING([Insight], CHARINDEX(' amounting to', [Insight]) + 12, 
                                     CHARINDEX(' (TTM)', [Insight]) - CHARINDEX(' amounting to', [Insight]) - 12),
                            '[Amount]'
                        ),
                        SUBSTRING([Insight], CHARINDEX(' in ', [Insight]) + 4, 
                                 CHARINDEX(' countries', [Insight]) - CHARINDEX(' in ', [Insight]) - 4),
                        '[Countries]'
                    ),
                    SUBSTRING([Insight], CHARINDEX(' made in ', [Insight]) + 8, 
                             CHARINDEX(' currency', [Insight]) - CHARINDEX(' made in ', [Insight]) - 8),
                    '[Currency]'
                ),
                -- This replaces additional company names in the same insight
                CASE 
                    WHEN [Insight] LIKE '%could have used USB FX services%' 
                    THEN REPLACE([Insight], 
                        SUBSTRING([Insight], 
                            CHARINDEX(' could have used USB FX services', [Insight]) - LEN(
                                SUBSTRING([Insight], 1, 
                                    CHARINDEX(' could have used USB FX services', [Insight]) - 1)
                            ),
                            LEN(
                                SUBSTRING([Insight], 1, 
                                    CHARINDEX(' could have used USB FX services', [Insight]) - 1)
                            )
                        ),
                        '[Company]'
                    )
                    ELSE [Insight]
                END
            )
        WHEN [Insight] LIKE '%could have used USB FX services%' THEN
            REPLACE(
                REPLACE(
                    REPLACE(
                        [Insight],
                        SUBSTRING([Insight], 1, CHARINDEX(' could have used USB FX services', [Insight]) - 1),
                        '[Company]'
                    ),
                    SUBSTRING([Insight], CHARINDEX(' amounting to', [Insight]) + 12, 
                             CHARINDEX(' (TTM)', [Insight]) - CHARINDEX(' amounting to', [Insight]) - 12),
                    '[Amount]'
                ),
                SUBSTRING([Insight], CHARINDEX(' in ', [Insight]) + 4, 
                         CHARINDEX(' countries', [Insight]) - CHARINDEX(' in ', [Insight]) - 4),
                '[Countries]'
            )
        ELSE [Insight]
    END AS CleanedInsight
FROM [dbo].[vn_export_test]
WHERE 
    [Insight Type] = 'Money Movement'
    AND [Insight Sub Type] = 'FX';