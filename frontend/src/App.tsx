import {
  BarChart3,
  CircleUserRound,
  Home,
  House,
  LogIn,
  Sparkles,
} from 'lucide-react'
import { NavLink, Route, Routes, useLocation } from 'react-router'
import styles from './App.module.css'
import { AdminPage } from './admin/AdminPage'
import { AccountPage, AccountRecoveryPage, JoinPage, LoginPage } from './auth/AuthPages'
import { useAccount } from './auth/account'
import { ChartPage } from './catalog/ChartPage'
import { RecommendPage } from './catalog/RecommendPage'
import { HomePage, RecentPage } from './home/HomePages'
import { TrackDetailPage } from './track/TrackDetailPage'

function AccountArea() {
  const location = useLocation()
  const { data: account } = useAccount()

  return (
    <div className={styles.accountArea}>
      {account ? (
        <NavLink className={styles.loginButton} to="/me">
          <CircleUserRound aria-hidden="true" size={17} />
          <span>{account.account.publicNickname}</span>
        </NavLink>
      ) : (
        <NavLink className={styles.loginButton} to={`/login?returnTo=${encodeURIComponent(location.pathname)}`}>
          <LogIn aria-hidden="true" size={17} />
          <span>로그인</span>
        </NavLink>
      )}
    </div>
  )
}

function Navigation() {
  const links = [
    { to: '/', label: '홈', icon: Home, end: true },
    { to: '/chart', label: '차트', icon: BarChart3 },
    { to: '/recommend', label: '추천하기', icon: Sparkles },
  ]

  return (
    <nav className={styles.navigation} aria-label="주요 메뉴">
      {links.map(({ to, label, icon: Icon, end }) => (
        <NavLink key={to} to={to} end={end} className={({ isActive }) => isActive ? styles.activeNav : undefined}>
          <Icon aria-hidden="true" size={18} />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

function NotFoundPage() {
  return (
    <section className={styles.notFoundPage}>
      <div className={styles.notFoundContent}>
        <img className={styles.notFoundLogo} src="/trackpick-logo.png" alt="" aria-hidden="true" />
        <p className={styles.notFoundCode}>404</p>
        <h1>유효하지 않은 요청입니다.</h1>
        <p>주소가 잘못되었거나 페이지가 이동되었을 수 있습니다.</p>
        <NavLink className={styles.notFoundHomeLink} to="/">
          <House aria-hidden="true" size={17} />
          홈으로 돌아가기
        </NavLink>
      </div>
    </section>
  )
}

function App() {
  return (
    <div className={styles.app}>
      <header className={styles.topBar}>
        <NavLink className={styles.brand} to="/" aria-label="TrackPick 홈">
          <img className={styles.brandMark} src="/trackpick-logo.png" alt="" aria-hidden="true" />
          <span>TrackPick</span>
        </NavLink>
        <Navigation />
        <AccountArea />
      </header>

      <main className={styles.main}>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/chart" element={<ChartPage />} />
          <Route path="/recent" element={<RecentPage />} />
          <Route path="/tracks/:trackId" element={<TrackDetailPage />} />
          <Route path="/recommend" element={<RecommendPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/join" element={<JoinPage />} />
          <Route path="/recover/password" element={<AccountRecoveryPage />} />
          <Route path="/me" element={<AccountPage />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>

      <footer className={styles.footer}>TrackPick · 매일 00:00 KST 차트 갱신</footer>
      <div className={styles.mobileNav}><Navigation /></div>
    </div>
  )
}

export default App
