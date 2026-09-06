import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CircleHelp, Eye, EyeOff, KeyRound, LockKeyhole, LogIn, ShieldCheck, Trash2, UserPlus, X } from 'lucide-react'
import { useState } from 'react'
import { useForm, type UseFormRegisterReturn } from 'react-hook-form'
import { NavLink, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { ApiError, deleteAccount, login, logout, requestPasswordRecovery, resetPassword, signUp, type AccountResponse } from '../api/client'
import styles from '../App.module.css'
import { accountQueryKey, useAccount } from './account'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function isValidPassword(value: string) {
  const length = Array.from(value).length
  return length >= 8 && length <= 16 && /[A-Za-z]/.test(value) && /[0-9]/.test(value) && !/\s/.test(value)
}

function safeReturnTo(value: string | null) {
  return value?.startsWith('/') && !value.startsWith('//') ? value : '/'
}

function PasswordField({
  label,
  error,
  registration,
  showGuidelines = false,
}: {
  label: string
  error?: string
  registration: UseFormRegisterReturn
  showGuidelines?: boolean
}) {
  const [visible, setVisible] = useState(false)
  const inputId = `${registration.name}-input`
  return (
    <div className={styles.field}>
      <span className={styles.fieldLabel}>
        <label htmlFor={inputId}>{label}</label>
        {showGuidelines && (
          <span className={styles.passwordGuide} tabIndex={0} aria-label="비밀번호 입력 규칙">
            <CircleHelp aria-hidden="true" size={14} />
            <span className={styles.passwordTooltip} role="tooltip">
              영문자(대·소문자 허용)와 숫자를 각각 1개 이상 포함한 8~16자 · 특수문자 허용 · 공백 불가
            </span>
          </span>
        )}
      </span>
      <span className={styles.passwordInput}>
        <input id={inputId} aria-label={label} type={visible ? 'text' : 'password'} autoComplete="current-password" {...registration} />
        <button type="button" onClick={() => setVisible((current) => !current)} aria-label={visible ? '비밀번호 숨기기' : '비밀번호 보기'}>
          {visible ? <EyeOff aria-hidden="true" size={18} /> : <Eye aria-hidden="true" size={18} />}
        </button>
      </span>
      {error && <span className={styles.fieldError}>{error}</span>}
    </div>
  )
}

function AuthHeading({ mode }: { mode: 'login' | 'join' }) {
  return (
    <div className={styles.authHeading}>
      <span className={styles.authIcon}>{mode === 'login' ? <LogIn aria-hidden="true" /> : <UserPlus aria-hidden="true" />}</span>
      <p className={styles.eyebrow}>{mode === 'login' ? 'WELCOME BACK' : 'JOIN TRACKPICK'}</p>
      <h1>{mode === 'login' ? '다시 음악을 발견해요.' : '익명으로 음악을 나눠요.'}</h1>
    </div>
  )
}

type LoginFields = {
  email: string
  password: string
  rememberMe: boolean
}

export function LoginPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const returnTo = safeReturnTo(searchParams.get('returnTo'))
  const [serverError, setServerError] = useState('')
  const { data: account } = useAccount()
  const { register, handleSubmit, formState: { errors } } = useForm<LoginFields>({
    defaultValues: { email: '', password: '', rememberMe: false },
  })
  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (response) => {
      queryClient.setQueryData(accountQueryKey, response)
      navigate(returnTo, { replace: true })
    },
    onError: (error) => setServerError(error instanceof ApiError ? error.message : '로그인하지 못했습니다.'),
  })

  if (account) return <Navigate to={returnTo} replace />

  return (
    <div className={styles.authPage}>
      <AuthHeading mode="login" />
      <form className={styles.authForm} onSubmit={handleSubmit((fields) => { setServerError(''); mutation.mutate(fields) })} noValidate>
        {searchParams.get('registered') === '1' && (
          <p className={styles.successBanner}>확인 메일을 보냈습니다. 이메일 인증 후 로그인해 주세요.</p>
        )}
        {searchParams.get('verified') === '1' && (
          <p className={styles.successBanner}>이메일 인증이 완료되었습니다. 로그인해 주세요.</p>
        )}
        <label className={styles.field}>
          <span>이메일</span>
          <input type="email" autoComplete="email" {...register('email', {
            required: '이메일을 입력해 주세요.',
            pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: '올바른 이메일을 입력해 주세요.' },
          })} />
          {errors.email && <span className={styles.fieldError}>{errors.email.message}</span>}
        </label>
        <PasswordField
          label="비밀번호"
          error={errors.password?.message}
          registration={register('password', { required: '비밀번호를 입력해 주세요.' })}
        />
        <div className={styles.authFormMeta}>
          <label className={styles.checkField}>
            <input type="checkbox" {...register('rememberMe')} />
            <span>로그인 상태 유지</span>
          </label>
          <nav className={styles.authRecoveryLinks} aria-label="계정 복구">
            <NavLink to="/recover/password">비밀번호 찾기</NavLink>
          </nav>
        </div>
        {serverError && <p className={styles.formError} role="alert">{serverError}</p>}
        <button className={styles.submitButton} disabled={mutation.isPending} type="submit">
          <LogIn aria-hidden="true" size={18} />
          {mutation.isPending ? '로그인 중...' : '로그인'}
        </button>
        <p className={styles.authSwitch}>아직 계정이 없나요? <NavLink to={`/join?returnTo=${encodeURIComponent(returnTo)}`}>계정 만들기</NavLink></p>
      </form>
    </div>
  )
}

