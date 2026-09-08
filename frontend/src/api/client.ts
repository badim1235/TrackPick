import type { components } from './generated'

export type SystemHealth = components['schemas']['SystemHealthResponse']
export type AccountResponse = components['schemas']['AccountResponse']
export type ActivityResponse = components['schemas']['ActivityResponse']
export type FeedbackCategory = components['schemas']['FeedbackCategory']
export type FeedbackRequest = components['schemas']['FeedbackRequest']
export type FeedbackResponse = components['schemas']['FeedbackResponse']
export type SignUpRequest = components['schemas']['SignUpRequest']
export type SignUpResponse = components['schemas']['SignUpResponse']
export type LoginRequest = components['schemas']['LoginRequest']
export type PasswordRecoveryRequest = components['schemas']['PasswordRecoveryRequest']
export type PasswordResetRequest = components['schemas']['PasswordResetRequest']
export type MusicSearchResponse = components['schemas']['MusicSearchResponse']
export type MusicSearchItem = components['schemas']['MusicSearchItem']
export type GenreResponse = components['schemas']['GenreResponse']
export type Genre = components['schemas']['Genre']
export type HomeResponse = components['schemas']['HomeResponse']
export type HomeTrackCard = components['schemas']['HomeTrackCard']
export type RecentTracksResponse = components['schemas']['RecentTracksResponse']
export type TrackDetailResponse = components['schemas']['TrackDetailResponse']
export type DailyChartResponse = components['schemas']['DailyChartResponse']
export type CreateRecommendationRequest = components['schemas']['CreateRecommendationRequest']
export type CreateRecommendationResponse = components['schemas']['CreateRecommendationResponse']
export type CreateVoteResponse = components['schemas']['CreateVoteResponse']
export type CreateReportRequest = components['schemas']['CreateReportRequest']
export type CreateReportResponse = components['schemas']['CreateReportResponse']
export type FlaggedUsersResponse = components['schemas']['FlaggedUsersResponse']
export type UserReportsResponse = components['schemas']['UserReportsResponse']
export type ModerationRequest = components['schemas']['ModerationRequest']
export type ModerationResult = components['schemas']['ModerationResult']

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly details: Record<string, unknown>

  constructor(code: string, message: string, status: number, details: Record<string, unknown> = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.details = details
  }
}

async function parseError(response: Response): Promise<ApiError> {
  const fallback = '요청을 처리하지 못했습니다. 다시 시도해 주세요.'
  try {
    const body = await response.json() as {
      error?: { code?: string; message?: string; details?: Record<string, unknown> }
    }
    return new ApiError(
      body.error?.code ?? 'UNKNOWN_ERROR',
      body.error?.message ?? fallback,
      response.status,
      body.error?.details ?? {},
    )
  } catch {
    return new ApiError('UNKNOWN_ERROR', fallback, response.status)
  }
}

async function csrfToken(): Promise<string> {
  const response = await fetch('/api/v1/auth/csrf', {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  const body = await response.json() as components['schemas']['CsrfResponse']
  return body.token
}

async function mutate<T>(path: string, body?: unknown, method = 'POST'): Promise<T> {
  const token = await csrfToken()
  const response = await fetch(path, {
    method,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': token,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw await parseError(response)
  const responseText = await response.text()
  return responseText ? JSON.parse(responseText) as T : undefined as T
}

export async function fetchSystemHealth(): Promise<SystemHealth> {
  const response = await fetch('/api/v1/system/health', {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<SystemHealth>
}

export async function fetchAccount(): Promise<AccountResponse | null> {
  const response = await fetch('/api/v1/me', {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (response.status === 401) return null
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<AccountResponse>
}

export async function fetchMyActivity(cursor?: string): Promise<ActivityResponse> {
  const params = new URLSearchParams()
  if (cursor) params.set('cursor', cursor)
  const query = params.size ? `?${params}` : ''
  const response = await fetch(`/api/v1/me/activity${query}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<ActivityResponse>
}

export async function submitFeedback(body: FeedbackRequest): Promise<FeedbackResponse> {
  return mutate<FeedbackResponse>('/api/v1/feedback', body)
}

export async function searchMusic(query: string): Promise<MusicSearchResponse> {
  const params = new URLSearchParams({ query })
  const response = await fetch(`/api/v1/music/search?${params}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<MusicSearchResponse>
}

export async function fetchGenres(): Promise<GenreResponse> {
  const response = await fetch('/api/v1/genres', {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<GenreResponse>
}

export async function fetchHome(): Promise<HomeResponse> {
  const response = await fetch('/api/v1/home', {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<HomeResponse>
}

export async function fetchRecentTracks(cursor?: string): Promise<RecentTracksResponse> {
  const params = new URLSearchParams()
  if (cursor) params.set('cursor', cursor)
  const query = params.size ? `?${params}` : ''
  const response = await fetch(`/api/v1/tracks/recent${query}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<RecentTracksResponse>
}

export async function fetchTrackDetail(trackId: string): Promise<TrackDetailResponse> {
  const response = await fetch(`/api/v1/tracks/${encodeURIComponent(trackId)}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<TrackDetailResponse>
}

export async function fetchDailyChart(
  genre = 'all',
  date?: string,
  cursor?: string,
): Promise<DailyChartResponse> {
  const params = new URLSearchParams({ genre })
  if (date) params.set('date', date)
  if (cursor) params.set('cursor', cursor)
  const response = await fetch(`/api/v1/charts/daily?${params}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<DailyChartResponse>
}

export function createRecommendation(
  body: CreateRecommendationRequest,
): Promise<CreateRecommendationResponse> {
  return mutate('/api/v1/recommendations', body)
}

export function createVote(trackId: string): Promise<CreateVoteResponse> {
  return mutate(`/api/v1/tracks/${encodeURIComponent(trackId)}/votes`)
}

export function createReport(
  recommendationId: string,
  body: CreateReportRequest,
): Promise<CreateReportResponse> {
  return mutate(`/api/v1/recommendations/${encodeURIComponent(recommendationId)}/reports`, body)
}

export async function fetchFlaggedUsers(query = ''): Promise<FlaggedUsersResponse> {
  const params = new URLSearchParams()
  if (query) params.set('query', query)
  const suffix = params.size ? `?${params}` : ''
  const response = await fetch(`/api/v1/admin/reports/users${suffix}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<FlaggedUsersResponse>
}

export async function fetchReportedUser(userId: string): Promise<UserReportsResponse> {
  const response = await fetch(`/api/v1/admin/reports/users/${encodeURIComponent(userId)}`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<UserReportsResponse>
}

export function moderateReportedUser(
  userId: string,
  body: ModerationRequest,
): Promise<ModerationResult> {
  return mutate(`/api/v1/admin/reports/users/${encodeURIComponent(userId)}`, body, 'PATCH')
}

export function signUp(body: SignUpRequest): Promise<SignUpResponse> {
  return mutate('/api/v1/auth/sign-up', body)
}

export function login(body: LoginRequest): Promise<AccountResponse> {
  return mutate('/api/v1/auth/login', body)
}

export function logout(): Promise<void> {
  return mutate('/api/v1/auth/logout')
}

export function deleteAccount(password: string): Promise<void> {
  return mutate('/api/v1/me', { password }, 'DELETE')
}

export function requestPasswordRecovery(body: PasswordRecoveryRequest): Promise<void> {
  return mutate('/api/v1/auth/password-recovery', body)
}

export function resetPassword(body: PasswordResetRequest): Promise<void> {
  return mutate('/api/v1/auth/password-reset', body)
}
