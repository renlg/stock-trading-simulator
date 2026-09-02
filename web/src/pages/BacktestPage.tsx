import { ExperimentOutlined } from '@ant-design/icons'
import { AutoComplete, Button, Card, Col, DatePicker, Form, Input, InputNumber, Row, Select, Space, Statistic, Table, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import * as echarts from 'echarts'
import { useCallback, useEffect, useRef, useState } from 'react'
import api from '../api'
import PageHeader from '../components/PageHeader'
import type { ApiResponse } from '../types'
import { getData, money, riseColor } from '../utils'
import dayjs from 'dayjs'

interface SearchStock { code: string; name: string }
interface ParamDef { key: string; label: string; type: string; defaultValue: number; min: number; max: number }
interface StrategyInfo { key: string; name: string; description: string; params: ParamDef[] }
interface EquityPoint { date: string; value: number }
interface TradeRecord { date: string; action: string; price: number; shares: number; amount: number; fee: number; cash: number }
interface BacktestResult {
  code: string; name: string; strategy: string; initialCapital: number; finalEquity: number
  metrics: { totalReturnPct: number; annualReturnPct: number; maxDrawdownPct: number; sharpe: number; winRate: number; tradeCount: number }
  equityCurve: EquityPoint[]; trades: TradeRecord[]
}

export default function BacktestPage() {
  const [strategies, setStrategies] = useState<StrategyInfo[]>([])
  const [stockOptions, setStockOptions] = useState<{ value: string; label: string }[]>([])
  const [selectedCode, setSelectedCode] = useState('')
  const [selectedStrategy, setSelectedStrategy] = useState('')
  const [params, setParams] = useState<Record<string, number>>({})
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([dayjs().subtract(1, 'year'), dayjs()])
  const [initialCapital, setInitialCapital] = useState(100000)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<BacktestResult | null>(null)
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstance = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    const load = async () => {
      try {
        const { data } = await api.get<ApiResponse<StrategyInfo[]>>('/backtest/strategies')
        const list = getData(data)
        if (list) {
          setStrategies(list)
          if (list.length > 0) {
            setSelectedStrategy(list[0].key)
            const defaults: Record<string, number> = {}
            list[0].params.forEach((p) => { defaults[p.key] = p.defaultValue })
            setParams(defaults)
          }
        }
      } catch { message.error('策略列表加载失败') }
    }
    void load()
  }, [])

  const searchStock = useCallback(async (q: string) => {
    if (!q.trim() || q.trim().length < 1) { setStockOptions([]); return }
    try {
      const { data } = await api.get<ApiResponse<SearchStock[]>>(`/stocks/search?q=${encodeURIComponent(q.trim())}&limit=10`)
      const list = getData(data)
      if (list) setStockOptions(list.map((s) => ({ value: s.code, label: `${s.code} ${s.name}` })))
    } catch { /* ignore */ }
  }, [])

  const onStrategyChange = (key: string) => {
    setSelectedStrategy(key)
    const s = strategies.find((x) => x.key === key)
    if (s) {
      const defaults: Record<string, number> = {}
      s.params.forEach((p) => { defaults[p.key] = p.defaultValue })
      setParams(defaults)
    }
  }

  const currentStrategy = strategies.find((s) => s.key === selectedStrategy)

  const runBacktest = async () => {
    if (!selectedCode) return message.warning('请选择股票')
    if (!selectedStrategy) return message.warning('请选择策略')
    setLoading(true)
    setResult(null)
    try {
      const { data } = await api.post<ApiResponse<BacktestResult>>('/backtest/run', {
        code: selectedCode,
        strategy: selectedStrategy,
        params,
        startDate: dateRange[0].format('YYYY-MM-DD'),
        endDate: dateRange[1].format('YYYY-MM-DD'),
        initialCapital,
      })
      const res = getData(data)
      if (res) {
        setResult(res)
        message.success('回测完成')
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '回测运行失败')
    } finally { setLoading(false) }
  }

  useEffect(() => {
    if (!result || !chartRef.current) return
    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current)
    }
    const chart = chartInstance.current
    const dates = result.equityCurve.map((p) => p.date)
    const values = result.equityCurve.map((p) => p.value)
    chart.setOption({
      tooltip: { trigger: 'axis', formatter: (p: any) => `${p[0].axisValue}<br/>${money(p[0].value)}` },
      grid: { left: 80, right: 30, top: 30, bottom: 40 },
      xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', axisLabel: { formatter: (v: number) => `${(v / 10000).toFixed(1)}万` } },
      series: [{
        type: 'line', data: values, smooth: true, symbol: 'none', lineStyle: { width: 2 },
        areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(245,34,45,0.3)' },
          { offset: 1, color: 'rgba(245,34,45,0.02)' },
        ]) },
        itemStyle: { color: '#f5222d' },
      }],
    })
    const onResize = () => chart.resize()
    window.addEventListener('resize', onResize)
    return () => { window.removeEventListener('resize', onResize) }
  }, [result])

  useEffect(() => {
    return () => { chartInstance.current?.dispose() }
  }, [])

  const tradeColumns: ColumnsType<TradeRecord> = [
    { title: '日期', dataIndex: 'date', width: 120 },
    { title: '操作', dataIndex: 'action', width: 80, render: (v: string) => <Typography.Text style={{ color: v === 'BUY' ? '#f5222d' : '#3f8600', fontWeight: 600 }}>{v === 'BUY' ? '买入' : '卖出'}</Typography.Text> },
    { title: '成交价', dataIndex: 'price', align: 'right', render: money },
    { title: '数量(股)', dataIndex: 'shares', align: 'right' },
    { title: '成交金额', dataIndex: 'amount', align: 'right', render: money },
    { title: '手续费', dataIndex: 'fee', align: 'right', render: money },
    { title: '剩余现金', dataIndex: 'cash', align: 'right', render: money },
  ]

  return (
    <>
      <PageHeader title="策略回测" description="使用历史数据验证交易策略的表现" />
      <Card bordered={false} className="section-card">
        <Form layout="vertical">
          <Row gutter={16}>
            <Col xs={24} md={8}>
              <Form.Item label="选择股票" required>
                <AutoComplete
                  value={selectedCode}
                  options={stockOptions}
                  onSearch={searchStock}
                  onSelect={(v) => setSelectedCode(v)}
                  onChange={(v) => setSelectedCode(v)}
                  style={{ width: '100%' }}
                >
                  <Input placeholder="输入代码或名称搜索，如 600519" allowClear />
                </AutoComplete>
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="选择策略" required>
                <Select value={selectedStrategy || undefined} placeholder="请选择策略" onChange={onStrategyChange} style={{ width: '100%' }}
                  options={strategies.map((s) => ({ value: s.key, label: `${s.name} - ${s.description}` }))} />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="回测区间" required>
                <DatePicker.RangePicker value={dateRange} onChange={(v) => { if (v && v[0] && v[1]) setDateRange([v[0], v[1]]) }} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          {currentStrategy && currentStrategy.params.length > 0 && (
            <Row gutter={16}>
              {currentStrategy.params.map((p) => (
                <Col xs={12} md={6} key={p.key}>
                  <Form.Item label={p.label}>
                    <InputNumber
                      value={params[p.key] ?? p.defaultValue}
                      min={p.min} max={p.max}
                      step={p.type === 'int' ? 1 : 0.1}
                      precision={p.type === 'int' ? 0 : 2}
                      onChange={(v) => setParams((prev) => ({ ...prev, [p.key]: Number(v ?? p.defaultValue) }))}
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                </Col>
              ))}
              <Col xs={12} md={6}>
                <Form.Item label="初始资金(元)">
                  <InputNumber value={initialCapital} min={1000} step={10000} precision={0} onChange={(v) => setInitialCapital(Number(v ?? 100000))} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
          )}
          <Button type="primary" icon={<ExperimentOutlined />} size="large" loading={loading} onClick={() => void runBacktest()} disabled={!selectedCode || !selectedStrategy}>
            开始回测
          </Button>
        </Form>
      </Card>

      {result && (
        <>
          <Row gutter={[16, 16]} className="stat-grid" style={{ marginTop: 16 }}>
            <Col xs={12} sm={8} md={4}>
              <Card bordered={false}><Statistic title="总收益率" value={result.metrics.totalReturnPct} precision={2} suffix="%" valueStyle={{ color: riseColor(result.metrics.totalReturnPct) }} /></Card>
            </Col>
            <Col xs={12} sm={8} md={4}>
              <Card bordered={false}><Statistic title="年化收益" value={result.metrics.annualReturnPct} precision={2} suffix="%" valueStyle={{ color: riseColor(result.metrics.annualReturnPct) }} /></Card>
            </Col>
            <Col xs={12} sm={8} md={4}>
              <Card bordered={false}><Statistic title="最大回撤" value={result.metrics.maxDrawdownPct} precision={2} suffix="%" valueStyle={{ color: '#3f8600' }} /></Card>
            </Col>
            <Col xs={12} sm={8} md={4}>
              <Card bordered={false}><Statistic title="夏普比率" value={result.metrics.sharpe} precision={2} /></Card>
            </Col>
            <Col xs={12} sm={8} md={4}>
              <Card bordered={false}><Statistic title="胜率" value={result.metrics.winRate} precision={2} suffix="%" /></Card>
            </Col>
            <Col xs={12} sm={8} md={4}>
              <Card bordered={false}><Statistic title="交易次数" value={result.metrics.tradeCount} prefix="" suffix="次" /></Card>
            </Col>
          </Row>

          <Card bordered={false} className="section-card" title={<Typography.Title level={4}>资金曲线</Typography.Title>}>
            <div ref={chartRef} style={{ width: '100%', height: 400 }} />
          </Card>

          <Card bordered={false} className="section-card" title={<Typography.Title level={4}>交易明细</Typography.Title>}>
            <Table<TradeRecord> rowKey={(_, i) => String(i)} columns={tradeColumns} dataSource={result.trades} pagination={{ pageSize: 20 }} scroll={{ x: 700 }} />
          </Card>
        </>
      )}
    </>
  )
}
