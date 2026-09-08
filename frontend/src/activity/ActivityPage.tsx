import { useInfiniteQuery } from '@tanstack/react-query'
import { Disc3, LoaderCircle, ThumbsUp } from 'lucide-react'
import { useState } from 'react'
import { Navigate, NavLink, useLocation } from 'react-router'
import { ApiError, fetchMyActivity } from '../api/client'
import styles from '../App.module.css'
import { useAccount } from '../auth/account'

function activityErrorMessage(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : '활동 기록을 불러오지 못했습니다. 다시 시도해 주세요.'
}

function formatDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'long' }).format(new Date(year, month - 1, day))
}

function ActivityArtwork({ src }: { src: string | null }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return <span className={styles.activityArtworkFallback}><Disc3 aria-hidden="true" size={24} /></span>
  }
  return <img className={styles.activityArtwork} src={src} alt="" onError={() => setFailed(true)} />
}

export function ActivityPage() {
  const location = useLocation()
  const { data: account, isPending: accountPending } = useAccount()
  const activity = useInfiniteQuery({
    queryKey: ['my-activity'],
    queryFn: ({ pageParam }) => fetchMyActivity(pageParam ?? undefined),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.page.nextCursor ?? undefined,
    enabled: Boolean(account),
    staleTime: 15_000,
  })

  if (accountPending) {
    return <div className={styles.activityMessage}>계정 정보를 확인하는 중...</div>
  }
  if (!account) {
    return <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname)}`} replace />
  }

  const firstPage = activity.data?.pages[0]
  const items = activity.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <div className={styles.activityPage}>
      <header className={styles.activityHeader}>
        <p className={styles.eyebrow}>MY ACTIVITY</p>
        <h1>나의 활동</h1>
        <p>{account.account.publicNickname}</p>
      </header>

      {activity.isPending ? (
        <div className={styles.activityMessage} aria-busy="true">
          <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={22} />
          활동 기록을 불러오는 중...
        </div>
      ) : activity.isError ? (
        <div className={styles.activityMessage} role="alert">
          <strong>활동 기록을 표시할 수 없습니다.</strong>
          <span>{activityErrorMessage(activity.error)}</span>
          <button type="button" onClick={() => activity.refetch()}>다시 시도</button>
        </div>
      ) : firstPage ? (
        <>
          <section className={styles.activitySummary} aria-label="활동 요약">
            <div><span>추천 횟수</span><strong>{firstPage.summary.recommendationCount}</strong></div>
            <div><span>최초 등록</span><strong>{firstPage.summary.firstPickCount}</strong></div>
            <div><span>받은 추천</span><strong>{firstPage.summary.receivedVoteCount}</strong></div>
          </section>

          {firstPage.summary.highestVoted ? (
            <section className={styles.activityHighlight} aria-labelledby="activity-highlight-heading">
              <span id="activity-highlight-heading">가장 많은 추천을 받은 곡</span>
              <NavLink className={styles.activityTrackLink} to={`/tracks/${firstPage.summary.highestVoted.trackId}`}>
                <ActivityArtwork src={firstPage.summary.highestVoted.albumCoverUrl} />
                <span className={styles.activityTrackIdentity}>
                  <span>
                    <strong>{firstPage.summary.highestVoted.title}</strong>
                  </span>
                  <small>{firstPage.summary.highestVoted.artistName}</small>
                  <time dateTime={firstPage.summary.highestVoted.recommendedOn}>
                    {formatDate(firstPage.summary.highestVoted.recommendedOn)}
                  </time>
                </span>
                <span className={styles.activityVoteCount}>
                  <ThumbsUp aria-hidden="true" size={14} /> {firstPage.summary.highestVoted.voteCount}표
                </span>
              </NavLink>
            </section>
          ) : null}

          <section className={styles.activityLog} aria-labelledby="activity-log-heading">
            <div className={styles.activityLogHeading}>
              <div>
                <p className={styles.sectionLabel}>PICK LOG</p>
                <h2 id="activity-log-heading">추천한 곡</h2>
              </div>
              <span>최신순</span>
            </div>
            {items.length === 0 ? (
              <div className={styles.activityEmpty}>
                <Disc3 aria-hidden="true" size={28} />
                <strong>아직 추천한 곡이 없어요.</strong>
                <NavLink to="/recommend">첫 곡 추천하기</NavLink>
              </div>
            ) : (
              <ul className={styles.activityList}>
                {items.map((item) => (
                  <li key={item.recommendationId}>
                    <NavLink className={styles.activityTrackLink} to={`/tracks/${item.trackId}`}>
                      <ActivityArtwork src={item.albumCoverUrl} />
                      <span className={styles.activityTrackIdentity}>
                        <span>
                          <strong>{item.title}</strong>
                          {item.firstPick ? <small className={styles.firstPickBadge}>FIRST PICK</small> : null}
                        </span>
                        <small>{item.artistName}</small>
                        <time dateTime={item.recommendedOn}>{formatDate(item.recommendedOn)}</time>
                      </span>
                      <span className={styles.activityVoteCount}><ThumbsUp aria-hidden="true" size={14} /> {item.voteCount}표</span>
                    </NavLink>
                    {item.comment ? <blockquote>“{item.comment}”</blockquote> : <p>현재 볼 수 없는 한줄평입니다.</p>}
                  </li>
                ))}
              </ul>
            )}
            {activity.hasNextPage ? (
              <button
                className={styles.loadMoreButton}
                type="button"
                disabled={activity.isFetchingNextPage}
                onClick={() => activity.fetchNextPage()}
              >
                {activity.isFetchingNextPage ? '불러오는 중...' : '더보기'}
              </button>
            ) : null}
          </section>
        </>
      ) : null}
    </div>
  )
}
