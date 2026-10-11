import type { QuestionFormPayload } from './question'

export type AiTaskStatus = 'QUEUED' | 'RUNNING' | 'RETRY_WAIT' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'APPLIED'
export interface AiDraft {
  questionText: string
  correctAnswer: string
  analysis: string
  knowledgePointIds: number[]
  warnings: string[]
}
export interface AiTask {
  id: string
  status: AiTaskStatus
  fileName: string
  contentType: string
  imageSize: number
  rootId: number | null
  provider: string
  model: string
  attemptCount: number
  retryCount: number
  draft: AiDraft | null
  errorCode: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  availableAt: string
  questionId: number | null
  appliedAt: string | null
}
export interface AiTaskPage { items: AiTask[]; page: number; size: number; totalElements: number; totalPages: number }
export interface AiConfiguration {
  enabled: boolean; demo: boolean; model: string; dailyCallLimit: number; callsToday: number
  concurrency: number; maxAttempts: number; maxManualRetries: number
}
export interface AiCall {
  id: number; attempt: number; provider: string; model: string; status: string; errorCode: string | null
  inputTokens: number | null; outputTokens: number | null; durationMs: number | null
  estimatedCost: number | null; createdAt: string; completedAt: string | null
}
export interface AiStatistics {
  taskCount: number; activeCount: number; succeededCount: number; failedCount: number
  callCount: number; failedCallCount: number; inputTokens: number | null; outputTokens: number | null
  averageDurationMs: number | null; estimatedCost: number | null; configuration: AiConfiguration
}
export interface AiCallEntry { taskId: string; fileName: string; call: AiCall }
export interface AiCallPage { items: AiCallEntry[]; page: number; size: number; totalElements: number; totalPages: number }
export const aiCallStatusLabels: Record<string, string> = {
  RUNNING: '正在执行', SUCCEEDED: '成功', FAILED: '失败', INTERRUPTED: '处理已中断', CANCELLED: '结果已取消',
}
export const aiStatusLabels: Record<AiTaskStatus, string> = {
  QUEUED: '等待识别', RUNNING: '正在识别', RETRY_WAIT: '等待自动重试', SUCCEEDED: '草稿待核对',
  FAILED: '识别失败', CANCELLED: '已取消', APPLIED: '已保存错题',
}
export function isAiTaskActive(task: AiTask): boolean {
  return ['QUEUED', 'RUNNING', 'RETRY_WAIT'].includes(task.status)
}
export function draftToForm(draft: AiDraft): QuestionFormPayload {
  return { questionText: draft.questionText, correctAnswer: draft.correctAnswer, analysis: draft.analysis,
    wrongAnswer: '', errorReason: '', knowledgePointIds: [...draft.knowledgePointIds] }
}
