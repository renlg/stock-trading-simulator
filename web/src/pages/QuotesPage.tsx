import { ReloadOutlined } from '@ant-design/icons'
import { Button, Card, Table, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api'
import PageHeader from '../components/PageHeader'
import type { ApiResponse, Quote } from '../types'
import { getData, money, riseColor, signedPct } from '../utils'

const columns: ColumnsType<Quote> = [
  { title: '股票代码', dataIndex: 'code', width: 110, render: (v: string) => <Typography.Text strong>{v}</Typography.Text> },
  { title: '股票名称', dataIndex: 'name', width: 120 },
  { title: '当前价格', dataIndex: 'price', align: 'right', render: (v: number, row) => <span style={{ color: riseColor(row.change) }}>{money(v)}</span> },
  { title: '涨跌额', dataIndex: 'change', align: 'right', render: (v: number) => <span style={{ color: riseColor(v) }}>{v > 0 ? '+' : ''}{v.toFixed(2)}</span> },
  { title: '涨跌幅', dataIndex: 'changePct', align: 'right', render: (v: number) => <span style={{ color: riseColor(v), fontWeight: 600 }}>{signedPct(v)}</span> },
  { title: '最高', dataIndex: 'high', align: 'right', render: money },
  { title: '最低', dataIndex: 'low', align: 'right', render: money },
]

export default function QuotesPage() {
  const [quotes, setQuotes] = useState<Quote[]>([])
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()
  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    try {
      const { data } = await api.get<ApiResponse<Quote[]>>('/quote')
      const result = getData(data)
      if (result) setQuotes(result)
    } catch { if (!quiet) message.error('行情加载失败') }
    finally { if (!quiet) setLoading(false) }
  }, [])

  useEffect(() => {
    void load()
    const timer = window.setInterval(() => void load(true), 5000)
    return () => window.clearInterval(timer)
  }, [load])

  return (
    <>
      <PageHeader title="行情中心" description="行情每 5 秒自动刷新，点击股票可直接交易" extra={<Button icon={<ReloadOutlined />} onClick={() => void load()}>立即刷新</Button>} />
      <Card bordered={false}><Table rowKey="code" columns={columns} dataSource={quotes} loading={loading} pagination={{ pageSize: 15, showSizeChanger: true }} onRow={(row) => ({ onClick: () => navigate(`/trade?code=${encodeURIComponent(row.code)}`), className: 'clickable-row' })} scroll={{ x: 800 }} /></Card>
    </>
  )
}
