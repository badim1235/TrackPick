ALTER TABLE users
    ADD COLUMN activity VARCHAR(20) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE users
    ADD CONSTRAINT users_activity_check
        CHECK (activity IN ('NORMAL', 'FLAGGED', 'BAN'));

ALTER TABLE content_reports
    ADD COLUMN reported_user_id UUID,
    ADD COLUMN resolved_by_user_id UUID,
    ADD COLUMN resolution_action VARCHAR(20);

UPDATE content_reports report
SET reported_user_id = recommendation.recommender_user_id
FROM recommendations recommendation
WHERE recommendation.id = report.recommendation_id;

ALTER TABLE content_reports
    ALTER COLUMN reported_user_id SET NOT NULL,
    ADD CONSTRAINT content_reports_reported_user_fk
        FOREIGN KEY (reported_user_id) REFERENCES users (id),
    ADD CONSTRAINT content_reports_resolved_by_user_fk
        FOREIGN KEY (resolved_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT content_reports_resolution_action_check
        CHECK (resolution_action IS NULL OR resolution_action IN ('DISMISS', 'BAN'));

CREATE INDEX content_reports_reported_status_created_idx
    ON content_reports (reported_user_id, status, created_at DESC);

CREATE INDEX content_reports_resolved_by_user_id_idx
    ON content_reports (resolved_by_user_id)
    WHERE resolved_by_user_id IS NOT NULL;

CREATE INDEX users_activity_nickname_idx
    ON users (activity, public_nickname);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE content_reports FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE content_reports FROM authenticated;
    END IF;
END
$$;
