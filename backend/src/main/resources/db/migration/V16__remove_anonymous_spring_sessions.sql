DELETE FROM spring_session AS session
WHERE NOT EXISTS (
    SELECT 1
    FROM spring_session_attributes AS attribute
    WHERE attribute.session_primary_id = session.primary_id
      AND attribute.attribute_name = 'SPRING_SECURITY_CONTEXT'
);
