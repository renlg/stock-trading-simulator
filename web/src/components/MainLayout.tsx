import { ApiOutlined, BarChartOutlined, ExperimentOutlined, LogoutOutlined, OrderedListOutlined, PieChartOutlined, SwapOutlined } from '@ant-design/icons'
import { Button, Layout, Menu, Space, Typography } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

const { Header, Content, Sider } = Layout

const items = [
  { key: '/quotes', icon: <BarChartOutlined />, label: '行情中心' },
  { key: '/trade', icon: <SwapOutlined />, label: '交易' },
  { key: '/positions', icon: <PieChartOutlined />, label: '持仓' },
  { key: '/conditions', icon: <ApiOutlined />, label: '条件单' },
  { key: '/account', icon: <BarChartOutlined />, label: '资金统计' },
  { key: '/orders', icon: <OrderedListOutlined />, label: '订单历史' },
  { key: '/backtest', icon: <ExperimentOutlined />, label: '策略回测' },
]

export default function MainLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const logout = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('username')
    navigate('/login', { replace: true })
  }

  return (
    <Layout className="app-shell">
      <Sider width={220} breakpoint="lg" collapsedWidth="0" className="app-sider">
        <div className="brand"><span className="brand-mark">股</span><span>模拟交易</span></div>
        <Menu theme="dark" mode="inline" selectedKeys={[location.pathname]} items={items} onClick={({ key }) => navigate(key)} />
      </Sider>
      <Layout>
        <Header className="app-header">
          <Typography.Title level={4} className="system-title">模拟股票交易系统</Typography.Title>
          <Space size="middle">
            <span className="username">{localStorage.getItem('username') || '用户'}</span>
            <Button type="text" icon={<LogoutOutlined />} onClick={logout}>退出登录</Button>
          </Space>
        </Header>
        <Content className="app-content"><Outlet /></Content>
      </Layout>
    </Layout>
  )
}
