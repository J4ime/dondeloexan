-- DondeLoExan schema
-- Réplica de la BD local de la app (Room, db version 20) + usuarios.
-- Autenticación: se usa Supabase Auth (schema auth). Tabla pública extra:
--   public.profiles (perfil atado a auth.users, creado por trigger en signup).
-- Contenido multi-usuario: columna user_id en cada tabla + Row Level Security
-- (cada usuario solo ve/edita sus propias filas vía auth.uid()).
-- Política: recrear (DROP si existe). Tipos fieles a Room:
--   Long (epoch ms) -> BIGINT, Int -> INTEGER, Float -> DOUBLE PRECISION,
--   Text -> TEXT, booleanos Room (0/1) -> INTEGER con defaults 0/1,
--   enum WatchStatus -> TEXT + CHECK.

DROP TABLE IF EXISTS public.critic_reviews CASCADE;
DROP TABLE IF EXISTS public.fa_movie_data CASCADE;
DROP TABLE IF EXISTS public.blacklist CASCADE;
DROP TABLE IF EXISTS public.search_history CASCADE;
DROP TABLE IF EXISTS public.tv_show_progress CASCADE;
DROP TABLE IF EXISTS public.tv_shows CASCADE;
DROP TABLE IF EXISTS public.movies CASCADE;
DROP TABLE IF EXISTS public.user_platforms CASCADE;
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

-- ── Contenido por usuario ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.movies (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    local_id            BIGINT,
    content_id          TEXT,
    tmdb_id             INTEGER,
    imdb_id             TEXT,
    title               TEXT NOT NULL,
    year                INTEGER,
    release_date        TEXT,
    poster_url          TEXT,
    rating_tmdb         DOUBLE PRECISION,
    rating_imdb         DOUBLE PRECISION,
    certification       TEXT,
    status              TEXT NOT NULL DEFAULT 'POR_VER'
                        CHECK (status IN ('POR_VER', 'YA_VISTA', 'SIGUIENDO')),
    liked               INTEGER NOT NULL DEFAULT 0,
    streaming_platforms TEXT,
    watched_at          BIGINT,
    added_at            BIGINT NOT NULL,
    last_refreshed_at   BIGINT,
    fa_id               INTEGER
);
-- local_id = id de la fila Room local; permite sync idempotente (upsert por usuario).
ALTER TABLE ONLY public.movies ADD CONSTRAINT uq_movies_user_id_local_id UNIQUE (user_id, local_id);

CREATE TABLE IF NOT EXISTS public.tv_shows (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    local_id                BIGINT,
    content_id              TEXT,
    tmdb_id                 INTEGER,
    imdb_id                 TEXT,
    title                   TEXT NOT NULL,
    year                    INTEGER,
    poster_url              TEXT,
    rating_tmdb             DOUBLE PRECISION,
    rating_imdb             DOUBLE PRECISION,
    certification           TEXT,
    status                  TEXT NOT NULL DEFAULT 'POR_VER'
                            CHECK (status IN ('POR_VER', 'YA_VISTA', 'SIGUIENDO')),
    liked                   INTEGER NOT NULL DEFAULT 0,
    total_episodes          INTEGER,
    streaming_platforms     TEXT,
    added_at                BIGINT NOT NULL,
    next_episode_air_date   TEXT,
    next_episode_number     INTEGER,
    next_episode_season     INTEGER,
    series_status           TEXT,
    in_production           INTEGER,
    num_seasons             INTEGER,
    last_watched_at         BIGINT,
    finished_at             BIGINT,
    released_episodes       INTEGER,
    last_refreshed_at       BIGINT,
    fa_id                   INTEGER
);
-- La FK de progreso usa (user_id, id).
ALTER TABLE ONLY public.tv_shows ADD CONSTRAINT uq_tv_shows_user_id_id UNIQUE (user_id, id);
-- local_id = id de la fila Room local; permite sync idempotente.
ALTER TABLE ONLY public.tv_shows ADD CONSTRAINT uq_tv_shows_user_id_local_id UNIQUE (user_id, local_id);

CREATE TABLE IF NOT EXISTS public.tv_show_progress (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     UUID NOT NULL,
    local_id    BIGINT,
    tv_show_id  BIGINT NOT NULL,
    season      INTEGER NOT NULL,
    episode     INTEGER NOT NULL,
    watched_at  BIGINT NOT NULL,
    CONSTRAINT fk_tv_show_progress_show
        FOREIGN KEY (user_id, tv_show_id) REFERENCES public.tv_shows (user_id, id)
        ON DELETE CASCADE
);
-- Coincide con onConflict = REPLACE de TvShowProgressDao: 1 fila por capítulo.
CREATE UNIQUE INDEX IF NOT EXISTS ux_tv_show_progress_show_season_episode
    ON public.tv_show_progress (user_id, tv_show_id, season, episode);
