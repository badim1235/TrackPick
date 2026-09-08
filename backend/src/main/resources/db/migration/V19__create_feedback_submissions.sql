CREATE TABLE feedback_submissions (
    id UUID PRIMARY KEY,
    category VARCHAR(30) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT feedback_submissions_category_check
        CHECK (category IN ('ERROR', 'UI_USABILITY', 'OTHER')),
    CONSTRAINT feedback_submissions_content_check
        CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000)
);

CREATE INDEX feedback_submissions_created_idx
    ON feedback_submissions (created_at DESC, id DESC);

ALTER TABLE feedback_submissions ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE feedback_submissions FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE feedback_submissions FROM authenticated;
    END IF;
END
$$;
