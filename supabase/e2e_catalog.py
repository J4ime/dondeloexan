"""E2E: catálogo global compartido entre dos usuarios.

Simula lo que hace la app vía PostgREST:
1. Usuario A escribe el detalle de una peli en movies / fa_movie_data / critic_reviews
   con el RPC catalog_merge (upsert null-safe).
2. Usuario B lo lee SIN haber hecho nunca la petición a las APIs -> la nube es la fuente.
3. Merge null-safe: A re-sube la fila "pobre" con rating en null; B sigue viendo 7.5
   y el título nuevo SÍ se aplica.
4. Limpieza: B borra las filas de catálogo de prueba (todas las autenticadas pueden).

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


def main() -> None:
    a_email = f"e2e_catalog_a_{SUFFIX}@example.com"
    b_email = f"e2e_catalog_b_{SUFFIX}@example.com"

    for email in (a_email, b_email):
        status, text = signup(email)
        if status not in (200, 201, 422):
            raise SystemExit(f"signup falló: {text}")

    token_a = token(a_email)
    token_b = token(b_email)

    content_id = f"tmdb-e2e-{SUFFIX}"
    now = int(time.time() * 1000)

    # 1) Usuario A escribe el catálogo (datos ricos) vía RPC.
    movie = {
        "content_id": content_id,
        "title": "Peli E2E Catálogo",
        "rating_tmdb": 7.5,
        "tmdb_id": 999_999,
        "updated_at": now,
    }
    status, text = rpc_merge(token_a, "movies", [movie])
    print(f"A catalog_merge movies -> {status}: {text}")
    if status not in (200, 204):
        raise SystemExit(text)

    fa = {
        "content_id": content_id,
        "fa_id": 888_888,
        "fa_rating": 8.1,
        "platform_releases_json": '[{"platformName":"Netflix","dateLabel":"estreno"}]',
        "cached_at": now,
    }
    status, text = rpc_merge(token_a, "fa_movie_data", [fa])
    print(f"A catalog_merge fa_movie_data -> {status}: {text}")
    if status not in (200, 204):
        raise SystemExit(text)

    critic = {
        "content_id": content_id,
        "reviews_json": '[{"critic":"El País","score":"8"}]',
        "cached_at": now,
    }
    status, text = rpc_merge(token_a, "critic_reviews", [critic])
    print(f"A catalog_merge critic_reviews -> {status}: {text}")
    if status not in (200, 204):
        raise SystemExit(text)

    # 2) Usuario B lee sin haber llamado nunca a las APIs.
    status, text = req("GET", f"/rest/v1/movies?content_id=eq.{content_id}", token=token_b)
    movies = json.loads(text)
    print(f"B lee movies -> {status}: {movies}")
    assert status == 200 and movies and movies[0]["rating_tmdb"] == 7.5, "B no ve la peli"

    status, text = req(
        "GET", f"/rest/v1/fa_movie_data?content_id=eq.{content_id}", token=token_b
    )
    fa_rows = json.loads(text)
    print(f"B lee fa_movie_data -> {status}: {fa_rows}")
    assert status == 200 and fa_rows and fa_rows[0]["fa_rating"] == 8.1, "B no ve fa_movie_data"

    status, text = req(
        "GET", f"/rest/v1/critic_reviews?content_id=eq.{content_id}", token=token_b
    )
    critic_rows = json.loads(text)
    print(f"B lee critic_reviews -> {status}: {critic_rows}")
    assert status == 200 and critic_rows, "B no ve critic_reviews"

    # 3) Merge null-safe: A re-sube la fila "pobre" (rating null, título nuevo).
    poor = dict(movie)
    poor["rating_tmdb"] = None
    poor["title"] = "Peli E2E Catálogo (título tonto)"
    status, text = rpc_merge(token_a, "movies", [poor])
    print(f"A catalog_merge movies pobre -> {status}: {text}")
    if status not in (200, 204):
        raise SystemExit(text)

    status, text = req("GET", f"/rest/v1/movies?content_id=eq.{content_id}", token=token_b)
    merged = json.loads(text)
    print(f"B lee tras merge -> {status}: {merged}")
    assert status == 200 and merged, "B ya no ve la peli"
    assert merged[0]["rating_tmdb"] == 7.5, "El merge sobrescribió con null el rating"
    assert merged[0]["title"] == "Peli E2E Catálogo (título tonto)", "El título nuevo no se aplicó"

    # 4) Limpieza del catálogo de prueba (RLS permite a cualquier autenticada).
    for table in ("movies", "fa_movie_data", "critic_reviews"):
        status, text = req(
            "DELETE",
            f"/rest/v1/{table}?content_id=eq.{content_id}",
            token=token_b,
        )
        print(f"B limpia {table} -> {status}")

    print("E2E CATÁLOGO: OK")
    print("Usuarios de prueba (no borrables sin service key):", a_email, b_email)


if __name__ == "__main__":
    main()