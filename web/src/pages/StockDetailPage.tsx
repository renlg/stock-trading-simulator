import { ArrowLeftOutlined } from '@ant-design/icons'
import { Button, Card, Descriptions, Radio, Space, Spin, Statistic, Typography } from 'antd'
import * as echarts from 'echarts'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import api from '../api'
import type { ApiResponse } from '../types'
import { getData, money, riseColor, signedPct } from '../utils'

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

const UP_COLOR = '#ef232a'
const DOWN_COLOR = '#14b143'

export default function StockDetailPage() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const [quote, setQuote] = useState<QuoteData | null>(null)
  const [klineType, setKlineType] = useState<'day' | 'min5'>('day')
  const [klineData, setKlineData] = useState<KlineData | null>(null)
  const [loading, setLoading] = useState(true)
  const klineChartRef = useRef<HTMLDivElement>(null)
  const timeChartRef = useRef<HTMLDivElement>(null)
  const klineChartInstance = useRef<echarts.ECharts | null>(null)
  const timeChartInstance = useRef<echarts.ECharts | null>(null)

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

  useEffect(() => {
    setLoading(true)
    Promise.all([loadQuote(), loadKline(klineType)]).finally(() => setLoading(false))
  }, [loadQuote, loadKline, klineType])

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
    // 分时图用 min5 数据; 如果当前是 day 则也加载 min5 来画分时
    if (klineType !== 'min5') return

    if (!timeChartInstance.current) {
      timeChartInstance.current = echarts.init(timeChartRef.current)
    }
    const chart = timeChartInstance.current
    const bars = klineData.bars
    // 取当日数据
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

  // resize
  useEffect(() => {
    const handleResize = () => {
      klineChartInstance.current?.resize()
      timeChartInstance.current?.resize()
    }
    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      klineChartInstance.current?.dispose()
      timeChartInstance.current?.dispose()
    }
  }, [])

  const changeVal = quote?.change ?? 0

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
      </Space>
    </Spin>
  )
}
