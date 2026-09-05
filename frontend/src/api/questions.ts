import http from './http'
import type { MessageResponse } from '../types/api'
import type {
  QuestionDetail,
  QuestionFormPayload,
  QuestionImageResponse,
  QuestionPageQuery,
  QuestionPageResponse,
} from '../types/question'

export async function createQuestion(
  request: QuestionFormPayload,
): Promise<QuestionDetail> {
  const response = await http.post<QuestionDetail>('/questions', request)
  return response.data
}

export async function getQuestion(id: number): Promise<QuestionDetail> {
  const response = await http.get<QuestionDetail>(`/questions/${id}`)
  return response.data
}

export async function getQuestions(
  query: QuestionPageQuery,
): Promise<QuestionPageResponse> {
  const response = await http.get<QuestionPageResponse>('/questions', {
    params: {
      page: query.page,
      size: query.size,
      subject: query.subject || undefined,
      reviewStatus: query.reviewStatus || undefined,
    },
  })
  return response.data
}

export async function updateQuestion(
  id: number,
  request: QuestionFormPayload,
): Promise<QuestionDetail> {
  const response = await http.put<QuestionDetail>(`/questions/${id}`, request)
  return response.data
}

export async function deleteQuestion(id: number): Promise<MessageResponse> {
  const response = await http.delete<MessageResponse>(`/questions/${id}`)
  return response.data
}

export async function uploadQuestionImage(
  id: number,
  file: File,
): Promise<QuestionImageResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.put<QuestionImageResponse>(
    `/questions/${id}/image`,
    formData,
  )
  return response.data
}

export async function removeQuestionImage(id: number): Promise<MessageResponse> {
  const response = await http.delete<MessageResponse>(`/questions/${id}/image`)
  return response.data
}

export function getQuestionImageUrl(id: number, cacheKey?: string): string {
  const query = cacheKey ? `?v=${encodeURIComponent(cacheKey)}` : ''
  return `/api/questions/${id}/image${query}`
}
