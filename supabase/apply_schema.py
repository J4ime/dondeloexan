import os

import psycopg


def main() -> None:
    host = os.environ["DONDELOEXAN_DB_HOST"]
    user = os.environ["DONDELOEXAN_DB_USER"]
    password = os.environ["DONDELOEXAN_DB_PASSWORD"]
    port = os.environ.get("DONDELOEXAN_DB_PORT", "5432")
    dbname = os.environ.get("DONDELOEXAN_DB_NAME", "postgres")

    here = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(here, "schema.sql"), encoding="utf-8") as f:
        script = f.read()

    conninfo = psycopg.conninfo.make_conninfo(
        host=host,
        port=port,
        dbname=dbname,
        user=user,
        password=password,
        sslmode="require",
    )

    with psycopg.connect(conninfo, autocommit=True) as conn:
        with conn.cursor() as cur:
            cur.execute(script)
            cur.execute(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename"
            )
            tables = [row[0] for row in cur.fetchall()]
            cur.execute("SELECT count(*) FROM user_platforms")
            n_platforms = cur.fetchone()[0]
            print("TABLES:", ", ".join(tables))
            print("USER_PLATFORMS:", n_platforms)


if __name__ == "__main__":
    main()