DO $$
DECLARE
    target_role TEXT;
BEGIN
    FOREACH target_role IN ARRAY ARRAY['anon', 'authenticated']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = target_role) THEN
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM %I',
                target_role
            );
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM %I',
                target_role
            );
            EXECUTE format(
                'REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM %I',
                target_role
            );
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL PRIVILEGES ON TABLES FROM %I',
                target_role
            );
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL PRIVILEGES ON SEQUENCES FROM %I',
                target_role
            );
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM %I',
                target_role
            );
        END IF;
    END LOOP;
END
$$;

REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
