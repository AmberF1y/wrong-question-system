import http from './http'
import type { AiCall, AiCallPage, AiConfiguration, AiStatistics, AiTask, AiTaskPage, AiTaskStatus } from '../types/ai'
import type { QuestionDetail, QuestionFormPayload } from '../types/question'

export async function getAiConfiguration(): Promise<AiConfiguration> {
  return (await http.get<AiConfiguration>('/ai/configuration')).data
}
export async function createAiTask(id: string, file: File, rootId?: number): Promise<AiTask> {
  const body = new FormData()
  body.append('file', file)
  if (rootId !== undefined) body.append('rootId', String(rootId))
  return (await http.post<AiTask>('/ai/tasks', body, { headers: { 'Idempotency-Key': id }, timeout: 30_000 })).data
}
export async function getAiTask(id: string): Promise<AiTask> {
  return (await http.get<AiTask>(`/ai/tasks/${encodeURIComponent(id)}`)).data
}
export async function getAiTasks(page: number, status?: AiTaskStatus): Promise<AiTaskPage> {
  return (await http.get<AiTaskPage>('/ai/tasks', { params: { page, size: 20, status } })).data
}
export async function getAiStatistics(): Promise<AiStatistics> { return (await http.get<AiStatistics>('/ai/statistics')).data }
export async function getAiCalls(id: string): Promise<AiCall[]> { return (await http.get<AiCall[]>(`/ai/tasks/${id}/calls`)).data }
export async function getAllAiCalls(page: number, status?: string): Promise<AiCallPage> {
  return (await http.get<AiCallPage>('/ai/calls', { params: { page, size: 20, status } })).data
}
export async function retryAiTask(id: string, expectedRetryCount: number): Promise<AiTask> {
  return (await http.post<AiTask>(`/ai/tasks/${id}/retry`, null, { params: { expectedRetryCount } })).data
}
export async function cancelAiTask(id: string): Promise<AiTask> { return (await http.post<AiTask>(`/ai/tasks/${id}/cancel`)).data }
export async function deleteAiTask(id: string): Promise<void> { await http.delete(`/ai/tasks/${id}`) }
export async function applyAiDraft(id: string, payload: QuestionFormPayload): Promise<QuestionDetail> {
  return (await http.post<QuestionDetail>(`/ai/tasks/${id}/apply`, payload, { timeout: 30_000 })).data
}
export function aiImageUrl(id: string): string { return `/api/ai/tasks/${encodeURIComponent(id)}/image` }
