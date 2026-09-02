import { Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Position } from '../types'
import { money, number, riseColor, signedPct } from '../utils'

export const positionColumns: ColumnsType<Position> = [
  { title: '股票代码', dataIndex: 'code', width: 110 },
  { title: '股票名称', dataIndex: 'name', width: 120 },
  { title: '持仓数量', dataIndex: 'quantity', align: 'right', render: number },
  { title: '平均成本', dataIndex: 'avgCost', align: 'right', render: money },
  { title: '当前价格', dataIndex: 'price', align: 'right', render: money },
  { title: '市值', dataIndex: 'marketValue', align: 'right', render: money },
  { title: '浮动盈亏', dataIndex: 'profit', align: 'right', render: (v: number) => <Typography.Text style={{ color: riseColor(v) }}>{v > 0 ? '+' : ''}{money(v)}</Typography.Text> },
  { title: '盈亏比例', dataIndex: 'profitPct', align: 'right', render: (v: number) => <Typography.Text style={{ color: riseColor(v) }}>{signedPct(v)}</Typography.Text> },
]

export default function PositionTable({ data, loading = false, pagination = false }: { data: Position[]; loading?: boolean; pagination?: false | object }) {
  return <Table rowKey="code" columns={positionColumns} dataSource={data} loading={loading} pagination={pagination} scroll={{ x: 900 }} />
}
