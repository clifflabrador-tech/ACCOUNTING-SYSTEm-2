-- ============================================================================
--  Chart of accounts
--  *** PLACEHOLDER ***  Replace the rows below with the chart of accounts
--  given by your instructor, then re-run this script. Keep these rules:
--    * account_type : ASSET | LIABILITY | EQUITY | REVENUE | EXPENSE
--    * normal_side  : DEBIT | CREDIT
--      (contra accounts are the opposite of their type, e.g. Accumulated
--       Depreciation = ASSET / CREDIT, Owner's Drawings = EQUITY / DEBIT)
--  The Income Statement / Balance Sheet placement is derived automatically.
-- ============================================================================
USE accounting_db;

-- Uncomment to reload a new chart (only possible while there are no journal entries):
-- DELETE FROM accounts;

INSERT INTO accounts (account_code, account_title, account_type, normal_side) VALUES
-- Assets
('1010', 'Cash',                                        'ASSET',     'DEBIT'),
('1020', 'Accounts Receivable',                         'ASSET',     'DEBIT'),
('1030', 'Supplies',                                    'ASSET',     'DEBIT'),
('1040', 'Prepaid Insurance',                           'ASSET',     'DEBIT'),
('1110', 'Office Equipment',                            'ASSET',     'DEBIT'),
('1120', 'Accumulated Depreciation - Office Equipment', 'ASSET',     'CREDIT'),
-- Liabilities
('2010', 'Accounts Payable',                            'LIABILITY', 'CREDIT'),
('2020', 'Salaries Payable',                            'LIABILITY', 'CREDIT'),
-- Owner's equity
('3010', 'Owner''s Capital',                            'EQUITY',    'CREDIT'),
('3020', 'Owner''s Drawings',                           'EQUITY',    'DEBIT'),
-- Revenue
('4010', 'Service Revenue',                             'REVENUE',   'CREDIT'),
-- Expenses
('5010', 'Salaries Expense',                            'EXPENSE',   'DEBIT'),
('5020', 'Utilities Expense',                           'EXPENSE',   'DEBIT'),
('5030', 'Supplies Expense',                            'EXPENSE',   'DEBIT'),
('5040', 'Insurance Expense',                           'EXPENSE',   'DEBIT'),
('5050', 'Depreciation Expense',                        'EXPENSE',   'DEBIT');
