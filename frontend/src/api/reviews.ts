import http from './http'
import type {
  DueReviewResponse,
  ReviewActionResponse,
  ReviewRating,
  SubmitReviewEvaluationRequest,
} from '../types/review'

export async function getNextDueReview(subject?: string): Promise<DueReviewResponse> {
  const response = await http.get<DueReviewResponse>('/reviews/due/next', {
    params: {
      subject: subject || undefined,
    },
  })
  return response.data
}

export async function submitReviewEvaluation(
  questionId: number,
  rating: ReviewRating,
): Promise<ReviewActionResponse> {
  const request: SubmitReviewEvaluationRequest = { rating }
  const response = await http.post<ReviewActionResponse>(
    `/reviews/${questionId}/evaluations`,
    request,
  )
  return response.data
}

export async function reactivateQuestion(
  questionId: number,
): Promise<ReviewActionResponse> {
  const response = await http.post<ReviewActionResponse>(
    `/reviews/${questionId}/reactivate`,
  )
  return response.data
}
