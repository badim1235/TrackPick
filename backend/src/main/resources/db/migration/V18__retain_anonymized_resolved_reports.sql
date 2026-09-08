ALTER TABLE content_reports
    DROP CONSTRAINT IF EXISTS content_reports_reporter_user_id_fkey,
    DROP CONSTRAINT IF EXISTS content_reports_recommendation_id_fkey,
    DROP CONSTRAINT IF EXISTS content_reports_reported_user_fk;

ALTER TABLE content_reports
    ALTER COLUMN reporter_user_id DROP NOT NULL,
    ALTER COLUMN recommendation_id DROP NOT NULL,
    ALTER COLUMN reported_user_id DROP NOT NULL;

ALTER TABLE content_reports
    ADD CONSTRAINT content_reports_reporter_user_fk
        FOREIGN KEY (reporter_user_id) REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT content_reports_recommendation_fk
        FOREIGN KEY (recommendation_id) REFERENCES recommendations (id) ON DELETE SET NULL,
    ADD CONSTRAINT content_reports_reported_user_fk
        FOREIGN KEY (reported_user_id) REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT content_reports_pending_references_check
        CHECK (
            status <> 'PENDING'
            OR (
                reporter_user_id IS NOT NULL
                AND recommendation_id IS NOT NULL
                AND reported_user_id IS NOT NULL
            )
        );
