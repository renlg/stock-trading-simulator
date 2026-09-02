import { Card, Statistic, message } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import api from '../api'
import PageHeader from '../components/PageHeader'
import PositionTable from '../components/PositionTable'
import type { ApiResponse, Position } from '../types'
import { getData } from '../utils'

export default function PositionsPage() {
  const [positions, setPositions] = useState<Position[]>([])
  const [loading, setLoading] = useState(true)
  const total = useMemo(() => positions.reduce((sum, item) => sum + Number(item.marketValue || 0), 0), [positions])
  useEffect(() => {
    const load = async () => {
      try { const { data } = await api.get<ApiResponse<Position[]>>('/portfolio'); const result = getData(data); if (result) setPositions(result) }
      catch { message.error('持仓加载失败') } finally { setLoading(false) }
    }
    void load()
  }, [])
  return <><PageHeader title="我的持仓" description="查看当前持仓市值与浮动盈亏" /><Card bordered={false}><PositionTable data={positions} loading={loading} /><div className="table-summary"><Statistic title="总市值" value={total} precision={2} prefix="¥" /></div></Card></>
}
