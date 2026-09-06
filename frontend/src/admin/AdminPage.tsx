import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Ban,
  CheckCircle2,
  ChevronRight,
  Flag,
  LoaderCircle,
  Search,
  ShieldCheck,
  X,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, Navigate } from 'react-router'
import {
  ApiError,
  fetchFlaggedUsers,
  fetchReportedUser,
  moderateReportedUser,
  type ModerationRequest,
} from '../api/client'
import styles from '../App.module.css'
import { useAccount } from '../auth/account'

const REASON_LABELS = {
  ABUSIVE_LANGUAGE: '욕설 또는 비방',
  SPAM: '광고 또는 도배',
  OTHER: '기타',
} as const

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Seoul',
  }).format(new Date(value))
}

function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : '신고 내역을 불러오지 못했습니다.'
}

export function AdminPage() {
  const {
    data: account,
    isPending: accountPending,
    isFetching: accountFetching,
    isError: accountError,
  } = useAccount({ fresh: true })

  if (accountPending || accountFetching) {
    return <div className={styles.adminGate}><LoaderCircle className={styles.spinningIcon} aria-hidden="true" /> 권한을 확인하는 중...</div>
  }
  if (accountError) {
    return <div className={styles.adminGate} role="alert">권한을 확인하지 못했습니다. 다시 시도해 주세요.</div>
  }
  if (!account) return <Navigate to="/login?returnTo=%2Fadmin" replace />
  if (!account.account.admin) return <Navigate to="/404" replace />

  return <AdminConsole />
}

