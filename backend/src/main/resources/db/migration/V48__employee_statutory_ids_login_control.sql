-- Statutory identifiers for payroll and returns (Ghana): SSNIT number, Ghana Card (national ID) and TIN.
ALTER TABLE hr_employees ADD COLUMN ssnit_number VARCHAR(20);
ALTER TABLE hr_employees ADD COLUMN national_id VARCHAR(20);
ALTER TABLE hr_employees ADD COLUMN tin VARCHAR(20);
CREATE UNIQUE INDEX ux_hr_employees_ssnit ON hr_employees(ssnit_number) WHERE ssnit_number IS NOT NULL;
CREATE UNIQUE INDEX ux_hr_employees_national_id ON hr_employees(national_id) WHERE national_id IS NOT NULL;

-- Set when HR switched the employee's login off on a suspension or exit, so it is switched back on when they return.
ALTER TABLE hr_employees ADD COLUMN login_disabled_by_hr BOOLEAN NOT NULL DEFAULT FALSE;
