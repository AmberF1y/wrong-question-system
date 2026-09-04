import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { deleteQuestion, getQuestion } from '../api/questions'
import { reactivateQuestion } from '../api/reviews'
import type { QuestionDetail } from '../types/question'
import type { ReviewActionResponse } from '../types/review'
import QuestionDetailView from './QuestionDetailView.vue'

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
  push: vi.fn(),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: mocks.messageSuccess,
      error: mocks.messageError,
    },
    ElMessageBox: {
      confirm: mocks.confirm,
    },
  }
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '42' } }),
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('../api/questions', () => ({
  deleteQuestion: vi.fn(),
  getQuestion: vi.fn(),
}))

vi.mock('../api/reviews', () => ({
  reactivateQuestion: vi.fn(),
}))

const mockedGetQuestion = vi.mocked(getQuestion)
const mockedDeleteQuestion = vi.mocked(deleteQuestion)
const mockedReactivateQuestion = vi.mocked(reactivateQuestion)

const activeQuestion: QuestionDetail = {
  id: 42,
  questionText: '解释虚拟地址转换过程',
  wrongAnswer: '错误答案',
  correctAnswer: '正确答案',
  analysis: '解析内容',
  errorReason: '错误原因',
  subject: '408',
  imagePath: null,
  knowledgePoints: [{ id: 2, name: '存储系统', parentId: 1 }],
  createdTime: '2026-09-01T10:00:00',
  updatedTime: '2026-09-01T10:00:00',
  reviewStatus: 'ACTIVE',
  nextReviewDate: '2026-09-04',
  consecutiveProficientCount: 0,
  lastReviewedAt: '2026-09-03T02:00:00Z',
}

const masteredQuestion: QuestionDetail = {
  ...activeQuestion,
  reviewStatus: 'MASTERED',
  nextReviewDate: null,
  consecutiveProficientCount: 2,
}

const reactivationResponse: ReviewActionResponse = {
  questionId: 42,
  eventType: 'REACTIVATION',
  rating: null,
  occurredAt: '2026-09-04T02:00:00Z',
  reviewStatus: 'ACTIVE',
  nextReviewDate: '2026-09-04',
  consecutiveProficientCount: 0,
  lastReviewedAt: '2026-09-04T02:00:00Z',
}

function axiosError(status?: number, code?: string, message = '请求处理失败'): unknown {
  return {
    isAxiosError: true,
    response:
      status === undefined
        ? undefined
        : {
            status,
            data: { status, code, message },
          },
  }
}

function mountView() {
  return mount(QuestionDetailView, {
    global: {
      plugins: [ElementPlus],
    },
  })
}

