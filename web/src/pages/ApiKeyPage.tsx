import { CopyOutlined, DeleteOutlined, KeyOutlined, PlusOutlined } from '@ant-design/icons'
import { Button, Card, Form, Input, Modal, Popconfirm, Space, Table, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useState } from 'react'
import api from '../api'
import PageHeader from '../components/PageHeader'
import type { ApiKeyItem, ApiResponse } from '../types'
import { getData, time } from '../utils'

interface CreatedKey { apiKey: string; id: number | string }

export default function ApiKeyPage() {
  const [keys, setKeys] = useState<ApiKeyItem[]>([])
  const [loading, setLoading] = useState(true)
  const [generating, setGenerating] = useState(false)
  const [plainKey, setPlainKey] = useState('')
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try { const { data } = await api.get<ApiResponse<ApiKeyItem[]>>('/apikeys'); const result = getData(data); if (result) setKeys(result) }
    catch { message.error('API Key 加载失败') } finally { setLoading(false) }
  }
  useEffect(() => { void load() }, [])

  const create = async ({ name }: { name: string }) => {
    setGenerating(true)
    try {
      const { data } = await api.post<ApiResponse<CreatedKey>>('/apikeys', { name })
      const result = getData(data)
      if (result) { setPlainKey(result.apiKey); form.resetFields(); await load() }
    } catch { message.error('生成 API Key 失败') } finally { setGenerating(false) }
  }
  const remove = async (id: ApiKeyItem['id']) => {
    try { const { data } = await api.delete<ApiResponse<unknown>>(`/apikeys/${id}`); if (getData(data) !== undefined) { message.success('API Key 已删除'); await load() } }
    catch { message.error('删除失败') }
  }
  const copy = async () => {
    try { await navigator.clipboard.writeText(plainKey); message.success('已复制到剪贴板') }
    catch { message.error('复制失败，请手动复制') }
  }
  const columns: ColumnsType<ApiKeyItem> = [
    { title: '名称', dataIndex: 'name' },
    { title: 'API Key', dataIndex: 'apiKeyMask', render: (v: string) => <Typography.Text code>{v ? (v.includes('*') ? v : `${v.slice(0, 8)}***`) : '—'}</Typography.Text> },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, render: time },
    { title: '最后使用', dataIndex: 'lastUsedAt', width: 180, render: time },
    { title: '操作', key: 'action', width: 90, render: (_, row) => <Popconfirm title="确认删除该 API Key？" description="删除后无法恢复。" onConfirm={() => void remove(row.id)}><Button type="text" danger icon={<DeleteOutlined />}>删除</Button></Popconfirm> },
  ]
  return (
    <>
      <PageHeader title="账号设置" description="创建和管理用于程序化交易的 API Key" />
      <Card bordered={false} title={<Space><KeyOutlined />生成 API Key</Space>}>
        <Form form={form} layout="inline" onFinish={create} className="key-form">
          <Form.Item name="name" rules={[{ required: true, message: '请输入名称' }]}><Input placeholder="例如：量化策略测试" maxLength={50} /></Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" icon={<PlusOutlined />} loading={generating}>生成</Button></Form.Item>
        </Form>
      </Card>
      <Card bordered={false} title="现有 API Key" className="section-card"><Table rowKey="id" columns={columns} dataSource={keys} loading={loading} scroll={{ x: 700 }} /></Card>
      <Card bordered={false} title="API 调用说明" className="section-card">
        <Typography.Paragraph type="secondary">在请求头中通过 Bearer 方式携带 API Key。以下示例通过公开接口获取行情：</Typography.Paragraph>
        <pre className="code-block"><code>{`curl -H "Authorization: Bearer <api-key>" \\\n+  http://localhost:8080/api/v1/quote\n\ncurl -H "Authorization: Bearer <api-key>" \\\n+  http://localhost:8080/api/v1/quote/000001`}</code></pre>
      </Card>
      <Modal title="API Key 已生成" open={Boolean(plainKey)} footer={<Button type="primary" onClick={() => setPlainKey('')}>我已保存</Button>} onCancel={() => setPlainKey('')} maskClosable={false}>
        <Typography.Paragraph type="danger" strong>请立即保存，只显示一次</Typography.Paragraph>
        <Input.TextArea value={plainKey} readOnly autoSize={{ minRows: 2, maxRows: 4 }} />
        <Button icon={<CopyOutlined />} onClick={() => void copy()} className="copy-button">复制 API Key</Button>
      </Modal>
    </>
  )
}
