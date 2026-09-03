import { CalendarOutlined } from '@ant-design/icons'
import { Badge, Calendar, Card, Modal, Typography, message } from 'antd'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { useEffect, useState } from 'react'
import api from '../api'
import PageHeader from '../components/PageHeader'
import type { ApiResponse } from '../types'
import { getData } from '../utils'

export default function CalendarPage() {
  const [tradingDays, setTradingDays] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(true)

  const load = async () => {
    setLoading(true)
    try {
      const { data } = await api.get<ApiResponse<string[]>>('/calendar')
      const result = getData(data)
      if (result) setTradingDays(new Set(result))
    } catch {
      message.error('交易日历加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void load() }, [])

  const isWeekend = (date: Dayjs) => {
    const dow = date.day()
    return dow === 0 || dow === 6
  }

  const isTradingDay = (date: Dayjs) => tradingDays.has(date.format('YYYY-MM-DD'))

  const handleDateClick = (date: Dayjs) => {
    if (isWeekend(date)) return
    const dateStr = date.format('YYYY-MM-DD')
    const trading = isTradingDay(date)

    if (trading) {
      Modal.confirm({
        title: '标记为非交易日',
        content: `将 ${dateStr} 标记为休市（节假日）？标记后该日将无法交易。`,
        okText: '标记休市',
        cancelText: '取消',
        onOk: async () => {
          try {
            await api.post<ApiResponse<unknown>>('/calendar/holidays', { date: dateStr, isHoliday: true })
            message.success(`已将 ${dateStr} 标记为非交易日`)
            await load()
          } catch {
            message.error('操作失败')
          }
        },
      })
    } else {
      Modal.confirm({
        title: '恢复为交易日',
        content: `将 ${dateStr} 恢复为交易日？恢复后该日可以交易（用于调休补班开市）。`,
        okText: '恢复交易日',
        cancelText: '取消',
        onOk: async () => {
          try {
            await api.post<ApiResponse<unknown>>('/calendar/holidays', { date: dateStr, isHoliday: false })
            message.success(`已将 ${dateStr} 恢复为交易日`)
            await load()
          } catch {
            message.error('操作失败')
          }
        },
      })
    }
  }

  const cellRender = (date: Dayjs) => {
    const dow = date.day()
    const weekend = dow === 0 || dow === 6
    const trading = isTradingDay(date)

    let bg: string
    let color: string
    if (weekend) {
      bg = 'transparent'
      color = '#bbb'
    } else if (trading) {
      bg = '#f6ffed'
      color = '#389e0d'
    } else {
      bg = '#fff2f0'
      color = '#cf1322'
    }

    return (
      <div
        onClick={(e) => { e.stopPropagation(); handleDateClick(date) }}
        style={{
          width: '100%',
          height: '100%',
          padding: '2px 4px',
          borderRadius: 4,
          background: bg,
          color,
          cursor: weekend ? 'default' : 'pointer',
          fontSize: 12,
          lineHeight: '20px',
        }}
      >
        <span>{date.date()}</span>
        {!weekend && (
          <Badge
            status={trading ? 'success' : 'error'}
            text={<span style={{ fontSize: 10, color }}>{trading ? '交易' : '休市'}</span>}
            style={{ display: 'block', marginTop: -2 }}
          />
        )}
      </div>
    )
  }

  return (
    <>
      <PageHeader
        title="交易日历"
        description="交易日以沪深交易所实际休市安排为准，官方公布后可手动维护"
      />
      <Card bordered={false} loading={loading}>
        <div style={{ marginBottom: 16, display: 'flex', gap: 24, alignItems: 'center' }}>
          <span><Badge status="success" text="交易日" /></span>
          <span><Badge status="error" text="节假日（工作日休市）" /></span>
          <span style={{ color: '#bbb' }}>
            <CalendarOutlined /> 周末
          </span>
          <Typography.Text type="secondary" style={{ marginLeft: 'auto', fontSize: 12 }}>
            点击工作日可切换交易/休市状态
          </Typography.Text>
        </div>
        <Calendar cellRender={cellRender} />
      </Card>
    </>
  )
}
