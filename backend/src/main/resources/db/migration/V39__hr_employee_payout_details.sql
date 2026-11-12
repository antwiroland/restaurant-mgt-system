-- How each employee is paid, for payroll payment files: the preferred method and the bank or Mobile Money details.
-- Account numbers are personal data: shown in full only to payroll staff who prepare or pay payroll, and to the
-- employee; others with payroll access see them masked.
ALTER TABLE hr_employees ADD COLUMN payout_method VARCHAR(20)
    CHECK (payout_method IS NULL OR payout_method IN ('BANK', 'MOBILE_MONEY', 'CASH'));
ALTER TABLE hr_employees ADD COLUMN bank_name VARCHAR(120);
ALTER TABLE hr_employees ADD COLUMN bank_branch VARCHAR(120);
ALTER TABLE hr_employees ADD COLUMN bank_account_name VARCHAR(160);
ALTER TABLE hr_employees ADD COLUMN bank_account_number VARCHAR(40);
ALTER TABLE hr_employees ADD COLUMN momo_network VARCHAR(30);
ALTER TABLE hr_employees ADD COLUMN momo_number VARCHAR(20);
ALTER TABLE hr_employees ADD COLUMN payout_updated_at TIMESTAMPTZ;
