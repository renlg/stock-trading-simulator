# -*- coding: utf-8 -*-
"""A股 K线 全历史回填 v2 — web.ifzq.gtimg.cn 按日期段往前翻
v2 改动: 域名换 web.ifzq(原 ifzq 反爬), 识别反爬HTML快速跳过, 频率0.4s
幂等 upsert, 断点续跑, 只补沪深(北交所 920 段无历史)
用法:
  python3 -u backfill_kline.py            # 全量(所有缺历史的股票)
  python3 -u backfill_kline.py 600519     # 指定一只
  python3 -u backfill_kline.py batch N    # 第N批(每批500只)
"""
import json, os, sqlite3, ssl, sys, time, urllib.request, urllib.parse

DB_PATH = "/opt/a-stock/data/stock.db"
BATCH_SIZE = 500
STEP_DAYS = 640
START = "2001-01-01"   # 尽量早(web接口可到2002前)
START_TOLERANCE = "2001-01-15"   # covered 判断容差: 2001-01-01是元旦休市, 第一根bar是01-02, 容差到01-15避免永远判定未覆盖
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE

def get(url, timeout=12, retries=2, base_delay=2):
    req = urllib.request.Request(url)
    req.add_header("User-Agent", UA)
    req.add_header("Referer", "https://gu.qq.com/")
    last = None
    for i in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r:
                body = r.read().decode("utf-8", "replace")
                # 反爬检测: 返回 HTML 而非 JSON
                if body.lstrip().startswith("<"):
                    return None  # 反爬, 快速失败
                return body
        except Exception as e:
            last = e
            time.sleep(base_delay * (i + 1))
    raise last

def market(code):
    # 北交所: 92x(新)/4xx/8xx 无历史数据, 返回 None 跳过
    if code.startswith(("92", "4", "8")):
        return None
    # 沪深前缀: 6/9=sh, 其余=sz
    if code.startswith(("6", "9")):
        return "sh"
    if code.startswith(("0", "3")):
        return "sz"
    return None

def db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA journal_mode=WAL")
    return conn

def upsert(conn, row):
    conn.execute(
        "INSERT OR REPLACE INTO kline_daily "
        "(sec_code, trade_date, open, close, high, low, volume, amount, pct_chg) "
        "VALUES (?,?,?,?,?,?,?,?,?)", row)

def fetch_segment(conn, code, mk, start, end):
    url = ("https://ifzq.gtimg.cn/appstock/app/fqkline/get?param=%s%s,day,%s,%s,%d,qfq"
           % (mk, code, start, end, STEP_DAYS))
    body = get(url)
    if body is None:
        return "blocked"
    try:
        j = json.loads(body)
    except Exception:
        return "blocked"
    d = (j.get("data") or {}).get("%s%s" % (mk, code)) or {}
    kl = d.get("qfqday") or d.get("day") or []
    if not kl:
        return None
    earliest = kl[0][0]
    for p in kl:
        if len(p) < 6:
            continue
        o, c, h, l = float(p[1]), float(p[2]), float(p[3]), float(p[4])
        pct = (c - o) / o * 100 if o else None
        upsert(conn, (code, p[0], o, c, h, l, float(p[5]), None, pct))
    conn.commit()
    return earliest

