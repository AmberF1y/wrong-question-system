export type ReviewStatus = 'ACTIVE' | 'MASTERED'

export type ReviewRating =
  | 'NOT_KNOWN'
  | 'FUZZY'
  | 'BASICALLY_MASTERED'
  | 'PROFICIENT'

export type ReviewEventType = 'EVALUATION' | 'REACTIVATION' | 'SPOT_CHECK'

export interface DueQuestion {
  id: number
  questionText: string
  imagePath: string | null
  subject: string
  nextReviewDate: string
}

export interface DueReviewResponse {
  dueCount: number
  question: DueQuestion | null
}

export interface SpotCheckQuestion {
  id: number
  questionText: string
  imagePath: string | null
  subject: string
}

export interface MasteredSpotCheckResponse {
  completedToday: boolean
  eligibleCount: number
  cooldownDays: number
  question: SpotCheckQuestion | null
}

export interface SubmitReviewEvaluationRequest {
  rating: ReviewRating
}

export interface ReviewActionResponse {
  questionId: number
  eventType: ReviewEventType
  rating: ReviewRating | null
  occurredAt: string
  reviewStatus: ReviewStatus
  nextReviewDate: string | null
  consecutiveProficientCount: number
  lastReviewedAt: string
}
