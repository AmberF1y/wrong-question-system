import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LearningAssistantView from './LearningAssistantView.vue'
import * as api from '../api/rag'
import { getKnowledgePointTree } from '../api/knowledge-points'
import type { RagRequest, RagSource } from '../types/rag'
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn(), query: {} as Record<string, string> }))
vi.mock('vue-router', () => ({ useRouter: () => router, useRoute: () => ({ query: router.query }) }))
vi.mock('../api/knowledge-points', () => ({ getKnowledgePointTree: vi.fn() }))
vi.mock('../api/rag', () => ({ getRagConfiguration: vi.fn(), getRagIndex: vi.fn(), createRagSession: vi.fn(), getRagSessions: vi.fn(), deleteRagSession: vi.fn(), getRagHistory: vi.fn(), askRag: vi.fn(), buildRagIndex: vi.fn(), getRagRequest: vi.fn(), cancelRagRequest: vi.fn(), retryRagRequest: vi.fn() }))
const session = '12345678-1234-1234-1234-123456789abc'
const source: RagSource = { reference: 'S1', questionId: 42, chunkNumber: 0, questionPreview: '链表反转', subject: '数据结构', knowledgePoints: ['链表'], excerpt: '先保存 next，再修改指针。$O(n)$', score: .02, method: 'KEYWORD', documentHash: 'hash', updatedAt: '2026-10-13T00:00:00Z', available: true, changed: false }
const request: RagRequest = { id: session, sessionId: session, kind: 'CHAT', question: '链表反转', rootId: null, reviewStatus: null, focusQuestionId: null, followUp: false, status: 'SUCCEEDED', phase: 'DONE', retryCount: 0, answer: '已找到相关错题', answerType: 'RETRIEVAL_ONLY', citations: [], sources: [source], warnings: ['尚未配置回答模型'], retrievalMethod: 'KEYWORD', errorCode: null, errorMessage: null, createdAt: source.updatedAt, updatedAt: source.updatedAt, completedAt: source.updatedAt }
const empty = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
function view() { return mount(LearningAssistantView, { global: { plugins: [ElementPlus] } }) }
function unavailable() { return { isAxiosError: true, response: { status: 500 } } }
describe('personal question RAG assistant', () => {
  beforeEach(() => {
    vi.resetAllMocks(); localStorage.clear(); router.query = {}
    vi.mocked(getKnowledgePointTree).mockResolvedValue([])
    vi.mocked(api.getRagConfiguration).mockResolvedValue({ generationEnabled: false, demo: false, model: 'model', embeddingEnabled: false, embeddingModel: '', topK: 6, callsToday: 0, dailyCallLimit: 20, maxManualRetries: 2 })
    vi.mocked(api.getRagIndex).mockResolvedValue({ questionCount: 1, indexedQuestionCount: 0, staleQuestionCount: 1, chunkCount: 0, embeddedChunkCount: 0, embeddingEnabled: false, embeddingModel: '', maxQuestions: 5000, maxChunks: 10000 })
    vi.mocked(api.getRagSessions).mockResolvedValue(empty)
    vi.mocked(api.getRagHistory).mockResolvedValue(empty)
    vi.mocked(api.createRagSession).mockResolvedValue({ id: session, title: '新对话', createdAt: source.updatedAt, updatedAt: source.updatedAt })
    vi.mocked(api.askRag).mockResolvedValue(request)
    vi.mocked(api.getRagRequest).mockResolvedValue(request)
  })
  it('allows real local retrieval while clearly identifying unconfigured generation', async () => {
    const wrapper = view(); await flushPromises()
    expect(wrapper.text()).toContain('尚未配置回答模型')
    await wrapper.get('[data-testid="rag-question"]').setValue('链表反转')
    expect(wrapper.get('[data-testid="rag-ask"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-testid="rag-ask"]').text()).toBe('检索错题'); wrapper.unmount()
  })
  it('uses a stable request id and supplies the requested focus without silently enabling follow-up', async () => {
    router.query = { questionId: '42' }
    const wrapper = view(); await flushPromises(); await wrapper.get('[data-testid="rag-question"]').setValue('解释链表反转')
    await wrapper.get('[data-testid="rag-ask"]').trigger('click'); await flushPromises()
    expect(api.askRag).toHaveBeenCalledWith(expect.stringMatching(/^[0-9a-f-]{36}$/), session, expect.objectContaining({ question: '解释链表反转', focusQuestionId: 42, followUp: false }))
    expect(localStorage.getItem('wrongquestion.rag.pending-request')).toBeNull(); wrapper.unmount()
  })
  it('preserves uncertain submissions and synchronizes without automatically resending', async () => {
    vi.mocked(api.askRag).mockRejectedValueOnce(unavailable())
    const wrapper = view(); await flushPromises(); await wrapper.get('[data-testid="rag-question"]').setValue('链表反转'); await wrapper.get('[data-testid="rag-ask"]').trigger('click'); await flushPromises()
    const id = localStorage.getItem('wrongquestion.rag.pending-request')!
    expect(id).toMatch(/^[0-9a-f-]{36}$/); expect(wrapper.find('[data-testid="rag-pending"]').exists()).toBe(true)
    await wrapper.get('[data-testid="rag-pending"] button').trigger('click'); await flushPromises()
    expect(api.getRagRequest).toHaveBeenCalledWith(id); expect(api.askRag).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem('wrongquestion.rag.pending-request')).toBeNull(); wrapper.unmount()
  })
  it('restores a pending id after reload and does not resend when it is missing', async () => {
    localStorage.setItem('wrongquestion.rag.pending-request', session)
    vi.mocked(api.getRagRequest).mockRejectedValue({ isAxiosError: true, response: { status: 404 } })
    const wrapper = view(); await flushPromises()
    expect(wrapper.text()).toContain('不会自动重发'); expect(api.askRag).not.toHaveBeenCalled(); expect(wrapper.get('[data-testid="rag-ask"]').attributes('disabled')).toBeDefined(); wrapper.unmount()
  })
  it('shows source changes and keeps a usable link to the current question', async () => {
    localStorage.setItem('wrongquestion.rag.session', session)
    vi.mocked(api.getRagHistory).mockResolvedValue({ ...empty, items: [{ ...request, sources: [{ ...source, changed: true }] }], totalElements: 1, totalPages: 1 })
    const wrapper = view(); await flushPromises()
    expect(wrapper.text()).toContain('原错题已修改'); expect(wrapper.text()).toContain('先保存 next')
    const button = wrapper.findAll('button').find(b => b.text().includes('查看完整错题'))!; await button.trigger('click'); expect(router.push).toHaveBeenCalledWith('/questions/42'); wrapper.unmount()
  })
  it('keeps deleted source snapshots but removes the link', async () => {
    localStorage.setItem('wrongquestion.rag.session', session)
    vi.mocked(api.getRagHistory).mockResolvedValue({ ...empty, items: [{ ...request, sources: [{ ...source, available: false }] }], totalElements: 1, totalPages: 1 })
    const wrapper = view(); await flushPromises(); expect(wrapper.text()).toContain('原错题已删除'); expect(wrapper.text()).not.toContain('查看完整错题'); wrapper.unmount()
  })
  it('escapes generated HTML and displays known citation references', async () => {
    localStorage.setItem('wrongquestion.rag.session', session)
    vi.mocked(api.getRagHistory).mockResolvedValue({ ...empty, items: [{ ...request, answerType: 'GENERATED', answer: '<img src=x onerror=alert(1)> [S1]', citations: ['S1'] }], totalElements: 1, totalPages: 1 })
    const wrapper = view(); await flushPromises(); expect(wrapper.get('[data-testid="rag-answer"]').text()).toContain('<img src=x'); expect(wrapper.find('[data-testid="rag-answer"] img').exists()).toBe(false); expect(wrapper.text()).toContain('回答引用'); wrapper.unmount()
  })
  it('sends the observed retry count and never retries automatically', async () => {
    const failed = { ...request, status: 'FAILED', answer: null, retryCount: 1, errorMessage: '模型超时' }
    localStorage.setItem('wrongquestion.rag.session', session)
    vi.mocked(api.getRagHistory).mockResolvedValue({ ...empty, items: [failed], totalElements: 1, totalPages: 1 }); vi.mocked(api.retryRagRequest).mockResolvedValue(request)
    const wrapper = view(); await flushPromises(); expect(api.retryRagRequest).not.toHaveBeenCalled()
    await wrapper.findAll('button').find(b => b.text().includes('人工重试'))!.trigger('click'); await flushPromises()
    expect(api.retryRagRequest).toHaveBeenCalledWith(request.id, 1); wrapper.unmount()
  })
  it('restores the chosen scope for new questions after reload', async () => {
    localStorage.setItem('wrongquestion.rag.scope', JSON.stringify({ rootId: 3, reviewStatus: 'MASTERED' }))
    const wrapper = view(); await flushPromises(); await wrapper.get('[data-testid="rag-question"]').setValue('边界条件')
    await wrapper.get('[data-testid="rag-ask"]').trigger('click'); await flushPromises()
    expect(api.getRagIndex).toHaveBeenCalledWith(3)
    expect(api.askRag).toHaveBeenCalledWith(expect.any(String), session, expect.objectContaining({ rootId: 3, reviewStatus: 'MASTERED' })); wrapper.unmount()
  })
  it('reuses the same session id after an uncertain session creation response', async () => {
    vi.mocked(api.createRagSession).mockRejectedValueOnce(unavailable())
    const wrapper = view(); await flushPromises(); await wrapper.get('[data-testid="rag-question"]').setValue('边界条件')
    await wrapper.get('[data-testid="rag-ask"]').trigger('click'); await flushPromises(); expect(api.askRag).not.toHaveBeenCalled()
    const id = localStorage.getItem('wrongquestion.rag.pending-session')!
    await wrapper.get('[data-testid="rag-ask"]').trigger('click'); await flushPromises()
    expect(api.createRagSession).toHaveBeenNthCalledWith(1, id); expect(api.createRagSession).toHaveBeenNthCalledWith(2, id)
    expect(localStorage.getItem('wrongquestion.rag.pending-session')).toBeNull(); wrapper.unmount()
  })
})
