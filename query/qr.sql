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
                                '[Company]',
                                1,
                                LEN(SUBSTRING([Insight], 1, CHARINDEX(' already uses FX', [Insight]) - 1))
                            ),
                            SUBSTRING([Insight], CHARINDEX(' amounting to', [Insight]) + 12, 
                                     CHARINDEX(' (TTM)', [Insight]) - CHARINDEX(' amounting to', [Insight]) - 12),
                            '[Amount]',
                            1,
                            LEN(SUBSTRING([Insight], CHARINDEX(' amounting to', [Insight]) + 12, 
                                     CHARINDEX(' (TTM)', [Insight]) - CHARINDEX(' amounting to', [Insight]) - 12))
                        ),
                        SUBSTRING([Insight], CHARINDEX(' in ', [Insight]) + 4, 
                                 CHARINDEX(' countries', [Insight]) - CHARINDEX(' in ', [Insight]) - 4),
                        '[Countries]',
                        1,
                        LEN(SUBSTRING([Insight], CHARINDEX(' in ', [Insight]) + 4, 
                                 CHARINDEX(' countries', [Insight]) - CHARINDEX(' in ', [Insight]) - 4))
                    ),
                    SUBSTRING([Insight], CHARINDEX(' made in ', [Insight]) + 8, 
                             CHARINDEX(' currency', [Insight]) - CHARINDEX(' made in ', [Insight]) - 8),
                    '[Currency]',
                    1,
                    LEN(SUBSTRING([Insight], CHARINDEX(' made in ', [Insight]) + 8, 
                             CHARINDEX(' currency', [Insight]) - CHARINDEX(' made in ', [Insight]) - 8))
                ),
                -- This replaces additional company names in the same insight
                CASE 
                    WHEN [Insight] LIKE '%could have used USB FX services%' 
                    THEN REPLACE([Insight], 
                        SUBSTRING([Insight], 1, 
                            CHARINDEX(' could have used USB FX services', [Insight]) - 1),
                        '[Company]',
                        1,
                        LEN(SUBSTRING([Insight], 1, 
                            CHARINDEX(' could have used USB FX services', [Insight]) - 1))
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
                        '[Company]',
                        1,
                        LEN(SUBSTRING([Insight], 1, CHARINDEX(' could have used USB FX services', [Insight]) - 1))
                    ),
                    SUBSTRING([Insight], CHARINDEX(' amounting to', [Insight]) + 12, 
                             CHARINDEX(' (TTM)', [Insight]) - CHARINDEX(' amounting to', [Insight]) - 12),
                    '[Amount]',
                    1,
                    LEN(SUBSTRING([Insight], CHARINDEX(' amounting to', [Insight]) + 12, 
                             CHARINDEX(' (TTM)', [Insight]) - CHARINDEX(' amounting to', [Insight]) - 12))
                ),
                SUBSTRING([Insight], CHARINDEX(' in ', [Insight]) + 4, 
                         CHARINDEX(' countries', [Insight]) - CHARINDEX(' in ', [Insight]) - 4),
                '[Countries]',
                1,
                LEN(SUBSTRING([Insight], CHARINDEX(' in ', [Insight]) + 4, 
                         CHARINDEX(' countries', [Insight]) - CHARINDEX(' in ', [Insight]) - 4))
            )
        ELSE [Insight]
    END AS CleanedInsight
FROM [dbo].[vn_export_test]
WHERE 
    [Insight Type] = 'Money Movement'
    AND [Insight Sub Type] = 'FX';