-- local_id = id de la fila Room local; permite sync idempotente del progreso.
ALTER TABLE ONLY public.tv_show_progress ADD CONSTRAINT uq_tv_show_progress_user_local
    UNIQUE (user_id, local_id);

CREATE TABLE IF NOT EXISTS public.search_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    local_id    BIGINT,
    query       TEXT NOT NULL,
    searched_at BIGINT NOT NULL
);
ALTER TABLE ONLY public.search_history ADD CONSTRAINT uq_search_history_user_local
    UNIQUE (user_id, local_id);

CREATE TABLE IF NOT EXISTS public.user_platforms (
    user_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    platform_name TEXT NOT NULL,
    is_active     INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id, platform_name)
);

CREATE TABLE IF NOT EXISTS public.blacklist (
    user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_id TEXT NOT NULL,
    title      TEXT NOT NULL,
    type       TEXT NOT NULL,
    added_at   BIGINT NOT NULL,
    PRIMARY KEY (user_id, content_id)
);

CREATE TABLE IF NOT EXISTS public.critic_reviews (
    user_id      UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_id   TEXT NOT NULL,
    reviews_json TEXT NOT NULL,
    cached_at    BIGINT NOT NULL,
    PRIMARY KEY (user_id, content_id)
);

CREATE TABLE IF NOT EXISTS public.fa_movie_data (
    user_id                UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_id             TEXT NOT NULL,
    fa_id                  INTEGER,
    fa_rating              DOUBLE PRECISION,
    platform_releases_json TEXT,
    cached_at              BIGINT NOT NULL,
    PRIMARY KEY (user_id, content_id)
);

-- Índices de filtrado por usuario (RLS) ──────────────────────────────────────

CREATE INDEX IF NOT EXISTS idx_movies_user_id        ON public.movies (user_id);
CREATE INDEX IF NOT EXISTS idx_tv_shows_user_id      ON public.tv_shows (user_id);
CREATE INDEX IF NOT EXISTS idx_tv_show_progress_user ON public.tv_show_progress (user_id);
CREATE INDEX IF NOT EXISTS idx_search_history_user   ON public.search_history (user_id);

-- ── Row Level Security ──────────────────────────────────────────────────────

ALTER TABLE public.profiles      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.movies        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tv_shows      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tv_show_progress ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.search_history   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_platforms   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.blacklist        ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.critic_reviews   ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fa_movie_data    ENABLE ROW LEVEL SECURITY;

-- profiles: el usuario lee/edita su propio perfil.
CREATE POLICY profiles_select_own ON public.profiles
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY profiles_insert_own ON public.profiles
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY profiles_update_own ON public.profiles
    FOR UPDATE USING (auth.uid() = user_id);

-- películas / series / progreso / historial (id identity + user_id).
CREATE POLICY movies_select_own ON public.movies
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY movies_insert_own ON public.movies
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY movies_update_own ON public.movies
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY movies_delete_own ON public.movies
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY tv_shows_select_own ON public.tv_shows
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY tv_shows_insert_own ON public.tv_shows
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY tv_shows_update_own ON public.tv_shows
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY tv_shows_delete_own ON public.tv_shows
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

CREATE POLICY critic_reviews_select_own ON public.critic_reviews
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY critic_reviews_insert_own ON public.critic_reviews
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY critic_reviews_update_own ON public.critic_reviews
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY critic_reviews_delete_own ON public.critic_reviews
    FOR DELETE USING (auth.uid() = user_id);

CREATE POLICY fa_movie_data_select_own ON public.fa_movie_data
    FOR SELECT USING (auth.uid() = user_id);
CREATE POLICY fa_movie_data_insert_own ON public.fa_movie_data
    FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY fa_movie_data_update_own ON public.fa_movie_data
    FOR UPDATE USING (auth.uid() = user_id);
CREATE POLICY fa_movie_data_delete_own ON public.fa_movie_data
    FOR DELETE USING (auth.uid() = user_id);

-- ── Permisos de rol ─────────────────────────────────────────────────────────

GRANT USAGE ON SCHEMA public TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO authenticated;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO authenticated;