CREATE OR REPLACE FUNCTION public.is_strict_semver(value text)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
STRICT
AS
$$
SELECT
    value ~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-([0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*))?$'
    AND NOT EXISTS (
        SELECT 1
        FROM regexp_split_to_table(
            substring(value FROM '^[0-9]+\.[0-9]+\.[0-9]+-(.*)$'),
            '\.'
        ) AS identifier
        WHERE identifier ~ '^0[0-9]+$'
    );
$$;

DO
$$
DECLARE
    invalid_count bigint;
    invalid_sample text;
BEGIN
    WITH invalid_versions AS (
        SELECT 'models' AS source, version::text AS version FROM public.models
        WHERE NOT public.is_strict_semver(version::text)
        UNION ALL
        SELECT 'notations', version::text FROM public.notations
        WHERE NOT public.is_strict_semver(version::text)
        UNION ALL
        SELECT 'components', version::text FROM public.components
        WHERE NOT public.is_strict_semver(version::text)
        UNION ALL
        SELECT 'relations', version::text FROM public.relations
        WHERE NOT public.is_strict_semver(version::text)
        UNION ALL
        SELECT 'diagrams', version::text FROM public.diagrams
        WHERE NOT public.is_strict_semver(version::text)
    ),
    sample AS (
        SELECT source, version
        FROM invalid_versions
        ORDER BY source, version
        LIMIT 10
    )
    SELECT
        (SELECT count(*) FROM invalid_versions),
        (SELECT string_agg(format('%s=%s', source, version), ', ') FROM sample)
    INTO invalid_count, invalid_sample;

    IF invalid_count > 0 THEN
        RAISE EXCEPTION
            'Cannot enforce strict SemVer: % invalid persisted version(s); sample: %',
            invalid_count,
            invalid_sample;
    END IF;
END;
$$;

ALTER DOMAIN public.version_type DROP CONSTRAINT version_type_check;
ALTER DOMAIN public.version_type
    ADD CONSTRAINT version_type_check CHECK (public.is_strict_semver(VALUE));
