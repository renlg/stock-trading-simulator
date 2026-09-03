CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    balance REAL NOT NULL DEFAULT 1000000,
    role TEXT NOT NULL DEFAULT 'user',
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT
);

CREATE TABLE IF NOT EXISTS auth_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    access_token TEXT NOT NULL,
    refresh_token TEXT NOT NULL,
    created_at TEXT,
    access_expires_at TEXT,
    refresh_expires_at TEXT
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

CREATE TABLE IF NOT EXISTS watch_stocks (
    user_id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT,
    added_at TEXT,
    PRIMARY KEY(user_id, code)
);

CREATE TABLE IF NOT EXISTS positions (
    user_id INTEGER,
    code TEXT,
    quantity INTEGER,
    avg_cost REAL,
    available_date TEXT,
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
    triggered_at TEXT,
    fail_count INTEGER NOT NULL DEFAULT 0,
    fail_reason TEXT
);

CREATE TABLE IF NOT EXISTS trade_calendar (
    trade_date TEXT PRIMARY KEY,
    note TEXT
);

CREATE INDEX IF NOT EXISTS idx_tokens_user ON auth_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON orders(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conditions_status ON conditions(status);