describe('QuestionDetailView reactivation', () => {
  beforeEach(() => {
    mocks.confirm.mockReset()
    mocks.messageSuccess.mockReset()
    mocks.messageError.mockReset()
    mocks.push.mockReset()
    mockedGetQuestion.mockReset()
    mockedDeleteQuestion.mockReset()
    mockedReactivateQuestion.mockReset()
  })

  it('does not show the reactivation action for an active question', async () => {
    mockedGetQuestion.mockResolvedValue(activeQuestion)
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="reactivate-question"]').exists()).toBe(false)
  })

  it('shows the action for a mastered question and does nothing after cancellation', async () => {
    mockedGetQuestion.mockResolvedValue(masteredQuestion)
    mocks.confirm.mockRejectedValue(new Error('cancelled'))
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-question"]').trigger('click')
    await flushPromises()

    expect(mocks.confirm).toHaveBeenCalledWith(
      expect.stringContaining('立即进入今日队列'),
      '重新加入复习',
      expect.objectContaining({ confirmButtonText: '确认重新加入' }),
    )
    expect(mockedReactivateQuestion).not.toHaveBeenCalled()
  })

  it('reactivates once, refreshes the detail and stays on the page', async () => {
    mockedGetQuestion
      .mockResolvedValueOnce(masteredQuestion)
      .mockResolvedValueOnce(activeQuestion)
    mockedReactivateQuestion.mockResolvedValue(reactivationResponse)
    mocks.confirm.mockResolvedValue('confirm')
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-question"]').trigger('click')
    await flushPromises()

    expect(mockedReactivateQuestion).toHaveBeenCalledTimes(1)
    expect(mockedReactivateQuestion).toHaveBeenCalledWith(42)
    expect(mockedGetQuestion).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('复习中')
    expect(wrapper.find('[data-testid="go-to-daily-review"]').exists()).toBe(true)
    expect(mocks.push).not.toHaveBeenCalled()
    expect(mocks.messageSuccess).toHaveBeenCalledWith('错题已重新加入今日复习队列')
  })

  it('allows only one confirmation flow when reactivation is clicked repeatedly', async () => {
    let resolveConfirmation!: (value: string) => void
    const pendingConfirmation = new Promise<string>((resolve) => {
      resolveConfirmation = resolve
    })
    mockedGetQuestion
      .mockResolvedValueOnce(masteredQuestion)
      .mockResolvedValueOnce(activeQuestion)
    mockedReactivateQuestion.mockResolvedValue(reactivationResponse)
    mocks.confirm.mockReturnValue(pendingConfirmation)
    const wrapper = mountView()
    await flushPromises()

    const button = wrapper.get('[data-testid="reactivate-question"]')
    await button.trigger('click')
    await button.trigger('click')

    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mockedReactivateQuestion).not.toHaveBeenCalled()
    expect(button.attributes('disabled')).toBeDefined()

    resolveConfirmation('confirm')
    await flushPromises()

    expect(mockedReactivateQuestion).toHaveBeenCalledTimes(1)
  })

  it('does not repeat a POST when the reactivation response is unavailable', async () => {
    mockedGetQuestion
      .mockResolvedValueOnce(masteredQuestion)
      .mockResolvedValueOnce(activeQuestion)
    mockedReactivateQuestion.mockRejectedValue(axiosError())
    mocks.confirm.mockResolvedValue('confirm')
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-question"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="reactivation-uncertain"]').exists()).toBe(true)
    expect(mockedReactivateQuestion).toHaveBeenCalledTimes(1)
    expect(mockedGetQuestion).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="sync-question-detail"]').trigger('click')
    await flushPromises()

    expect(mockedReactivateQuestion).toHaveBeenCalledTimes(1)
    expect(mockedGetQuestion).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('复习中')
    expect(wrapper.find('[data-testid="go-to-daily-review"]').exists()).toBe(true)
  })

  it('shows a 409 message and reloads the detail from the server', async () => {
    mockedGetQuestion
      .mockResolvedValueOnce(masteredQuestion)
      .mockResolvedValueOnce(activeQuestion)
    mockedReactivateQuestion.mockRejectedValue(
      axiosError(409, 'REVIEW_NOT_MASTERED', '只有已掌握错题可以重新加入复习'),
    )
    mocks.confirm.mockResolvedValue('confirm')
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-question"]').trigger('click')
    await flushPromises()

    expect(mocks.messageError).toHaveBeenCalledWith('只有已掌握错题可以重新加入复习')
    expect(mockedGetQuestion).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('复习中')
    expect(mockedReactivateQuestion).toHaveBeenCalledTimes(1)
  })

  it('enters the existing not-found state when the question disappears', async () => {
    mockedGetQuestion
      .mockResolvedValueOnce(masteredQuestion)
      .mockRejectedValueOnce(axiosError(404, 'QUESTION_NOT_FOUND', '错题不存在'))
    mockedReactivateQuestion.mockRejectedValue(
      axiosError(404, 'QUESTION_NOT_FOUND', '错题不存在'),
    )
    mocks.confirm.mockResolvedValue('confirm')
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('[data-testid="reactivate-question"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('这道错题不存在或已经被删除')
    expect(mockedGetQuestion).toHaveBeenCalledTimes(2)
  })
})
