SELECT
    [Insight Type],
    [Insight Sub Type],
    [Insight] AS OriginalInsight,
    CASE 
        WHEN [Insight] LIKE '%already uses FX%' THEN
            REPLACE(
                REPLACE(
                    REPLACE(
                        REPLACE(
                            [Insight],
                            -- Company name replacement - handles the first word up to the first space
                            LEFT([Insight], CHARINDEX(' ', [Insight] + ' ') - 1), 
                            'Company'
                        ),
                        -- Amount replacement - safer extraction
                        CASE WHEN CHARINDEX('amounting to', [Insight]) > 0 
                             THEN SUBSTRING([Insight], 
                                  CHARINDEX('amounting to', [Insight]), 
                                  CASE WHEN CHARINDEX('(', [Insight], CHARINDEX('amounting to', [Insight])) > 0
                                       THEN CHARINDEX('(', [Insight], CHARINDEX('amounting to', [Insight])) - CHARINDEX('amounting to', [Insight])
                                       ELSE LEN([Insight]) END)
                             ELSE '' END,
                        'Amount'
                    ),
                    -- Countries replacement - safer extraction
                    CASE WHEN CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight])) > 0
                         THEN SUBSTRING([Insight],
                              CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight])),
                              CASE WHEN CHARINDEX('(', [Insight], CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight]))) > 0
                                   THEN CHARINDEX('(', [Insight], CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight]))) - CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight]))
                                   ELSE LEN([Insight]) END)
                         ELSE '' END,
                    'Countries'
                ),
                -- Currency replacement - safer extraction
                CASE WHEN CHARINDEX('made in ', [Insight]) > 0
                     THEN SUBSTRING([Insight],
                          CHARINDEX('made in ', [Insight]),
                          CASE WHEN CHARINDEX('(', [Insight], CHARINDEX('made in ', [Insight])) > 0
                               THEN CHARINDEX('(', [Insight], CHARINDEX('made in ', [Insight])) - CHARINDEX('made in ', [Insight])
                               ELSE LEN([Insight]) END)
                     ELSE '' END,
                'Currency'
            )
        WHEN [Insight] LIKE '%could have used USD FX services%' THEN
            REPLACE(
                REPLACE(
                    REPLACE(
                        REPLACE(
                            [Insight],
                            -- Company name replacement - handles the first word up to the first space
                            LEFT([Insight], CHARINDEX(' ', [Insight] + ' ') - 1),
                            'Company'
                        ),
                        -- Amount replacement - safer extraction
                        CASE WHEN CHARINDEX('amounting to', [Insight]) > 0 
                             THEN SUBSTRING([Insight], 
                                  CHARINDEX('amounting to', [Insight]), 
                                  CASE WHEN CHARINDEX('(', [Insight], CHARINDEX('amounting to', [Insight])) > 0
                                       THEN CHARINDEX('(', [Insight], CHARINDEX('amounting to', [Insight])) - CHARINDEX('amounting to', [Insight])
                                       ELSE LEN([Insight]) END)
                             ELSE '' END,
                        'Amount'
                    ),
                    -- Countries replacement - safer extraction
                    CASE WHEN CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight])) > 0
                         THEN SUBSTRING([Insight],
                              CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight])),
                              CASE WHEN CHARINDEX('(', [Insight], CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight]))) > 0
                                   THEN CHARINDEX('(', [Insight], CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight]))) - CHARINDEX('in ', [Insight], CHARINDEX('(', [Insight]))
                                   ELSE LEN([Insight]) END)
                         ELSE '' END,
                    'Countries'
                ),
                -- Currency replacement - safer extraction
                CASE WHEN CHARINDEX('made in ', [Insight]) > 0
                     THEN SUBSTRING([Insight],
                          CHARINDEX('made in ', [Insight]),
                          CASE WHEN CHARINDEX('(', [Insight], CHARINDEX('made in ', [Insight])) > 0
                               THEN CHARINDEX('(', [Insight], CHARINDEX('made in ', [Insight])) - CHARINDEX('made in ', [Insight])
                               ELSE LEN([Insight]) END)
                     ELSE '' END,
                'Currency'
            )
        ELSE [Insight] -- If no match, return the original insight
    END AS CleanedInsight
FROM 
    [dbo].[vw_export_test]
WHERE 
    [Insight Type] = 'Money Movements'
    AND [Insight Sub Type] = 'FX';