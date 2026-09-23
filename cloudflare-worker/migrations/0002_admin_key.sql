CREATE TABLE IF NOT EXISTS admin_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  key_hash TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
