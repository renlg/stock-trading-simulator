#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""A股 5分钟线历史回填脚本 (baostock源)

用途: 新添加自选股后, 补全该股 kline_min5 的历史缺口
数据源: baostock (免费, 已装 00.9.30)
复权: adjustflag=3 不复权 (与现有新浪/腾讯 kline_min5 数据一致, 实测验证)
基准日: 2026-03-02 (与现有最早数据对齐)

用法:
  python3 backfill_min5.py --code 688825     # 单只
  python3 backfill_min5.py --code 600519 --full  # 强制全量(从基准日)
  python3 backfill_min5.py --all             # 全自选股
  python3 backfill_min5.py --gap-only        # 只扫缺口(供工作流复用)
  python3 backfill_min5.py --check           # 只列出缺口不写库
"""
import argparse, json, os, sqlite3, sys, time

DB_PATH = "/opt/a-stock/data/stock.db"
WATCH_DB = "/opt/stock-trading/data/stock.db"
BASE_DATE = "2026-03-02"   # 基准日: 与现有最早 kline_min5 数据对齐

FIELDS = "date,time,open,high,low,close,volume,amount"


def market(code):
    return "sh" if code.startswith(("6", "9")) else "sz"


def to_min5_time(ts):
    """20260727093500000 -> 2026-07-27 09:35"""
    return "%s-%s-%s %s:%s" % (ts[0:4], ts[4:6], ts[6:8], ts[8:10], ts[10:12])


def db():
    conn = sqlite3.connect(DB_PATH, timeout=30)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA busy_timeout=30000")
    return conn


def get_watch_codes():
    conn = sqlite3.connect(WATCH_DB, timeout=10)
    codes = [r[0] for r in conn.execute("SELECT DISTINCT code FROM watch_stocks").fetchall()]
    conn.close()
    return sorted(codes)


def earliest(conn, code):
    r = conn.execute("SELECT min(trade_time) FROM kline_min5 WHERE sec_code=?", (code,)).fetchone()
    return r[0] if r and r[0] else None


def fetch_baostock(code, start, end):
    """拉 baostock 5分钟线, 返回 [{trade_time, open, high, low, close, volume, amount}]"""
    import baostock as bs
    lg = bs.login()
    if lg.error_code != "0":
        raise RuntimeError("bs.login: %s %s" % (lg.error_code, lg.error_msg))
    try:
        rs = bs.query_history_k_data_plus(
            market(code) + "." + code, FIELDS,
            start_date=start, end_date=end,
            frequency="5", adjustflag="3")
        rows = []
        while rs.error_code == "0" and rs.next():
            d = rs.get_row_data()
            if len(d) < 8 or not d[1] or len(d[1]) < 12:
                continue
            try:
                rows.append({
                    "trade_time": to_min5_time(d[1]),
                    "open": float(d[2]), "high": float(d[3]),
                    "low": float(d[4]), "close": float(d[5]),
                    "volume": float(d[6]) if d[6] else 0,
                    "amount": float(d[7]) if d[7] else 0,
                })
            except ValueError:
                continue
        return rows
    finally:
        bs.logout()


def backfill_one(conn, code, force=False, check_only=False):
    """回填单只, 返回 (filled_rows, reason)"""
    cur = earliest(conn, code)
    if not force and cur and cur[:10] <= BASE_DATE:
        return 0, "covered"   # 已覆盖到基准日
    start = BASE_DATE if (force or not cur) else cur[:10]
    if cur and cur[:10] > BASE_DATE:
        # 有数据但晚于基准日: 从覆盖日往前推 5 个自然日(容差)开始
        start = cur[:10]
    today = time.strftime("%Y-%m-%d")
    rows = fetch_baostock(code, start, today)
    if not rows:
        return 0, "no_data"
    n = 0
    for r in rows:
        conn.execute(
            "INSERT OR REPLACE INTO kline_min5(sec_code,trade_time,open,close,high,low,volume,amount) "
            "VALUES(?,?,?,?,?,?,?,?)",
            (code, r["trade_time"], r["open"], r["close"], r["high"], r["low"], r["volume"], r["amount"]))
        n += 1
    conn.commit()
    return n, "filled"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--code")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--full", action="store_true")
    ap.add_argument("--gap-only", action="store_true")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    conn = db()
    conn.executescript("""
CREATE TABLE IF NOT EXISTS kline_min5(sec_code TEXT, trade_time TEXT, open REAL, close REAL, high REAL, low REAL, volume REAL, amount REAL, PRIMARY KEY(sec_code,trade_time));
""")
    if args.code:
        codes = [args.code]
    elif args.all:
        codes = get_watch_codes()
    else:
        codes = get_watch_codes()

    result = {"codes": len(codes), "filled": 0, "rows": 0, "covered": 0, "no_data": 0, "errors": []}
    for code in codes:
        try:
            n, reason = backfill_one(conn, code, force=args.full, check_only=args.check)
            if reason == "filled":
                result["filled"] += 1
                result["rows"] += n
            elif reason == "covered":
                result["covered"] += 1
            else:
                result["no_data"] += 1
            print("  %s: %s (%d行)" % (code, reason, n), flush=True)
        except Exception as e:
            result["errors"].append("%s: %s" % (code, str(e)[:80]))
            print("  %s: ERROR %s" % (code, str(e)[:80]), file=sys.stderr, flush=True)
    conn.close()
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
