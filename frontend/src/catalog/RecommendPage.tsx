import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Check,
  CircleCheck,
  CircleUserRound,
  Clock3,
  Disc3,
  ExternalLink,
  Headphones,
  LoaderCircle,
  LogIn,
  RotateCcw,
  Search,
  Send,
  ThumbsUp,
} from 'lucide-react'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { NavLink, useSearchParams } from 'react-router'
import styles from '../App.module.css'
import {
  ApiError,
  createRecommendation,
  createVote,
  type AccountResponse,
  type MusicSearchItem,
  searchMusic,
} from '../api/client'
import { accountQueryKey, useAccount } from '../auth/account'

function TrackArtwork({ track }: { track: MusicSearchItem }) {
  const [failed, setFailed] = useState(false)

  if (!track.albumCoverUrl || failed) {
    return <div className={styles.artworkFallback}><Disc3 aria-hidden="true" size={28} /></div>
  }
  return (
    <img
      className={styles.trackArtwork}
      src={track.albumCoverUrl}
      alt=""
      loading="lazy"
      onError={() => setFailed(true)}
    />
  )
}

function recommendationError(error: unknown) {
  if (!(error instanceof ApiError)) return '추천을 등록하지 못했습니다. 다시 시도해 주세요.'
  return error.message
}

function voteErrorMessage(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : '곡을 추천하지 못했습니다. 다시 시도해 주세요.'
}

function recommendationAvailableMessage(value: string | null) {
  if (!value) return '잠시 후 다시 추천할 수 있어요.'
  const [, month, day] = value.split('-').map(Number)
  return `${month}월 ${day}일부터 다시 추천할 수 있어요.`
}

