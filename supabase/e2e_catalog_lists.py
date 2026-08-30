"""E2E fase 2: temporadas, episodios y listas derivadas en el catálogo global.

Simula lo que hace la app vía PostgREST:
1. Usuario A escribe con el RPC catalog_merge (upsert null-safe) en:
   - tv_seasons (2 temporadas), tv_episodes (2 episodios de la 1)
   - content_lists: collection, similar, director_movies, relationships
2. Usuario B las lee SIN haber llamado nunca a las APIs -> la nube es la fuente,
   y respeta orden por pos / season_number / episode_number.
3. Merge null-safe: A re-sube una temporada "pobre" (overview en null); B sigue
   viendo el overview y SÍ aplica el name nuevo.
4. Limpieza: B borra las filas de prueba.

Requiere env: DONDELOEXAN_SUPABASE_URL, DONDELOEXAN_SUPABASE_ANON.
"""

import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

URL = os.environ["DONDELOEXAN_SUPABASE_URL"].rstrip("/")
ANON = os.environ["DONDELOEXAN_SUPABASE_ANON"]

PASSWORD = "Test123456!"
SUFFIX = str(int(time.time()))


def req(method, path, token=None, body=None):
    headers = {
        "apikey": ANON,
        "Content-Type": "application/json",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(URL + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            text = resp.read().decode()
            return resp.status, text
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def rpc_merge(token, table, rows):
    body = {"_p_table": table, "_p_rows": rows}
    return req("POST", "/rest/v1/rpc/catalog_merge", token=token, body=body)


def signup(email):
    status, text = req("POST", "/auth/v1/signup", body={"email": email, "password": PASSWORD})
    print(f"signup {email} -> {status}")
    return status, text


def token(email):
    status, text = req(
        "POST",
        "/auth/v1/token?grant_type=password",
        body={"email": email, "password": PASSWORD},
    )
    print(f"token {email} -> {status}")
    if status != 200:
        raise SystemExit(f"No se pudo autenticar {email}: {text}")
    return json.loads(text)["access_token"]


def get_rows(token_, table, query):
    status, text = req("GET", f"/rest/v1/{table}?{query}", token=token_)
    return status, json.loads(text) if status == 200 else text


def main() -> None:
    a_email = f"e2e_lists_a_{SUFFIX}@example.com"
    b_email = f"e2e_lists_b_{SUFFIX}@example.com"

    for email in (a_email, b_email):
        status, text = signup(email)
        if status not in (200, 201, 422):
            raise SystemExit(f"signup falló: {text}")

    token_a = token(a_email)
    token_b = token(b_email)

    series_id = f"tmdb-e2e-series-{SUFFIX}"
    now = int(time.time() * 1000)
    rel = {"platformName": "Netflix", "pos": 0}

    # 1) Usuario A escribe la serie base, temporadas/episodios/listas vía RPC.
    series_row = {
        "content_id": series_id,
        "title": "Serie E2E Temporadas",
        "tmdb_id": 999_998,
        "updated_at": now,
    }
    status, text = rpc_merge(token_a, "tv_shows", [series_row])
    print(f"A catalog_merge tv_shows -> {status}: {text}")
    if status not in (200, 204):
        raise SystemExit(text)

    seasons = [
        {
            "content_id": series_id,
            "season_number": 1,
            "name": "Temporada 1",
            "episode_count": 2,
            "air_date": "2010-10-31",
            "overview": "El comienzo",
            "tmdb_season_id": 111_111,
        },
        {
            "content_id": series_id,
            "season_number": 2,
            "name": "Temporada 2",
            "episode_count": 0,
            "air_date": None,
            "overview": None,
            "tmdb_season_id": None,
        },
    ]
    episodes = [
        {
            "content_id": series_id,
            "season_number": 1,
            "episode_number": 2,
            "name": "Episodio 2",
            "overview": "La noche fría",
            "air_date": "2010-11-07",
            "still_path": "/la-noche.jpg",
            "vote_average": 8.2,
            "episode_type": "standard",
        },
        {
            "content_id": series_id,
            "season_number": 1,
            "episode_number": 1,
            "name": "Episodio 1",
            "overview": "El despertar",
            "air_date": "2010-10-31",
            "still_path": "/el-despertar.jpg",
            "vote_average": 8.0,
            "episode_type": "season_premiere",
        },
    ]
    preview = {
        "id": "tmdb-603",
        "source": "TMDB",
        "tmdbId": 603,
        "title": "Fight Club",
        "type": "MOVIE",
        "year": 1999,
        "coverUrl": "https://image.tmdb.org/t/p/w500/fc.jpg",
        "directors": ["David Fincher"],
        "ratingImdb": 8.8,
        "genres": ["Drama"],
    }
    lists = [
        # collection
        {"content_id": "collection-10", "list_type": "collection", "pos": 0,
         "related_content_id": "tmdb-550", "related_data": json.dumps(dict(preview, id="tmdb-550", title="Fight Club", tmdbId=550))},
        {"content_id": "collection-10", "list_type": "collection", "pos": 1,
         "related_content_id": "tmdb-603", "related_data": json.dumps(preview)},
        # similar (el content_id es el de la serie)
        {"content_id": series_id, "list_type": "similar", "pos": 0,
         "related_content_id": "tmdb-1408", "related_data": json.dumps(dict(preview, id="tmdb-1408", title="Breaking Bad", tmdbId=1408, type="SERIES"))},
        # director_movies (sin exclusión; la exclusión se aplica en cliente)
        {"content_id": "director-7467", "list_type": "director_movies", "pos": 0,
         "related_content_id": "tmdb-603", "related_data": json.dumps(preview)},
        # relationships (clave series-{wikidata}|{imdb})
        {"content_id": "series-Q13983|tt0903747", "list_type": "relationships", "pos": 0,
         "related_content_id": "tmdb-1408", "related_data": json.dumps(dict(preview, id="tmdb-1408", title="Breaking Bad", tmdbId=1408))},
    ]

    status, text = rpc_merge(token_a, "tv_seasons", seasons)
    print(f"A catalog_merge tv_seasons -> {status}: {text}")
    if status not in (200, 204):
        raise SystemExit(text)

    status, text = rpc_merge(token_a, "tv_episodes", episodes)
    print(f"A catalog_merge tv_episodes -> {status}: {text}")
    if status not in (200, 204):
        raise SystemExit(text)

    status, text = rpc_merge(token_a, "content_lists", lists)
    print(f"A catalog_merge content_lists -> {status}: {text}")
    if status not in (200, 204):
        raise SystemExit(text)

    # 2) Usuario B lee todo sin haber llamado a las APIs.
    status, seasons_b = get_rows(token_b, "tv_seasons", f"content_id=eq.{series_id}&order=season_number.asc")
    print(f"B lee tv_seasons -> {status}: {seasons_b}")
    assert status == 200 and len(seasons_b) == 2, "B no ve las 2 temporadas"
    assert [s["season_number"] for s in seasons_b] == [1, 2], "Orden de temporadas incorrecto"
    assert seasons_b[0]["overview"] == "El comienzo", "overview perdido"

    status, epis = get_rows(token_b, "tv_episodes", f"content_id=eq.{series_id}&order=episode_number.asc")
    print(f"B lee tv_episodes -> {status}: {epis}")
    assert status == 200 and len(epis) == 2, "B no ve los 2 episodios"
    assert [e["episode_number"] for e in epis] == [1, 2], "Orden de episodios incorrecto"
    assert epis[0]["vote_average"] == 8.0 and epis[1]["vote_average"] == 8.2, "vote_average perdido"

    status, coll = get_rows(token_b, "content_lists", f"content_id=eq.collection-10&order=pos.asc")
    print(f"B lee collection -> {status}: {len(coll) if status == 200 else coll}")
    assert status == 200 and len(coll) == 2, "B no ve la collection"
    assert [c["pos"] for c in coll] == [0, 1], "Orden de pos incorrecto"
    assert json.loads(coll[1]["related_data"])["ratingImdb"] == 8.8, "related_data corrupto"

    status, simp = get_rows(token_b, "content_lists", f"content_id=eq.{series_id}&list_type=eq.similar&order=pos.asc")
    print(f"B lee similar -> {status}: {len(simp) if status == 200 else simp}")
    assert status == 200 and len(simp) == 1, "B no ve similar"

    status, dirv = get_rows(token_b, "content_lists", "content_id=eq.director-7467&order=pos.asc")
    print(f"B lee director_movies -> {status}: {len(dirv) if status == 200 else dirv}")
    assert status == 200 and len(dirv) == 1 and dirv[0]["list_type"] == "director_movies", "B no ve director_movies"

    status, rels = get_rows(token_b, "content_lists", "content_id=eq.series-Q13983%7Ctt0903747&order=pos.asc")
    print(f"B lee relationships -> {status}: {len(rels) if status == 200 else rels}")
    assert status == 200 and len(rels) == 1 and rels[0]["related_content_id"] == "tmdb-1408", "B no ve relationships"

    # 3) Merge null-safe en tv_seasons: re-subir S1 "pobre" (overview null, name nuevo).
    poor = dict(seasons[0])
    poor["overview"] = None
    poor["name"] = "Temporada 1 (título tonto)"
    status, text = rpc_merge(token_a, "tv_seasons", [poor])
    print(f"A catalog_merge tv_seasons pobre -> {status}: {text}")
    if status not in (200, 204):
        raise SystemExit(text)

    status, s1 = get_rows(token_b, "tv_seasons", f"content_id=eq.{series_id}&season_number=eq.1")
    print(f"B lee S1 tras merge -> {status}: {s1}")
    assert status == 200 and s1, "B ya no ve la temporada"
    assert s1[0]["overview"] == "El comienzo", "El merge sobrescribió con null el overview"
    assert s1[0]["name"] == "Temporada 1 (título tonto)", "El name nuevo no se aplicó"

    # 4) Limpieza.
    for table, query in (
        ("tv_seasons", f"content_id=eq.{series_id}"),
        ("tv_episodes", f"content_id=eq.{series_id}"),
        ("content_lists", "content_id=eq.collection-10"),
        ("content_lists", f"content_id=eq.{series_id}"),
        ("content_lists", "content_id=eq.director-7467"),
        ("content_lists", "content_id=eq.series-Q13983%7Ctt0903747"),
        ("tv_shows", f"content_id=eq.{series_id}"),
    ):
        status, text = req("DELETE", f"/rest/v1/{table}?{query}", token=token_b)
        print(f"B limpia {table} {query} -> {status}")

    print("E2E LISTAS/TEMPORADAS: OK")
    print("Usuarios de prueba (no borrables sin service key):", a_email, b_email)


if __name__ == "__main__":
    main()