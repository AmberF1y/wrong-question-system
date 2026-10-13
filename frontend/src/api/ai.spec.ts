import { beforeEach, describe, expect, it, vi } from 'vitest'
import http from './http'
import { applyAiDraft, createAiTask, retryAiTask } from './ai'
vi.mock('./http', () => ({ default: { post: vi.fn() } }))
describe('AI API contracts', () => {
  beforeEach(() => vi.mocked(http.post).mockReset())
  it('sends multipart input with an explicit stable idempotency key and subject scope', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: { id: 'task' } })
    const file = new File(['image'], '题目.png', { type: 'image/png' })
    await createAiTask('stable-id', file, 12)
    const [path, body, config] = vi.mocked(http.post).mock.calls[0]!
    expect(path).toBe('/ai/tasks')
    expect((body as FormData).get('file')).toBe(file)
    expect((body as FormData).get('rootId')).toBe('12')
    expect(config?.headers).toEqual({ 'Idempotency-Key': 'stable-id' })
  })
  it('carries the observed retry generation to protect against stale duplicate retries', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: {} })
    await retryAiTask('task', 1)
    expect(http.post).toHaveBeenCalledWith('/ai/tasks/task/retry', null, { params: { expectedRetryCount: 1 } })
  })
  it('saves confirmed fields through the task apply endpoint instead of separately uploading the image', async () => {
    vi.mocked(http.post).mockResolvedValue({ data: { id: 42 } })
    const payload = { questionText: '核对题目', correctAnswer: '答案', analysis: '解析', wrongAnswer: '错答', errorReason: '原因', knowledgePointIds: [2] }
    expect((await applyAiDraft('task', payload)).id).toBe(42)
    expect(http.post).toHaveBeenCalledWith('/ai/tasks/task/apply', payload, { timeout: 30_000 })
  })
})