def backfill_one(conn, code, force=False):
    mk = market(code)
    if not mk:
        return 0, "bj_skip"
    row = conn.execute(
        "SELECT MIN(trade_date) FROM kline_daily WHERE sec_code=?", (code,)).fetchone()
    existing_earliest = row[0] if row else None
    if force and existing_earliest:
        # 强制重拉: 清掉该股全部K线, 用完整2年段重拉(修复3年段截断缺口)
        conn.execute("DELETE FROM kline_daily WHERE sec_code=?", (code,))
        conn.commit()
        existing_earliest = None
    if existing_earliest and existing_earliest <= START_TOLERANCE:
        return 0, "covered"
    end = existing_earliest if existing_earliest else "2026-12-31"
    segments = [
        ("2001-01-01", "2002-12-31"),
        ("2002-01-01", "2003-12-31"),
        ("2003-01-01", "2004-12-31"),
        ("2004-01-01", "2005-12-31"),
        ("2005-01-01", "2006-12-31"),
        ("2006-01-01", "2007-12-31"),
        ("2007-01-01", "2008-12-31"),
        ("2008-01-01", "2009-12-31"),
        ("2009-01-01", "2010-12-31"),
        ("2010-01-01", "2011-12-31"),
        ("2011-01-01", "2012-12-31"),
        ("2012-01-01", "2013-12-31"),
        ("2013-01-01", "2014-12-31"),
        ("2014-01-01", "2015-12-31"),
        ("2015-01-01", "2016-12-31"),
        ("2016-01-01", "2017-12-31"),
        ("2017-01-01", "2018-12-31"),
        ("2018-01-01", "2019-12-31"),
        ("2019-01-01", "2020-12-31"),
        ("2020-01-01", "2021-12-31"),
        ("2021-01-01", "2022-12-31"),
        ("2022-01-01", "2023-12-31"),
        ("2023-01-01", end),
    ]
    added = 0
    blocked = False
    for s, e in segments:
        if existing_earliest and existing_earliest <= s:
            break
        if e < START:
            continue
        try:
            r = fetch_segment(conn, code, mk, s, e)
            if r == "blocked":
                blocked = True
                break
            if r:
                added += 1
        except Exception:
            pass
        time.sleep(0.6)
    return added, "blocked" if blocked else "ok"

def run_batch(conn, batch_no, force=False):
    offset = (batch_no - 1) * BATCH_SIZE
    rows = conn.execute(
        "SELECT sec_code FROM stock_pool ORDER BY sec_code LIMIT ? OFFSET ?",
        (BATCH_SIZE, offset)).fetchall()
    if not rows:
        print("批次 %d: 无股票" % batch_no, flush=True)
        return False
    codes = [r[0] for r in rows]
    t0 = time.time()
    stats = {"ok": 0, "covered": 0, "blocked": 0, "fail": 0, "bj": 0}
    consecutive_blocked = 0
    for i, code in enumerate(codes):
        try:
            added, st = backfill_one(conn, code, force=force)
            stats[st if st in stats else "ok"] += 1
            if st == "blocked":
                consecutive_blocked += 1
            else:
                consecutive_blocked = 0
        except Exception:
            stats["fail"] += 1
            consecutive_blocked += 1
        if consecutive_blocked >= 20:
            print("风控熔断: 连续%d只blocked, 停止批次%d" % (consecutive_blocked, batch_no), flush=True)
            break
        if (i + 1) % 100 == 0:
            print("  %d/%d 耗时%.0fs" % (i + 1, len(codes), time.time() - t0), flush=True)
        time.sleep(0.15)
    print("批次 %d 结束: %s 耗时%.0fs" % (batch_no, stats, time.time() - t0), flush=True)
    # 全部covered/跳过(无新增)且非force => 说明历史已补完, 返回True让main提前结束
    if not force and stats["ok"] == 0 and stats["covered"] + stats["bj"] == len(codes) and stats["fail"] == 0:
        return True
    return False

def main():
    conn = db()
    args = sys.argv[1:]
    force = False
    if args and args[0] == "refill":
        force = True
        args = args[1:]
    if not args:
        total = conn.execute("SELECT COUNT(*) FROM stock_pool").fetchone()[0]
        nb = (total + BATCH_SIZE - 1) // BATCH_SIZE
        print("全量: 池%d只, %d批, force=%s" % (total, nb, force), flush=True)
        # 硬性结束时间: 14:00 强制退出, 避免与 15:30 起的日线/估值/资金流同步流程写库冲突
        today = time.strftime("%Y-%m-%d")
        import datetime
        deadline = time.mktime(time.strptime(today + " 14:00:00", "%Y-%m-%d %H:%M:%S"))
        for b in range(1, nb + 1):
            if time.time() > deadline:
                print("已达14:00截止时间, 提前退出(避免与同步流程写锁冲突)", flush=True)
                break
            if run_batch(conn, b, force=force):
                print("批次%d全部covered, 历史已补完, 提前退出" % b, flush=True)
                break
    elif args[0] == "batch":
        run_batch(conn, int(args[1]), force=force)
    else:
        code = args[0]
        added, st = backfill_one(conn, code, force=force)
        print("%s: 补%d段 %s" % (code, added, st), flush=True)
    conn.close()

if __name__ == "__main__":
    main()
