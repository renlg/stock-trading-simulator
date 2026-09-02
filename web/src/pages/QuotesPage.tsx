import { DeleteOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { Button, Card, Input, Modal, Popconfirm, Table, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api'
import PageHeader from '../components/PageHeader'
import type { ApiResponse, Quote } from '../types'
import { getData, money, riseColor, signedPct } from '../utils'

interface SearchStock { code: string; name: string; industry?: string | null }

export default function QuotesPage() {
  const [quotes, setQuotes] = useState<Quote[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [searching, setSearching] = useState(false)
  const [results, setResults] = useState<SearchStock[]>([])

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true)
    try {
      const { data } = await api.get<ApiResponse<Quote[]>>('/quote')
      const result = getData(data)
      if (result) setQuotes(result)
    } catch { if (!quiet) message.error('行情加载失败') }
    finally { if (!quiet) setLoading(false) }
  }, [])

  const remove = useCallback(async (code: string) => {
    try {
      await api.delete<ApiResponse<unknown>>(`/stocks/watch/${code}`)
      message.success('已移除')
      void load(true)
    } catch { message.error('移除失败') }
  }, [load])

  useEffect(() => {
    void load()
    const timer = window.setInterval(() => void load(true), 5000)
    return () => window.clearInterval(timer)
  }, [load])

  const doSearch = async (q: string) => {
    if (!q.trim()) return
    setSearching(true)
    try {
      const { data } = await api.get<ApiResponse<SearchStock[]>>(`/stocks/search?q=${encodeURIComponent(q.trim())}&limit=10`)
      const result = getData(data)
      setResults(result ?? [])
      if (result?.length === 0) message.info('未找到匹配股票')
    } catch { message.error('搜索失败') }
    finally { setSearching(false) }
  }

  const addStock = async (s: SearchStock) => {
    try {
      await api.post<ApiResponse<unknown>>('/stocks/watch', { code: s.code })
      message.success(`已添加 ${s.name}`)
      setResults((prev) => prev.filter((x) => x.code !== s.code))
      void load(true)
    } catch (e: any) {
      message.error(e?.response?.data?.message || '添加失败')
    }
  }

  const columns: ColumnsType<Quote> = [
    { title: '股票代码', dataIndex: 'code', width: 110, render: (v: string) => <Typography.Text strong>{v}</Typography.Text> },
    { title: '股票名称', dataIndex: 'name', width: 120, render: (v: string, row) => <Link to={`/stock/${row.code}`}>{v}</Link> },
    { title: '当前价格', dataIndex: 'price', align: 'right', render: (v: number, row) => <span style={{ color: riseColor(row.change) }}>{money(v)}</span> },
    { title: '涨跌额', dataIndex: 'change', align: 'right', render: (v: number) => <span style={{ color: riseColor(v) }}>{v > 0 ? '+' : ''}{v.toFixed(2)}</span> },
    { title: '涨跌幅', dataIndex: 'changePct', align: 'right', render: (v: number) => <span style={{ color: riseColor(v), fontWeight: 600 }}>{signedPct(v)}</span> },
    { title: '最高', dataIndex: 'high', align: 'right', render: money },
    { title: '最低', dataIndex: 'low', align: 'right', render: money },
    {
      title: '操作', key: 'action', width: 70, align: 'center',
      render: (_, row) => (
        <Popconfirm title="移除该股票？" description="从关注池删除，不再显示行情" okText="移除" cancelText="取消" onConfirm={() => void remove(row.code)}>
          <Button type="text" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="行情中心"
        description="行情每 5 秒自动刷新"
        extra={
          <span>
            <Button icon={<PlusOutlined />} type="primary" onClick={() => setOpen(true)} style={{ marginRight: 8 }}>添加股票</Button>
            <Button icon={<ReloadOutlined />} onClick={() => void load()}>立即刷新</Button>
          </span>
        }
      />
      <Card bordered={false}>
        <Table rowKey="code" columns={columns} dataSource={quotes} loading={loading} pagination={{ pageSize: 15, showSizeChanger: true }} scroll={{ x: 800 }} />
      </Card>

      <Modal title="添加股票" open={open} onCancel={() => setOpen(false)} footer={null} width={560}>
        <Input.Search
          placeholder="输入股票代码或名称搜索，如 600519 / 茅台"
          enterButton="搜索"
          loading={searching}
          onSearch={(v) => void doSearch(v)}
          allowClear
          style={{ marginBottom: 12 }}
        />
        <Table<SearchStock>
          rowKey="code"
          size="small"
          dataSource={results}
          pagination={false}
          locale={{ emptyText: '输入关键词搜索 A 股' }}
          columns={[
            { title: '代码', dataIndex: 'code', width: 100 },
            { title: '名称', dataIndex: 'name' },
            { title: '行业', dataIndex: 'industry', render: (v?: string | null) => v || '-' },
            { title: '', key: 'op', width: 80, align: 'center', render: (_, row) => <Button type="link" size="small" onClick={() => void addStock(row)}>添加</Button> },
          ]}
        />
      </Modal>
    </>
  )
}
