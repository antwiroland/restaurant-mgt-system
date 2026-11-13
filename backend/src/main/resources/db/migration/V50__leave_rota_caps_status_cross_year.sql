-- Most days of a type one person may take in a calendar year (types without a balance: maternity, compassionate...).
ALTER TABLE hr_leave_types ADD COLUMN max_days_per_year NUMERIC(6, 2);
UPDATE hr_leave_types SET max_days_per_year = 84 WHERE code = 'MATERNITY';
UPDATE hr_leave_types SET max_days_per_year = 5 WHERE code = 'PATERNITY';
UPDATE hr_leave_types SET max_days_per_year = 5 WHERE code = 'COMPASSIONATE';

-- Leave running into the next year: the days that fall in the next year (charged to that year's balance).
ALTER TABLE hr_leave_requests ADD COLUMN next_year_days NUMERIC(6, 2) NOT NULL DEFAULT 0;

-- The employee shows "On leave" because of approved leave (switched back automatically when it ends).
ALTER TABLE hr_employees ADD COLUMN status_from_leave BOOLEAN NOT NULL DEFAULT FALSE;
