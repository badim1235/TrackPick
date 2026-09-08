CREATE INDEX IF NOT EXISTS auth_ip_request_limits_updated_at_idx
    ON auth_ip_request_limits (updated_at);

CREATE INDEX IF NOT EXISTS signup_ip_events_created_at_idx
    ON signup_ip_events (created_at);

CREATE INDEX IF NOT EXISTS signup_ip_blocks_blocked_until_idx
    ON signup_ip_blocks (blocked_until);

CREATE INDEX IF NOT EXISTS content_reports_resolved_at_idx
    ON content_reports (resolved_at)
    WHERE status <> 'PENDING' AND resolved_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS recommendations_user_activity_idx
    ON recommendations (recommender_user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS votes_track_date_created_idx
    ON votes (track_id, voted_on, created_at);
