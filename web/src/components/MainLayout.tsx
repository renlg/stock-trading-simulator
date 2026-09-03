import { ApiOutlined, BarChartOutlined, CalendarOutlined, ExperimentOutlined, LogoutOutlined, OrderedListOutlined, PieChartOutlined, SettingOutlined, SwapOutlined } from '@ant-design/icons'
import { Button, Layout, Menu, Space, Typography } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

const { Header, Content, Sider } = Layout

const baseItems = [
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
  const isAdmin = localStorage.getItem('role') === 'admin'
  const items = isAdmin
    ? [...baseItems, { key: '/calendar', icon: <CalendarOutlined />, label: '交易日历' }, { key: '/admin/users', icon: <SettingOutlined />, label: '账号管理' }]
    : baseItems

  const logout = () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
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
