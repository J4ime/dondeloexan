-- DondeLoExan schema (v3: catálogo global + biblioteca por usuario)
-- Autenticación: Supabase Auth (schema auth). public.profiles se crea por trigger
-- en el alta de usuario, junto con las 13 plataformas por defecto.
--
-- Dos mundos:
--   1) CATÁLOGO GLOBAL (sin user_id): movies, tv_shows, tv_seasons, tv_episodes,
--      critic_reviews, fa_movie_data y content_lists. Lo alimentan TODOS los
--      usuarios con sesión (RLS: cualquier authenticated puede leer/escribir).
--      Identificación: CONTENT_ID como PK (p. ej. "tmdb-123" o "imdb-tt…").
--      La app hace UPSERT (merge por content_id), NUNCA borra catálogo.
--   2) USUARIOS (user_id + RLS auth.uid()): user_movies, user_tv_shows
--      (relación biblioteca), tv_show_progress, search_history, user_platforms,
--      blacklist. La app hace snapshot (borra filas del usuario y re-sube).
--
-- La PK (user_id, id) con id UUID autogenerado (DEFAULT gen_random_uuid()) en las
-- tablas de usuario permite descargar todo el contenido de un usuario rápido y
-- evita que la app tenga que enviar ids. En user_movies/user_tv_shows la PK es
-- (user_id, content_id): relación pura.
-- Política: recrear (DROP si existe).

DROP TABLE IF EXISTS public.content_lists CASCADE;
DROP TABLE IF EXISTS public.tv_episodes CASCADE;
DROP TABLE IF EXISTS public.tv_seasons CASCADE;
DROP TABLE IF EXISTS public.critic_reviews CASCADE;
DROP TABLE IF EXISTS public.fa_movie_data CASCADE;
DROP TABLE IF EXISTS public.blacklist CASCADE;
DROP TABLE IF EXISTS public.search_history CASCADE;
DROP TABLE IF EXISTS public.tv_show_progress CASCADE;
DROP TABLE IF EXISTS public.user_platforms CASCADE;
DROP TABLE IF EXISTS public.user_tv_shows CASCADE;
DROP TABLE IF EXISTS public.user_movies CASCADE;
DROP TABLE IF EXISTS public.tv_shows CASCADE;
DROP TABLE IF EXISTS public.movies CASCADE;
DROP TABLE IF EXISTS public.profiles CASCADE;