type RecoveryFields = { email: string }
type ResetFields = { password: string; passwordConfirmation: string }

export function AccountRecoveryPage() {
  const [sent, setSent] = useState(false)
  const [resetComplete, setResetComplete] = useState(false)
  const [serverError, setServerError] = useState('')
  const recoveryToken = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('access_token')
  const recoveryForm = useForm<RecoveryFields>()
  const resetForm = useForm<ResetFields>({ mode: 'onChange', reValidateMode: 'onChange' })
  const recoveryMutation = useMutation({
    mutationFn: requestPasswordRecovery,
    onSuccess: () => setSent(true),
    onError: (error) => setServerError(error instanceof ApiError ? error.message : '복구 메일을 보내지 못했습니다.'),
  })
  const resetMutation = useMutation({
    mutationFn: resetPassword,
    onSuccess: () => {
      window.history.replaceState(null, '', window.location.pathname)
      setResetComplete(true)
    },
    onError: (error) => setServerError(error instanceof ApiError ? error.message : '비밀번호를 변경하지 못했습니다.'),
  })

  return (
    <div className={styles.authPage}>
      <div className={styles.authHeading}>
        <span className={styles.authIcon}><KeyRound aria-hidden="true" /></span>
        <p className={styles.eyebrow}>ACCOUNT RECOVERY</p>
        <h1>비밀번호를 다시 설정해요.</h1>
      </div>
      {recoveryToken && !resetComplete ? (
        <form className={styles.authForm} onSubmit={resetForm.handleSubmit(({ password }) => {
          setServerError('')
          resetMutation.mutate({ accessToken: recoveryToken, password })
        })} noValidate>
          <p>새로 사용할 비밀번호를 입력해 주세요.</p>
          <PasswordField
            label="새 비밀번호"
            showGuidelines
            error={resetForm.formState.errors.password?.message}
            registration={resetForm.register('password', {
              required: '비밀번호를 입력해 주세요.',
              validate: (value) => isValidPassword(value) || '8~16자의 영문자와 숫자를 포함하고 공백 없이 입력해 주세요.',
            })}
          />
          <PasswordField
            label="새 비밀번호 확인"
            error={resetForm.formState.errors.passwordConfirmation?.message}
            registration={resetForm.register('passwordConfirmation', {
              required: '비밀번호를 한 번 더 입력해 주세요.',
              validate: (value) => value === resetForm.getValues('password') || '비밀번호가 일치하지 않습니다.',
            })}
          />
          {serverError && <p className={styles.formError} role="alert">{serverError}</p>}
          <button className={styles.submitButton} type="submit" disabled={resetMutation.isPending}>
            <KeyRound aria-hidden="true" size={18} />
            {resetMutation.isPending ? '변경 중...' : '비밀번호 변경'}
          </button>
        </form>
      ) : (
        <form className={styles.authForm} onSubmit={recoveryForm.handleSubmit((fields) => {
          setServerError('')
          recoveryMutation.mutate(fields)
        })} noValidate>
          <p>{resetComplete ? '비밀번호가 변경되었습니다.' : '가입한 이메일로 비밀번호 재설정 링크를 보내드립니다.'}</p>
          {!resetComplete && (
            <label className={styles.field}>
              <span>이메일</span>
              <input type="email" autoComplete="email" {...recoveryForm.register('email', {
                required: '이메일을 입력해 주세요.',
                pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: '올바른 이메일을 입력해 주세요.' },
              })} />
              {recoveryForm.formState.errors.email && <span className={styles.fieldError}>{recoveryForm.formState.errors.email.message}</span>}
            </label>
          )}
          {sent && <p className={styles.recoveryStatus}>메일을 보냈습니다. 받은 메일함을 확인해 주세요.</p>}
          {serverError && <p className={styles.formError} role="alert">{serverError}</p>}
          {!resetComplete && (
            <button className={styles.submitButton} type="submit" disabled={recoveryMutation.isPending || sent}>
              <KeyRound aria-hidden="true" size={18} />
              {recoveryMutation.isPending ? '메일 보내는 중...' : sent ? '메일 전송 완료' : '재설정 메일 보내기'}
            </button>
          )}
          <NavLink className={styles.authSwitch} to="/login">로그인으로 돌아가기</NavLink>
        </form>
      )}
    </div>
  )
}

