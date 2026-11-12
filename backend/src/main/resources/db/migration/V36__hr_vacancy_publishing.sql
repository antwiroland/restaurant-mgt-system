-- Public careers page: HR chooses which open vacancies are advertised on the website.
-- Existing vacancies start unpublished, so nothing appears publicly until someone publishes it.
ALTER TABLE hr_vacancies ADD COLUMN published BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE hr_vacancies ADD COLUMN published_at TIMESTAMPTZ;

CREATE INDEX idx_hr_vacancies_published ON hr_vacancies(status, published);
