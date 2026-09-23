CREATE TABLE IF NOT EXISTS remote_config (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  url1 TEXT NOT NULL,
  url2 TEXT NOT NULL,
  config_version INTEGER NOT NULL CHECK (config_version >= 1),
  updated_at TEXT NOT NULL,
  update_id TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS config_history (
  config_version INTEGER PRIMARY KEY,
  url1 TEXT NOT NULL,
  url2 TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

INSERT OR IGNORE INTO remote_config (id, url1, url2, config_version, updated_at, update_id)
VALUES (1, 'https://lite.tiktok.com/t/ZS9AJY6f4n6Sw-GQnDP/',
  'https://lite.tiktok.com/t/ZS9rdoB6rsLHp-UHtJt/', 1, '2026-09-22T00:00:00.000Z', 'initial');

INSERT OR IGNORE INTO config_history (config_version, url1, url2, updated_at)
SELECT config_version, url1, url2, updated_at FROM remote_config WHERE id = 1;
