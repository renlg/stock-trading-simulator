import { PlusOutlined } from '@ant-design/icons'
import { Button, Card, Form, InputNumber, Modal, Popconfirm, Segmented, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import api from '../api'
import PageHeader from '../components/PageHeader'
import type { ApiResponse, ConditionOrder, Quote } from '../types'
import { getData, money, number, time } from '../utils'

const statusMap: Record<string, { text: string; color: string }> = {
  active: { text: '激活中', color: 'processing' }, triggered: { text: '已触发', color: 'success' },
  failed: { text: '已失败', color: 'error' }, cancelled: { text: '已取消', color: 'default' }, canceled: { text: '已取消', color: 'default' },
}

export default function ConditionsPage() {
  const [items, setItems] = useState<ConditionOrder[]>([])
  const [quotes, setQuotes] = useState<Quote[]>([])
  const [loading, setLoading] = useState(true)
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()
  const type = Form.useWatch('type', form) || 'buy'

  const load = async () => {
    setLoading(true)
    try {
      const [conditionsRes, quotesRes] = await Promise.all([api.get<ApiResponse<ConditionOrder[]>>('/conditions'), api.get<ApiResponse<Quote[]>>('/quote')])
      const conditions = getData(conditionsRes.data); const quoteList = getData(quotesRes.data)
      if (conditions) setItems(conditions); if (quoteList) setQuotes(quoteList)
    } catch { message.error('条件单加载失败') } finally { setLoading(false) }
  }
  useEffect(() => { void load() }, [])

  const create = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      const { data } = await api.post<ApiResponse<unknown>>('/conditions', values)
      if (getData(data) !== undefined) { message.success('条件单创建成功'); setOpen(false); form.resetFields(); await load() }
    } catch { message.error('条件单创建失败') } finally { setSaving(false) }
  }
  const cancel = async (id: ConditionOrder['id']) => {
    try { const { data } = await api.post<ApiResponse<unknown>>(`/conditions/${id}/cancel`); if (getData(data) !== undefined) { message.success('条件单已取消'); await load() } }
    catch { message.error('取消失败') }
  }
  const columns: ColumnsType<ConditionOrder> = [
    { title: '股票代码', dataIndex: 'code' },
    { title: '类型', dataIndex: 'type', render: (v: string) => { const side = v?.toLowerCase(); return <Tag color={side === 'buy' ? 'red' : 'green'}>{side === 'buy' ? '买入' : '卖出'}</Tag> } },
    { title: '触发价格', dataIndex: 'triggerPrice', align: 'right', render: money },
    { title: '数量', dataIndex: 'quantity', align: 'right', render: number },
    { title: '状态', dataIndex: 'status', render: (v: string) => { const status = statusMap[v?.toLowerCase()] || { text: v, color: 'default' }; return <Tag color={status.color}>{status.text}</Tag> } },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, render: time },
    { title: '操作', key: 'action', render: (_, row) => row.status?.toLowerCase() === 'active' ? <Popconfirm title="确认取消该条件单？" onConfirm={() => void cancel(row.id)}><Button type="link" danger>取消</Button></Popconfirm> : '—' },
  ]
  return (
    <>
      <PageHeader title="条件单" description="价格达到条件时，系统自动提交模拟交易" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => { form.setFieldsValue({ type: 'buy', quantity: 100 }); setOpen(true) }}>新建条件单</Button>} />
      <Card bordered={false}><Table rowKey="id" columns={columns} dataSource={items} loading={loading} scroll={{ x: 800 }} /></Card>
      <Modal title="新建条件单" open={open} okText="创建" cancelText="取消" confirmLoading={saving} onOk={() => void create()} onCancel={() => setOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item name="code" label="股票" rules={[{ required: true, message: '请选择股票' }]}><Select showSearch optionFilterProp="label" placeholder="请选择股票" options={quotes.map((q) => ({ value: q.code, label: `${q.code} ${q.name}（现价 ${money(q.price)}）` }))} /></Form.Item>
          <Form.Item name="type" label="交易类型" rules={[{ required: true }]}><Segmented block options={[{ label: '买入', value: 'buy' }, { label: '卖出', value: 'sell' }]} /></Form.Item>
          <Form.Item name="triggerPrice" label="触发价格" rules={[{ required: true, message: '请输入触发价格' }]}><InputNumber min={0.01} step={0.01} precision={2} prefix="¥" style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="quantity" label="数量" rules={[{ required: true, message: '请输入数量' }]}><InputNumber min={1} step={100} precision={0} addonAfter="股" style={{ width: '100%' }} /></Form.Item>
          <Typography.Text type="secondary">{type === 'buy' ? '买入：现价≤触发价时自动买入' : '卖出：现价≥触发价时自动卖出'}</Typography.Text>
        </Form>
      </Modal>
    </>
  )
}
