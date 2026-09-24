ALTER TABLE remote_config ADD COLUMN name1 TEXT NOT NULL DEFAULT '';
ALTER TABLE remote_config ADD COLUMN name2 TEXT NOT NULL DEFAULT '';
ALTER TABLE remote_config ADD COLUMN share_name TEXT NOT NULL DEFAULT '';
ALTER TABLE remote_config ADD COLUMN share_url TEXT NOT NULL DEFAULT '';

UPDATE remote_config SET share_url = url1 WHERE share_url = '';

ALTER TABLE config_history ADD COLUMN name1 TEXT NOT NULL DEFAULT '';
ALTER TABLE config_history ADD COLUMN name2 TEXT NOT NULL DEFAULT '';
ALTER TABLE config_history ADD COLUMN share_name TEXT NOT NULL DEFAULT '';
ALTER TABLE config_history ADD COLUMN share_url TEXT NOT NULL DEFAULT '';

UPDATE config_history SET share_url = url1 WHERE share_url = '';