export function RecommendPage() {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const { data: account, isLoading: isAccountLoading } = useAccount()
  const [query, setQuery] = useState(() => searchParams.get('query')?.trim() ?? '')
  const [queryError, setQueryError] = useState<string | null>(null)
  const [searchAuthError, setSearchAuthError] = useState<string | null>(null)
  const [selectedTrack, setSelectedTrack] = useState<MusicSearchItem | null>(null)
  const [comment, setComment] = useState('')
  const [composeError, setComposeError] = useState<string | null>(null)
  const [voteError, setVoteError] = useState<string | null>(null)
  const [votedTrackIds, setVotedTrackIds] = useState<Set<string>>(() => new Set())
  const [cooldownTrackId, setCooldownTrackId] = useState<string | null>(null)
  const [visibleResultCount, setVisibleResultCount] = useState(10)
  const composeSectionRef = useRef<HTMLElement>(null)
  const shouldScrollToCompose = useRef(false)
  const search = useMutation({ mutationFn: searchMusic })
  const create = useMutation({
    mutationFn: createRecommendation,
    onSuccess: async () => {
      setComposeError(null)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['account'] }),
        queryClient.invalidateQueries({ queryKey: ['daily-chart'] }),
        queryClient.invalidateQueries({ queryKey: ['home'] }),
        queryClient.invalidateQueries({ queryKey: ['recent-tracks'] }),
      ])
    },
  })
  const vote = useMutation({
    mutationFn: createVote,
    onSuccess: (data, trackId) => {
      setVoteError(null)
      setVotedTrackIds((current) => new Set(current).add(trackId))
      queryClient.setQueryData<AccountResponse>(accountQueryKey, (current) => current
        ? { ...current, quota: data.quota }
        : current)
      void queryClient.invalidateQueries({ queryKey: ['daily-chart'] })
      void queryClient.invalidateQueries({ queryKey: ['home'] })
      void queryClient.invalidateQueries({ queryKey: ['recent-tracks'] })
    },
    onError: (error, trackId) => {
      if (error instanceof ApiError && error.details.quota) {
        const quota = error.details.quota as AccountResponse['quota']
        queryClient.setQueryData<AccountResponse>(accountQueryKey, (current) => current
          ? { ...current, quota }
          : current)
      }
      if (error instanceof ApiError && error.code === 'ALREADY_VOTED') {
        setVoteError(null)
        setVotedTrackIds((current) => new Set(current).add(trackId))
        void queryClient.invalidateQueries({ queryKey: accountQueryKey })
        void queryClient.invalidateQueries({ queryKey: ['daily-chart'] })
        void queryClient.invalidateQueries({ queryKey: ['home'] })
        void queryClient.invalidateQueries({ queryKey: ['recent-tracks'] })
        return
      }
      setVoteError(voteErrorMessage(error))
    },
  })

  useEffect(() => {
    if (!selectedTrack || !shouldScrollToCompose.current) return
    shouldScrollToCompose.current = false
    composeSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [selectedTrack])

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!account) {
      setQueryError(null)
      setSearchAuthError('로그인이 필요합니다.')
      return
    }
    const normalized = query.trim().replace(/\s+/g, ' ')
    if (Array.from(normalized).length < 1 || Array.from(normalized).length > 100) {
      setQueryError('검색어는 1~100자로 입력해 주세요.')
      return
    }
    setQueryError(null)
    setSearchAuthError(null)
    shouldScrollToCompose.current = false
    setSelectedTrack(null)
    setVisibleResultCount(10)
    setVoteError(null)
    setCooldownTrackId(null)
    create.reset()
    vote.reset()
    search.mutate(normalized)
  }

  function selectTrack(track: MusicSearchItem) {
    if (track.existingTrack.action !== 'SELECT') return
    const deselecting = selectedTrack?.externalTrackId === track.externalTrackId
    shouldScrollToCompose.current = !deselecting
    setSelectedTrack(deselecting ? null : track)
    setComposeError(null)
    create.reset()
    vote.reset()
    setVoteError(null)
  }

  function submitRecommendation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalizedComment = comment.trim()
    if (!selectedTrack) {
      setComposeError('추천할 곡을 선택해 주세요.')
      return
    }
    if (Array.from(normalizedComment).length < 1 || Array.from(normalizedComment).length > 120) {
      setComposeError('한줄평은 1~120자로 입력해 주세요.')
      return
    }
    setComposeError(null)
    create.mutate({
      provider: selectedTrack.provider,
      externalTrackId: selectedTrack.externalTrackId,
      comment: normalizedComment,
    })
  }

  function resetRecommendation() {
    setQuery('')
    shouldScrollToCompose.current = false
    setSelectedTrack(null)
    setComment('')
    search.reset()
    create.reset()
    setVisibleResultCount(10)
  }

  const requestError = search.error instanceof ApiError
    ? search.error.message
    : search.isError
      ? '음악 검색 서비스에 잠시 연결할 수 없습니다.'
      : null
  const quotaEmpty = account?.quota.remaining === 0

  if (create.data) {
    return (
      <div className={styles.recommendPage}>
        <section className={styles.recommendSuccess} aria-labelledby="recommend-success-heading">
          <CircleCheck aria-hidden="true" size={38} />
          <p className={styles.eyebrow}>TRACK PICKED</p>
          <h1 id="recommend-success-heading">곡을 추천했어요.</h1>
          <div className={styles.successTrack}>
            {create.data.track.albumCoverUrl ? <img src={create.data.track.albumCoverUrl} alt="" /> : null}
            <div>
              <strong>{create.data.track.title}</strong>
              <span>{create.data.track.artistName}</span>
              <small>{create.data.recommendation.primaryGenre.displayName}</small>
            </div>
          </div>
          <blockquote>{create.data.recommendation.comment}</blockquote>
          <p className={styles.successQuota}>
            오늘의 추천 {create.data.quota.used}/{create.data.quota.limit} · {create.data.quota.remaining}회 남음
          </p>
          <div className={styles.successActions}>
            <NavLink to="/">홈으로</NavLink>
            <NavLink to={`/tracks/${create.data.track.id}`}>곡 상세</NavLink>
            {create.data.quota.remaining > 0 ? (
              <button type="button" onClick={resetRecommendation}>
                <RotateCcw aria-hidden="true" size={16} /> 다른 곡 추천
              </button>
            ) : null}
          </div>
        </section>
      </div>
    )
  }

  return (
    <div className={styles.recommendPage}>
      <header className={styles.recommendHeader}>
        <div className={styles.recommendIcon}><Headphones aria-hidden="true" size={28} /></div>
        <p className={styles.eyebrow}>PICK A TRACK</p>
        <h1>어떤 곡을 추천할까요?</h1>
      </header>

      <form className={styles.searchShell} onSubmit={submitSearch}>
        <Search aria-hidden="true" size={20} />
        <input
          aria-label="곡 또는 아티스트 검색"
          placeholder="곡 또는 아티스트 검색"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setSearchAuthError(null)
          }}
          disabled={isAccountLoading}
        />
        <button type="submit" disabled={isAccountLoading || search.isPending}>
          {search.isPending ? <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={18} /> : null}
          검색
        </button>
      </form>
      {searchAuthError ? <p className={styles.searchAuthError} role="alert">{searchAuthError}</p> : null}
      {queryError ? <p className={styles.searchError} role="alert">{queryError}</p> : null}

      {account ? (
        <p className={styles.loginNotice}><CircleUserRound aria-hidden="true" size={16} /> 오늘의 추천 {account.quota.used}/{account.quota.limit}</p>
      ) : (
        <NavLink className={styles.loginNotice} to="/login?returnTo=%2Frecommend"><LogIn aria-hidden="true" size={16} /> 추천곡을 검색하려면 로그인해 주세요.</NavLink>
      )}

      {requestError ? (
        <div className={styles.searchMessage} role="alert">
          <strong>검색하지 못했습니다.</strong>
          <span>{requestError}</span>
        </div>
      ) : null}

      {search.data ? (
        <section className={styles.searchResults} aria-labelledby="search-results-heading">
          <div className={styles.resultsHeader}>
            <div>
              <h2 id="search-results-heading">검색 결과 {search.data.items.length}곡</h2>
            </div>
            <span>관련도순</span>
          </div>

          {search.data.items.length === 0 ? (
            <div className={styles.searchMessage}>
              <strong>검색 결과가 없습니다.</strong>
              <span>곡명과 아티스트를 함께 입력해 보세요.</span>
            </div>
          ) : (
            <ol className={styles.trackResults}>
              {search.data.items.slice(0, visibleResultCount).map((track) => {
                const selected = selectedTrack?.externalTrackId === track.externalTrackId
                const existingTrackId = track.existingTrack.trackId
                const hasVoted = track.existingTrack.action === 'VOTED'
                  || (existingTrackId ? votedTrackIds.has(existingTrackId) : false)
                const isVoting = vote.isPending && vote.variables === existingTrackId
                const waiting = track.existingTrack.action === 'WAIT'
                return (
                  <li className={selected ? styles.selectedTrack : undefined} key={track.externalTrackId}>
                    <TrackArtwork track={track} />
                    <div className={styles.trackIdentity}>
                      <div className={styles.trackTitleLine}>
                        <strong>{track.title}</strong>
                        {track.explicit ? <span className={styles.explicitBadge}>Explicit</span> : null}
                      </div>
                      <span>{track.artistName}</span>
                      <small>{[track.albumName, track.releaseYear, track.primaryGenreName].filter(Boolean).join(' · ')}</small>
                    </div>
                    <div className={styles.trackActionColumn}>
                      <div className={styles.trackActions}>
                        {track.externalUrl ? (
                          <a href={track.externalUrl} target="_blank" rel="noreferrer">
                            Apple Music <ExternalLink aria-hidden="true" size={14} />
                          </a>
                        ) : null}
                        {track.existingTrack.registered && existingTrackId ? (
                          <NavLink to={`/tracks/${existingTrackId}`}>상세</NavLink>
                        ) : null}
                        {track.existingTrack.action === 'VOTE' || hasVoted ? (
                          <button
                            className={hasVoted ? styles.votedButton : undefined}
                            type="button"
                            aria-pressed={hasVoted}
                            disabled={hasVoted || quotaEmpty || vote.isPending}
                            onClick={() => vote.mutate(existingTrackId)}
                          >
                            {isVoting ? (
                              <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={15} />
                            ) : hasVoted ? (
                              <Check aria-hidden="true" size={15} />
                            ) : (
                              <ThumbsUp aria-hidden="true" size={15} />
                            )}
                            {isVoting ? '추천 중' : hasVoted ? '추천 완료' : quotaEmpty ? '추천권 없음' : '추천'}
                          </button>
                        ) : waiting ? (
                          <button
                            className={styles.waitingButton}
                            type="button"
                            aria-expanded={cooldownTrackId === track.externalTrackId}
                            onClick={() => setCooldownTrackId((current) => (
                              current === track.externalTrackId ? null : track.externalTrackId
                            ))}
                          >
                            <Clock3 aria-hidden="true" size={15} /> 추천 대기
                          </button>
                      ) : (
                        <button
                          type="button"
                          aria-pressed={selected}
                          onClick={() => selectTrack(track)}
                        >
                          {selected ? <Check aria-hidden="true" size={16} /> : null}
                          {selected ? '선택됨' : '선택'}
                        </button>
                      )}
                      </div>
                      {waiting && cooldownTrackId === track.externalTrackId ? (
                        <p className={styles.cooldownNotice} role="status">
                          {recommendationAvailableMessage(track.existingTrack.recommendationAvailableOn)}
                        </p>
                      ) : null}
                    </div>
                  </li>
                )
              })}
            </ol>
          )}
          {voteError ? <p className={styles.searchError} role="alert">{voteError}</p> : null}
          {search.data.items.length > visibleResultCount ? (
            <button
              className={styles.loadMoreButton}
              type="button"
              onClick={() => setVisibleResultCount((count) => count + 10)}
            >
              더보기 ({Math.min(10, search.data.items.length - visibleResultCount)}곡)
            </button>
          ) : null}
          <p className={styles.attribution}>{search.data.attribution}</p>
        </section>
      ) : null}

      {selectedTrack ? (
        <section ref={composeSectionRef} className={styles.composeSection} aria-labelledby="compose-heading">
          <div className={styles.resultsHeader}>
            <div>
              <h2 id="compose-heading">추천 내용 작성</h2>
            </div>
            <span>추천권 1회 사용</span>
          </div>
          <div className={styles.composeTrack}>
            <TrackArtwork track={selectedTrack} />
            <div>
              <strong>{selectedTrack.title}</strong>
              <span>{selectedTrack.artistName}</span>
              {selectedTrack.preview.url ? (
                <audio
                  className={styles.previewPlayer}
                  controls
                  controlsList="nodownload noplaybackrate noremoteplayback"
                  preload="none"
                  src={selectedTrack.preview.url}
                  aria-label={`${selectedTrack.title} 30초 미리듣기`}
                />
              ) : <small>미리듣기를 제공하지 않는 곡입니다.</small>}
            </div>
          </div>
          <form className={styles.composeForm} onSubmit={submitRecommendation}>
            <label className={styles.field}>
              한줄평
              <textarea
                aria-label="한줄평"
                value={comment}
                maxLength={120}
                placeholder="이 노래에 대한 한줄평을 남겨주세요."
                onChange={(event) => setComment(event.target.value)}
              />
              <span className={styles.characterCount}>{Array.from(comment).length}/120</span>
            </label>
            {composeError || create.isError ? (
              <p className={styles.formError} role="alert">{composeError ?? recommendationError(create.error)}</p>
            ) : null}
            {quotaEmpty ? <p className={styles.formError}>오늘의 추천권을 모두 사용했습니다.</p> : null}
            <button className={styles.submitButton} type="submit" disabled={create.isPending || quotaEmpty}>
              {create.isPending ? <LoaderCircle className={styles.spinningIcon} aria-hidden="true" size={18} /> : <Send aria-hidden="true" size={17} />}
              추천하기
            </button>
          </form>
        </section>
      ) : null}
    </div>
  )
}
