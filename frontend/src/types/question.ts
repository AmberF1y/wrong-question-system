import type { KnowledgePoint } from './knowledge-point'

export type ReviewStatus = 'ACTIVE' | 'MASTERED'

export interface QuestionFormPayload {
  questionText: string
  wrongAnswer: string
  correctAnswer: string
  analysis: string
  errorReason: string
  knowledgePointIds: number[]
}

export interface QuestionSummary {
  id: number
  questionText: string
  subject: string
  knowledgePoints: KnowledgePoint[]
  createdTime: string
  updatedTime: string
  reviewStatus: ReviewStatus
  nextReviewDate: string | null
  consecutiveProficientCount: number
  lastReviewedAt: string | null
}

export interface QuestionDetail extends QuestionSummary {
  wrongAnswer: string
  correctAnswer: string
  analysis: string
  errorReason: string
  imagePath: string | null
}

export interface QuestionPageResponse {
  items: QuestionSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface QuestionPageQuery {
  page: number
  size: number
  subject?: string
  reviewStatus?: ReviewStatus
}
