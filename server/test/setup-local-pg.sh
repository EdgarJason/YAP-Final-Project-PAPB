#!/usr/bin/env bash
# Setup PostgreSQL lokal (loopback) untuk tes regresi
# server/test/supabase-postgres.test.mjs — IDEMPOTEN, aman dijalankan ulang.
#
# Latar belakang: instalasi sistem di VM ini TIDAK bertahan setelah restart
# (/usr/lib/postgresql, cluster, role, dan database ikut hilang; hanya
# ~/workspace yang bertahan). Setelah VM restart: instal dulu
# `apt-get install -y postgresql` (Ubuntu noble -> PG16), lalu jalankan
# skrip ini lagi. Skrip ini TIDAK menyentuh proyek Supabase user dan tidak
# memakai layanan cloud apa pun.
#
# Yang dibuat: cluster 16 main (bila belum ada), role yap_test (superuser,
# login), anon, authenticated, service_role, database yap_cloud_test1 dan
# yap_cloud_test2 (milik yap_test), serta baris trust di pg_hba.conf untuk
# keempat role itu pada kedua database via 127.0.0.1/::1 saja.
set -euo pipefail
PG_VERSION="${PG_VERSION:-16}"

if [ "$(id -u)" -eq 0 ]; then SUDO=""; elif command -v sudo >/dev/null 2>&1; then SUDO="sudo"; else
  echo "jalankan sebagai root atau sediakan sudo" >&2; exit 1
fi

command -v psql >/dev/null 2>&1 || {
  echo "psql tidak ditemukan — instal dulu: apt-get install -y postgresql" >&2; exit 1
}

if ! pg_lsclusters 2>/dev/null | grep -q "^${PG_VERSION}[[:space:]]"; then
  $SUDO pg_createcluster "$PG_VERSION" main --start
fi
$SUDO pg_ctlcluster "$PG_VERSION" main start || true

# Role + database (sebagai superuser postgres).
$SUDO su postgres -c "psql -X -v ON_ERROR_STOP=1 -q" <<'SQL'
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'yap_test') THEN
    CREATE ROLE yap_test LOGIN SUPERUSER;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN
    CREATE ROLE anon LOGIN;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'authenticated') THEN
    CREATE ROLE authenticated LOGIN;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'service_role') THEN
    CREATE ROLE service_role LOGIN;
  END IF;
END $$;
SQL
for db in yap_cloud_test1 yap_cloud_test2; do
  if ! $SUDO su postgres -c "psql -X -tAc \"SELECT 1 FROM pg_database WHERE datname = '$db'\"" | grep -q 1; then
    $SUDO su postgres -c "createdb -O yap_test $db"
  fi
done

# Trust auth loopback hanya untuk DB + role tes (baris paling atas agar
# menang atas aturan scram-sha-256 default). Idempotent.
HBA="/etc/postgresql/$PG_VERSION/main/pg_hba.conf"
for spec in "yap_cloud_test1 yap_test" "yap_cloud_test1 anon" "yap_cloud_test1 authenticated" \
            "yap_cloud_test1 service_role" "yap_cloud_test2 yap_test" "yap_cloud_test2 anon" \
            "yap_cloud_test2 authenticated" "yap_cloud_test2 service_role"; do
  set -- $spec
  for cidr in 127.0.0.1/32 ::1/128; do
    line="host $1 $2 $cidr trust"
    grep -qxF "$line" "$HBA" || $SUDO sed -i "1i $line" "$HBA"
  done
done
# pg_hba.conf pernah root-owned 660 sehingga user postgres tidak bisa
# membacanya — pastikan postgres yang memilikinya.
$SUDO chown postgres:postgres "$HBA" 2>/dev/null || true
$SUDO chmod 640 "$HBA" 2>/dev/null || true
$SUDO pg_ctlcluster "$PG_VERSION" main reload

# Verifikasi koneksi loopback untuk kedua role utama.
for db in yap_cloud_test1 yap_cloud_test2; do
  PGPASSWORD='' psql -h 127.0.0.1 -U yap_test -d "$db" -X -tAc 'SELECT 1' >/dev/null
  PGPASSWORD='' psql -h 127.0.0.1 -U anon -d "$db" -X -tAc 'SELECT 1' >/dev/null
done
echo "LOCAL-PG-OK cluster=$PG_VERSION dbs=yap_cloud_test1,yap_cloud_test2 roles=yap_test,anon,authenticated,service_role"
