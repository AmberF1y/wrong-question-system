import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAiTask, getAiConfiguration, getAiTask } from '../api/ai'
import { getKnowledgePointTree } from '../api/knowledge-points'
import AiIntakeView from './AiIntakeView.vue'
const mocks = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('../api/knowledge-points', () => ({ getKnowledgePointTree: vi.fn() }))
vi.mock('../api/ai', () => ({ createAiTask: vi.fn(), getAiConfiguration: vi.fn(), getAiTask: vi.fn() }))
const configuration = { enabled: true, demo: false, model: 'vision', dailyCallLimit: 20, callsToday: 0, concurrency: 2, maxAttempts: 3, maxManualRetries: 2 }
const id = '12345678-1234-1234-1234-123456789abc'
const imageStub = { name: 'QuestionImageField', props: ['disabled'], emits: ['change'], template: '<div />' }
function mountView() { return mount(AiIntakeView, { global: { plugins: [createPinia(), ElementPlus], stubs: { QuestionImageField: imageStub } } }) }
function unavailable() { return { isAxiosError: true, response: { status: 500 } } }
describe('AI intake', () => {
  beforeEach(() => {
    vi.clearAllMocks(); localStorage.clear()
    vi.mocked(getAiConfiguration).mockResolvedValue(configuration)
    vi.mocked(getKnowledgePointTree).mockResolvedValue([])
  })
  it('keeps external recognition disabled when the provider is not configured', async () => {
    vi.mocked(getAiConfiguration).mockResolvedValue({ ...configuration, enabled: false })
    const wrapper = mountView(); await flushPromises()
    wrapper.getComponent({ name: 'QuestionImageField' }).vm.$emit('change', { file: new File(['png'], 'x.png') })
    await flushPromises()
    expect(wrapper.get('[data-testid="start-recognition"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('尚未配置'); expect(createAiTask).not.toHaveBeenCalled(); wrapper.unmount()
  })
  it('clearly labels demo mode before submitting a picture', async () => {
    vi.mocked(getAiConfiguration).mockResolvedValue({ ...configuration, demo: true })
    const wrapper = mountView(); await flushPromises()
    expect(wrapper.text()).toContain('不会识别图片内容'); wrapper.unmount()
  })
  it('persists the pending id and looks up server state after a lost response without resending the image', async () => {
    vi.mocked(createAiTask).mockRejectedValueOnce(unavailable())
    const wrapper = mountView(); await flushPromises()
    wrapper.getComponent({ name: 'QuestionImageField' }).vm.$emit('change', { file: new File(['png'], 'x.png') })
    await flushPromises(); await wrapper.get('[data-testid="start-recognition"]').trigger('click'); await flushPromises()
    expect(createAiTask).toHaveBeenCalledTimes(1)
    const pending = localStorage.getItem('wrongquestion.ai.pending-task')!
    expect(pending).toMatch(/^[0-9a-f-]{36}$/)
    expect(wrapper.find('[data-testid="pending-task"]').exists()).toBe(true)
    vi.mocked(getAiTask).mockResolvedValue({ id: pending } as Awaited<ReturnType<typeof getAiTask>>)
    await wrapper.get('[data-testid="pending-task"] button').trigger('click'); await flushPromises()
    expect(getAiTask).toHaveBeenCalledWith(pending); expect(createAiTask).toHaveBeenCalledTimes(1)
    expect(mocks.push).toHaveBeenCalledWith(`/ai/tasks/${pending}`)
    expect(localStorage.getItem('wrongquestion.ai.pending-task')).toBeNull(); wrapper.unmount()
  })
  it('restores an uncertain submission after reload without requiring the original browser File', async () => {
    localStorage.setItem('wrongquestion.ai.pending-task', id)
    const wrapper = mountView(); await flushPromises()
    expect(wrapper.find('[data-testid="pending-task"]').exists()).toBe(true)
    expect(wrapper.getComponent({ name: 'QuestionImageField' }).props('disabled')).toBe(true)
    expect(createAiTask).not.toHaveBeenCalled(); wrapper.unmount()
  })
})
