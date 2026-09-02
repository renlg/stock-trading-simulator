import { ArrowLeftOutlined } from '@ant-design/icons'
import { Button, Card, Descriptions, Empty, Radio, Space, Spin, Statistic, Table, Tabs, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import * as echarts from 'echarts'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../api'
import type { ApiResponse } from '../types'
import { bigMoney, getData, money, riseColor, signedPct } from '../utils'

interface QuoteData {
  code: string
  name: string
  price: number
  prevClose: number
  high: number
  low: number
  open: number
  time: string
  change: number
  changePct: number
}

interface KlineBar {
  time: string
  open: number
  close: number
  high: number
  low: number
  volume: number
  amount?: number
  pctChg?: number
}

interface KlineData {
  code: string
  name: string
  type: string
  bars: KlineBar[]
}

interface ValuationItem {
  tradeDate: string
  peTtm: number
  pb: number
  psTtm: number
  totalMv: number
  circMv: number
  divYield: number
}

interface MoneyflowItem {
  tradeDate: string
  mainNet: number
  superNet: number
  bigNet: number
  midNet: number
  smallNet: number
}

interface HolderItem {
  endDate: string
  holderNum: number
  holderNumChg: number
  avgHold: number
}

interface MarginItem {
  tradeDate: string
  rzBalance: number
  rqVolume: number
  rzrqBalance: number
  rqBalance: number
  rqMcl: number
  rzrqChg: number
}

interface ConsensusItem {
  secName: string
  ratingOrgNum: number
  ratingBuy: number
  ratingAdd: number
  ratingNeutral: number
  ratingReduce: number
  ratingSale: number
  eps1: number
  year1: string
  eps2: number
  year2: string
  eps3: number
  year3: string
  eps4: number
  year4: string
  aimpriceMax: number
  aimpriceMin: number
  fetchDate: string
}

interface NorthboundItem {
  endDate: string
  secName: string
  holdShares: number
  holdSharesRatio: number
  holdMarketCap: number
  orgQuantity: number
  totalSharesRatio: number
  dateType: string
}

interface FinancialItem {
  reportPeriod: string
  reportType: string
  noticeDate: string
  totalOperateIncome: number
  parentNetProfit: number
  basicEps: number
  weightAvgRoe: number
  ystz: number
  sjltz: number
}

interface EventsItem {
  title: string
  eventDate: string
  category: string
  pdfUrl?: string
}

interface DetailData {
  code: string
  name: string
  valuation: ValuationItem[]
  moneyflow: MoneyflowItem[]
  holder: HolderItem[]
  margin: MarginItem[]
  consensus: ConsensusItem[]
  northbound: NorthboundItem[]
  financial: FinancialItem[]
  events: EventsItem[]
}

const UP_COLOR = '#ef232a'
const DOWN_COLOR = '#14b143'

const eventCategoryColors: Record<string, string> = {
  '业绩预告': 'orange', '股东增减持': 'blue', '分红': 'red',
  '回购': 'green', '诉讼': 'default', '重大合同': 'purple',
  '其他': 'default',
}

export default function StockDetailPage() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const [quote, setQuote] = useState<QuoteData | null>(null)
  const [klineType, setKlineType] = useState<'day' | 'min5'>('day')
  const [klineData, setKlineData] = useState<KlineData | null>(null)
  const [loading, setLoading] = useState(true)
  const [detail, setDetail] = useState<DetailData | null>(null)
  const klineChartRef = useRef<HTMLDivElement>(null)
  const timeChartRef = useRef<HTMLDivElement>(null)
  const klineChartInstance = useRef<echarts.ECharts | null>(null)
  const timeChartInstance = useRef<echarts.ECharts | null>(null)
  const peChartRef = useRef<HTMLDivElement>(null)
  const moneyflowChartRef = useRef<HTMLDivElement>(null)
  const ratingChartRef = useRef<HTMLDivElement>(null)
  const peChartInstance = useRef<echarts.ECharts | null>(null)
  const moneyflowChartInstance = useRef<echarts.ECharts | null>(null)
  const ratingChartInstance = useRef<echarts.ECharts | null>(null)

  const loadQuote = useCallback(async () => {
    if (!code) return
    try {
      const { data } = await api.get<ApiResponse<QuoteData>>(`/stocks/quote/${code}`)
      const result = getData(data)
      if (result) setQuote(result)
    } catch { /* ignore */ }
  }, [code])

  const loadKline = useCallback(async (type: 'day' | 'min5') => {
    if (!code) return
    try {
      const { data } = await api.get<ApiResponse<KlineData>>(`/stocks/kline/${code}`, { params: { type, limit: 120 } })
      const result = getData(data)
      if (result) setKlineData(result)
    } catch { /* ignore */ }
  }, [code])

  const loadDetail = useCallback(async () => {
    if (!code) return
    try {
      const { data } = await api.get<ApiResponse<DetailData>>(`/stocks/detail/${code}`)
      const result = getData(data)
      if (result) setDetail(result)
    } catch { /* ignore */ }
  }, [code])

  useEffect(() => {
    setLoading(true)
    Promise.all([loadQuote(), loadKline(klineType), loadDetail()]).finally(() => setLoading(false))
  }, [loadQuote, loadKline, loadDetail, klineType])

  useEffect(() => {
    const timer = window.setInterval(() => { void loadQuote() }, 5000)
    return () => window.clearInterval(timer)
  }, [loadQuote])

  // K线图渲染
  useEffect(() => {
    if (!klineChartRef.current || !klineData || klineData.bars.length === 0) return
    if (!klineChartInstance.current) {
      klineChartInstance.current = echarts.init(klineChartRef.current)
    }
    const chart = klineChartInstance.current
    const bars = klineData.bars
    const times = bars.map(b => b.time)
    const ohlc = bars.map(b => [b.open, b.close, b.low, b.high])
    const volumes = bars.map((b, i) => ({
      value: b.volume,
      itemStyle: { color: b.close >= b.open ? UP_COLOR : DOWN_COLOR },
    }))

    chart.setOption({
      animation: false,
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross' },
        formatter: (params: any) => {
          const idx = params[0]?.dataIndex
          if (idx == null) return ''
          const bar = bars[idx]
          const color = bar.close >= bar.open ? UP_COLOR : DOWN_COLOR
          let html = `<div style="font-size:12px"><b>${bar.time}</b><br/>`
          html += `开: <span style="color:${color}">${bar.open.toFixed(2)}</span><br/>`
          html += `收: <span style="color:${color}">${bar.close.toFixed(2)}</span><br/>`
          html += `高: <span style="color:${color}">${bar.high.toFixed(2)}</span><br/>`
          html += `低: <span style="color:${color}">${bar.low.toFixed(2)}</span><br/>`
          html += `量: ${Number(bar.volume).toLocaleString()}`
          if (bar.pctChg != null) html += `<br/>涨跌幅: <span style="color:${color}">${bar.pctChg.toFixed(2)}%</span>`
          html += '</div>'
          return html
        },
      },
      axisPointer: { link: [{ xAxisIndex: 'all' }] },
      grid: [
        { left: 60, right: 30, top: 30, height: '55%' },
        { left: 60, right: 30, top: '73%', height: '18%' },
      ],
      xAxis: [
        { type: 'category', data: times, gridIndex: 0, axisLabel: { show: false }, axisTick: { show: false } },
        { type: 'category', data: times, gridIndex: 1, axisLabel: { fontSize: 10, rotate: klineType === 'min5' ? 45 : 0 } },
      ],
      yAxis: [
        { scale: true, gridIndex: 0, splitLine: { lineStyle: { type: 'dashed' } } },
        { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { show: false }, splitLine: { show: false } },
      ],
      dataZoom: [
        { type: 'inside', xAxisIndex: [0, 1], start: bars.length > 60 ? 50 : 0, end: 100 },
        { type: 'slider', xAxisIndex: [0, 1], bottom: 5, height: 20 },
      ],
      series: [
        {
          type: 'candlestick',
          data: ohlc,
          xAxisIndex: 0,
          yAxisIndex: 0,
          itemStyle: {
            color: UP_COLOR,
            color0: DOWN_COLOR,
            borderColor: UP_COLOR,
            borderColor0: DOWN_COLOR,
          },
        },
        {
          type: 'bar',
          data: volumes,
          xAxisIndex: 1,
          yAxisIndex: 1,
        },
      ],
    }, true)
  }, [klineData, klineType])

  // 分时图渲染
  useEffect(() => {
    if (!timeChartRef.current || !klineData || klineData.bars.length === 0) return
    if (klineType !== 'min5') return

    if (!timeChartInstance.current) {
      timeChartInstance.current = echarts.init(timeChartRef.current)
    }
    const chart = timeChartInstance.current
    const bars = klineData.bars
    const lastDate = bars.length > 0 ? bars[bars.length - 1].time.substring(0, 10) : ''
    const todayBars = bars.filter(b => b.time.startsWith(lastDate))
    if (todayBars.length === 0) return

    const times = todayBars.map(b => b.time.substring(11))
    const closes = todayBars.map(b => b.close)
    const volumes = todayBars.map((b, i) => ({
      value: b.volume,
      itemStyle: { color: i === 0 ? '#999' : (b.close >= todayBars[i - 1].close ? UP_COLOR : DOWN_COLOR) },
    }))
    const prevClose = quote?.prevClose ?? todayBars[0].open
    const minPrice = Math.min(...closes, prevClose)
    const maxPrice = Math.max(...closes, prevClose)
    const padding = (maxPrice - minPrice) * 0.1 || 0.5

    chart.setOption({
      animation: false,
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          const idx = params[0]?.dataIndex
          if (idx == null) return ''
          const bar = todayBars[idx]
          const color = bar.close >= prevClose ? UP_COLOR : DOWN_COLOR
          const chg = prevClose === 0 ? 0 : ((bar.close - prevClose) / prevClose * 100)
          return `<div style="font-size:12px"><b>${bar.time}</b><br/>价格: <span style="color:${color}">${bar.close.toFixed(2)}</span><br/>涨跌: <span style="color:${color}">${signedPct(chg)}</span><br/>量: ${Number(bar.volume).toLocaleString()}</div>`
        },
      },
      axisPointer: { link: [{ xAxisIndex: 'all' }] },
      grid: [
        { left: 60, right: 30, top: 30, height: '55%' },
        { left: 60, right: 30, top: '73%', height: '18%' },
      ],
      xAxis: [
        { type: 'category', data: times, gridIndex: 0, axisLabel: { show: false }, axisTick: { show: false } },
        { type: 'category', data: times, gridIndex: 1, axisLabel: { fontSize: 10 } },
      ],
      yAxis: [
        {
          scale: true, gridIndex: 0, min: prevClose - padding, max: prevClose + padding,
          splitLine: { lineStyle: { type: 'dashed' } },
          axisLabel: {
            formatter: (v: number) => v.toFixed(2),
            color: (v: any) => {
              const val = Number(v)
              return val > prevClose ? UP_COLOR : val < prevClose ? DOWN_COLOR : '#999'
            },
          },
        },
        { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { show: false }, splitLine: { show: false } },
      ],
      series: [
        {
          type: 'line',
          data: closes,
          xAxisIndex: 0,
          yAxisIndex: 0,
          smooth: true,
          symbol: 'none',
          lineStyle: { color: '#1677ff', width: 1.5 },
          areaStyle: { color: 'rgba(22,119,255,0.08)' },
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { type: 'dashed', color: '#999' },
            data: [{ yAxis: prevClose }],
            label: { formatter: prevClose.toFixed(2), fontSize: 10 },
          },
        },
        {
          type: 'bar',
          data: volumes,
          xAxisIndex: 1,
          yAxisIndex: 1,
        },
      ],
    }, true)
  }, [klineData, klineType, quote])

  // 分时图: 如果当前是 day 模式, 额外加载 min5 数据来画分时
  useEffect(() => {
    if (klineType === 'min5') return
    if (!timeChartRef.current || !code) return
    let cancelled = false
    ;(async () => {
      try {
        const { data } = await api.get<ApiResponse<KlineData>>(`/stocks/kline/${code}`, { params: { type: 'min5', limit: 48 } })
        const result = getData(data)
        if (cancelled || !result || result.bars.length === 0) return
        if (!timeChartInstance.current) {
          timeChartInstance.current = echarts.init(timeChartRef.current)
        }
        const chart = timeChartInstance.current
        const bars = result.bars
        const lastDate = bars[bars.length - 1].time.substring(0, 10)
        const todayBars = bars.filter(b => b.time.startsWith(lastDate))
        if (todayBars.length === 0) return

        const times = todayBars.map(b => b.time.substring(11))
        const closes = todayBars.map(b => b.close)
        const volumes = todayBars.map((b, i) => ({
          value: b.volume,
          itemStyle: { color: i === 0 ? '#999' : (b.close >= todayBars[i - 1].close ? UP_COLOR : DOWN_COLOR) },
        }))
        const prevClose = quote?.prevClose ?? todayBars[0].open
        const minPrice = Math.min(...closes, prevClose)
        const maxPrice = Math.max(...closes, prevClose)
        const padding = (maxPrice - minPrice) * 0.1 || 0.5

        chart.setOption({
          animation: false,
          tooltip: {
            trigger: 'axis',
            formatter: (params: any) => {
              const idx = params[0]?.dataIndex
              if (idx == null) return ''
              const bar = todayBars[idx]
              const color = bar.close >= prevClose ? UP_COLOR : DOWN_COLOR
              const chg = prevClose === 0 ? 0 : ((bar.close - prevClose) / prevClose * 100)
              return `<div style="font-size:12px"><b>${bar.time}</b><br/>价格: <span style="color:${color}">${bar.close.toFixed(2)}</span><br/>涨跌: <span style="color:${color}">${signedPct(chg)}</span><br/>量: ${Number(bar.volume).toLocaleString()}</div>`
            },
          },
          axisPointer: { link: [{ xAxisIndex: 'all' }] },
          grid: [
            { left: 60, right: 30, top: 30, height: '55%' },
            { left: 60, right: 30, top: '73%', height: '18%' },
          ],
          xAxis: [
            { type: 'category', data: times, gridIndex: 0, axisLabel: { show: false }, axisTick: { show: false } },
            { type: 'category', data: times, gridIndex: 1, axisLabel: { fontSize: 10 } },
          ],
          yAxis: [
            {
              scale: true, gridIndex: 0, min: prevClose - padding, max: prevClose + padding,
              splitLine: { lineStyle: { type: 'dashed' } },
              axisLabel: {
                formatter: (v: number) => v.toFixed(2),
                color: (v: any) => {
                  const val = Number(v)
                  return val > prevClose ? UP_COLOR : val < prevClose ? DOWN_COLOR : '#999'
                },
              },
            },
            { scale: true, gridIndex: 1, splitNumber: 2, axisLabel: { show: false }, splitLine: { show: false } },
          ],
          series: [
            {
              type: 'line',
              data: closes,
              xAxisIndex: 0,
              yAxisIndex: 0,
              smooth: true,
              symbol: 'none',
              lineStyle: { color: '#1677ff', width: 1.5 },
              areaStyle: { color: 'rgba(22,119,255,0.08)' },
              markLine: {
                silent: true,
                symbol: 'none',
                lineStyle: { type: 'dashed', color: '#999' },
                data: [{ yAxis: prevClose }],
                label: { formatter: prevClose.toFixed(2), fontSize: 10 },
              },
            },
            {
              type: 'bar',
              data: volumes,
              xAxisIndex: 1,
              yAxisIndex: 1,
            },
          ],
        }, true)
      } catch { /* ignore */ }
    })()
    return () => { cancelled = true }
  }, [klineType, code, quote])

  // PE 历史趋势图
  useEffect(() => {
    if (!peChartRef.current || !detail || detail.valuation.length === 0) return
    if (!peChartInstance.current) {
      peChartInstance.current = echarts.init(peChartRef.current)
    }
    const chart = peChartInstance.current
    const data = [...detail.valuation].reverse()
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 50, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: data.map(d => d.tradeDate), axisLabel: { fontSize: 10 } },
      yAxis: { type: 'value', scale: true, splitLine: { lineStyle: { type: 'dashed' } } },
      series: [{
        type: 'line', data: data.map(d => d.peTtm), smooth: true, symbol: 'none',
        lineStyle: { color: '#1677ff', width: 1.5 },
        areaStyle: { color: 'rgba(22,119,255,0.08)' },
      }],
    }, true)
  }, [detail])

  // 资金流向柱状图
  useEffect(() => {
    if (!moneyflowChartRef.current || !detail || detail.moneyflow.length === 0) return
    if (!moneyflowChartInstance.current) {
      moneyflowChartInstance.current = echarts.init(moneyflowChartRef.current)
    }
    const chart = moneyflowChartInstance.current
    const data = [...detail.moneyflow].reverse()
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['主力', '超大单', '大单', '中单', '小单'], bottom: 0, textStyle: { fontSize: 11 } },
      grid: { left: 60, right: 20, top: 10, bottom: 40 },
      xAxis: { type: 'category', data: data.map(d => d.tradeDate), axisLabel: { fontSize: 10 } },
      yAxis: { type: 'value', scale: true, splitLine: { lineStyle: { type: 'dashed' } }, axisLabel: { formatter: (v: number) => bigMoney(v) } },
      series: [
        { name: '主力', type: 'bar', stack: 'flow', data: data.map(d => d.mainNet), itemStyle: { color: '#1677ff' } },
        { name: '超大单', type: 'bar', stack: 'detail', data: data.map(d => d.superNet), itemStyle: { color: '#f5222d' } },
        { name: '大单', type: 'bar', stack: 'detail', data: data.map(d => d.bigNet), itemStyle: { color: '#fa8c16' } },
        { name: '中单', type: 'bar', stack: 'detail', data: data.map(d => d.midNet), itemStyle: { color: '#faad14' } },
        { name: '小单', type: 'bar', stack: 'detail', data: data.map(d => d.smallNet), itemStyle: { color: '#52c41a' } },
      ],
    }, true)
  }, [detail])

  // 一致预期评级分布图
  useEffect(() => {
    if (!ratingChartRef.current || !detail || detail.consensus.length === 0) return
    if (!ratingChartInstance.current) {
      ratingChartInstance.current = echarts.init(ratingChartRef.current)
    }
    const chart = ratingChartInstance.current
    const c = detail.consensus[0]
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { fontSize: 11 } },
      series: [{
        type: 'pie',
        radius: ['40%', '65%'],
        center: ['50%', '45%'],
        label: { formatter: '{b}: {c}家' },
        data: [
          { value: c.ratingBuy, name: '买入', itemStyle: { color: '#f5222d' } },
          { value: c.ratingAdd, name: '增持', itemStyle: { color: '#fa8c16' } },
          { value: c.ratingNeutral, name: '中性', itemStyle: { color: '#1677ff' } },
          { value: c.ratingReduce, name: '减持', itemStyle: { color: '#52c41a' } },
          { value: c.ratingSale, name: '卖出', itemStyle: { color: '#8c8c8c' } },
        ].filter(d => d.value > 0),
      }],
    }, true)
  }, [detail])

  // resize
  useEffect(() => {
    const handleResize = () => {
      klineChartInstance.current?.resize()
      timeChartInstance.current?.resize()
      peChartInstance.current?.resize()
      moneyflowChartInstance.current?.resize()
      ratingChartInstance.current?.resize()
    }
    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      klineChartInstance.current?.dispose()
      timeChartInstance.current?.dispose()
      peChartInstance.current?.dispose()
      moneyflowChartInstance.current?.dispose()
      ratingChartInstance.current?.dispose()
    }
  }, [])

  const changeVal = quote?.change ?? 0

  // ---- Tab 表格列定义 ----
  const valuationColumns: ColumnsType<ValuationItem> = [
    { title: '日期', dataIndex: 'tradeDate', width: 110 },
    { title: 'PE(TTM)', dataIndex: 'peTtm', render: (v: number) => v?.toFixed(2) },
    { title: 'PB', dataIndex: 'pb', render: (v: number) => v?.toFixed(2) },
    { title: 'PS(TTM)', dataIndex: 'psTtm', render: (v: number) => v?.toFixed(2) },
    { title: '总市值', dataIndex: 'totalMv', render: (v: number) => bigMoney(v) },
    { title: '流通市值', dataIndex: 'circMv', render: (v: number) => bigMoney(v) },
    { title: '股息率(%)', dataIndex: 'divYield', render: (v: number) => v?.toFixed(2) },
  ]

  const moneyflowColumns: ColumnsType<MoneyflowItem> = [
    { title: '日期', dataIndex: 'tradeDate', width: 110 },
    { title: '主力净流入', dataIndex: 'mainNet', render: (v: number) => <span style={{ color: riseColor(v) }}>{bigMoney(v)}</span> },
    { title: '超大单净流入', dataIndex: 'superNet', render: (v: number) => <span style={{ color: riseColor(v) }}>{bigMoney(v)}</span> },
    { title: '大单净流入', dataIndex: 'bigNet', render: (v: number) => <span style={{ color: riseColor(v) }}>{bigMoney(v)}</span> },
    { title: '中单净流入', dataIndex: 'midNet', render: (v: number) => <span style={{ color: riseColor(v) }}>{bigMoney(v)}</span> },
    { title: '小单净流入', dataIndex: 'smallNet', render: (v: number) => <span style={{ color: riseColor(v) }}>{bigMoney(v)}</span> },
  ]

  const holderColumns: ColumnsType<HolderItem> = [
    { title: '日期', dataIndex: 'endDate', width: 110 },
    { title: '股东户数(户)', dataIndex: 'holderNum', render: (v: number) => Number(v).toLocaleString() },
    { title: '变动(%)', dataIndex: 'holderNumChg', render: (v: number) => <span style={{ color: riseColor(v) }}>{signedPct(v)}</span> },
    { title: '户均持股(股)', dataIndex: 'avgHold', render: (v: number) => Number(v).toLocaleString() },
  ]

  const marginColumns: ColumnsType<MarginItem> = [
    { title: '日期', dataIndex: 'tradeDate', width: 110 },
    { title: '融资融券余额', dataIndex: 'rzrqBalance', render: (v: number) => bigMoney(v) },
    { title: '融资余额', dataIndex: 'rzBalance', render: (v: number) => bigMoney(v) },
    { title: '融券余额', dataIndex: 'rqBalance', render: (v: number) => bigMoney(v) },
    { title: '涨跌幅(%)', dataIndex: 'rzrqChg', render: (v: number) => <span style={{ color: riseColor(v) }}>{signedPct(v)}</span> },
  ]

  const northboundColumns: ColumnsType<NorthboundItem> = [
    { title: '日期', dataIndex: 'endDate', width: 110 },
    { title: '持股(万股)', dataIndex: 'holdShares', render: (v: number) => (v / 1e4).toFixed(2) },
    { title: '持股占比(%)', dataIndex: 'holdSharesRatio', render: (v: number) => v?.toFixed(2) },
    { title: '持股市值', dataIndex: 'holdMarketCap', render: (v: number) => bigMoney(v) },
    { title: '占总股本(%)', dataIndex: 'totalSharesRatio', render: (v: number) => v?.toFixed(2) },
    { title: '机构数', dataIndex: 'orgQuantity' },
  ]

  const financialColumns: ColumnsType<FinancialItem> = [
    { title: '报告期', dataIndex: 'reportPeriod', width: 100 },
    { title: '类型', dataIndex: 'reportType', width: 80 },
    { title: '公告日', dataIndex: 'noticeDate', width: 110 },
    { title: '营业总收入(亿)', dataIndex: 'totalOperateIncome', render: (v: number) => (v / 1e8).toFixed(2) },
    { title: '归母净利润(亿)', dataIndex: 'parentNetProfit', render: (v: number) => (v / 1e8).toFixed(2) },
    { title: '基本EPS', dataIndex: 'basicEps', render: (v: number) => v?.toFixed(2) },
    { title: 'ROE(%)', dataIndex: 'weightAvgRoe', render: (v: number) => v?.toFixed(2) },
    { title: '营收同比(%)', dataIndex: 'ystz', render: (v: number) => <span style={{ color: riseColor(v) }}>{signedPct(v)}</span> },
    { title: '净利同比(%)', dataIndex: 'sjltz', render: (v: number) => <span style={{ color: riseColor(v) }}>{signedPct(v)}</span> },
  ]

  const eventsColumns: ColumnsType<EventsItem> = [
    { title: '日期', dataIndex: 'eventDate', width: 110 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '分类', dataIndex: 'category', width: 120, render: (v: string) => <Tag color={eventCategoryColors[v] || 'default'}>{v}</Tag> },
    { title: '公告', width: 100, render: (_: any, record: EventsItem) => record.pdfUrl ? <a href={record.pdfUrl} target="_blank" rel="noopener noreferrer">查看公告</a> : '-' },
  ]

  const tabItems = [
    {
      key: 'valuation',
      label: '估值',
      children: detail?.valuation?.length ? (
        <>
          <Table<ValuationItem> columns={valuationColumns} dataSource={detail.valuation} rowKey="tradeDate" size="small" pagination={false} scroll={{ x: 700 }} />
          <div ref={peChartRef} style={{ width: '100%', height: 260, marginTop: 16 }} />
        </>
      ) : <Empty description="暂无估值数据" />,
    },
    {
      key: 'moneyflow',
      label: '资金',
      children: detail?.moneyflow?.length ? (
        <>
          <Table<MoneyflowItem> columns={moneyflowColumns} dataSource={detail.moneyflow} rowKey="tradeDate" size="small" pagination={false} scroll={{ x: 800 }} />
          <div ref={moneyflowChartRef} style={{ width: '100%', height: 260, marginTop: 16 }} />
        </>
      ) : <Empty description="暂无资金流向数据" />,
    },
    {
      key: 'holder',
      label: '股东',
      children: detail?.holder?.length ? (
        <Table<HolderItem> columns={holderColumns} dataSource={detail.holder} rowKey="endDate" size="small" pagination={false} />
      ) : <Empty description="暂无股东数据" />,
    },
    {
      key: 'margin',
      label: '两融',
      children: detail?.margin?.length ? (
        <Table<MarginItem> columns={marginColumns} dataSource={detail.margin} rowKey="tradeDate" size="small" pagination={false} />
      ) : <Empty description="暂无两融数据" />,
    },
    {
      key: 'consensus',
      label: '一致预期',
      children: detail?.consensus?.length ? (() => {
        const c = detail.consensus[0]
        return (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions size="small" column={{ xs: 1, sm: 2, md: 3 }} bordered>
              <Descriptions.Item label="覆盖机构数">{c.ratingOrgNum}</Descriptions.Item>
              <Descriptions.Item label="目标价区间">{c.aimpriceMin?.toFixed(2)} ~ {c.aimpriceMax?.toFixed(2)}</Descriptions.Item>
              <Descriptions.Item label="更新日期">{c.fetchDate}</Descriptions.Item>
            </Descriptions>
            <Descriptions size="small" column={{ xs: 2, sm: 3, md: 5 }} bordered title="评级分布">
              <Descriptions.Item label="买入"><span style={{ color: '#f5222d', fontWeight: 600 }}>{c.ratingBuy}</span>家</Descriptions.Item>
              <Descriptions.Item label="增持"><span style={{ color: '#fa8c16', fontWeight: 600 }}>{c.ratingAdd}</span>家</Descriptions.Item>
              <Descriptions.Item label="中性"><span style={{ color: '#1677ff', fontWeight: 600 }}>{c.ratingNeutral}</span>家</Descriptions.Item>
              <Descriptions.Item label="减持"><span style={{ color: '#52c41a', fontWeight: 600 }}>{c.ratingReduce}</span>家</Descriptions.Item>
              <Descriptions.Item label="卖出"><span style={{ color: '#8c8c8c', fontWeight: 600 }}>{c.ratingSale}</span>家</Descriptions.Item>
            </Descriptions>
            <Descriptions size="small" column={{ xs: 1, sm: 2 }} bordered title="盈利预测(EPS)">
              {c.year1 && <Descriptions.Item label={`${c.year1}E EPS`}>{c.eps1?.toFixed(2)}</Descriptions.Item>}
              {c.year2 && <Descriptions.Item label={`${c.year2}E EPS`}>{c.eps2?.toFixed(2)}</Descriptions.Item>}
              {c.year3 && <Descriptions.Item label={`${c.year3}E EPS`}>{c.eps3?.toFixed(2)}</Descriptions.Item>}
              {c.year4 && <Descriptions.Item label={`${c.year4}E EPS`}>{c.eps4?.toFixed(2)}</Descriptions.Item>}
            </Descriptions>
            <div ref={ratingChartRef} style={{ width: '100%', height: 280 }} />
          </Space>
        )
      })() : <Empty description="暂无一致预期数据" />,
    },
    {
      key: 'northbound',
      label: '北向',
      children: detail?.northbound?.length ? (
        <Table<NorthboundItem> columns={northboundColumns} dataSource={detail.northbound} rowKey="endDate" size="small" pagination={false} />
      ) : <Empty description="暂无北向持股数据" />,
    },
    {
      key: 'financial',
      label: '财报',
      children: detail?.financial?.length ? (
        <Table<FinancialItem> columns={financialColumns} dataSource={detail.financial} rowKey={(r) => `${r.reportPeriod}-${r.noticeDate}`} size="small" pagination={false} scroll={{ x: 1000 }} />
      ) : <Empty description="暂无财报数据" />,
    },
    {
      key: 'events',
      label: '重大事件',
      children: detail?.events?.length ? (
        <Table<EventsItem> columns={eventsColumns} dataSource={detail.events} rowKey={(r) => `${r.eventDate}-${r.title}`} size="small" pagination={false} scroll={{ x: 700 }} />
      ) : <Empty description="暂无重大事件数据" />,
    },
  ]

  return (
    <Spin spinning={loading}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <Button icon={<ArrowLeftOutlined />} type="link" onClick={() => navigate('/quotes')}>返回行情</Button>

        <Card bordered={false}>
          <Space align="start" size="large">
            <div>
              <Typography.Title level={4} style={{ marginBottom: 0 }}>
                {quote?.name ?? code}
                <Typography.Text type="secondary" style={{ marginLeft: 12, fontSize: 14 }}>{code}</Typography.Text>
              </Typography.Title>
            </div>
            <Statistic
              title="最新价"
              value={quote?.price ?? 0}
              precision={2}
              valueStyle={{ color: riseColor(changeVal), fontSize: 28, fontWeight: 700 }}
            />
            <Statistic
              title="涨跌额"
              value={changeVal}
              precision={2}
              valueStyle={{ color: riseColor(changeVal) }}
              prefix={changeVal > 0 ? '+' : ''}
            />
            <Statistic
              title="涨跌幅"
              value={quote?.changePct ?? 0}
              precision={2}
              suffix="%"
              valueStyle={{ color: riseColor(changeVal), fontWeight: 600 }}
              prefix={changeVal > 0 ? '+' : ''}
            />
          </Space>
          <Descriptions size="small" column={6} style={{ marginTop: 16 }}>
            <Descriptions.Item label="昨收">{money(quote?.prevClose)}</Descriptions.Item>
            <Descriptions.Item label="今开">{money(quote?.open)}</Descriptions.Item>
            <Descriptions.Item label="最高">{money(quote?.high)}</Descriptions.Item>
            <Descriptions.Item label="最低">{money(quote?.low)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{quote?.time ?? '—'}</Descriptions.Item>
          </Descriptions>
        </Card>

        <Card
          bordered={false}
          title="K线图"
          extra={
            <Radio.Group value={klineType} onChange={(e) => setKlineType(e.target.value)} optionType="button" buttonStyle="solid" size="small">
              <Radio.Button value="day">日线</Radio.Button>
              <Radio.Button value="min5">5分钟</Radio.Button>
            </Radio.Group>
          }
        >
          <div ref={klineChartRef} style={{ width: '100%', height: 420 }} />
        </Card>

        <Card bordered={false} title="分时图">
          <div ref={timeChartRef} style={{ width: '100%', height: 360 }} />
        </Card>

        <Card bordered={false}>
          <Tabs items={tabItems} defaultActiveKey="valuation" />
        </Card>
      </Space>
    </Spin>
  )
}
