# Runtime image migration verification runbook

## 1. Goal

Verify the runtime database before deciding whether to create `V10__replace_legacy_remote_image_urls.sql`. This runbook is read-only: do not run Flyway migrate, repair, clean, or any data-changing SQL.

## 2. Prerequisites

- Approved runtime access and a recent database backup.
- Active profile and datasource confirmed by the operator.
- Never commit a real `.env`, password, token, or production hostname. Use `${DB_URL}`, `${DB_USERNAME}`, and `${DB_PASSWORD}` only.

## 3. Identify profile and datasource

The default profile is `dev`; tests use H2. Production configuration expects PostgreSQL from `${DB_URL}`, `${DB_USERNAME}`, and `${DB_PASSWORD}`. Confirm the active profile from startup logs or deployment configuration, then inspect the JDBC URL without exposing credentials. Do not infer runtime state from H2 tests.

## 4. Flyway history (read-only)

Run against the confirmed runtime database:

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Record whether V2 and V3 succeeded, the highest applied version, and any failed migration. Do not run `UPDATE`, `DELETE`, `INSERT`, `ALTER`, `DROP`, `TRUNCATE`, `flyway clean`, or `flyway repair`.

## 5. Legacy image counts

The verified schema uses `dishes.image_url` and `users.avatar_url`.

```sql
SELECT count(*) AS total_dishes,
       count(*) FILTER (WHERE image_url ~ '^https?://') AS remote_images,
       count(*) FILTER (WHERE image_url LIKE '/uploads/menu/%') AS managed_uploads,
       count(*) FILTER (WHERE image_url = '/images/placeholders/menu-item.svg') AS placeholders,
       count(*) FILTER (WHERE image_url IS NULL OR btrim(image_url) = '') AS blank_images
FROM dishes;

SELECT count(*) AS total_users,
       count(*) FILTER (WHERE avatar_url ~ '^https?://') AS remote_avatars,
       count(*) FILTER (WHERE avatar_url LIKE '/uploads/avatars/%') AS managed_uploads,
       count(*) FILTER (WHERE avatar_url = '/images/placeholders/avatar.svg') AS placeholders,
       count(*) FILTER (WHERE avatar_url IS NULL OR btrim(avatar_url) = '') AS blank_avatars
FROM users;
```

Group remote values by domain only; do not fetch the URLs or disclose personal data.

## 6. URL classification

Classify each count as: known Unsplash dish seed, known avatar seed, custom remote URL, unknown URL, managed local path, or placeholder. Only exact known seed URLs are candidates for migration.

## 7. Decision criteria

Choose no migration unless all are true: runtime database and Flyway history are readable; V2/V3 succeeded; known legacy seed counts are non-zero; exact URLs are identified; backup exists; clone/H2 validation passes; and custom URLs are excluded. Then the next version is V10.

## 8. Proposed V10 design

Do not create it in this change. A future `V10__replace_legacy_remote_image_urls.sql` must use an exact allowlist:

```sql
UPDATE <dish_table> SET <image_column> = '/images/placeholders/menu-item.svg'
WHERE <image_column> IN ('<exact-seed-url-1>', '<exact-seed-url-2>');
```

Likewise update only the exact seed avatar URL. Never use `LIKE 'http%'`, modify `/uploads/**`, overwrite custom remote URLs, or edit V2/V3.

## 9. Backup, test, and rollback

Back up first; record counts and a small approved sample before/after; test on a clone or H2; validate Flyway; run full regression and menu/profile smoke tests. Flyway Community rollback is restore-from-backup or a new forward migration—never checksum repair.

## 10. Approval checklist

- [ ] Active runtime profile and datasource confirmed.
- [ ] Backup and restore plan approved.
- [ ] Flyway V2/V3 history verified.
- [ ] Legacy URL counts/domain classification reviewed.
- [ ] Exact allowlist approved; custom URLs excluded.
- [ ] Clone/H2 migration test and full regression pass.