function AdminConsole() {
  const queryClient = useQueryClient()
  const [searchInput, setSearchInput] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [pendingAction, setPendingAction] = useState<ModerationRequest['action'] | null>(null)
  const [resultMessage, setResultMessage] = useState('')

  const flaggedUsers = useQuery({
    queryKey: ['admin-reports', searchQuery],
    queryFn: () => fetchFlaggedUsers(searchQuery),
  })
  const userReports = useQuery({
    queryKey: ['admin-reported-user', selectedUserId],
    queryFn: () => fetchReportedUser(selectedUserId ?? ''),
    enabled: Boolean(selectedUserId),
  })
  const moderation = useMutation({
    mutationFn: ({ userId, action }: { userId: string; action: ModerationRequest['action'] }) => (
      moderateReportedUser(userId, { action })
    ),
    onSuccess: (result) => {
      setResultMessage(result.activity === 'BAN' ? '이용 제한 조치를 완료했습니다.' : '검토 대상을 정상 상태로 변경했습니다.')
      setPendingAction(null)
      setSelectedUserId(null)
      void queryClient.invalidateQueries({ queryKey: ['admin-reports'] })
    },
  })

  if (flaggedUsers.error instanceof ApiError && flaggedUsers.error.code === 'NOT_FOUND') {
    return <Navigate to="/404" replace />
  }

  const selectedUser = userReports.data?.user

  return (
    <div className={styles.adminPage}>
      <header className={styles.adminHeader}>
        <div>
          <p className={styles.eyebrow}>ADMIN</p>
          <h1>신고 관리</h1>
          <p>검토 기준에 도달한 사용자의 한줄평 신고 내역입니다.</p>
        </div>
        <ShieldCheck aria-hidden="true" size={32} strokeWidth={1.6} />
      </header>

      <form className={styles.adminSearch} onSubmit={(event) => {
        event.preventDefault()
        setResultMessage('')
        setSelectedUserId(null)
        setSearchQuery(searchInput.trim())
      }}>
        <Search aria-hidden="true" size={18} />
        <input
          aria-label="닉네임 또는 이메일 검색"
          placeholder="닉네임 또는 이메일 검색"
          maxLength={100}
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
        />
        <button type="submit">검색</button>
      </form>

      {resultMessage ? <p className={styles.adminSuccess} role="status">{resultMessage}</p> : null}
      {flaggedUsers.isError ? <p className={styles.formError} role="alert">{errorMessage(flaggedUsers.error)}</p> : null}

      <div className={styles.adminWorkspace}>
        <aside className={styles.adminQueue} aria-label="검토 대상 사용자">
          <div className={styles.adminSectionHeading}>
            <h2>검토 대기</h2>
            <span>{flaggedUsers.data?.items.length ?? 0}명</span>
          </div>
          {flaggedUsers.isPending ? (
            <p className={styles.adminEmpty}>목록을 불러오는 중...</p>
          ) : flaggedUsers.data?.items.length ? (
            <div className={styles.adminUserList}>
              {flaggedUsers.data.items.map((user) => (
                <button
                  key={user.id}
                  type="button"
                  className={selectedUserId === user.id ? styles.adminUserSelected : undefined}
                  onClick={() => { setResultMessage(''); setSelectedUserId(user.id) }}
                >
                  <span>
                    <strong>{user.publicNickname}</strong>
                    <small>{user.email}</small>
                  </span>
                  <span className={styles.adminReportCount}>{user.pendingReportCount}건</span>
                  <ChevronRight aria-hidden="true" size={17} />
                </button>
              ))}
            </div>
          ) : (
            <p className={styles.adminEmpty}>{searchQuery ? '검색된 검토 대상이 없습니다.' : '현재 검토할 사용자가 없습니다.'}</p>
          )}
        </aside>

        <section className={styles.adminDetail} aria-live="polite">
          {!selectedUserId ? (
            <div className={styles.adminDetailEmpty}>
              <Flag aria-hidden="true" size={28} strokeWidth={1.5} />
              <strong>검토할 사용자를 선택해 주세요.</strong>
            </div>
          ) : userReports.isPending ? (
            <div className={styles.adminDetailEmpty}>
              <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={24} />
              <span>신고 내역을 불러오는 중...</span>
            </div>
          ) : userReports.isError ? (
            <div className={styles.adminDetailEmpty} role="alert">
              <strong>신고 내역을 표시할 수 없습니다.</strong>
              <span>{errorMessage(userReports.error)}</span>
              <button type="button" onClick={() => userReports.refetch()}>다시 시도</button>
            </div>
          ) : selectedUser ? (
            <>
              <header className={styles.adminUserHeader}>
                <div>
                  <span>FLAGGED USER</span>
                  <h2>{selectedUser.publicNickname}</h2>
                  <p>{selectedUser.email}</p>
                </div>
                <strong>{selectedUser.pendingReportCount}건 검토 필요</strong>
              </header>

              <div className={styles.adminReportList}>
                {userReports.data.reports.map((report) => (
                  <article key={report.id} className={styles.adminReportItem}>
                    <header>
                      <span>{REASON_LABELS[report.reasonCode]}</span>
                      <time dateTime={report.createdAt}>{formatDateTime(report.createdAt)}</time>
                    </header>
                    <NavLink to={`/tracks/${report.recommendation.trackId}`}>
                      <strong>{report.recommendation.trackTitle}</strong>
                      <span>{report.recommendation.artistName}</span>
                    </NavLink>
                    <blockquote>“{report.recommendation.comment}”</blockquote>
                    {report.details ? <p>{report.details}</p> : null}
                    <footer>
                      신고자 {report.reporter.publicNickname}
                      <span>{report.status}</span>
                    </footer>
                  </article>
                ))}
              </div>

              <div className={styles.adminModerationActions}>
                <button type="button" onClick={() => setPendingAction('DISMISS')}>
                  <CheckCircle2 aria-hidden="true" size={17} /> 문제 없음
                </button>
                <button className={styles.adminBanButton} type="button" onClick={() => setPendingAction('BAN')}>
                  <Ban aria-hidden="true" size={17} /> 이용 제한
                </button>
              </div>
            </>
          ) : null}
        </section>
      </div>

      {pendingAction && selectedUser ? (
        <div className={styles.dialogBackdrop} role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget && !moderation.isPending) setPendingAction(null)
        }}>
          <section className={styles.adminConfirmDialog} role="dialog" aria-modal="true" aria-labelledby="moderation-title">
            <button
              className={styles.dialogCloseButton}
              type="button"
              aria-label="닫기"
              disabled={moderation.isPending}
              onClick={() => setPendingAction(null)}
            >
              <X aria-hidden="true" size={19} />
            </button>
            {pendingAction === 'BAN' ? <Ban className={styles.dialogIcon} aria-hidden="true" /> : <CheckCircle2 className={styles.dialogIcon} aria-hidden="true" />}
            <h2 id="moderation-title">{pendingAction === 'BAN' ? '이용을 제한할까요?' : '문제없는 신고인가요?'}</h2>
            <p>
              {pendingAction === 'BAN'
                ? `${selectedUser.publicNickname}님의 로그인을 차단하고 모든 한줄평을 숨깁니다.`
                : `${selectedUser.publicNickname}님을 정상 상태로 되돌리고 대기 중인 신고를 종결합니다.`}
            </p>
            {moderation.isError ? <p className={styles.formError} role="alert">{errorMessage(moderation.error)}</p> : null}
            <div className={styles.dialogActions}>
              <button type="button" onClick={() => setPendingAction(null)} disabled={moderation.isPending}>취소</button>
              <button
                className={pendingAction === 'BAN' ? styles.dangerButton : styles.confirmButton}
                type="button"
                disabled={moderation.isPending}
                onClick={() => moderation.mutate({ userId: selectedUser.id, action: pendingAction })}
              >
                {moderation.isPending ? '처리 중...' : pendingAction === 'BAN' ? '이용 제한' : '정상 처리'}
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  )
}
