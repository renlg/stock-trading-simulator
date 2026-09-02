import { LockOutlined, StockOutlined, UserOutlined } from '@ant-design/icons'
import { Button, Card, Form, Input, Segmented, Typography, message } from 'antd'
import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import api from '../api'
import type { ApiResponse } from '../types'
import { getData } from '../utils'

interface AuthResult { token: string; username: string }

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  if (localStorage.getItem('token')) return <Navigate to="/quotes" replace />

  const submit = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      const { data } = await api.post<ApiResponse<AuthResult | null>>(`/auth/${mode}`, values)
      const result = getData(data)
      if (result === undefined) return
      if (mode === 'register') {
        message.success('注册成功，请登录')
        setMode('login')
        return
      }
      if (!result?.token) {
        message.error('登录响应中缺少令牌')
        return
      }
      localStorage.setItem('token', result.token)
      localStorage.setItem('username', result.username || values.username)
      message.success('登录成功')
      navigate('/quotes', { replace: true })
    } catch {
      message.error('网络异常，请稍后重试')
    } finally { setLoading(false) }
  }

  return (
    <div className="login-page">
      <div className="login-intro">
        <div className="login-logo"><StockOutlined /></div>
        <Typography.Title>模拟股票交易系统</Typography.Title>
        <Typography.Paragraph>在真实行情节奏中练习策略，零风险积累交易经验。</Typography.Paragraph>
      </div>
      <Card className="login-card" bordered={false}>
        <Segmented block value={mode} options={[{ label: '登录', value: 'login' }, { label: '注册', value: 'register' }]} onChange={(v) => setMode(v as typeof mode)} />
        <Typography.Title level={3}>{mode === 'login' ? '欢迎回来' : '创建账号'}</Typography.Title>
        <Typography.Text type="secondary">{mode === 'login' ? '登录后开始您的模拟交易' : '注册一个新的模拟交易账号'}</Typography.Text>
        <Form layout="vertical" size="large" onFinish={submit} requiredMark={false} className="login-form">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}><Input prefix={<UserOutlined />} placeholder="请输入用户名" autoComplete="username" /></Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }, { min: 6, message: '密码至少为 6 位' }]}><Input.Password prefix={<LockOutlined />} placeholder="请输入密码" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></Form.Item>
          <Button block type="primary" htmlType="submit" loading={loading}>{mode === 'login' ? '登录' : '注册'}</Button>
        </Form>
      </Card>
    </div>
  )
}
