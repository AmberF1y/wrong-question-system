import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime } from './date-time'

describe('date-time formatting', () => {
  it('displays missing dates with a dash', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDateTime(undefined)).toBe('—')
  })

  it('keeps a local date unchanged', () => {
    expect(formatDate('2026-09-05')).toBe('2026-09-05')
  })

  it('keeps an invalid timestamp readable', () => {
    expect(formatDateTime('not-a-date')).toBe('not-a-date')
  })
})
