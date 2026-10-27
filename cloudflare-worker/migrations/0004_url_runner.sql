-- URL runner (schemaVersion 3): ordered URL list (max 120) + per-position
-- wait seconds. Stored in new tables only; the legacy remote_config /
-- config_history tables (3 destinations, schemaVersion 2) are untouched.
-- Apply explicitly before deploying. HTTP handlers perform no schema changes.

CREATE TABLE IF NOT EXISTS url_runner_config (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  urls_json TEXT NOT NULL,
  individual_seconds_json TEXT NOT NULL,
  common_seconds INTEGER NOT NULL,
  config_version INTEGER NOT NULL CHECK (config_version >= 0),
  updated_at TEXT NOT NULL,
  update_id TEXT NOT NULL DEFAULT '',
  options_json TEXT NOT NULL DEFAULT '{"loopMode":"STOP","waitFromForeground":true,"startIndex":0}'
);

CREATE TABLE IF NOT EXISTS url_runner_history (
  config_version INTEGER PRIMARY KEY,
  urls_json TEXT NOT NULL,
  individual_seconds_json TEXT NOT NULL,
  common_seconds INTEGER NOT NULL,
  updated_at TEXT NOT NULL,
  options_json TEXT NOT NULL DEFAULT '{"loopMode":"STOP","waitFromForeground":true,"startIndex":0}'
);
