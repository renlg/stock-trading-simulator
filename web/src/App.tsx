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
import ApiKeyPage from './pages/ApiKeyPage'
import StockDetailPage from './pages/StockDetailPage'

function RequireAuth() {
  return localStorage.getItem('token') ? <MainLayout /> : <Navigate to="/login" replace />
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
          <Route path="/apikey" element={<ApiKeyPage />} />
        </Route>
        <Route path="*" element={<Navigate to={localStorage.getItem('token') ? '/quotes' : '/login'} replace />} />
      </Routes>
    </BrowserRouter>
  )
}
