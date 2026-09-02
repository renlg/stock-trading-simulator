import { message } from 'antd'
import type { ApiResponse } from '../types'

export function getData<T>(response: ApiResponse<T>): T | undefined {
  if (response.code !== 0) {
    message.error(response.message || '操作失败')
    return undefined
  }
  return response.data
}

export const money = (value?: number) => `¥${Number(value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export const bigMoney = (value?: number): string => {
  const v = Number(value ?? 0)
  const abs = Math.abs(v)
  if (abs >= 1e8) return `${(v / 1e8).toFixed(2)}亿`
  if (abs >= 1e4) return `${(v / 1e4).toFixed(2)}万`
  return v.toFixed(2)
}

export const number = (value?: number) => Number(value ?? 0).toLocaleString('zh-CN')
export const time = (value?: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—'
export const riseColor = (value: number) => value > 0 ? '#f5222d' : value < 0 ? '#3f8600' : '#595959'
export const signedPct = (value?: number) => `${Number(value ?? 0) > 0 ? '+' : ''}${Number(value ?? 0).toFixed(2)}%`