type JoinFields = {
  password: string
  passwordConfirmation: string
  email: string
}

export function JoinPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const returnTo = safeReturnTo(searchParams.get('returnTo'))
  const [serverError, setServerError] = useState('')
  const { data: account } = useAccount()
  const { register, handleSubmit, getValues, setError, formState: { errors } } = useForm<JoinFields>({
    mode: 'onChange',
    reValidateMode: 'onChange',
  })
  const mutation = useMutation({
    mutationFn: signUp,
    onSuccess: () => navigate(`/login?registered=1&returnTo=${encodeURIComponent(returnTo)}`, { replace: true }),
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'EMAIL_TAKEN') {
        setError('email', { type: 'server', message: '이미 존재하는 이메일입니다.' }, { shouldFocus: true })
        return
      }
      setServerError(error instanceof ApiError ? error.message : '계정을 만들지 못했습니다.')
    },
  })

  if (account) return <Navigate to={returnTo} replace />

  return (
    <div className={styles.authPage}>
      <AuthHeading mode="join" />
      <form className={styles.authForm} onSubmit={handleSubmit(({ password, email }) => {
        setServerError('')
        mutation.mutate({ password, email })
      })} noValidate>
        <label className={styles.field}>
          <span>이메일</span>
          <input type="email" autoComplete="email" {...register('email', {
            required: '이메일을 입력해 주세요.',
            pattern: { value: EMAIL_PATTERN, message: '올바른 이메일 형식이 아닙니다.' },
          })} />
          {errors.email && <span className={styles.fieldError}>{errors.email.message}</span>}
        </label>
        <PasswordField
          label="비밀번호"
          showGuidelines
          error={errors.password?.message}
          registration={register('password', {
            required: '비밀번호를 입력해 주세요.',
            validate: (value) => isValidPassword(value) || '8~16자의 영문자와 숫자를 포함하고 공백 없이 입력해 주세요.',
          })}
        />
        <PasswordField
          label="비밀번호 확인"
          error={errors.passwordConfirmation?.message}
          registration={register('passwordConfirmation', {
            required: '비밀번호를 한 번 더 입력해 주세요.',
            validate: (value) => value === getValues('password') || '비밀번호가 일치하지 않습니다.',
          })}
        />
        <div className={styles.privacyNote}>
          <LockKeyhole aria-hidden="true" size={18} />
          <p>이메일은 공개되지 않으며 비밀번호 복구에 사용됩니다.</p>
        </div>
        {serverError && <p className={styles.formError} role="alert">{serverError}</p>}
        <button className={styles.submitButton} disabled={mutation.isPending} type="submit">
          <UserPlus aria-hidden="true" size={18} />
          {mutation.isPending ? '계정 만드는 중...' : '계정 만들기'}
        </button>
        <p className={styles.authSwitch}>이미 계정이 있나요? <NavLink to={`/login?returnTo=${encodeURIComponent(returnTo)}`}>로그인</NavLink></p>
      </form>
    </div>
  )
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'long' }).format(new Date(value))
}

