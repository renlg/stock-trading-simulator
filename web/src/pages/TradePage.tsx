import { Card, Col, Form, InputNumber, Row, Segmented, Select, Space, Statistic, Typography, message } from 'antd'
import { Button } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import api from '../api'
import PageHeader from '../components/PageHeader'
import type { ApiResponse, Quote } from '../types'
import { getData, money, riseColor, signedPct } from '../utils'

export default function TradePage() {
  const [quotes, setQuotes] = useState<Quote[]>([])
  const [side, setSide] = useState<'buy' | 'sell'>('buy')
  const [code, setCode] = useState('')
  const [quantity, setQuantity] = useState<number>(100)
  const [submitting, setSubmitting] = useState(false)
  const [params, setParams] = useSearchParams()
  const quote = useMemo(() => quotes.find((item) => item.code === code), [quotes, code])

  useEffect(() => {
    const load = async () => {
      try {
        const { data } = await api.get<ApiResponse<Quote[]>>('/quote')
        const result = getData(data)
        if (!result) return
        setQuotes(result)
        const requested = params.get('code')
        setCode(result.some((item) => item.code === requested) ? requested! : result[0]?.code || '')
      } catch { message.error('股票列表加载失败') }
    }
    void load()
  }, [params])

  const submit = async () => {
    if (!code || !quantity) return message.warning('请选择股票并输入数量')
    setSubmitting(true)
    try {
      const { data } = await api.post<ApiResponse<unknown>>(`/trade/${side}`, { code, quantity })
      if (getData(data) !== undefined) message.success(`${side === 'buy' ? '买入' : '卖出'}委托已提交`)
    } catch { message.error('交易提交失败') }
    finally { setSubmitting(false) }
  }

  return (
    <>
      <PageHeader title="股票交易" description="选择股票，提交模拟买入或卖出委托" />
      <Card bordered={false} className="quote-summary">
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Select showSearch optionFilterProp="label" value={code || undefined} placeholder="请选择股票" style={{ width: 260 }} options={quotes.map((q) => ({ value: q.code, label: `${q.code} ${q.name}` }))} onChange={(v) => { setCode(v); setParams({ code: v }) }} />
          {quote && <Row gutter={[24, 16]}><Col xs={12} md={6}><Statistic title="股票代码" value={quote.code} /></Col><Col xs={12} md={6}><Statistic title="股票名称" value={quote.name} /></Col><Col xs={12} md={6}><Statistic title="当前价格" value={quote.price} precision={2} prefix="¥" valueStyle={{ color: riseColor(quote.change) }} /></Col><Col xs={12} md={6}><Statistic title="涨跌幅" value={signedPct(quote.changePct)} valueStyle={{ color: riseColor(quote.changePct) }} /></Col></Row>}
        </Space>
      </Card>
      <Card bordered={false} title="提交委托" className="trade-form-card">
        <Form layout="vertical" className="trade-form">
          <Form.Item label="交易方向"><Segmented block value={side} options={[{ label: '买入', value: 'buy' }, { label: '卖出', value: 'sell' }]} onChange={(v) => setSide(v as typeof side)} /></Form.Item>
          <Form.Item label="委托数量">
            <InputNumber
              min={side === 'buy' ? 100 : 1}
              step={100}
              precision={0}
              value={quantity}
              onChange={(v) => setQuantity(Number(v || 0))}
              addonAfter="股"
              placeholder={side === 'buy' ? '买入需为100股整数倍' : '卖出数量'}
              style={{ width: '100%' }}
            />
            {side === 'buy' && <Typography.Text type="secondary" style={{ fontSize: 12 }}>买入数量必须为100的整数倍</Typography.Text>}
          </Form.Item>
          <div className="estimate"><Typography.Text type="secondary">预计金额</Typography.Text><Typography.Title level={3}>{money((quote?.price || 0) * quantity)}</Typography.Title></div>
          <Button type="primary" danger={side === 'buy'} block size="large" loading={submitting} disabled={!quote || quantity <= 0} onClick={() => void submit()}>{side === 'buy' ? '买入' : '卖出'}</Button>
        </Form>
      </Card>
    </>
  )
}
