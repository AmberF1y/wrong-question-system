import http from './http'
import type { RagAsk, RagConfiguration, RagIndexStatus, RagPage, RagRequest, RagSession } from '../types/rag'
export async function getRagConfiguration(): Promise<RagConfiguration> { return (await http.get<RagConfiguration>('/rag/configuration')).data }
export async function getRagIndex(rootId?: number): Promise<RagIndexStatus> { return (await http.get<RagIndexStatus>('/rag/index', { params: { rootId } })).data }
export async function createRagSession(id: string): Promise<RagSession> { return (await http.post<RagSession>('/rag/sessions', null, { headers: { 'Idempotency-Key': id } })).data }
export async function getRagSessions(page = 0): Promise<RagPage<RagSession>> { return (await http.get<RagPage<RagSession>>('/rag/sessions', { params: { page, size: 20 } })).data }
export async function deleteRagSession(id: string): Promise<void> { await http.delete(`/rag/sessions/${encodeURIComponent(id)}`) }
export async function getRagHistory(id: string, page = 0): Promise<RagPage<RagRequest>> { return (await http.get<RagPage<RagRequest>>(`/rag/sessions/${encodeURIComponent(id)}/requests`, { params: { page, size: 20 } })).data }
export async function askRag(id: string, session: string, input: RagAsk): Promise<RagRequest> { return (await http.post<RagRequest>(`/rag/sessions/${encodeURIComponent(session)}/requests`, input, { headers: { 'Idempotency-Key': id } })).data }
export async function buildRagIndex(id: string, rootId?: number): Promise<RagRequest> { return (await http.post<RagRequest>('/rag/index', { rootId }, { headers: { 'Idempotency-Key': id } })).data }
export async function getRagRequest(id: string): Promise<RagRequest> { return (await http.get<RagRequest>(`/rag/requests/${encodeURIComponent(id)}`)).data }
export async function cancelRagRequest(id: string): Promise<RagRequest> { return (await http.post<RagRequest>(`/rag/requests/${encodeURIComponent(id)}/cancel`)).data }
export async function retryRagRequest(id: string, expectedRetryCount: number): Promise<RagRequest> { return (await http.post<RagRequest>(`/rag/requests/${encodeURIComponent(id)}/retry`, null, { params: { expectedRetryCount } })).data }
