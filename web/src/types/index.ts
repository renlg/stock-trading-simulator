export interface ApiResponse<T> { code: number; message: string; data: T }

export interface Quote {
  code: string; name: string; price: number; prevClose: number; change: number;
  changePct: number; high: number; low: number; updatedAt: string
}

export interface Position {
  code: string; name: string; quantity: number; avgCost: number; price: number;
  marketValue: number; profit: number; profitPct: number
}

export interface ConditionOrder {
  id: number | string; code: string; type: 'buy' | 'sell'; triggerPrice: number;
  quantity: number; status: string; createdAt: string
}

export interface Order {
  id: number | string; code: string; side: 'buy' | 'sell'; price: number;
  quantity: number; amount: number; status: string; source: string; createdAt: string
}

export interface Account {
  balance: number; marketValue: number; totalAssets: number; totalProfit: number; positions: Position[]
}

export interface ApiKeyItem {
  id: number | string; name: string; apiKeyMask: string; createdAt: string; lastUsedAt?: string | null
}
