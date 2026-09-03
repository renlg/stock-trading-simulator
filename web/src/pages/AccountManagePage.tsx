import { Button, Form, Input, Modal, Popconfirm, Space, Switch, Table, Tag, message } from 'antd'
import { useEffect, useState } from 'react'
import api from '../api'
import type { AdminUser, ApiResponse } from '../types'
import { getData, time } from '../utils'
import PageHeader from '../components/PageHeader'

export default function AccountManagePage() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loading, setLoading] = useState(false)
  const [pwdModal, setPwdModal] = useState<{ visible: boolean; user?: AdminUser }>({ visible: false })
  const [pwdForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      const { data } = await api.get<ApiResponse<AdminUser[]>>('/admin/users')
      const result = getData(data)
      if (result) setUsers(result)
    } finally { setLoading(false) }
  }

  useEffect(() => { load() }, [])

  const handleResetPassword = async (values: { password: string }) => {
    if (!pwdModal.user) return
    const { data } = await api.post(`/admin/users/${pwdModal.user.id}/reset-password`, values)
    if (getData(data) !== undefined) {
      message.success('密码已重置')
      setPwdModal({ visible: false })
      pwdForm.resetFields()
    }
  }

  const handleToggleStatus = async (user: AdminUser) => {
    const action = user.status === 'active' ? 'disable' : 'enable'
    const { data } = await api.post(`/admin/users/${user.id}/${action}`)
    if (getData(data) !== undefined) {
      message.success(user.status === 'active' ? '已禁用' : '已启用')
      load()
    }
  }

  const handleDelete = async (user: AdminUser) => {
    const { data } = await api.delete(`/admin/users/${user.id}`)
    if (getData(data) !== undefined) {
      message.success('已删除')
      load()
    }
  }

  const currentUsername = localStorage.getItem('username')

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '用户名', dataIndex: 'username' },
    { title: '角色', dataIndex: 'role', width: 100, render: (v: string) => v === 'admin' ? <Tag color="red">管理员</Tag> : <Tag>普通用户</Tag> },
    { title: '余额', dataIndex: 'balance', width: 140, render: (v: number) => `¥${Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` },
    { title: '状态', dataIndex: 'status', width: 100, render: (v: string) => v === 'active' ? <Tag color="green">正常</Tag> : <Tag color="red">已禁用</Tag> },
    { title: '创建时间', dataIndex: 'createdAt', width: 180, render: (v: string) => time(v) },
    {
      title: '操作', width: 240, render: (_: unknown, record: AdminUser) => {
        const isSelf = record.username === currentUsername
        return (
          <Space>
            <Button size="small" onClick={() => { setPwdModal({ visible: true, user: record }); pwdForm.resetFields() }}>重置密码</Button>
            {!isSelf && (
              <Popconfirm title={record.status === 'active' ? '确定禁用该账号？' : '确定启用该账号？'} onConfirm={() => handleToggleStatus(record)}>
                <Switch size="small" checked={record.status === 'active'} checkedChildren="启用" unCheckedChildren="禁用" />
              </Popconfirm>
            )}
            {!isSelf && (
              <Popconfirm title="确定删除该账号？此操作不可恢复。" onConfirm={() => handleDelete(record)}>
                <Button size="small" danger>删除</Button>
              </Popconfirm>
            )}
          </Space>
        )
      },
    },
  ]

  return (
    <>
      <PageHeader title="账号管理" description="管理系统中的所有用户账号" />
      <Table rowKey="id" loading={loading} dataSource={users} columns={columns} pagination={{ pageSize: 20 }} />
      <Modal title={`重置密码 - ${pwdModal.user?.username ?? ''}`} open={pwdModal.visible} onCancel={() => setPwdModal({ visible: false })} onOk={() => pwdForm.submit()} destroyOnClose>
        <Form form={pwdForm} layout="vertical" onFinish={handleResetPassword}>
          <Form.Item name="password" label="新密码" rules={[{ required: true, message: '请输入新密码' }, { min: 6, message: '密码至少为 6 位' }]}>
            <Input.Password placeholder="请输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
