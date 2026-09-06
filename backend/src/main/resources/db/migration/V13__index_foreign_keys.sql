CREATE INDEX IF NOT EXISTS content_reports_recommendation_id_idx
    ON content_reports (recommendation_id);

CREATE INDEX IF NOT EXISTS daily_rankings_genre_id_idx
    ON daily_rankings (genre_id);

CREATE INDEX IF NOT EXISTS daily_rankings_run_date_idx
    ON daily_rankings (ranking_run_id, ranking_date);

CREATE INDEX IF NOT EXISTS daily_rankings_track_id_idx
    ON daily_rankings (track_id);

CREATE INDEX IF NOT EXISTS recommendations_recommender_user_id_idx
    ON recommendations (recommender_user_id);

CREATE INDEX IF NOT EXISTS recommendations_track_genre_idx
    ON recommendations (track_id, primary_genre_id);

CREATE INDEX IF NOT EXISTS track_genres_genre_id_idx
    ON track_genres (genre_id);

CREATE INDEX IF NOT EXISTS votes_track_id_idx
    ON votes (track_id);
