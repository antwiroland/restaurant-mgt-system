-- What a payroll rule is filed as (SSNIT and PAYE returns, year-to-date figures); null for anything else.
ALTER TABLE hr_payroll_rules ADD COLUMN report_as VARCHAR(20);

-- Unused days of these leave types are paid out in the final pay of someone who leaves.
ALTER TABLE hr_leave_types ADD COLUMN pay_out_on_exit BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE hr_leave_types SET pay_out_on_exit = TRUE WHERE code = 'ANNUAL';

-- ID numbers as they were when the payslip was calculated, for the returns.
ALTER TABLE hr_payslips ADD COLUMN ssnit_number VARCHAR(20);
ALTER TABLE hr_payslips ADD COLUMN tin VARCHAR(20);

-- Loans, salary advances and other deductions taken every pay run until repaid or stopped.
CREATE TABLE hr_payroll_recurring_deductions (
    id                 UUID PRIMARY KEY,
    employee_id        UUID NOT NULL REFERENCES hr_employees(id),
    kind               VARCHAR(20) NOT NULL,
    description        VARCHAR(120) NOT NULL,
    installment_amount NUMERIC(14, 2) NOT NULL,
    total_amount       NUMERIC(14, 2),
    start_date         DATE NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    stopped_reason     VARCHAR(300),
    created_by         UUID,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_hr_recurring_deductions_employee ON hr_payroll_recurring_deductions(employee_id);

CREATE TABLE hr_payroll_recurring_installments (
    id           UUID PRIMARY KEY,
    deduction_id UUID NOT NULL REFERENCES hr_payroll_recurring_deductions(id),
    run_id       UUID NOT NULL REFERENCES hr_payroll_runs(id),
    payslip_id   UUID NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_hr_recurring_installments_deduction ON hr_payroll_recurring_installments(deduction_id);
CREATE INDEX idx_hr_recurring_installments_run ON hr_payroll_recurring_installments(run_id);
