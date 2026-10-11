import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { applyAiDraft, getAiCalls, getAiConfiguration, getAiTask, retryAiTask } from '../api/ai'
import { getKnowledgePointTree } from '../api/knowledge-points'
import type { AiTask } from '../types/ai'
import type { QuestionFormPayload } from '../types/question'
import AiTaskDetailView from './AiTaskDetailView.vue'
const mocks = vi.hoisted(() => ({ push: vi.fn(), success: vi.fn(), error: vi.fn(), route: { params: { id: '12345678-1234-1234-1234-123456789abc' } } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }), useRoute: () => mocks.route }))
vi.mock('element-plus', async () => ({ ...await vi.importActual<typeof import('element-plus')>('element-plus'), ElMessage: { success: mocks.success, error: mocks.error } }))
vi.mock('../api/knowledge-points', () => ({ getKnowledgePointTree: vi.fn() }))
vi.mock('../api/ai', () => ({
  applyAiDraft: vi.fn(), getAiTask: vi.fn(), getAiCalls: vi.fn(), getAiConfiguration: vi.fn(), retryAiTask: vi.fn(), cancelAiTask: vi.fn(),
  aiImageUrl: (id: string) => `/api/ai/tasks/${id}/image`,
}))
const id = mocks.route.params.id
const completed: AiTask = {
  id, status: 'SUCCEEDED', fileName: '原图.png', contentType: 'image/png', imageSize: 12, rootId: 1, provider: 'compatible', model: 'vision',
  attemptCount: 1, retryCount: 0, draft: { questionText: '原图题目 $x^2$', correctAnswer: '', analysis: '', knowledgePointIds: [2], warnings: ['请核对原图'] },
  errorCode: null, errorMessage: null, createdAt: '2026-10-11T00:00:00Z', updatedAt: '2026-10-11T00:00:01Z', availableAt: '2026-10-11T00:00:00Z', questionId: null, appliedAt: null,
}
const payload: QuestionFormPayload = { questionText: '人工核对题目', correctAnswer: '答案', analysis: '解析', wrongAnswer: '我的答案', errorReason: '原因', knowledgePointIds: [2] }
const formStub = { name: 'QuestionForm', props: ['initialValue', 'submitting', 'fieldErrors', 'showImageField'], emits: ['submit', 'cancel', 'change'], template: '<div data-testid="draft-form" />' }
function mountView() { return mount(AiTaskDetailView, { global: { plugins: [createPinia(), ElementPlus], stubs: { QuestionForm: formStub, QuestionImageDisplay: { props: ['src'], template: '<img :src="src" />' } } } }) }
describe('AI draft confirmation', () => {
  beforeEach(() => {
    vi.clearAllMocks(); localStorage.clear()
    vi.mocked(getAiTask).mockResolvedValue(completed)
    vi.mocked(getAiCalls).mockResolvedValue([])
    vi.mocked(getKnowledgePointTree).mockResolvedValue([])
    vi.mocked(getAiConfiguration).mockResolvedValue({ enabled: true, demo: false, model: 'vision', dailyCallLimit: 20, callsToday: 1, concurrency: 2, maxAttempts: 3, maxManualRetries: 2 })
  })
  it('removes the editable form when refresh confirms the task was deleted elsewhere', async () => {
    const wrapper = mountView(); await flushPromises()
    vi.mocked(getAiTask).mockRejectedValueOnce({ isAxiosError: true, response: { status: 404, data: { code: 'AI_TASK_NOT_FOUND', message: '识别任务不存在' } } })
    await wrapper.findAll('button').find(button => button.text() === '刷新任务状态')!.trigger('click'); await flushPromises()
    expect(wrapper.find('[data-testid="draft-form"]').exists()).toBe(false); expect(wrapper.text()).toContain('识别任务不存在')
    wrapper.unmount()
  })
  it('shows a draft without automatically saving and leaves personal answers blank', async () => {
    const wrapper = mountView(); await flushPromises()
    const form = wrapper.getComponent({ name: 'QuestionForm' })
    expect(form.props('initialValue')).toEqual({ ...payload, questionText: completed.draft!.questionText, correctAnswer: '', analysis: '', wrongAnswer: '', errorReason: '' })
    expect(form.props('showImageField')).toBe(false)
    expect(wrapper.get('img').attributes('src')).toBe(`/api/ai/tasks/${id}/image`)
    expect(applyAiDraft).not.toHaveBeenCalled(); wrapper.unmount()
  })
  it('persists edited fields and restores them after navigating back to the draft', async () => {
    const wrapper = mountView(); await flushPromises()
    wrapper.getComponent({ name: 'QuestionForm' }).vm.$emit('change', payload); await flushPromises(); wrapper.unmount()
    const restored = mountView(); await flushPromises()
    expect(restored.getComponent({ name: 'QuestionForm' }).props('initialValue')).toEqual(payload); restored.unmount()
  })
  it('does not resend a save after a lost response and synchronizes to the saved question', async () => {
    vi.mocked(applyAiDraft).mockRejectedValueOnce({ isAxiosError: true, response: { status: 500 } })
    const wrapper = mountView(); await flushPromises()
    wrapper.getComponent({ name: 'QuestionForm' }).vm.$emit('submit', payload); await flushPromises()
    expect(wrapper.find('[data-testid="apply-uncertain"]').exists()).toBe(true)
    expect(wrapper.getComponent({ name: 'QuestionForm' }).props('submitting')).toBe(true)
    wrapper.getComponent({ name: 'QuestionForm' }).vm.$emit('submit', payload); await flushPromises()
    expect(applyAiDraft).toHaveBeenCalledTimes(1)
    vi.mocked(getAiTask).mockResolvedValueOnce({ ...completed, status: 'APPLIED', questionId: 42 })
    await wrapper.get('[data-testid="sync-apply"]').trigger('click'); await flushPromises()
    expect(mocks.push).toHaveBeenCalledWith('/questions/42'); expect(applyAiDraft).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem(`wrongquestion.ai.apply-pending.${id}`)).toBeNull(); wrapper.unmount()
  })
  it('permits a new save only after explicit synchronization confirms the draft remains unsaved', async () => {
    vi.mocked(applyAiDraft).mockRejectedValueOnce({ isAxiosError: true })
    const wrapper = mountView(); await flushPromises()
    wrapper.getComponent({ name: 'QuestionForm' }).vm.$emit('submit', payload); await flushPromises()
    await wrapper.get('[data-testid="sync-apply"]').trigger('click'); await flushPromises()
    expect(wrapper.find('[data-testid="apply-uncertain"]').exists()).toBe(false)
    expect(wrapper.getComponent({ name: 'QuestionForm' }).props('submitting')).toBe(false)
    expect(applyAiDraft).toHaveBeenCalledTimes(1); wrapper.unmount()
  })
  it('keeps a saved question accessible without offering another confirmation form', async () => {
    vi.mocked(getAiTask).mockResolvedValue({ ...completed, status: 'APPLIED', questionId: 42 })
    const wrapper = mountView(); await flushPromises()
    expect(wrapper.find('[data-testid="applied-task"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="draft-form"]').exists()).toBe(false); wrapper.unmount()
  })
  it('keeps the draft usable when loading call records fails', async () => {
    vi.mocked(getAiCalls).mockRejectedValue(new Error('调用记录暂不可用'))
    const wrapper = mountView(); await flushPromises()
    expect(wrapper.find('[data-testid="draft-form"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('调用记录暂不可用'); wrapper.unmount()
  })
  it('sends the observed retry count and waits for task state instead of generating a second task', async () => {
    vi.mocked(getAiTask).mockResolvedValue({ ...completed, status: 'FAILED', draft: null })
    vi.mocked(retryAiTask).mockResolvedValue({ ...completed, status: 'QUEUED', draft: null, retryCount: 1 })
    const wrapper = mountView(); await flushPromises()
    const button = wrapper.findAll('button').find(button => button.text() === '重新识别')!
    await button.trigger('click'); await flushPromises()
    expect(retryAiTask).toHaveBeenCalledWith(id, 0); wrapper.unmount()
  })
})
