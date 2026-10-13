export interface RagConfiguration { generationEnabled: boolean; demo: boolean; model: string; embeddingEnabled: boolean; embeddingModel: string; topK: number; callsToday: number; dailyCallLimit: number; maxManualRetries: number }
export interface RagSession { id: string; title: string; createdAt: string; updatedAt: string }
export interface RagSource { reference: string; questionId: number; chunkNumber: number; questionPreview: string; subject: string; knowledgePoints: string[]; excerpt: string; score: number; method: string; documentHash: string; updatedAt: string; available: boolean; changed: boolean }
export interface RagRequest { id: string; sessionId: string | null; kind: 'CHAT' | 'INDEX'; question: string; rootId: number | null; reviewStatus: string | null; focusQuestionId: number | null; followUp: boolean; status: string; phase: string; retryCount: number; answer: string | null; answerType: string | null; citations: string[]; sources: RagSource[]; warnings: string[]; retrievalMethod: string | null; errorCode: string | null; errorMessage: string | null; createdAt: string; updatedAt: string; completedAt: string | null }
export interface RagPage<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface RagAsk { question: string; rootId?: number; reviewStatus?: string; focusQuestionId?: number; followUp: boolean }
export interface RagIndexStatus { questionCount: number; indexedQuestionCount: number; staleQuestionCount: number; chunkCount: number; embeddedChunkCount: number; embeddingEnabled: boolean; embeddingModel: string; maxQuestions: number; maxChunks: number }
export const ragStatus: Record<string, string> = { QUEUED: '等待处理', RUNNING: '正在处理', SUCCEEDED: '已完成', FAILED: '处理失败', CANCELLED: '已取消' }
export const ragPhase: Record<string, string> = { QUEUED: '等待处理', INDEXING: '更新索引', EMBEDDING: '向量检索', RETRIEVING: '查找相关错题', GENERATING: '整理回答', DONE: '已结束' }
export const ragMethods: Record<string, string> = { FOCUS: '当前错题', KEYWORD: '关键词', SEMANTIC: '语义', HYBRID: '混合检索' }
export function isRagActive(request: RagRequest): boolean { return ['QUEUED', 'RUNNING'].includes(request.status) }
