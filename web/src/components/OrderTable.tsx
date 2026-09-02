import { Tag, Table } from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import type { Order } from '../types'
import { money, number, time } from '../utils'

const sourceMap: Record<string, string> = { web: '网页', api: 'API', condition: '条件单' }
const statusMap: Record<string, string> = { success: '已成交', filled: '已成交', failed: '失败', pending: '处理中', cancelled: '已取消', canceled: '已取消' }

export const orderColumns: ColumnsType<Order> = [
  { title: '时间', dataIndex: 'createdAt', width: 180, render: time },
  { title: '股票代码', dataIndex: 'code', width: 110 },
  { title: '方向', dataIndex: 'side', width: 80, render: (v: string) => { const side = v?.toLowerCase(); return <Tag color={side === 'buy' ? 'red' : 'green'}>{side === 'buy' ? '买入' : '卖出'}</Tag> } },
  { title: '价格', dataIndex: 'price', align: 'right', render: money },
  { title: '数量', dataIndex: 'quantity', align: 'right', render: number },
  { title: '金额', dataIndex: 'amount', align: 'right', render: money },
  { title: '来源', dataIndex: 'source', render: (v: string) => sourceMap[v?.toLowerCase()] || v || '—' },
  { title: '状态', dataIndex: 'status', render: (v: string) => statusMap[v?.toLowerCase()] || v || '—' },
]

export default function OrderTable({ data, loading = false, pagination = false, onChange }: { data: Order[]; loading?: boolean; pagination?: false | TablePaginationConfig; onChange?: (pagination: TablePaginationConfig) => void }) {
  return <Table rowKey="id" columns={orderColumns} dataSource={data} loading={loading} pagination={pagination} onChange={onChange} scroll={{ x: 900 }} />
}
