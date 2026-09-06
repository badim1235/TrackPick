import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function renderApp(initialEntry = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('TrackPick app shell', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('shows the home discovery sections and login action', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    renderApp()

    expect(screen.getByRole('heading', { name: '오늘 발견한 음악을 공유해요.' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '오늘의 추천' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument()
  })

  it('renders a dedicated page for an unknown address', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))

    renderApp('/this-page-does-not-exist')

    expect(screen.getByText('404')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '유효하지 않은 요청입니다.' })).toBeInTheDocument()
    expect(screen.getByText('주소가 잘못되었거나 페이지가 이동되었을 수 있습니다.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '홈으로 돌아가기' })).toHaveAttribute('href', '/')
  })

  it('renders trending and recent tracks on the home feed', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/home') {
        const track = (id: string, title: string, voteCount: number, createdAt: string) => ({
          id,
          title,
          artistName: 'TrackDrop Artist',
          albumName: 'Discovery Album',
          albumCoverUrl: 'https://example.com/cover.jpg',
          releaseYear: 2026,
          explicit: false,
          primaryGenre: { id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 },
          recommendation: {
            id: `30000000-0000-0000-0000-${id.slice(-12)}`,
            comment: '홈에서 발견한 곡이에요.',
            commentAvailable: true,
            recommenderNickname: '새벽리듬4881',
            createdAt,
          },
          todayVoteCount: voteCount,
          viewer: null,
          preview: {
            available: true,
            provider: 'APPLE_MUSIC',
            kind: 'OFFICIAL_30_SECOND_CLIP',
            startPosition: 'PROVIDER_SELECTED',
            url: `https://example.com/${id}.m4a`,
          },
          externalLinks: [{ provider: 'APPLE_MUSIC', url: `https://music.apple.com/kr/song/${id}` }],
        })
        return Response.json({
          asOf: '2026-09-01T06:00:00Z',
          quota: null,
          trending: {
            title: '오늘의 추천',
            items: [track('20000000-0000-0000-0000-000000000001', '인기곡', 4, '2026-09-01T05:00:00Z')],
            viewAllPath: '/chart',
          },
          recent: {
            title: '최근 등록된 곡',
            items: [track('20000000-0000-0000-0000-000000000002', '최신곡', 1, '2026-09-01T05:30:00Z')],
            viewAllPath: '/recent',
          },
        })
      }
      return new Response('', { status: 404 })
    })
    renderApp()

    expect(await screen.findByText('인기곡')).toBeInTheDocument()
    expect(screen.getByText('최신곡')).toBeInTheDocument()
    expect(screen.getByText('오늘 4표')).toBeInTheDocument()
    expect(screen.getByLabelText('인기곡 30초 미리듣기')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '인기곡' })).toHaveAttribute(
      'href',
      '/tracks/20000000-0000-0000-0000-000000000001',
    )
    expect(screen.getByRole('link', { name: /전체 보기/ })).toHaveAttribute('href', '/recent')
  })

  it('renders the recent tracks page', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/tracks/recent') {
        return Response.json({
          asOf: '2026-09-01T06:00:00Z',
          items: [{
            id: '20000000-0000-0000-0000-000000000003',
            title: '새로 온 노래',
            artistName: 'Recent Artist',
            albumName: 'Recent Album',
            albumCoverUrl: null,
            releaseYear: 2026,
            explicit: false,
            primaryGenre: { id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 },
            recommendation: {
              id: '30000000-0000-0000-0000-000000000003',
              comment: '새로운 발견이에요.',
              commentAvailable: true,
              recommenderNickname: '푸른멜로디1934',
              createdAt: '2026-09-01T05:30:00Z',
            },
            todayVoteCount: 1,
            viewer: null,
            preview: {
              available: false,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: null,
            },
            externalLinks: [],
          }],
          page: { size: 20, hasMore: false, nextCursor: null },
          quota: null,
        })
      }
      return new Response('', { status: 404 })
    })
    renderApp('/recent')

    expect(await screen.findByText('새로 온 노래')).toBeInTheDocument()
    expect(screen.getByText('Recent Artist')).toBeInTheDocument()
    expect(screen.getByText(/9월 1일 14:30/)).toBeInTheDocument()
  })

  it('moves from home to the chart', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const user = userEvent.setup()
    renderApp()

    await user.click(screen.getAllByRole('link', { name: /차트/ })[0])
    expect(screen.getByRole('heading', { name: '오늘의 차트' })).toBeInTheDocument()
  })

  it('renders a public track detail with preview, ranks, and a login vote action', async () => {
    const trackId = '20000000-0000-0000-0000-000000000001'
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === `/api/v1/tracks/${trackId}`) {
        return Response.json({
          track: {
            id: trackId,
            title: '0+0',
            artistName: '한로로',
            albumName: '이상비행',
            albumCoverUrl: 'https://example.com/cover.jpg',
            releaseYear: 2025,
            isrc: null,
            explicit: false,
            providerGenreName: 'Rock',
            primaryGenre: {
              id: '10000000-0000-0000-0000-000000000020',
              code: 'rock', displayName: 'Rock', sortOrder: 200,
            },
            genres: [{
              id: '10000000-0000-0000-0000-000000000020',
              code: 'rock', displayName: 'Rock', sortOrder: 200,
            }],
            recommendation: {
              id: '30000000-0000-0000-0000-000000000001',
              comment: '처음 들은 순간부터 좋았어요.',
              commentAvailable: true,
              recommenderNickname: '새벽리듬4881',
              createdAt: '2026-09-01T04:00:00Z',
            },
            viewer: null,
            preview: {
              available: true,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: 'https://example.com/preview.m4a',
            },
            providerReferences: [{
              provider: 'APPLE_MUSIC',
              externalTrackId: '1828393595',
              externalUrl: 'https://music.apple.com/kr/song/1828393595',
              metadataRefreshedAt: '2026-09-01T04:00:00Z',
            }],
          },
          today: {
            voteCount: 7,
            overallRank: 2,
            genreRank: 1,
            asOf: '2026-09-03T04:00:00Z',
          },
          quota: null,
          actions: {
            canVote: false,
            canRecommend: false,
            reason: 'UNAUTHENTICATED',
            recommendationAvailableOn: '2026-09-06',
          },
        })
      }
      return new Response('', { status: 404 })
    })
    renderApp(`/tracks/${trackId}`)

    expect(await screen.findByRole('heading', { name: '0+0' })).toBeInTheDocument()
    expect(screen.getByText('한로로')).toBeInTheDocument()
    expect(screen.getByText('7표')).toBeInTheDocument()
    expect(screen.getByText('2위')).toBeInTheDocument()
    expect(screen.getByText(/처음 들은 순간부터 좋았어요/)).toBeInTheDocument()
    expect(screen.getByLabelText('0+0 30초 미리듣기')).toHaveAttribute(
      'src',
      'https://example.com/preview.m4a',
    )
    expect(screen.getByRole('link', { name: /로그인하고 추천/ })).toHaveAttribute(
      'href',
      `/login?returnTo=%2Ftracks%2F${trackId}`,
    )
  })

  it('shows the recommendation cooldown on the track detail page', async () => {
    const trackId = '20000000-0000-0000-0000-000000000002'
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === `/api/v1/tracks/${trackId}`) {
        return Response.json({
          track: {
            id: trackId,
            title: '기다리는 곡',
            artistName: '한로로',
            albumName: null,
            albumCoverUrl: null,
            releaseYear: null,
            isrc: null,
            explicit: false,
            providerGenreName: 'Rock',
            primaryGenre: {
              id: '10000000-0000-0000-0000-000000000020',
              code: 'rock', displayName: 'Rock', sortOrder: 200,
            },
            genres: [],
            recommendation: {
              id: '30000000-0000-0000-0000-000000000002',
              comment: '다시 듣고 싶은 곡이에요.',
              commentAvailable: true,
              recommenderNickname: '푸른멜로디1934',
              createdAt: '2026-09-01T04:00:00Z',
            },
            viewer: null,
            preview: {
              available: false,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: null,
            },
            providerReferences: [{
              provider: 'APPLE_MUSIC',
              externalTrackId: 'cooling-detail',
              externalUrl: null,
              metadataRefreshedAt: '2026-09-01T04:00:00Z',
            }],
          },
          today: { voteCount: 0, overallRank: null, genreRank: null, asOf: '2026-09-02T04:00:00Z' },
          quota: null,
          actions: {
            canVote: false,
            canRecommend: false,
            reason: 'RECOMMENDATION_COOLDOWN',
            recommendationAvailableOn: '2026-09-04',
          },
        })
      }
      return new Response('', { status: 404 })
    })

    renderApp(`/tracks/${trackId}`)

    expect(await screen.findByText('최근 추천된 곡이에요.')).toBeInTheDocument()
    expect(screen.getByText('9월 4일부터 다시 추천할 수 있어요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '추천 대기' })).toBeDisabled()
  })

  it('submits a one-line review report and marks it complete', async () => {
    const trackId = '20000000-0000-0000-0000-000000000009'
    const recommendationId = '30000000-0000-0000-0000-000000000009'
    let reported = false
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'viewer@example.com', publicNickname: '새벽리듬4881',
            emailVerified: true, createdAt: '2026-09-01T00:00:00Z', admin: false,
          },
          quota: { date: '2026-09-06', limit: 4, used: 0, remaining: 4, resetAt: '2026-09-06T15:00:00Z' },
        })
      }
      if (url === `/api/v1/tracks/${trackId}`) {
        return Response.json({
          track: {
            id: trackId, title: '신고 테스트 곡', artistName: '테스트 아티스트', albumName: null,
            albumCoverUrl: null, releaseYear: null, isrc: null, explicit: false, providerGenreName: 'Rock',
            primaryGenre: { id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 },
            genres: [],
            recommendation: {
              id: recommendationId, comment: '확인이 필요한 한줄평', commentAvailable: true,
              recommenderNickname: '푸른멜로디1934', createdAt: '2026-09-06T04:00:00Z',
            },
            viewer: { hasVotedToday: false },
            preview: { available: false, provider: 'APPLE_MUSIC', kind: 'OFFICIAL_30_SECOND_CLIP', startPosition: 'PROVIDER_SELECTED', url: null },
            providerReferences: [{ provider: 'APPLE_MUSIC', externalTrackId: 'report-track', externalUrl: null, metadataRefreshedAt: '2026-09-06T04:00:00Z' }],
          },
          today: { voteCount: 1, overallRank: 1, genreRank: 1, asOf: '2026-09-06T05:00:00Z' },
          quota: { date: '2026-09-06', limit: 4, used: 0, remaining: 4, resetAt: '2026-09-06T15:00:00Z' },
          actions: {
            canVote: true, canRecommend: false, reason: null, recommendationAvailableOn: '2026-09-09',
            canReport: !reported, hasReported: reported,
          },
        })
      }
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === `/api/v1/recommendations/${recommendationId}/reports` && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          reasonCode: 'SPAM',
          details: '반복 광고 문구입니다.',
        })
        reported = true
        return Response.json({
          report: { id: '40000000-0000-0000-0000-000000000009', status: 'PENDING', createdAt: '2026-09-06T05:10:00Z' },
        }, { status: 201 })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp(`/tracks/${trackId}`)

    await user.click(await screen.findByRole('button', { name: '신고' }))
    const dialog = screen.getByRole('dialog', { name: '한줄평 신고' })
    await user.selectOptions(within(dialog).getByRole('combobox', { name: '신고 사유' }), 'SPAM')
    await user.type(within(dialog).getByRole('textbox', { name: /추가 설명/ }), '반복 광고 문구입니다.')
    await user.click(within(dialog).getByRole('button', { name: '신고하기' }))

    expect(await screen.findByText('신고 완료')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/recommendations/${recommendationId}/reports`,
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('lets an administrator review and ban a flagged user', async () => {
    const targetId = '50000000-0000-0000-0000-000000000001'
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'admin@example.com', publicNickname: '관리자',
            emailVerified: true, createdAt: '2026-09-01T00:00:00Z', admin: true,
          },
          quota: { date: '2026-09-06', limit: 4, used: 0, remaining: 4, resetAt: '2026-09-06T15:00:00Z' },
        })
      }
      if (url === '/api/v1/admin/reports/users') {
        return Response.json({
          items: [{
            id: targetId, email: 'target@example.com', publicNickname: '검토대상', activity: 'FLAGGED',
            pendingReportCount: 3, firstReportedAt: '2026-09-06T01:00:00Z', latestReportedAt: '2026-09-06T03:00:00Z',
          }],
        })
      }
      if (url === `/api/v1/admin/reports/users/${targetId}` && init?.method !== 'PATCH') {
        return Response.json({
          user: { id: targetId, email: 'target@example.com', publicNickname: '검토대상', activity: 'FLAGGED', pendingReportCount: 3 },
          reports: [{
            id: '60000000-0000-0000-0000-000000000001', reasonCode: 'ABUSIVE_LANGUAGE',
            details: '비방 표현이 있습니다.', status: 'PENDING', createdAt: '2026-09-06T03:00:00Z',
            reporter: { id: '70000000-0000-0000-0000-000000000001', publicNickname: '신고자' },
            recommendation: {
              id: '80000000-0000-0000-0000-000000000001', comment: '확인이 필요한 한줄평',
              trackId: '20000000-0000-0000-0000-000000000001', trackTitle: '검토할 곡', artistName: '아티스트',
            },
          }],
        })
      }
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === `/api/v1/admin/reports/users/${targetId}` && init?.method === 'PATCH') {
        expect(JSON.parse(String(init.body))).toEqual({ action: 'BAN' })
        return Response.json({
          userId: targetId, activity: 'BAN', accountStatus: 'SUSPENDED',
          resolvedReportCount: 3, resolvedAt: '2026-09-06T04:00:00Z',
        })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/admin')

    await user.click(await screen.findByRole('button', { name: /검토대상/ }))
    expect(await screen.findByText(/확인이 필요한 한줄평/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '이용 제한' }))
    const dialog = screen.getByRole('dialog', { name: '이용을 제한할까요?' })
    await user.click(within(dialog).getByRole('button', { name: '이용 제한' }))

    expect(await screen.findByText('이용 제한 조치를 완료했습니다.')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/admin/reports/users/${targetId}`,
      expect.objectContaining({ method: 'PATCH' }),
    )
  })

  it('rechecks cached admin access and hides the page from a regular user', async () => {
    const cachedAdmin = {
      account: {
        email: 'admin@example.com', publicNickname: '관리자',
        emailVerified: true, createdAt: '2026-09-01T00:00:00Z', admin: true,
      },
      quota: { date: '2026-09-06', limit: 4, used: 0, remaining: 4, resetAt: '2026-09-06T15:00:00Z' },
    }
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'regular@example.com', publicNickname: '일반사용자',
            emailVerified: true, createdAt: '2026-09-01T00:00:00Z', admin: false,
          },
          quota: { date: '2026-09-06', limit: 4, used: 0, remaining: 4, resetAt: '2026-09-06T15:00:00Z' },
        })
      }
      return new Response('', { status: 404 })
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(['account'], cachedAdmin)

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/admin']}>
          <App />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByRole('heading', { name: '유효하지 않은 요청입니다.' })).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/v1/admin/reports/users', expect.anything())
  })

  it('renders registered tracks from the live daily chart', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/genres') {
        return Response.json({
          items: [{ id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 }],
        })
      }
      if (url === '/api/v1/charts/daily?genre=all') {
        return Response.json({
          date: '2026-09-01',
          status: 'LIVE',
          scope: { type: 'ALL', genre: null },
          asOf: '2026-09-01T05:00:00Z',
          items: [{
            rank: 1,
            voteCount: 1,
            hasVotedToday: false,
            track: {
              id: '20000000-0000-0000-0000-000000000001',
              title: '0+0',
              artistName: '한로로',
              albumName: '이상비행',
              albumCoverUrl: 'https://example.com/cover.jpg',
              releaseYear: 2025,
              explicit: false,
              primaryGenre: { id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 },
              comment: '오늘 계속 듣고 싶은 곡이에요.',
              recommenderNickname: '새벽리듬4881',
              preview: { available: true, provider: 'APPLE_MUSIC', url: 'https://example.com/preview.m4a' },
              externalUrl: 'https://music.apple.com/kr/song/0-0',
            },
          }],
          page: { size: 20, hasMore: false, nextCursor: null },
          quota: null,
          actions: { canVote: true },
        })
      }
      return new Response('', { status: 404 })
    })
    renderApp('/chart')

    expect(await screen.findByText('0+0')).toBeInTheDocument()
    const chartHeading = screen.getByRole('heading', { name: '오늘의 차트' })
    expect(chartHeading.parentElement).toHaveTextContent('LIVE · 14:00 기준')
    expect(screen.getByText('한로로')).toBeInTheDocument()
    expect(screen.getByText('1표')).toBeInTheDocument()
    expect(screen.getByText(/오늘 계속 듣고 싶은 곡이에요/)).toBeInTheDocument()
    expect(screen.getByLabelText('0+0 30초 미리듣기')).toHaveAttribute(
      'src',
      'https://example.com/preview.m4a',
    )
  })

  it('renders a finalized historical chart without voting actions', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'listener@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-08-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-03', limit: 4, used: 0, remaining: 4,
            resetAt: '2026-09-03T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/genres') {
        return Response.json({
          items: [{ id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 }],
        })
      }
      if (url === '/api/v1/charts/daily?genre=all&date=2026-08-31') {
        return Response.json({
          date: '2026-08-31',
          status: 'FINAL',
          scope: { type: 'ALL', genre: null },
          asOf: '2026-09-01T15:00:00Z',
          items: [{
            rank: 1,
            voteCount: 7,
            hasVotedToday: false,
            track: {
              id: '20000000-0000-0000-0000-000000000031',
              title: '지난 여름의 노래',
              artistName: 'Historical Artist',
              albumName: 'Archive',
              albumCoverUrl: null,
              releaseYear: 2026,
              explicit: false,
              primaryGenre: { id: '10000000-0000-0000-0000-000000000020', code: 'rock', displayName: 'Rock', sortOrder: 200 },
              comment: '그날 가장 많이 추천받은 곡이에요.',
              recommenderNickname: '푸른멜로디1934',
              preview: { available: false, provider: 'APPLE_MUSIC', url: null },
              externalUrl: null,
            },
          }],
          page: { size: 20, hasMore: true, nextCursor: 'final-page-2' },
          quota: null,
          actions: { canVote: false },
        })
      }
      return new Response('', { status: 404 })
    })
    renderApp('/chart?date=2026-08-31')

    expect(await screen.findByRole('heading', { name: '2026년 8월 31일 차트' })).toBeInTheDocument()
    expect(screen.getByLabelText('차트 날짜')).toHaveValue('2026-08-31')
    expect(await screen.findByText('지난 여름의 노래')).toBeInTheDocument()
    expect(screen.getByText('DAILY CHART')).toBeInTheDocument()
    expect(screen.queryByText(/FINAL/)).not.toBeInTheDocument()
    expect(screen.getByText('과거 차트 보기')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '오늘 차트' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '더보기' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '추천' })).not.toBeInTheDocument()
  })

  it('shows the login form with a persistent-login choice', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    renderApp('/login')

    expect(screen.getByRole('heading', { name: '다시 음악을 발견해요.' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: '로그인 상태 유지' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '비밀번호 찾기' })).toHaveAttribute('href', '/recover/password')
    expect(screen.getByRole('link', { name: '계정 만들기' })).toBeInTheDocument()
  })

  it('submits login credentials only to the login endpoint', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === '/api/v1/auth/login' && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          email: 'listener@example.com',
          password: 'wrongpass1',
          rememberMe: false,
        })
        return Response.json({
          error: {
            code: 'INVALID_CREDENTIALS',
            message: '로그인 정보를 확인해 주세요.',
            details: {},
          },
        }, { status: 401 })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(screen.getByLabelText('이메일'), 'listener@example.com')
    await user.type(screen.getByLabelText('비밀번호', { selector: 'input' }), 'wrongpass1')
    await user.click(screen.getByRole('button', { name: /^로그인$/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('로그인 정보를 확인해 주세요.')
    expect(screen.queryByText('이미 존재하는 이메일입니다.')).not.toBeInTheDocument()
  })

  it('requires the current password before deleting an account', async () => {
    let deleted = false
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/me' && init?.method === 'DELETE') {
        expect(JSON.parse(String(init.body))).toEqual({ password: 'chatgpt5555' })
        expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('csrf-token')
        deleted = true
        return new Response(null, { status: 204 })
      }
      if (url === '/api/v1/me') {
        if (deleted) return new Response(null, { status: 401 })
        return Response.json({
          account: {
            email: 'listener@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-08-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-03', limit: 4, used: 1, remaining: 3,
            resetAt: '2026-09-03T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      return new Response(null, { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/me')

    await user.click(await screen.findByRole('button', { name: '회원 탈퇴' }))
    const dialog = screen.getByRole('dialog', { name: '회원 탈퇴' })
    expect(dialog).toHaveTextContent('추천·투표 기록이 모두 삭제되며 되돌릴 수 없습니다.')
    await user.type(screen.getByLabelText('현재 비밀번호', { selector: 'input' }), 'chatgpt5555')
    await user.click(screen.getByRole('button', { name: '탈퇴하기' }))

    await waitFor(() => expect(deleted).toBe(true))
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/me', expect.objectContaining({ method: 'DELETE' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('opens the account recovery entry pages', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    renderApp('/recover/password')

    expect(screen.getByRole('heading', { name: '비밀번호를 다시 설정해요.' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '로그인으로 돌아가기' })).toHaveAttribute('href', '/login')
  })

  it('accepts an empty password recovery response', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === '/api/v1/auth/password-recovery' && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({ email: 'listener@example.com' })
        return new Response(null, { status: 202 })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recover/password')

    await user.type(screen.getByLabelText('이메일'), 'listener@example.com')
    await user.click(screen.getByRole('button', { name: '재설정 메일 보내기' }))

    expect(await screen.findByText('메일을 보냈습니다. 받은 메일함을 확인해 주세요.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('validates the confirmed signup policy before submission', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const user = userEvent.setup()
    renderApp('/join')

    await user.type(screen.getByLabelText('이메일'), 'invalid')
    await user.type(screen.getByLabelText('비밀번호', { selector: 'input' }), 'lettersOnly')
    await user.type(screen.getByLabelText('비밀번호 확인', { selector: 'input' }), 'different1')
    await user.click(screen.getByRole('button', { name: '계정 만들기' }))

    expect(screen.getByText('8~16자의 영문자와 숫자를 포함하고 공백 없이 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('비밀번호가 일치하지 않습니다.')).toBeInTheDocument()
    expect(screen.getByText('올바른 이메일 형식이 아닙니다.')).toBeInTheDocument()
  })

  it('clears invalid email guidance when the format becomes valid', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const user = userEvent.setup()
    renderApp('/join')

    const emailInput = screen.getByLabelText('이메일')
    await user.type(emailInput, 'invalid')
    expect(screen.getByText('올바른 이메일 형식이 아닙니다.')).toBeInTheDocument()

    await user.clear(emailInput)
    await user.type(emailInput, 'listener@example.com')
    expect(screen.queryByText('올바른 이메일 형식이 아닙니다.')).not.toBeInTheDocument()
  })

  it('shows an existing email error below the sign-up field', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') return new Response('', { status: 401 })
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === '/api/v1/auth/sign-up') {
        return Response.json({
          error: {
            code: 'EMAIL_TAKEN',
            message: '이미 존재하는 이메일입니다.',
            details: {},
          },
        }, { status: 409 })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/join')

    await user.type(screen.getByLabelText('이메일'), 'listener@example.com')
    await user.type(screen.getByLabelText('비밀번호', { selector: 'input' }), 'chatgpt5555')
    await user.type(screen.getByLabelText('비밀번호 확인', { selector: 'input' }), 'chatgpt5555')
    await user.click(screen.getByRole('button', { name: '계정 만들기' }))

    expect(await screen.findByText('이미 존재하는 이메일입니다.')).toBeInTheDocument()
  })

  it('shows an invalid password message while typing', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const user = userEvent.setup()
    renderApp('/join')

    await user.type(screen.getByLabelText('비밀번호', { selector: 'input' }), 'letters only')

    expect(screen.getByText('8~16자의 영문자와 숫자를 포함하고 공백 없이 입력해 주세요.')).toBeInTheDocument()
  })

  it('exposes the password guidelines on sign-up', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    renderApp('/join')

    expect(screen.getByLabelText('비밀번호 입력 규칙')).toBeInTheDocument()
    expect(screen.getByRole('tooltip')).toHaveTextContent('영문자(대·소문자 허용)와 숫자를 각각 1개 이상 포함한 8~16자')
  })

  it('lets signed-out visitors type a search and asks them to log in on submit', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 401 }))
    const user = userEvent.setup()
    renderApp('/recommend')

    const input = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(input).toBeEnabled())
    await user.type(input, '한로로')
    await user.click(screen.getByRole('button', { name: '검색' }))

    expect(screen.getByRole('alert')).toHaveTextContent('로그인이 필요합니다.')
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining('/api/v1/music/search'), expect.anything())
  })

  it('searches the KR catalog and marks explicit tracks', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'music_user@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-09-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-01',
            limit: 4,
            used: 0,
            remaining: 4,
            resetAt: '2026-09-01T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/music/search?query=%EA%B3%A1') {
        return Response.json({
          provider: 'APPLE_MUSIC',
          storefront: 'KR',
          attribution: 'Music preview provided courtesy of iTunes',
          items: [{
            provider: 'APPLE_MUSIC',
            externalTrackId: '1234',
            title: 'Creep',
            artistName: 'Radiohead',
            albumName: 'Pablo Honey',
            albumCoverUrl: 'https://example.com/cover.jpg',
            releaseYear: 1993,
            isrc: null,
            explicit: true,
            primaryGenreName: 'Alternative',
            preview: {
              available: true,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: 'https://example.com/preview.m4a',
            },
            externalUrl: 'https://music.apple.com/kr/album/creep/1234',
            existingTrack: {
              registered: false,
              trackId: null,
              inCurrentChart: false,
              hasVotedToday: false,
              recommendationAvailableOn: null,
              action: 'SELECT',
            },
          }],
        })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recommend')

    const input = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(input).toBeEnabled())
    await user.type(input, '곡')
    await user.click(screen.getByRole('button', { name: '검색' }))

    expect(await screen.findByRole('heading', { name: '검색 결과 1곡' })).toBeInTheDocument()
    expect(screen.getByText('Creep')).toBeInTheDocument()
    expect(screen.getByText('Explicit')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Apple Music/ })).toHaveAttribute(
      'href',
      'https://music.apple.com/kr/album/creep/1234',
    )
  })

  it('shows search results ten at a time', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'music_user@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-09-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-01', limit: 4, used: 0, remaining: 4,
            resetAt: '2026-09-01T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/music/search?query=Radiohead') {
        return Response.json({
          provider: 'APPLE_MUSIC',
          storefront: 'KR',
          attribution: 'Music preview provided courtesy of iTunes',
          items: Array.from({ length: 20 }, (_, index) => ({
            provider: 'APPLE_MUSIC',
            externalTrackId: String(index + 1),
            title: `Search Track ${index + 1}`,
            artistName: 'Radiohead',
            albumName: 'Search Album',
            albumCoverUrl: 'https://example.com/cover.jpg',
            releaseYear: 2026,
            isrc: null,
            explicit: false,
            primaryGenreName: 'Alternative',
            preview: {
              available: true,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: `https://example.com/preview-${index + 1}.m4a`,
            },
            externalUrl: `https://music.apple.com/kr/song/${index + 1}`,
            existingTrack: {
              registered: false,
              trackId: null,
              inCurrentChart: false,
              hasVotedToday: false,
              recommendationAvailableOn: null,
              action: 'SELECT',
            },
          })),
        })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recommend')

    const input = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(input).toBeEnabled())
    await user.type(input, 'Radiohead')
    await user.click(screen.getByRole('button', { name: '검색' }))

    expect(await screen.findByText('Search Track 10')).toBeInTheDocument()
    expect(screen.queryByText('Search Track 11')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '더보기 (10곡)' }))

    expect(await screen.findByText('Search Track 20')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /더보기/ })).not.toBeInTheDocument()
  })

  it('votes for an existing track and updates the daily quota', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'music_user@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-09-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-01', limit: 4, used: 0, remaining: 4,
            resetAt: '2026-09-01T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/music/search?query=Existing') {
        return Response.json({
          provider: 'APPLE_MUSIC',
          storefront: 'KR',
          attribution: 'Music preview provided courtesy of iTunes',
          items: [{
            provider: 'APPLE_MUSIC',
            externalTrackId: '1234',
            title: 'Existing Track',
            artistName: 'TrackDrop Artist',
            albumName: 'Existing Album',
            albumCoverUrl: 'https://example.com/cover.jpg',
            releaseYear: 2026,
            isrc: null,
            explicit: false,
            primaryGenreName: 'Rock',
            preview: {
              available: true,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: 'https://example.com/preview.m4a',
            },
            externalUrl: 'https://music.apple.com/kr/song/1234',
            existingTrack: {
              registered: true,
              trackId: '20000000-0000-0000-0000-000000000001',
              inCurrentChart: true,
              hasVotedToday: false,
              recommendationAvailableOn: '2026-09-04',
              action: 'VOTE',
            },
          }],
        })
      }
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === '/api/v1/tracks/20000000-0000-0000-0000-000000000001/votes' && init?.method === 'POST') {
        return Response.json({
          vote: {
            trackId: '20000000-0000-0000-0000-000000000001',
            votedOn: '2026-09-01',
            createdAt: '2026-09-01T04:00:00Z',
          },
          todayVoteCount: 2,
          quota: {
            date: '2026-09-01', limit: 4, used: 1, remaining: 3,
            resetAt: '2026-09-01T15:00:00Z',
          },
        }, { status: 201 })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recommend')

    const input = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(input).toBeEnabled())
    await user.type(input, 'Existing')
    await user.click(screen.getByRole('button', { name: '검색' }))
    await user.click(await screen.findByRole('button', { name: '추천' }))

    expect(await screen.findByRole('button', { name: '추천 완료' })).toBeDisabled()
    expect(screen.getByText('오늘의 추천 1/4')).toBeInTheDocument()
  })

  it('shows the next recommendation date from the waiting action', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'music_user@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-09-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-02', limit: 4, used: 0, remaining: 4,
            resetAt: '2026-09-02T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/music/search?query=Cooling') {
        return Response.json({
          provider: 'APPLE_MUSIC',
          storefront: 'KR',
          attribution: 'Music preview provided courtesy of iTunes',
          items: [{
            provider: 'APPLE_MUSIC',
            externalTrackId: 'cooling-track',
            title: 'Cooling Track',
            artistName: 'TrackPick Artist',
            albumName: 'Cooling Album',
            albumCoverUrl: null,
            releaseYear: 2026,
            isrc: null,
            explicit: false,
            primaryGenreName: 'Rock',
            preview: {
              available: false,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: null,
            },
            externalUrl: null,
            existingTrack: {
              registered: true,
              trackId: '20000000-0000-0000-0000-000000000001',
              inCurrentChart: false,
              hasVotedToday: false,
              recommendationAvailableOn: '2026-09-04',
              action: 'WAIT',
            },
          }],
        })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recommend')

    const input = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(input).toBeEnabled())
    await user.type(input, 'Cooling')
    await user.click(screen.getByRole('button', { name: '검색' }))
    const waitingButton = await screen.findByRole('button', { name: '추천 대기' })
    await user.click(waitingButton)

    expect(screen.getByText('9월 4일부터 다시 추천할 수 있어요.')).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '한줄평' })).not.toBeInTheDocument()
  })

  it('registers a selected track with an Apple genre and one-line review', async () => {
    const scrollIntoView = vi.fn()
    Object.defineProperty(Element.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    })
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input)
      if (url === '/api/v1/me') {
        return Response.json({
          account: {
            email: 'music_user@example.com',
            publicNickname: '새벽리듬4881',
            emailVerified: true,
            createdAt: '2026-09-01T00:00:00Z',
          },
          quota: {
            date: '2026-09-01', limit: 4, used: 0, remaining: 4,
            resetAt: '2026-09-01T15:00:00Z',
          },
        })
      }
      if (url === '/api/v1/music/search?query=0%2B0') {
        return Response.json({
          provider: 'APPLE_MUSIC',
          storefront: 'KR',
          attribution: 'Music preview provided courtesy of iTunes',
          items: [{
            provider: 'APPLE_MUSIC',
            externalTrackId: '1828393595',
            title: '0+0',
            artistName: '한로로',
            albumName: '이상비행',
            albumCoverUrl: 'https://example.com/cover.jpg',
            releaseYear: 2025,
            isrc: null,
            explicit: false,
            primaryGenreName: 'Rock',
            preview: {
              available: true,
              provider: 'APPLE_MUSIC',
              kind: 'OFFICIAL_30_SECOND_CLIP',
              startPosition: 'PROVIDER_SELECTED',
              url: 'https://example.com/preview.m4a',
            },
            externalUrl: 'https://music.apple.com/kr/song/1828393595',
            existingTrack: {
              registered: false,
              trackId: null,
              inCurrentChart: false,
              hasVotedToday: false,
              recommendationAvailableOn: null,
              action: 'SELECT',
            },
          }],
        })
      }
      if (url === '/api/v1/auth/csrf') return Response.json({ token: 'csrf-token' })
      if (url === '/api/v1/recommendations' && init?.method === 'POST') {
        expect(JSON.parse(String(init.body))).toEqual({
          provider: 'APPLE_MUSIC',
          externalTrackId: '1828393595',
          comment: '잔잔하게 번지는 기타가 좋아요.',
        })
        return Response.json({
          track: {
            id: '20000000-0000-0000-0000-000000000001',
            title: '0+0', artistName: '한로로', albumName: '이상비행',
            albumCoverUrl: 'https://example.com/cover.jpg', releaseYear: 2025,
            explicit: false, primaryGenreName: 'Rock',
            preview: { available: true, provider: 'APPLE_MUSIC', url: 'https://example.com/preview.m4a' },
            externalUrl: 'https://music.apple.com/kr/song/1828393595',
          },
          recommendation: {
            id: '30000000-0000-0000-0000-000000000001',
            primaryGenre: {
              id: '10000000-0000-0000-0000-000000000020',
              code: 'rock', displayName: 'Rock', sortOrder: 200,
            },
            comment: '잔잔하게 번지는 기타가 좋아요.',
            createdAt: '2026-09-01T03:00:00Z',
          },
          vote: { created: true, votedOn: '2026-09-01' },
          quota: {
            date: '2026-09-01', limit: 4, used: 1, remaining: 3,
            resetAt: '2026-09-01T15:00:00Z',
          },
        }, { status: 201 })
      }
      return new Response('', { status: 404 })
    })
    const user = userEvent.setup()
    renderApp('/recommend')

    const searchInput = await screen.findByRole('textbox', { name: '곡 또는 아티스트 검색' })
    await waitFor(() => expect(searchInput).toBeEnabled())
    await user.type(searchInput, '0+0')
    await user.click(screen.getByRole('button', { name: '검색' }))
    await user.click(await screen.findByRole('button', { name: '선택' }))

    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' })
    expect(screen.queryByRole('combobox', { name: /대표 장르/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/Apple 분류:/)).not.toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: '한줄평' }), '잔잔하게 번지는 기타가 좋아요.')
    await user.click(screen.getByRole('button', { name: '추천하기' }))

    expect(await screen.findByRole('heading', { name: '곡을 추천했어요.' })).toBeInTheDocument()
    expect(screen.getByText('오늘의 추천 1/4 · 3회 남음')).toBeInTheDocument()
  })
})
