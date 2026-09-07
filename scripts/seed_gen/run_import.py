#!/usr/bin/env python3
"""Orchestrator: extract → upload to OSS → gen SQL → execute SQL."""
import os, sys, subprocess, time

BASE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(BASE))
ENV_FILE = os.path.join(ROOT, "hardware-mall-backend", ".env")


def load_env(path):
    env = {}
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def run_step(name, cmd, cwd=None):
    print(f"\n{'='*60}")
    print(f"  STEP: {name}")
    print(f"{'='*60}")
    t0 = time.time()
    result = subprocess.run(cmd, cwd=cwd or BASE, capture_output=False)
    elapsed = time.time() - t0
    if result.returncode != 0:
        print(f"\n[FAILED] {name} exited with code {result.returncode}")
        sys.exit(result.returncode)
    print(f"\n[DONE] {name} ({elapsed:.1f}s)")
    return result


def main():
    env = load_env(ENV_FILE)

    # Step 1: Extract catalog from Excel
    run_step("Extract catalog from Excel", [sys.executable, "extract.py"])

    # Step 2: Upload images to OSS (clears products/ first)
    run_step("Upload images to OSS", [sys.executable, "upload.py"])

    # Step 3: Generate SQL seed file
    run_step("Generate SQL seed file", [sys.executable, "gen_sql.py"])

    # Step 4: Execute SQL against database
    db_host = env.get("DB_HOST", "localhost")
    db_port = env.get("DB_PORT", "3306")
    db_name = env.get("DB_NAME", "hardware_mall")
    db_user = env.get("DB_USERNAME", "root")
    db_pass = env.get("DB_PASSWORD", "")

    sql_file = os.path.join(
        ROOT, "hardware-mall-backend", "src", "main", "resources", "db", "seed_real_products_v2.sql"
    )

    if not os.path.exists(sql_file):
        print(f"[ERROR] SQL file not found: {sql_file}")
        sys.exit(1)

    print(f"\n{'='*60}")
    print(f"  STEP: Execute SQL against database")
    print(f"{'='*60}")
    print(f"  Host: {db_host}:{db_port}")
    print(f"  Database: {db_name}")
    print(f"  SQL file: {sql_file}")

    import pymysql

    try:
        conn = pymysql.connect(
            host=db_host,
            port=int(db_port),
            user=db_user,
            password=db_pass,
            database=db_name,
            charset='utf8mb4',
            autocommit=True
        )
        cursor = conn.cursor()

        with open(sql_file, "r", encoding="utf-8") as f:
            sql_content = f.read()

        for statement in sql_content.split(';'):
            statement = statement.strip()
            if statement:
                cursor.execute(statement)

        conn.close()
        print(f"\n[DONE] SQL executed successfully")
    except Exception as e:
        print(f"\n[FAILED] MySQL execution error: {e}")
        sys.exit(1)
    print(f"\n{'='*60}")
    print(f"  ALL STEPS COMPLETED SUCCESSFULLY")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
