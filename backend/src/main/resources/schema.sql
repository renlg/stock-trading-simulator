CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    balance REAL NOT NULL DEFAULT 1000000,
    created_at TEXT
);

CREATE TABLE IF NOT EXISTS auth_tokens (
    token TEXT PRIMARY KEY,
    user_id INTEGER,
    created_at TEXT,
    expires_at TEXT
);

CREATE TABLE IF NOT EXISTS api_keys (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    name TEXT,
    api_key TEXT UNIQUE NOT NULL,
    created_at TEXT,
    last_used_at TEXT
);

CREATE TABLE IF NOT EXISTS stocks (
    code TEXT PRIMARY KEY,
    name TEXT,
    prev_close REAL,
    price REAL,
    high REAL,
    low REAL,
    updated_at TEXT
);

CREATE TABLE IF NOT EXISTS positions (
    user_id INTEGER,
    code TEXT,
    quantity INTEGER,
    avg_cost REAL,
    updated_at TEXT,
    PRIMARY KEY(user_id, code)
);

CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    code TEXT,
    side TEXT,
    price REAL,
    quantity INTEGER,
    amount REAL,
    status TEXT,
    source TEXT,
    created_at TEXT
);

CREATE TABLE IF NOT EXISTS conditions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER,
    code TEXT,
    type TEXT,
    trigger_price REAL,
    quantity INTEGER,
    status TEXT,
    created_at TEXT,
    triggered_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_tokens_user ON auth_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_user ON api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conditions_status ON conditions(status);
