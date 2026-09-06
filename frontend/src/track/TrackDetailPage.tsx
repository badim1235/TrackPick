import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  Check,
  Disc3,
  ExternalLink,
  Flag,
  LoaderCircle,
  LogIn,
  ThumbsUp,
  X,
} from 'lucide-react'
import { useState } from 'react'
import { NavLink, useParams } from 'react-router'
import styles from '../App.module.css'
import {
  ApiError,
  createReport,
  createVote,
  fetchTrackDetail,
  type AccountResponse,
  type CreateReportRequest,
} from '../api/client'
import { accountQueryKey, useAccount } from '../auth/account'
import { detailArtworkUrl } from './artwork'

function detailErrorMessage(error: unknown) {
  if (error instanceof ApiError && error.code === 'TRACK_NOT_FOUND') {
    return '등록된 곡을 찾을 수 없습니다.'
  }
  return error instanceof ApiError
    ? error.message
    : '곡 정보를 불러오지 못했습니다. 다시 시도해 주세요.'
}

function formatRegisteredAt(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Seoul',
  }).format(new Date(value))
}

function recommendationAvailableMessage(value: string) {
  const [, month, day] = value.split('-').map(Number)
  return `${month}월 ${day}일부터 다시 추천할 수 있어요.`
}

function DetailArtwork({ src }: { src: string | null }) {
  const [failed, setFailed] = useState(false)
  if (!src || failed) {
    return <div className={styles.detailArtworkFallback}><Disc3 aria-hidden="true" size={44} /></div>
  }
  return <img className={styles.detailArtwork} src={detailArtworkUrl(src)} alt="" onError={() => setFailed(true)} />
}

