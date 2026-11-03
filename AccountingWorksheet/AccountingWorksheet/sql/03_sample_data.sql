-- ============================================================================
--  OPTIONAL sample data (October 2026) - uses the placeholder chart of accounts.
--  Skip this script if you replaced the chart with your instructor's accounts.
--  Expected worksheet result:
--    Unadjusted TB 154,000.00 | Adjustments 8,000.00 | Adjusted TB 157,000.00
--    Net income 24,000.00     | Balance sheet difference 0.00
-- ============================================================================
USE accounting_db;

INSERT INTO journal_entries (entry_id, entry_date, description, entry_type) VALUES
(1,  '2026-10-01', 'Owner invested cash in the business',         'REGULAR'),
(2,  '2026-10-02', 'Purchased supplies on account',               'REGULAR'),
(3,  '2026-10-03', 'Paid one-year insurance premium',             'REGULAR'),
(4,  '2026-10-05', 'Purchased office equipment for cash',         'REGULAR'),
(5,  '2026-10-10', 'Received cash for services rendered',         'REGULAR'),
(6,  '2026-10-15', 'Services rendered on account',                'REGULAR'),
(7,  '2026-10-20', 'Paid salaries',                               'REGULAR'),
(8,  '2026-10-22', 'Paid utilities',                              'REGULAR'),
(9,  '2026-10-25', 'Paid part of accounts payable',               'REGULAR'),
(10, '2026-10-28', 'Owner withdrew cash for personal use',        'REGULAR'),
(11, '2026-10-31', 'Adjusting: supplies used during the month',   'ADJUSTING'),
(12, '2026-10-31', 'Adjusting: insurance expired',                'ADJUSTING'),
(13, '2026-10-31', 'Adjusting: depreciation of office equipment', 'ADJUSTING'),
(14, '2026-10-31', 'Adjusting: accrued salaries',                 'ADJUSTING');

INSERT INTO journal_lines (entry_id, account_code, debit, credit) VALUES
(1,  '1010', 100000.00,      0.00), (1,  '3010',      0.00, 100000.00),
(2,  '1030',   6000.00,      0.00), (2,  '2010',      0.00,   6000.00),
(3,  '1040',  12000.00,      0.00), (3,  '1010',      0.00,  12000.00),
(4,  '1110',  40000.00,      0.00), (4,  '1010',      0.00,  40000.00),
(5,  '1010',  30000.00,      0.00), (5,  '4010',      0.00,  30000.00),
(6,  '1020',  20000.00,      0.00), (6,  '4010',      0.00,  20000.00),
(7,  '5010',  15000.00,      0.00), (7,  '1010',      0.00,  15000.00),
(8,  '5020',   3000.00,      0.00), (8,  '1010',      0.00,   3000.00),
(9,  '2010',   2000.00,      0.00), (9,  '1010',      0.00,   2000.00),
(10, '3020',   5000.00,      0.00), (10, '1010',      0.00,   5000.00),
(11, '5030',   4000.00,      0.00), (11, '1030',      0.00,   4000.00),
(12, '5040',   1000.00,      0.00), (12, '1040',      0.00,   1000.00),
(13, '5050',   1000.00,      0.00), (13, '1120',      0.00,   1000.00),
(14, '5010',   2000.00,      0.00), (14, '2020',      0.00,   2000.00);