export function AccountPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [deletionOpen, setDeletionOpen] = useState(false)
  const { data, isPending } = useAccount()
  const deletionForm = useForm<{ password: string }>()
  const logoutMutation = useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.setQueryData<AccountResponse | null>(accountQueryKey, null)
      navigate('/', { replace: true })
    },
  })
  const deletionMutation = useMutation({
    mutationFn: ({ password }: { password: string }) => deleteAccount(password),
    onSuccess: () => {
      queryClient.clear()
      navigate('/', { replace: true })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === 'INVALID_CREDENTIALS') {
        deletionForm.setError('password', { message: '현재 비밀번호를 확인해 주세요.' }, { shouldFocus: true })
        return
      }
      deletionForm.setError('root', {
        message: error instanceof ApiError ? error.message : '회원 탈퇴를 처리하지 못했습니다.',
      })
    },
  })

  if (isPending) return <div className={styles.accountLoading}>계정 정보를 불러오는 중...</div>
  if (!data) return <Navigate to={`/login?returnTo=${encodeURIComponent(location.pathname)}`} replace />

  const { account, quota } = data
  const returnTo = safeReturnTo(searchParams.get('returnTo'))
  return (
    <div className={styles.accountPage}>
      {searchParams.get('joined') === '1' && (
        <div className={styles.successBanner}>
          <span>계정이 만들어졌습니다. 공개 닉네임을 확인해 보세요.</span>
          <NavLink to={returnTo}>계속하기</NavLink>
        </div>
      )}
      <header className={styles.accountHeader}>
        <p className={styles.eyebrow}>MY ACCOUNT</p>
        <h1>{account.publicNickname}</h1>
      </header>
      <section className={styles.accountDetails} aria-label="계정 정보">
        <div><span>이메일</span><strong>{account.email}</strong></div>
        <div><span>가입일</span><strong>{formatDate(account.createdAt)}</strong></div>
      </section>
      <section className={styles.quotaBand} aria-label="오늘의 추천권">
        <div><span>오늘의 추천</span><strong>{quota.used}/{quota.limit}</strong></div>
        <p>남은 추천권 {quota.remaining}회</p>
      </section>
      {account.admin ? (
        <NavLink className={styles.adminAccountLink} to="/admin">
          <ShieldCheck aria-hidden="true" size={19} />
          <span><strong>신고 관리</strong><small>검토가 필요한 신고 내역을 확인합니다.</small></span>
        </NavLink>
      ) : null}
      <div className={styles.accountActions}>
        <button className={styles.logoutButton} type="button" onClick={() => logoutMutation.mutate()} disabled={logoutMutation.isPending}>
          <LogIn aria-hidden="true" size={17} /> {logoutMutation.isPending ? '로그아웃 중...' : '로그아웃'}
        </button>
        <button className={styles.withdrawButton} type="button" onClick={() => setDeletionOpen(true)}>
          회원 탈퇴
        </button>
      </div>
      {deletionOpen && (
        <div className={styles.dialogBackdrop} role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget && !deletionMutation.isPending) setDeletionOpen(false)
        }}>
          <section className={styles.accountDialog} role="dialog" aria-modal="true" aria-labelledby="account-deletion-title">
            <button
              className={styles.dialogCloseButton}
              type="button"
              aria-label="닫기"
              disabled={deletionMutation.isPending}
              onClick={() => setDeletionOpen(false)}
            >
              <X aria-hidden="true" size={19} />
            </button>
            <Trash2 className={styles.dialogIcon} aria-hidden="true" size={22} />
            <h2 id="account-deletion-title">회원 탈퇴</h2>
            <p>계정과 추천·투표 기록이 모두 삭제되며 되돌릴 수 없습니다.</p>
            <form onSubmit={deletionForm.handleSubmit((fields) => deletionMutation.mutate(fields))} noValidate>
              <PasswordField
                label="현재 비밀번호"
                error={deletionForm.formState.errors.password?.message}
                registration={deletionForm.register('password', { required: '현재 비밀번호를 입력해 주세요.' })}
              />
              {deletionForm.formState.errors.root?.message && (
                <p className={styles.formError} role="alert">{deletionForm.formState.errors.root.message}</p>
              )}
              <div className={styles.dialogActions}>
                <button type="button" onClick={() => setDeletionOpen(false)} disabled={deletionMutation.isPending}>취소</button>
                <button className={styles.dangerButton} type="submit" disabled={deletionMutation.isPending}>
                  {deletionMutation.isPending ? '탈퇴 처리 중...' : '탈퇴하기'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  )
}
