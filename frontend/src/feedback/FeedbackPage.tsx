import { useMutation } from '@tanstack/react-query'
import { Check, Send } from 'lucide-react'
import { useState } from 'react'
import { ApiError, submitFeedback, type FeedbackCategory } from '../api/client'
import styles from '../App.module.css'

const CATEGORY_OPTIONS: { value: FeedbackCategory; label: string }[] = [
  { value: 'ERROR', label: '오류' },
  { value: 'UI_USABILITY', label: 'UI / 사용성' },
  { value: 'OTHER', label: '기타' },
]

function feedbackErrorMessage(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : '의견을 보내지 못했습니다. 다시 시도해 주세요.'
}

export function FeedbackPage() {
  const [category, setCategory] = useState<FeedbackCategory | null>(null)
  const [content, setContent] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const feedback = useMutation({
    mutationFn: submitFeedback,
    onSuccess: () => setFormError(null),
  })

  if (feedback.isSuccess) {
    return (
      <section className={styles.feedbackPage}>
        <div className={styles.feedbackComplete}>
          <span><Check aria-hidden="true" size={22} /></span>
          <h1>의견을 남겨주셔서 감사합니다.</h1>
          <button type="button" onClick={() => {
            setCategory(null)
            setContent('')
            feedback.reset()
          }}>다른 의견 보내기</button>
        </div>
      </section>
    )
  }

  return (
    <section className={styles.feedbackPage}>
      <header className={styles.feedbackHeader}>
        <p className={styles.eyebrow}>FEEDBACK</p>
        <h1>TrackPick 의견 보내기</h1>
        <p>서비스를 사용하면서 불편했던 점이나 개선되었으면 하는 점을 자유롭게 알려주세요.</p>
      </header>

      <form className={styles.feedbackForm} onSubmit={(event) => {
        event.preventDefault()
        const normalizedContent = content.trim()
        if (!category) {
          setFormError('의견 분류를 선택해 주세요.')
          return
        }
        if (!normalizedContent) {
          setFormError('의견을 입력해 주세요.')
          return
        }
        setFormError(null)
        feedback.mutate({ category, content: normalizedContent })
      }}>
        <fieldset className={styles.feedbackCategories}>
          <legend>어떤 부분에 대한 의견인가요?</legend>
          <div>
            {CATEGORY_OPTIONS.map((option) => (
              <label key={option.value}>
                <input
                  type="radio"
                  name="feedback-category"
                  value={option.value}
                  checked={category === option.value}
                  onChange={() => { setCategory(option.value); setFormError(null) }}
                />
                <strong>{option.label}</strong>
              </label>
            ))}
          </div>
        </fieldset>

        <label className={styles.feedbackContent} htmlFor="feedback-content">
          <span>의견을 자유롭게 적어주세요.</span>
          <textarea
            id="feedback-content"
            aria-label="의견을 자유롭게 적어주세요."
            value={content}
            maxLength={2000}
            onChange={(event) => { setContent(event.target.value); setFormError(null) }}
          />
          <small>{Array.from(content).length}/2000</small>
        </label>
        <p className={styles.feedbackPrivacyNote}>이름, 이메일 등 개인정보는 적지 말아 주세요.</p>

        {formError || feedback.isError ? (
          <p className={styles.formError} role="alert">
            {formError ?? feedbackErrorMessage(feedback.error)}
          </p>
        ) : null}

        <button className={styles.feedbackSubmit} type="submit" disabled={feedback.isPending}>
          <Send aria-hidden="true" size={17} />
          {feedback.isPending ? '보내는 중...' : '의견 보내기'}
        </button>
      </form>
    </section>
  )
}
