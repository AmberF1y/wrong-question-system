import { beforeEach, describe, expect, it, vi } from 'vitest'
import http from './http'
import { askRag, buildRagIndex, retryRagRequest } from './rag'
vi.mock('./http', () => ({ default: { post: vi.fn() } }))
describe('RAG request contract', () => {
  beforeEach(() => { vi.resetAllMocks(); vi.mocked(http.post).mockResolvedValue({ data: { id: 'request' } }) })
  it('passes the idempotency key with the question and scope', async () => { const input = { question: '二分查找', rootId: 3, followUp: false }; await askRag('request', 'session', input); expect(http.post).toHaveBeenCalledWith('/rag/sessions/session/requests', input, { headers: { 'Idempotency-Key': 'request' } }) })
  it('uses an independent id for index work and an observed counter for retries', async () => { await buildRagIndex('index', 3); await retryRagRequest('request', 1); expect(http.post).toHaveBeenCalledWith('/rag/index', { rootId: 3 }, { headers: { 'Idempotency-Key': 'index' } }); expect(http.post).toHaveBeenCalledWith('/rag/requests/request/retry', null, { params: { expectedRetryCount: 1 } }) })
})