-- ── Perfil de usuario ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.profiles (
    user_id      UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (user_id) VALUES (NEW.id)
    ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO public.user_platforms (user_id, platform_name, is_active)
    SELECT NEW.id, platform_name, 1
    FROM (VALUES
        ('Netflix'), ('Prime Video'), ('Disney+'), ('HBO Max'),
        ('Movistar+'), ('Apple TV+'), ('Paramount+'), ('SkyShowtime'),
        ('Filmin'), ('Atresplayer'), ('Mitele'), ('RTVE Play'), ('Cine')
    ) AS p(platform_name)
    ON CONFLICT (user_id, platform_name) DO NOTHING;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ── CATÁLOGO GLOBAL (sin user_id; todos los usuarios lo leen y alimentan) ──
-- Las columnas de listados (cast, directores, plataformas, enlaces…) guardan
-- JSON (TEXT) fiel a los modelos de dominio (PersonInfo, StreamingAvailability…).

CREATE TABLE IF NOT EXISTS public.movies (
    content_id              TEXT PRIMARY KEY,
    tmdb_id                 INTEGER,
    imdb_id                 TEXT,
    title                   TEXT NOT NULL,
    original_title          TEXT,
    year                    INTEGER,
    release_date            TEXT,
    spanish_release_date    TEXT,
    digital_release_date    TEXT,
    tv_release_date         TEXT,
    duration_minutes        INTEGER,
    rating_tmdb             DOUBLE PRECISION,
    rating_imdb             DOUBLE PRECISION,
    rating_rt               INTEGER,
    rating_metacritic       INTEGER,
    rating_filmaffinity     DOUBLE PRECISION,
    certification           TEXT,
    synopsis                TEXT,
    cover_url               TEXT,
    backdrop_url            TEXT,
    directors               TEXT,
    writers                 TEXT,
    cast_json           TEXT,
    music                   TEXT,
    cinematography          TEXT,
    production_companies    TEXT,
    genres                  TEXT,
    countries               TEXT,
    streaming_platforms     TEXT,
    external_links          TEXT,
    collection_tmdb_id      INTEGER,
    updated_at              BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS public.tv_shows (
    content_id              TEXT PRIMARY KEY,
    tmdb_id                 INTEGER,
    imdb_id                 TEXT,
    title                   TEXT NOT NULL,
    original_title          TEXT,
    year                    INTEGER,
    release_date            TEXT,
    spanish_release_date    TEXT,
    digital_release_date    TEXT,
    tv_release_date         TEXT,
    duration_minutes        INTEGER,
    rating_tmdb             DOUBLE PRECISION,
    rating_imdb             DOUBLE PRECISION,
    rating_rt               INTEGER,
    rating_metacritic       INTEGER,
    rating_filmaffinity     DOUBLE PRECISION,
    certification           TEXT,
    synopsis                TEXT,
    cover_url               TEXT,
    backdrop_url            TEXT,
    directors               TEXT,
    writers                 TEXT,
    cast_json           TEXT,
    music                   TEXT,
    cinematography          TEXT,
    production_companies    TEXT,
    genres                  TEXT,
    countries               TEXT,
    streaming_platforms     TEXT,
    external_links          TEXT,
    total_episodes          INTEGER,
    num_seasons             INTEGER,
    released_episodes       INTEGER,
    in_production           INTEGER,
    series_status           TEXT,
    next_episode_air_date   TEXT,
    next_episode_number     INTEGER,
    next_episode_season     INTEGER,
    updated_at              BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS public.tv_seasons (
    content_id      TEXT NOT NULL REFERENCES public.tv_shows(content_id) ON DELETE CASCADE,
    season_number   INTEGER NOT NULL,
    name            TEXT NOT NULL DEFAULT '',
    episode_count   INTEGER,
    air_date        TEXT,
    overview        TEXT,
    poster_path     TEXT,
    tmdb_season_id  INTEGER,
    PRIMARY KEY (content_id, season_number)
);

CREATE TABLE IF NOT EXISTS public.tv_episodes (
    content_id      TEXT NOT NULL REFERENCES public.tv_shows(content_id) ON DELETE CASCADE,
    season_number   INTEGER NOT NULL,
    episode_number  INTEGER NOT NULL,
    name            TEXT NOT NULL DEFAULT '',
    overview        TEXT,
    air_date        TEXT,
    still_path      TEXT,
    vote_average    DOUBLE PRECISION,
    episode_type    TEXT,
    PRIMARY KEY (content_id, season_number, episode_number)
);

CREATE TABLE IF NOT EXISTS public.critic_reviews (
    content_id   TEXT PRIMARY KEY,
    reviews_json TEXT NOT NULL,
    cached_at    BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS public.fa_movie_data (
    content_id             TEXT PRIMARY KEY,
    fa_id                  INTEGER,
    fa_rating              DOUBLE PRECISION,
    platform_releases_json TEXT,
    cached_at              BIGINT NOT NULL
);

-- Listas derivadas del detalle (colección, similares, pelis del director y
-- precuelas/secuelas o spinoffs). content_id = la fila de catálogo que las pide;
-- cada resultado relacionado va en una fila con su posición.
CREATE TABLE IF NOT EXISTS public.content_lists (
    content_id        TEXT NOT NULL,
    list_type         TEXT NOT NULL
                      CHECK (list_type IN ('collection', 'similar', 'director_movies', 'relationships')),
    pos                 INTEGER NOT NULL,
    related_content_id TEXT NOT NULL,
    related_data      TEXT,
    PRIMARY KEY (content_id, list_type, pos)
);

CREATE INDEX IF NOT EXISTS idx_content_lists_content ON public.content_lists (content_id);
CREATE INDEX IF NOT EXISTS idx_tv_episodes_show ON public.tv_episodes (content_id);

-- ── USUARIOS (biblioteca + estado) ──────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.user_movies (
    user_id            UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_id         TEXT NOT NULL REFERENCES public.movies(content_id),
    status             TEXT NOT NULL DEFAULT 'POR_VER'
                       CHECK (status IN ('POR_VER', 'YA_VISTA', 'SIGUIENDO')),
    liked              INTEGER NOT NULL DEFAULT 0,
    watched_at         BIGINT,
    added_at           BIGINT NOT NULL,
    last_refreshed_at  BIGINT,
    fa_id              INTEGER,
    PRIMARY KEY (user_id, content_id)
);

CREATE TABLE IF NOT EXISTS public.user_tv_shows (
    user_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_id          TEXT NOT NULL REFERENCES public.tv_shows(content_id),
    status              TEXT NOT NULL DEFAULT 'POR_VER'
                        CHECK (status IN ('POR_VER', 'YA_VISTA', 'SIGUIENDO')),
    liked               INTEGER NOT NULL DEFAULT 0,
    total_episodes      INTEGER,
    added_at            BIGINT NOT NULL,
    next_episode_air_date TEXT,
    next_episode_number INTEGER,
    next_episode_season INTEGER,
    series_status       TEXT,
    in_production       INTEGER,
    num_seasons         INTEGER,
    last_watched_at     BIGINT,
    finished_at         BIGINT,
    released_episodes   INTEGER,
    last_refreshed_at   BIGINT,
    fa_id               INTEGER,
    PRIMARY KEY (user_id, content_id)
);

CREATE TABLE IF NOT EXISTS public.tv_show_progress (
    id          UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_id  TEXT NOT NULL REFERENCES public.tv_shows(content_id),
    season      INTEGER NOT NULL,
    episode     INTEGER NOT NULL,
    watched_at  BIGINT NOT NULL,
    PRIMARY KEY (user_id, id),
    -- Se permiten varias filas por capítulo (cada una con su propio id).
    CONSTRAINT fk_tv_show_progress_show FOREIGN KEY (content_id)
        REFERENCES public.tv_shows(content_id)
);

CREATE TABLE IF NOT EXISTS public.search_history (
    id          UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    query       TEXT NOT NULL,
    searched_at BIGINT NOT NULL,
    PRIMARY KEY (user_id, id)
);

CREATE TABLE IF NOT EXISTS public.user_platforms (
    id            UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    platform_name TEXT NOT NULL,
    is_active     INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, id),
    CONSTRAINT uq_user_platforms_name UNIQUE (user_id, platform_name)
);

CREATE TABLE IF NOT EXISTS public.blacklist (
    id         UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_id TEXT NOT NULL,
    title      TEXT NOT NULL,
    type       TEXT NOT NULL,
    added_at   BIGINT NOT NULL,
    PRIMARY KEY (user_id, id),
    CONSTRAINT uq_blacklist_user_content UNIQUE (user_id, content_id)
);

-- ── Row Level Security ──────────────────────────────────────────────────────

ALTER TABLE public.profiles        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.movies          ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tv_shows        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tv_seasons      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tv_episodes     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.critic_reviews  ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fa_movie_data   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.content_lists   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_movies     ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_tv_shows   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tv_show_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.search_history   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_platforms   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blacklist        ENABLE ROW LEVEL SECURITY;

-- Catálogo global compartido: cualquier usuario autenticado lee y alimenta.
CREATE POLICY catalog_select_all ON public.movies
    FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_insert_all ON public.movies
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY catalog_update_all ON public.movies
    FOR UPDATE TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY catalog_delete_all ON public.movies
    FOR DELETE TO authenticated USING (true);

CREATE POLICY catalog_select_all ON public.tv_shows
    FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_insert_all ON public.tv_shows
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY catalog_update_all ON public.tv_shows
    FOR UPDATE TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY catalog_delete_all ON public.tv_shows
    FOR DELETE TO authenticated USING (true);

CREATE POLICY catalog_select_all ON public.tv_seasons
    FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_insert_all ON public.tv_seasons
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY catalog_update_all ON public.tv_seasons
    FOR UPDATE TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY catalog_delete_all ON public.tv_seasons
    FOR DELETE TO authenticated USING (true);

CREATE POLICY catalog_select_all ON public.tv_episodes
    FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_insert_all ON public.tv_episodes
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY catalog_update_all ON public.tv_episodes
    FOR UPDATE TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY catalog_delete_all ON public.tv_episodes
    FOR DELETE TO authenticated USING (true);

CREATE POLICY catalog_select_all ON public.critic_reviews
    FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_insert_all ON public.critic_reviews
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY catalog_update_all ON public.critic_reviews
    FOR UPDATE TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY catalog_delete_all ON public.critic_reviews
    FOR DELETE TO authenticated USING (true);

CREATE POLICY catalog_select_all ON public.fa_movie_data
    FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_insert_all ON public.fa_movie_data
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY catalog_update_all ON public.fa_movie_data
    FOR UPDATE TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY catalog_delete_all ON public.fa_movie_data
    FOR DELETE TO authenticated USING (true);

CREATE POLICY catalog_select_all ON public.content_lists
    FOR SELECT TO authenticated USING (true);
CREATE POLICY catalog_insert_all ON public.content_lists
    FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY catalog_update_all ON public.content_lists
    FOR UPDATE TO authenticated USING (true) WITH CHECK (true);
CREATE POLICY catalog_delete_all ON public.content_lists
    FOR DELETE TO authenticated USING (true);

-- Tablas por usuario: cada usuario solo ve/edita sus filas.
CREATE POLICY profiles_select_own ON public.profiles
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY profiles_insert_own ON public.profiles
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY profiles_update_own ON public.profiles
    FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY user_movies_select_own ON public.user_movies
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY user_movies_insert_own ON public.user_movies
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY user_movies_update_own ON public.user_movies
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY user_movies_delete_own ON public.user_movies
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY user_tv_shows_select_own ON public.user_tv_shows
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY user_tv_shows_insert_own ON public.user_tv_shows
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY user_tv_shows_update_own ON public.user_tv_shows
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY user_tv_shows_delete_own ON public.user_tv_shows
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY tv_show_progress_select_own ON public.tv_show_progress
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY tv_show_progress_insert_own ON public.tv_show_progress
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY tv_show_progress_update_own ON public.tv_show_progress
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY tv_show_progress_delete_own ON public.tv_show_progress
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY search_history_select_own ON public.search_history
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY search_history_insert_own ON public.search_history
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY search_history_delete_own ON public.search_history
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY user_platforms_select_own ON public.user_platforms
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY user_platforms_insert_own ON public.user_platforms
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY user_platforms_update_own ON public.user_platforms
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY user_platforms_delete_own ON public.user_platforms
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY blacklist_select_own ON public.blacklist
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY blacklist_insert_own ON public.blacklist
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY blacklist_delete_own ON public.blacklist
    FOR DELETE USING (auth.uid() = user_id);

-- ── Permisos de rol ─────────────────────────────────────────────────────────

GRANT USAGE ON SCHEMA public TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO authenticated;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO authenticated;

-- �� Merge null-safe del cat�logo global (RPC catalog_merge) ���������������
--
-- El upsert nativo de PostgREST (resolution=merge-duplicates) sobrescribe los
-- valores existentes con NULL, as� que la sync de una biblioteca "pobre"
-- destruir�a el cat�logo rico ya cacheado por otros usuarios.
--
-- Esta funci�n (security definer, invocada por la app v�a /rpc/catalog_merge)
-- re-ejecuta las columnas del payload con COALESCE: si la fila nueva trae NULL,
-- se conserva el valor existente. Nunca borra filas.

CREATE OR REPLACE FUNCTION public.catalog_merge(_p_table text, _p_rows jsonb)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    _catalog_tables CONSTANT text[] := ARRAY[
        'movies', 'tv_shows', 'tv_seasons', 'tv_episodes',
        'critic_reviews', 'fa_movie_data', 'content_lists'
    ];
    _table_name text;
    _pk_cols text;
    _col_list text;
    _update_set text;
BEGIN
    IF _p_table IS NULL OR lower(_p_table) <> ALL(_catalog_tables) THEN
        RAISE EXCEPTION 'tabla de cat�logo no permitida: %', _p_table;
    END IF;
    IF jsonb_typeof(_p_rows) <> 'array' OR jsonb_array_length(_p_rows) = 0 THEN
        RETURN;
    END IF;

    _table_name := format('public.%I', lower(_p_table));

    -- Columnas del payload que existen en la tabla (las claves no usadas se ignoran).
    SELECT string_agg(quote_ident(k.key), ', ' ORDER BY k.key)
    INTO _col_list
    FROM (
        SELECT DISTINCT o.key
        FROM jsonb_array_elements(_p_rows) r
        CROSS JOIN LATERAL jsonb_object_keys(r) AS o(key)
    ) k
    WHERE k.key IN (
        SELECT a.attname
        FROM pg_attribute a
        WHERE a.attrelid = _table_name::regclass
          AND a.attnum > 0
          AND NOT a.attisdropped
    );

    IF _col_list IS NULL OR _col_list = '' THEN
        RAISE EXCEPTION 'sin columnas v�lidas en el payload';
    END IF;

    -- Primary key real de la tabla (m�ltiple en seasons/episodes).
    SELECT string_agg(quote_ident(a.attname), ', ' ORDER BY k.ord)
    INTO _pk_cols
    FROM pg_index i
    JOIN pg_class c ON c.oid = i.indrelid
    CROSS JOIN LATERAL unnest(i.indkey) WITH ORDINALITY k(attnum, ord)
    JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = k.attnum
    WHERE c.oid = _table_name::regclass
      AND i.indisprimary;

    IF _pk_cols IS NULL OR _pk_cols = '' THEN
        RAISE EXCEPTION 'tabla sin primary key: %', _p_table;
    END IF;

    SELECT string_agg(
        format('%I = coalesce(excluded.%I, t.%I)', k.key, k.key, k.key),
        ', ' ORDER BY k.key
    )
    INTO _update_set
    FROM (
        SELECT DISTINCT o.key
        FROM jsonb_array_elements(_p_rows) r
        CROSS JOIN LATERAL jsonb_object_keys(r) AS o(key)
    ) k
    WHERE k.key IN (
        SELECT a.attname
        FROM pg_attribute a
        WHERE a.attrelid = _table_name::regclass
          AND a.attnum > 0
          AND NOT a.attisdropped
    );

    EXECUTE format(
        'insert into %s as t (%s) '
        || 'select %s from jsonb_populate_recordset(null::%s, $1) '
        || 'on conflict (%s) do update set %s',
        _table_name, _col_list, _col_list, _table_name, _pk_cols, _update_set
    ) USING _p_rows;
END;
$$;

REVOKE ALL ON FUNCTION public.catalog_merge(text, jsonb) FROM public;
GRANT EXECUTE ON FUNCTION public.catalog_merge(text, jsonb) TO anon, authenticated;