-- Recruitment: vacancies per branch and position, candidates (deduplicated, anonymisable), applications moving through a
-- hiring pipeline with full stage history, interviews/trial shifts, offers, and private candidate documents.

CREATE TABLE hr_vacancies (
    id UUID PRIMARY KEY,
    vacancy_number VARCHAR(30) NOT NULL UNIQUE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    position_id UUID REFERENCES hr_positions(id) ON DELETE SET NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    requirements TEXT,
    employment_type VARCHAR(20) NOT NULL,
    openings INT NOT NULL DEFAULT 1 CHECK (openings > 0),
    hired_count INT NOT NULL DEFAULT 0 CHECK (hired_count >= 0),
    salary_min NUMERIC(14, 2) CHECK (salary_min IS NULL OR salary_min >= 0),
    salary_max NUMERIC(14, 2) CHECK (salary_max IS NULL OR salary_max >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'OPEN', 'ON_HOLD', 'FILLED', 'CLOSED', 'CANCELLED')),
    closing_date DATE,
    hiring_manager_id UUID REFERENCES users(id) ON DELETE SET NULL,
    opened_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    close_reason VARCHAR(500),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min)
);

CREATE INDEX idx_hr_vacancies_branch ON hr_vacancies(branch_id, status);

CREATE TRIGGER hr_vacancies_set_updated_at
BEFORE UPDATE ON hr_vacancies
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_candidates (
    id UUID PRIMARY KEY,
    first_name VARCHAR(80) NOT NULL,
    last_name VARCHAR(80),
    phone VARCHAR(20),
    email VARCHAR(255),
    source VARCHAR(20) NOT NULL CHECK (source IN ('WALK_IN', 'REFERRAL', 'JOB_BOARD', 'SOCIAL_MEDIA', 'AGENCY', 'OTHER')),
    referred_by_employee_id UUID REFERENCES hr_employees(id) ON DELETE SET NULL,
    notes VARCHAR(2000),
    -- set when personal data was removed (data protection); the row stays so pipeline statistics remain correct
    anonymised_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_candidates_phone ON hr_candidates(phone);
CREATE INDEX idx_hr_candidates_email ON hr_candidates(LOWER(email));

CREATE TRIGGER hr_candidates_set_updated_at
BEFORE UPDATE ON hr_candidates
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_candidate_documents (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES hr_candidates(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(200) NOT NULL,
    uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_candidate_documents_candidate ON hr_candidate_documents(candidate_id);

CREATE TABLE hr_applications (
    id UUID PRIMARY KEY,
    vacancy_id UUID NOT NULL REFERENCES hr_vacancies(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES hr_candidates(id) ON DELETE CASCADE,
    stage VARCHAR(20) NOT NULL CHECK (stage IN ('APPLIED', 'SCREENING', 'INTERVIEW', 'TRIAL_SHIFT', 'OFFER', 'HIRED', 'REJECTED', 'WITHDRAWN')),
    reject_reason VARCHAR(500),
    offer_contract_type VARCHAR(20),
    offer_salary_type VARCHAR(20),
    offer_salary_amount NUMERIC(14, 2) CHECK (offer_salary_amount IS NULL OR offer_salary_amount >= 0),
    offer_weekly_hours NUMERIC(5, 2),
    offer_start_date DATE,
    offer_status VARCHAR(20) CHECK (offer_status IS NULL OR offer_status IN ('SENT', 'ACCEPTED', 'DECLINED')),
    offer_sent_at TIMESTAMPTZ,
    offer_responded_at TIMESTAMPTZ,
    employee_id UUID REFERENCES hr_employees(id) ON DELETE SET NULL,
    hired_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (vacancy_id, candidate_id)
);

CREATE INDEX idx_hr_applications_candidate ON hr_applications(candidate_id);

CREATE TRIGGER hr_applications_set_updated_at
BEFORE UPDATE ON hr_applications
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE hr_application_events (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES hr_applications(id) ON DELETE CASCADE,
    from_stage VARCHAR(20),
    to_stage VARCHAR(20) NOT NULL,
    note VARCHAR(500),
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_application_events_application ON hr_application_events(application_id, created_at);

CREATE TABLE hr_interviews (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES hr_applications(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('PHONE', 'IN_PERSON', 'VIDEO', 'TRIAL_SHIFT')),
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 30 CHECK (duration_minutes > 0 AND duration_minutes <= 720),
    interviewer_id UUID REFERENCES users(id) ON DELETE SET NULL,
    location VARCHAR(200),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SCHEDULED', 'PASSED', 'FAILED', 'NO_SHOW', 'CANCELLED')),
    score INT CHECK (score IS NULL OR (score >= 1 AND score <= 5)),
    feedback VARCHAR(2000),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hr_interviews_application ON hr_interviews(application_id);
CREATE INDEX idx_hr_interviews_interviewer ON hr_interviews(interviewer_id, scheduled_at);

CREATE TRIGGER hr_interviews_set_updated_at
BEFORE UPDATE ON hr_interviews
FOR EACH ROW EXECUTE FUNCTION set_updated_at();