export function TrackDetailPage() {
  const { trackId = '' } = useParams()
  const queryClient = useQueryClient()
  const { data: account } = useAccount()
  const [voteError, setVoteError] = useState<string | null>(null)
  const [reportOpen, setReportOpen] = useState(false)
  const [reportReason, setReportReason] = useState<CreateReportRequest['reasonCode']>('ABUSIVE_LANGUAGE')
  const [reportDetails, setReportDetails] = useState('')
  const [reportError, setReportError] = useState<string | null>(null)
  const detail = useQuery({
    queryKey: ['track-detail', trackId],
    queryFn: () => fetchTrackDetail(trackId),
    enabled: Boolean(trackId),
    staleTime: 15_000,
  })
  const vote = useMutation({
    mutationFn: () => createVote(trackId),
    onSuccess: (data) => {
      setVoteError(null)
      queryClient.setQueryData<AccountResponse>(accountQueryKey, (current) => current
        ? { ...current, quota: data.quota }
        : current)
      void queryClient.invalidateQueries({ queryKey: ['track-detail', trackId] })
      void queryClient.invalidateQueries({ queryKey: ['daily-chart'] })
      void queryClient.invalidateQueries({ queryKey: ['home'] })
      void queryClient.invalidateQueries({ queryKey: ['recent-tracks'] })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.details.quota) {
        const quota = error.details.quota as AccountResponse['quota']
        queryClient.setQueryData<AccountResponse>(accountQueryKey, (current) => current
          ? { ...current, quota }
          : current)
      }
      if (error instanceof ApiError && error.code === 'ALREADY_VOTED') {
        setVoteError(null)
        void queryClient.invalidateQueries({ queryKey: ['track-detail', trackId] })
        return
      }
      setVoteError(detailErrorMessage(error))
    },
  })
  const report = useMutation({
    mutationFn: ({ recommendationId, body }: {
      recommendationId: string
      body: CreateReportRequest
    }) => createReport(recommendationId, body),
    onSuccess: () => {
      setReportError(null)
      setReportOpen(false)
      setReportReason('ABUSIVE_LANGUAGE')
      setReportDetails('')
      void queryClient.invalidateQueries({ queryKey: ['track-detail', trackId] })
    },
    onError: (error) => {
      setReportError(error instanceof ApiError ? error.message : '신고를 접수하지 못했습니다.')
      if (error instanceof ApiError && error.code === 'ALREADY_REPORTED') {
        void queryClient.invalidateQueries({ queryKey: ['track-detail', trackId] })
      }
    },
  })

  if (detail.isPending) {
    return (
      <div className={styles.detailMessage} aria-busy="true">
        <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={24} />
        <span>곡 정보를 불러오는 중...</span>
      </div>
    )
  }

  if (detail.isError) {
    return (
      <div className={styles.detailMessage} role="alert">
        <Disc3 aria-hidden="true" size={30} strokeWidth={1.5} />
        <strong>곡을 표시할 수 없습니다.</strong>
        <span>{detailErrorMessage(detail.error)}</span>
        <button type="button" onClick={() => detail.refetch()}>다시 시도</button>
      </div>
    )
  }

  const { track, today, actions } = detail.data
  const provider = track.providerReferences[0]
  const hasVoted = track.viewer?.hasVotedToday ?? actions.reason === 'ALREADY_VOTED'
  const waiting = actions.reason === 'RECOMMENDATION_COOLDOWN'
  const recommendQuery = new URLSearchParams({
    query: `${track.title} ${track.artistName}`,
  }).toString()
  const metadata = [track.albumName, track.releaseYear, track.providerGenreName]
    .filter((value): value is string | number => value !== null)

  return (
    <article className={styles.trackDetailPage}>
      <NavLink className={styles.detailBackLink} to="/chart">
        <ArrowLeft aria-hidden="true" size={16} /> 차트로 돌아가기
      </NavLink>

      <header className={styles.detailHero}>
        <DetailArtwork src={track.albumCoverUrl} />
        <div className={styles.detailIdentity}>
          <p className={styles.eyebrow}>TRACK DETAIL</p>
          <div className={styles.detailTitleLine}>
            <h1>{track.title}</h1>
            {track.explicit ? <span className={styles.explicitBadge}>Explicit</span> : null}
          </div>
          <p className={styles.detailArtist}>{track.artistName}</p>
          {metadata.length ? <p className={styles.detailMetadata}>{metadata.join(' · ')}</p> : null}
          <span className={styles.detailGenre}>{track.primaryGenre.displayName}</span>

          {track.preview.url ? (
            <audio
              className={styles.detailPreview}
              controls
              controlsList="nodownload noplaybackrate noremoteplayback"
              preload="none"
              src={track.preview.url}
              aria-label={`${track.title} 30초 미리듣기`}
            />
          ) : <p className={styles.detailPreviewUnavailable}>미리듣기를 제공하지 않는 곡입니다.</p>}

          {provider?.externalUrl ? (
            <a className={styles.detailExternalLink} href={provider.externalUrl} target="_blank" rel="noreferrer">
              Apple Music에서 듣기 <ExternalLink aria-hidden="true" size={14} />
            </a>
          ) : null}
        </div>
      </header>

      <section className={styles.detailStats} aria-label="오늘의 추천 현황">
        <div><span>오늘 추천</span><strong>{today.voteCount}표</strong></div>
        <div><span>전체 순위</span><strong>{today.overallRank ? `${today.overallRank}위` : '-'}</strong></div>
        <div><span>{track.primaryGenre.displayName} 순위</span><strong>{today.genreRank ? `${today.genreRank}위` : '-'}</strong></div>
      </section>

      <section className={styles.detailRecommendation} aria-labelledby="detail-comment-heading">
        <p className={styles.sectionLabel}>FIRST PICK</p>
        <h2 id="detail-comment-heading">처음 이 곡을 추천한 한줄평</h2>
        {track.recommendation.commentAvailable && track.recommendation.comment ? (
          <blockquote>“{track.recommendation.comment}”</blockquote>
        ) : <p className={styles.detailHiddenComment}>현재 볼 수 없는 한줄평입니다.</p>}
        <div className={styles.detailRecommender}>
          <div>
            {track.recommendation.recommenderNickname ? <strong>{track.recommendation.recommenderNickname}</strong> : null}
            <time dateTime={track.recommendation.createdAt}>{formatRegisteredAt(track.recommendation.createdAt)} 등록</time>
          </div>
          {actions.canReport ? (
            <button className={styles.reportButton} type="button" onClick={() => { setReportError(null); setReportOpen(true) }}>
              <Flag aria-hidden="true" size={14} /> 신고
            </button>
          ) : actions.hasReported ? (
            <span className={styles.reportComplete}><Check aria-hidden="true" size={14} /> 신고 완료</span>
          ) : null}
        </div>
      </section>

      {reportOpen ? (
        <div className={styles.dialogBackdrop} role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget && !report.isPending) setReportOpen(false)
        }}>
          <section className={styles.reportDialog} role="dialog" aria-modal="true" aria-labelledby="report-dialog-title">
            <button
              className={styles.dialogCloseButton}
              type="button"
              aria-label="닫기"
              disabled={report.isPending}
              onClick={() => setReportOpen(false)}
            >
              <X aria-hidden="true" size={19} />
            </button>
            <Flag className={styles.dialogIcon} aria-hidden="true" size={22} />
            <h2 id="report-dialog-title">한줄평 신고</h2>
            <p>신고 사유를 확인한 뒤 관리자가 조치합니다.</p>
            <form onSubmit={(event) => {
              event.preventDefault()
              setReportError(null)
              report.mutate({
                recommendationId: track.recommendation.id,
                body: { reasonCode: reportReason, details: reportDetails.trim() || null },
              })
            }}>
              <label className={styles.field}>
                <span>신고 사유</span>
                <select
                  value={reportReason}
                  onChange={(event) => setReportReason(event.target.value as CreateReportRequest['reasonCode'])}
                >
                  <option value="ABUSIVE_LANGUAGE">욕설 또는 비방</option>
                  <option value="SPAM">광고 또는 도배</option>
                  <option value="OTHER">기타</option>
                </select>
              </label>
              <label className={styles.field}>
                <span>추가 설명 <small>선택</small></span>
                <textarea
                  value={reportDetails}
                  maxLength={500}
                  placeholder="확인이 필요한 내용을 남겨주세요."
                  onChange={(event) => setReportDetails(event.target.value)}
                />
                <span className={styles.characterCount}>{reportDetails.length}/500</span>
              </label>
              {reportError ? <p className={styles.formError} role="alert">{reportError}</p> : null}
              <div className={styles.dialogActions}>
                <button type="button" onClick={() => setReportOpen(false)} disabled={report.isPending}>취소</button>
                <button className={styles.dangerButton} type="submit" disabled={report.isPending}>
                  {report.isPending ? '접수 중...' : '신고하기'}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}

      <section className={styles.detailVoteBand} aria-label="곡 추천하기">
        <div>
          <strong>{waiting ? '최근 추천된 곡이에요.' : '이 곡이 마음에 드시나요?'}</strong>
          <span>
            {waiting
              ? recommendationAvailableMessage(actions.recommendationAvailableOn)
              : '추천하면 오늘의 추천권 1회를 사용합니다.'}
          </span>
        </div>
        {waiting ? (
          <button className={styles.waitingButton} type="button" disabled>추천 대기</button>
        ) : !account ? (
          <NavLink to={`/login?returnTo=${encodeURIComponent(`/tracks/${trackId}`)}`}>
            <LogIn aria-hidden="true" size={16} /> 로그인하고 추천
          </NavLink>
        ) : actions.canRecommend ? (
          <NavLink to={`/recommend?${recommendQuery}`}>
            <ThumbsUp aria-hidden="true" size={16} /> 다시 추천
          </NavLink>
        ) : (
          <button
            className={hasVoted ? styles.votedButton : undefined}
            type="button"
            disabled={!actions.canVote || vote.isPending}
            onClick={() => vote.mutate()}
          >
            {vote.isPending ? <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={16} />
              : hasVoted ? <Check aria-hidden="true" size={16} />
                : <ThumbsUp aria-hidden="true" size={16} />}
            {hasVoted ? '추천 완료' : actions.reason === 'DAILY_LIMIT_EXCEEDED' ? '추천권 없음' : '추천'}
          </button>
        )}
      </section>
      {voteError ? <p className={styles.detailVoteError} role="alert">{voteError}</p> : null}
    </article>
  )
}
