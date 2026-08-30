import os
import sys

import psycopg


def main() -> None:
    host = os.environ["DONDELOEXAN_DB_HOST"]
    user = os.environ["DONDELOEXAN_DB_USER"]
    password = os.environ["DONDELOEXAN_DB_PASSWORD"]
    port = os.environ.get("DONDELOEXAN_DB_PORT", "5432")
    dbname = os.environ.get("DONDELOEXAN_DB_NAME", "postgres")

    here = os.path.dirname(os.path.abspath(__file__))
    target = sys.argv[1] if len(sys.argv) > 1 else "schema.sql"
    with open(os.path.join(here, target), encoding="utf-8") as f:
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
                "SELECT proname, prosecdef FROM pg_proc WHERE proname = 'catalog_merge'"
            )
            rows = cur.fetchall()
            print("FUNCTIONS:", rows)


if __name__ == "__main__":
    main()