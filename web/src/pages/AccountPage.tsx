import { ArrowUpOutlined, BankOutlined, WalletOutlined } from '@ant-design/icons'
import { Card, Col, Row, Statistic, Typography, message } from 'antd'
import { useEffect, useState } from 'react'
import api from '../api'
import OrderTable from '../components/OrderTable'
import PageHeader from '../components/PageHeader'
import PositionTable from '../components/PositionTable'
import type { Account, ApiResponse, Order } from '../types'
import { getData, riseColor } from '../utils'

interface OrderPage { content: Order[]; totalElements: number }

export default function AccountPage() {
  const [account, setAccount] = useState<Account>({ balance: 0, marketValue: 0, totalAssets: 0, totalProfit: 0, positions: [] })
  const [orders, setOrders] = useState<Order[]>([])
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    const load = async () => {
      try {
        const [accountRes, ordersRes] = await Promise.all([
          api.get<ApiResponse<Account>>('/account'),
          api.get<ApiResponse<Order[] | OrderPage>>('/orders?page=0&size=10'),
        ])
        const accountData = getData(accountRes.data)
        const orderData = getData(ordersRes.data)
        if (accountData) setAccount({ ...accountData, positions: accountData.positions || [] })
        if (orderData) setOrders(Array.isArray(orderData) ? orderData.slice(0, 10) : (orderData.content || []).slice(0, 10))
      } catch { message.error('账户数据加载失败') } finally { setLoading(false) }
    }
    void load()
  }, [])
  return (
    <>
      <PageHeader title="资金统计" description="总览账户资金、资产与近期交易" />
      <Row gutter={[16, 16]} className="stat-grid">
        <Col xs={24} sm={12} xl={6}><Card bordered={false}><Statistic title="可用资金" value={account.balance} precision={2} prefix={<WalletOutlined />} suffix="元" /></Card></Col>
        <Col xs={24} sm={12} xl={6}><Card bordered={false}><Statistic title="持仓市值" value={account.marketValue} precision={2} prefix={<BankOutlined />} suffix="元" /></Card></Col>
        <Col xs={24} sm={12} xl={6}><Card bordered={false}><Statistic title="总资产" value={account.totalAssets} precision={2} prefix="¥" /></Card></Col>
        <Col xs={24} sm={12} xl={6}><Card bordered={false}><Statistic title="总盈亏" value={account.totalProfit} precision={2} prefix={<ArrowUpOutlined />} suffix="元" valueStyle={{ color: riseColor(account.totalProfit) }} /></Card></Col>
      </Row>
      <Card bordered={false} className="section-card" title={<Typography.Title level={4}>我的持仓</Typography.Title>}><PositionTable data={account.positions} loading={loading} /></Card>
      <Card bordered={false} className="section-card" title={<Typography.Title level={4}>最近订单</Typography.Title>}><OrderTable data={orders} loading={loading} /></Card>
    </>
  )
}
