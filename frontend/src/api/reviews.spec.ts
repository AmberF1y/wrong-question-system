import { beforeEach, describe, expect, it, vi } from 'vitest'
import http from './http'
import {
  getNextDueReview,
  reactivateQuestion,
  submitReviewEvaluation,
} from './reviews'
import type {
  DueReviewResponse,
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
