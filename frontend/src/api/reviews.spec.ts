import { beforeEach, describe, expect, it, vi } from 'vitest'
import http from './http'
import {
  getNextDueReview,
  getTodayMasteredSpotCheck,
  reactivateQuestion,
  submitMasteredSpotCheckEvaluation,
  submitReviewEvaluation,
} from './reviews'
import type {
  DueReviewResponse,
  MasteredSpotCheckResponse,
  ReviewActionResponse,
  ReviewRating,
} from '../types/review'

vi.mock('./http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

const mockedGet = vi.mocked(http.get)
const mockedPost = vi.mocked(http.post)

const dueResponse: DueReviewResponse = {
  dueCount: 0,
  question: null,
}

const spotCheckResponse: MasteredSpotCheckResponse = {
  completedToday: false,
  eligibleCount: 1,
  cooldownDays: 30,
  question: { id: 77, questionText: '抽查题', imagePath: null, subject: '数学' },
}

const actionResponse: ReviewActionResponse = {
  questionId: 42,
  eventType: 'EVALUATION',
  rating: 'FUZZY',
  occurredAt: '2026-09-04T02:00:00Z',
  reviewStatus: 'ACTIVE',
  nextReviewDate: '2026-09-07',
  consecutiveProficientCount: 0,
  lastReviewedAt: '2026-09-04T02:00:00Z',
}

describe('review API', () => {
  beforeEach(() => {
    mockedGet.mockReset()
    mockedPost.mockReset()
  })

  it('gets the all-subject queue without sending an empty subject', async () => {
    mockedGet.mockResolvedValue({ data: dueResponse } as never)

    await expect(getNextDueReview()).resolves.toEqual(dueResponse)
    expect(mockedGet).toHaveBeenCalledWith('/reviews/due/next', {
      params: { subject: undefined },
    })
  })

  it('sends the selected subject when getting the queue', async () => {
    mockedGet.mockResolvedValue({ data: dueResponse } as never)

    await getNextDueReview('数学')

    expect(mockedGet).toHaveBeenCalledWith('/reviews/due/next', {
      params: { subject: '数学' },
    })
  })

  it('gets the mastered spot check with the selected subject', async () => {
    mockedGet.mockResolvedValue({ data: spotCheckResponse } as never)

    await expect(getTodayMasteredSpotCheck('数学')).resolves.toEqual(
      spotCheckResponse,
    )
    expect(mockedGet).toHaveBeenCalledWith('/reviews/mastered/spot-check', {
      params: { subject: '数学' },
    })
  })

  it.each<ReviewRating>([
    'NOT_KNOWN',
    'FUZZY',
    'BASICALLY_MASTERED',
    'PROFICIENT',
  ])('submits only the %s rating to the question evaluation path', async (rating) => {
    mockedPost.mockResolvedValue({
      data: { ...actionResponse, rating },
    } as never)

    await submitReviewEvaluation(42, rating)

    expect(mockedPost).toHaveBeenCalledWith('/reviews/42/evaluations', { rating })
  })

  it('submits a mastered spot-check rating with its review scope', async () => {
    const response: ReviewActionResponse = {
      ...actionResponse,
      eventType: 'SPOT_CHECK',
      rating: 'PROFICIENT',
      reviewStatus: 'MASTERED',
      nextReviewDate: null,
      consecutiveProficientCount: 2,
    }
    mockedPost.mockResolvedValue({ data: response } as never)

    await expect(
      submitMasteredSpotCheckEvaluation(77, 'PROFICIENT', '数学'),
    ).resolves.toEqual(response)
    expect(mockedPost).toHaveBeenCalledWith(
      '/reviews/77/spot-check-evaluations',
      { rating: 'PROFICIENT' },
      { params: { subject: '数学' } },
    )
  })

  it('reactivates a question without a fabricated request body', async () => {
    const response: ReviewActionResponse = {
      ...actionResponse,
      eventType: 'REACTIVATION',
      rating: null,
      nextReviewDate: '2026-09-04',
    }
    mockedPost.mockResolvedValue({ data: response } as never)

    await expect(reactivateQuestion(42)).resolves.toEqual(response)
    expect(mockedPost).toHaveBeenCalledWith('/reviews/42/reactivate')
  })
})
