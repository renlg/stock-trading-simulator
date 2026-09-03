import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { BrowserRouter } from 'react-router-dom'
import MainLayout from './components/MainLayout'
import LoginPage from './pages/LoginPage'
import QuotesPage from './pages/QuotesPage'
import TradePage from './pages/TradePage'
import PositionsPage from './pages/PositionsPage'
import ConditionsPage from './pages/ConditionsPage'
import AccountPage from './pages/AccountPage'
import OrdersPage from './pages/OrdersPage'
import StockDetailPage from './pages/StockDetailPage'
import BacktestPage from './pages/BacktestPage'
import CalendarPage from './pages/CalendarPage'
import AccountManagePage from './pages/AccountManagePage'

function RequireAuth() {
  return localStorage.getItem('accessToken') ? <MainLayout /> : <Navigate to="/login" replace />
}

function RequireAdmin({ children }: { children: ReactNode }) {
  return localStorage.getItem('role') === 'admin' ? <>{children}</> : <Navigate to="/quotes" replace />
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth />}>
          <Route path="/quotes" element={<QuotesPage />} />
          <Route path="/stock/:code" element={<StockDetailPage />} />
          <Route path="/trade" element={<TradePage />} />
          <Route path="/positions" element={<PositionsPage />} />
          <Route path="/conditions" element={<ConditionsPage />} />
          <Route path="/account" element={<AccountPage />} />
          <Route path="/orders" element={<OrdersPage />} />
          <Route path="/backtest" element={<BacktestPage />} />
          <Route path="/calendar" element={<RequireAdmin><CalendarPage /></RequireAdmin>} />
          <Route path="/admin/users" element={<AccountManagePage />} />
        </Route>
        <Route path="*" element={<Navigate to={localStorage.getItem('accessToken') ? '/quotes' : '/login'} replace />} />
      </Routes>
    </BrowserRouter>
  )
}
