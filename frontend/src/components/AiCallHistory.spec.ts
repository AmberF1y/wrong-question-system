import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAllAiCalls } from '../api/ai'
import type { AiCallPage } from '../types/ai'
import AiCallHistory from './AiCallHistory.vue'

const mocks = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('../api/ai', () => ({ getAllAiCalls: vi.fn() }))
const result: AiCallPage = {
  items: [{ taskId: 'task-1', fileName: '原图.png', call: { id: 1, attempt: 1, provider: 'demo', model: 'local-demo', status: 'SUCCEEDED', errorCode: null, inputTokens: null, outputTokens: null, durationMs: 12, estimatedCost: null, createdAt: '2026-10-11T00:00:00Z', completedAt: '2026-10-11T00:00:01Z' } }],
  page: 0, size: 20, totalElements: 1, totalPages: 1,
}
function mountHistory() { return mount(AiCallHistory, { props: { refreshKey: 1 }, global: { plugins: [ElementPlus] } }) }
describe('global AI call history', () => {
  beforeEach(() => { vi.clearAllMocks(); vi.mocked(getAllAiCalls).mockResolvedValue(result) })
  it('shows unknown usage and cost, labels demo executions, and opens the source task', async () => {
    const wrapper = mountHistory(); await flushPromises()
    expect(wrapper.text()).toContain('未报告 / 未报告'); expect(wrapper.text()).toContain('未知'); expect(wrapper.text()).toContain('演示 · local-demo')
    await wrapper.findAll('button').find(button => button.text() === '原图.png')!.trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/ai/tasks/task-1'); wrapper.unmount()
  })
  it('keeps the previous records visible when refresh fails', async () => {
    const wrapper = mountHistory(); await flushPromises()
    vi.mocked(getAllAiCalls).mockRejectedValueOnce(new Error('offline'))
    await wrapper.setProps({ refreshKey: 2 }); await flushPromises()
    expect(wrapper.text()).toContain('原图.png'); expect(wrapper.find('.el-alert').exists()).toBe(true); wrapper.unmount()
  })
  it('labels RAG calls and opens the referenced learning request', async () => {
    vi.mocked(getAllAiCalls).mockResolvedValue({ ...result, items: [{ ...result.items[0]!, fileName: '解释链表反转', kind: 'RAG', purpose: 'GENERATION', targetPath: '/learning-assistant?request=rag-1' }] })
    const wrapper = mountHistory(); await flushPromises(); expect(wrapper.text()).toContain('学习问答')
    await wrapper.findAll('button').find(button => button.text() === '解释链表反转')!.trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/learning-assistant?request=rag-1'); wrapper.unmount()
  })
  it('ignores an old request that finishes after the latest refresh', async () => {
    let finishOld!: (page: AiCallPage) => void
    vi.mocked(getAllAiCalls).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    const wrapper = mountHistory(); await wrapper.setProps({ refreshKey: 2 }); await flushPromises()
    finishOld({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }); await flushPromises()
    expect(wrapper.text()).toContain('原图.png'); wrapper.unmount()
  })
  it('keeps polling a sent call after task cancellation and refreshes summary when the call finishes', async () => {
    vi.useFakeTimers()
    const running: AiCallPage = { ...result, items: [{ ...result.items[0]!, call: { ...result.items[0]!.call, status: 'RUNNING', completedAt: null } }] }
    vi.mocked(getAllAiCalls).mockResolvedValueOnce(running).mockResolvedValueOnce(result)
    const wrapper = mountHistory()
    try {
      await flushPromises(); expect(wrapper.text()).toContain('正在执行')
      await vi.advanceTimersByTimeAsync(5000); await flushPromises()
      expect(getAllAiCalls).toHaveBeenCalledTimes(2); expect(wrapper.emitted('completed')).toHaveLength(1)
      await vi.advanceTimersByTimeAsync(5000); expect(getAllAiCalls).toHaveBeenCalledTimes(2)
    } finally { wrapper.unmount(); vi.useRealTimers() }
  })
})
