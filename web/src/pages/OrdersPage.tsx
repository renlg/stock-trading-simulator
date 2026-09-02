import { Card, message } from 'antd'
import type { TablePaginationConfig } from 'antd/es/table'
import { useCallback, useEffect, useState } from 'react'
import api from '../api'
import OrderTable from '../components/OrderTable'
import PageHeader from '../components/PageHeader'
import type { ApiResponse, Order } from '../types'
import { getData } from '../utils'

interface OrderPage { content: Order[]; totalElements: number; number?: number; size?: number }

export default function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [total, setTotal] = useState(0)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const { data } = await api.get<ApiResponse<Order[] | OrderPage>>(`/orders?page=${page - 1}&size=${pageSize}`)
      const result = getData(data)
      if (result) {
        const bare = Array.isArray(result)
        setOrders(bare ? result : (result.content || []))
        setTotal(bare ? result.length : Number(result.totalElements || 0))
      }
    } catch { message.error('订单历史加载失败') } finally { setLoading(false) }
  }, [page, pageSize])
  useEffect(() => { void load() }, [load])

  const changePage = (pagination: TablePaginationConfig) => {
    const nextSize = pagination.pageSize || 10
    setPage(nextSize !== pageSize ? 1 : (pagination.current || 1))
    setPageSize(nextSize)
  }
  return <><PageHeader title="订单历史" description="查看全部模拟交易订单与执行状态" /><Card bordered={false}><OrderTable data={orders} loading={loading} onChange={changePage} pagination={{ current: page, pageSize, total, showSizeChanger: true, pageSizeOptions: [10, 20, 50], showTotal: (count) => `共 ${count} 条` }} /></Card></>
}
