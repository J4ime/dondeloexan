-- Merge null-safe del catálogo global (migración v4).
--
-- El upsert nativo de PostgREST (resolution=merge-duplicates) sobrescribe los
-- valores existentes con NULL, así que la sync de una biblioteca "pobre"
-- destruiría el catálogo rico ya cacheado por otros usuarios.
--
-- Esta función (security definer, ejecutada por la app vía /rpc/catalog_merge)
-- re-ejecuta las columnas del payload con COALESCE: si la fila nueva trae NULL,
-- se conserva el valor existente. Nunca borra filas.

create or replace function public.catalog_merge(_p_table text, _p_rows jsonb)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    _catalog_tables constant text[] := array[
        'movies', 'tv_shows', 'tv_seasons', 'tv_episodes',
        'critic_reviews', 'fa_movie_data', 'content_lists'
    ];
    _table_name text;
    _pk_cols text;
    _col_list text;
    _update_set text;
begin
    if _p_table is null or lower(_p_table) <> all(_catalog_tables) then
        raise exception 'tabla de catálogo no permitida: %', _p_table;
    end if;
    if jsonb_typeof(_p_rows) <> 'array' or jsonb_array_length(_p_rows) = 0 then
        return;
    end if;

    _table_name := format('public.%I', lower(_p_table));

    -- Columnas del payload que existen en la tabla (las claves no usadas se ignoran).
    select string_agg(quote_ident(k.key), ', ' order by k.key)
    into _col_list
    from (
        select distinct o.key
        from jsonb_array_elements(_p_rows) r
        cross join lateral jsonb_object_keys(r) as o(key)
    ) k
    where k.key in (
        select a.attname
        from pg_attribute a
        where a.attrelid = _table_name::regclass
          and a.attnum > 0
          and not a.attisdropped
    );

    if _col_list is null or _col_list = '' then
        raise exception 'sin columnas válidas en el payload';
    end if;

    -- Primary key real de la tabla (múltiple en seasons/episodes).
    select string_agg(quote_ident(a.attname), ', ' order by k.ord)
    into _pk_cols
    from pg_index i
    join pg_class c on c.oid = i.indrelid
    cross join lateral unnest(i.indkey) with ordinality k(attnum, ord)
    join pg_attribute a on a.attrelid = c.oid and a.attnum = k.attnum
    where c.oid = _table_name::regclass
      and i.indisprimary;

    if _pk_cols is null or _pk_cols = '' then
        raise exception 'tabla sin primary key: %', _p_table;
    end if;

    select string_agg(
        format('%I = coalesce(excluded.%I, t.%I)', k.key, k.key, k.key),
        ', ' order by k.key
    )
    into _update_set
    from (
        select distinct o.key
        from jsonb_array_elements(_p_rows) r
        cross join lateral jsonb_object_keys(r) as o(key)
    ) k
    where k.key in (
        select a.attname
        from pg_attribute a
        where a.attrelid = _table_name::regclass
          and a.attnum > 0
          and not a.attisdropped
    );

execute format(
        'insert into %s as t (%s) '
        || 'select %s from jsonb_populate_recordset(null::%s, $1) '
        || 'on conflict (%s) do update set %s',
        _table_name, _col_list, _col_list, _table_name, _pk_cols, _update_set
    ) using _p_rows;
end;
$$;

revoke all on function public.catalog_merge(text, jsonb) from public;
grant execute on function public.catalog_merge(text, jsonb) to anon, authenticated;
