import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getKnowledgePointTree } from '../api/knowledge-points'
import { getQuestion } from '../api/questions'
import { getNextDueReview, submitReviewEvaluation } from '../api/reviews'
import type { QuestionDetail } from '../types/question'
import type {
  DueReviewResponse,
  ReviewActionResponse,
} from '../types/review'
import DailyReviewView from './DailyReviewView.vue'

vi.mock('../api/knowledge-points', () => ({
  getKnowledgePointTree: vi.fn(),
}))

vi.mock('../api/questions', () => ({
  getQuestion: vi.fn(),
}))

vi.mock('../api/reviews', () => ({
  getNextDueReview: vi.fn(),
  submitReviewEvaluation: vi.fn(),
}))

const mockedGetTree = vi.mocked(getKnowledgePointTree)
const mockedGetQuestion = vi.mocked(getQuestion)
const mockedGetNextDue = vi.mocked(getNextDueReview)
const mockedSubmitEvaluation = vi.mocked(submitReviewEvaluation)

const dueQuestion = {
  id: 42,
  questionText: '解释虚拟地址转换过程',
  imagePath: null,
  subject: '408',
  nextReviewDate: '2026-09-04',
}

const dueResponse: DueReviewResponse = {
  dueCount: 3,
  question: dueQuestion,
}

const emptyResponse: DueReviewResponse = {
  dueCount: 0,
  question: null,
}

const questionDetail: QuestionDetail = {
  id: 42,
  questionText: dueQuestion.questionText,
  wrongAnswer: '漏掉了 TLB 未命中后的页表访问',
  correctAnswer: '先查 TLB，未命中后查询页表',
  analysis: '地址转换和 Cache 查询属于不同阶段',
  errorReason: '混淆了 TLB 与 Cache',
  subject: '408',
  imagePath: null,
  knowledgePoints: [{ id: 2, name: '存储系统', parentId: 1 }],
  createdTime: '2026-09-01T10:00:00',
  updatedTime: '2026-09-01T10:00:00',
  reviewStatus: 'ACTIVE',
  nextReviewDate: '2026-09-04',
  consecutiveProficientCount: 1,
  lastReviewedAt: '2026-08-21T02:00:00Z',
}

const masteredAction: ReviewActionResponse = {
  questionId: 42,
  eventType: 'EVALUATION',
  rating: 'PROFICIENT',
  occurredAt: '2026-09-04T02:00:00Z',
  reviewStatus: 'MASTERED',
  nextReviewDate: null,
  consecutiveProficientCount: 2,
  lastReviewedAt: '2026-09-04T02:00:00Z',
}

function axiosError(
  status?: number,
  code?: string,
  message = '请求处理失败',
  fieldErrors?: Record<string, string>,
): unknown {
  return {
    isAxiosError: true,
    response:
      status === undefined
        ? undefined
        : {
            status,
            data: { status, code, message, fieldErrors },
          },
  }
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function mountView() {
  return mount(DailyReviewView, {
    global: {
      plugins: [createPinia(), ElementPlus],
    },
  })
}

async function revealAnswer(wrapper: ReturnType<typeof mountView>): Promise<void> {
  await wrapper.get('[data-testid="show-answer"]').trigger('click')
  await flushPromises()
  await nextTick()
}

describe('DailyReviewView', () => {
  beforeEach(() => {
    mockedGetTree.mockReset()
    mockedGetQuestion.mockReset()
    mockedGetNextDue.mockReset()
    mockedSubmitEvaluation.mockReset()
    mockedGetTree.mockResolvedValue([])
  })

  it('loads a due question without requesting or exposing its answer', async () => {
    const pendingQueue = deferred<DueReviewResponse>()
    mockedGetNextDue.mockReturnValue(pendingQueue.promise)
    const wrapper = mountView()

    expect(wrapper.find('[data-testid="queue-loading"]').exists()).toBe(true)

    pendingQueue.resolve(dueResponse)
    await flushPromises()

    expect(wrapper.text()).toContain(dueQuestion.questionText)
    expect(wrapper.get('[data-testid="due-count"]').text()).toContain('3 道')
    expect(wrapper.text()).not.toContain(questionDetail.correctAnswer)
    expect(mockedGetQuestion).not.toHaveBeenCalled()
  })

  it('reveals the four answer fields only after an explicit click', async () => {
    mockedGetNextDue.mockResolvedValue(dueResponse)
    mockedGetQuestion.mockResolvedValue(questionDetail)
    const wrapper = mountView()
    await flushPromises()

    expect(mockedGetQuestion).not.toHaveBeenCalled()
    await revealAnswer(wrapper)

    expect(mockedGetQuestion).toHaveBeenCalledWith(42)
    expect(wrapper.get('[data-testid="answer-panel"]').text()).toContain(
      questionDetail.wrongAnswer,
    )
    expect(wrapper.text()).toContain(questionDetail.correctAnswer)
    expect(wrapper.text()).toContain(questionDetail.analysis)
    expect(wrapper.text()).toContain(questionDetail.errorReason)
    expect(wrapper.find('[data-testid="rating-panel"]').exists()).toBe(true)
  })

  it('keeps the question hidden from rating when answer loading fails and supports retry', async () => {
    mockedGetNextDue.mockResolvedValue(dueResponse)
    mockedGetQuestion
      .mockRejectedValueOnce(new Error('详情暂时不可用'))
      .mockResolvedValueOnce(questionDetail)
    const wrapper = mountView()
    await flushPromises()

    await revealAnswer(wrapper)

    expect(wrapper.text()).toContain(dueQuestion.questionText)
    expect(wrapper.get('[data-testid="answer-error"]').text()).toContain('详情暂时不可用')
    expect(wrapper.find('[data-testid="rating-panel"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain(questionDetail.correctAnswer)

    await wrapper.get('[data-testid="retry-answer"]').trigger('click')
    await flushPromises()

    expect(mockedGetQuestion).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain(questionDetail.correctAnswer)
    expect(wrapper.find('[data-testid="rating-panel"]').exists()).toBe(true)
  })

  it('shows the server result and waits for the user before loading the next question', async () => {
    mockedGetNextDue
      .mockResolvedValueOnce(dueResponse)
      .mockResolvedValueOnce(emptyResponse)
    mockedGetQuestion.mockResolvedValue(questionDetail)
    mockedSubmitEvaluation.mockResolvedValue(masteredAction)
    const wrapper = mountView()
    await flushPromises()
    await revealAnswer(wrapper)

    await wrapper.get('[data-testid="rating-PROFICIENT"]').trigger('click')
    await flushPromises()

    expect(mockedSubmitEvaluation).toHaveBeenCalledWith(42, 'PROFICIENT')
    expect(mockedGetNextDue).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="review-result"]').text()).toContain('本次评价已记录')
    expect(wrapper.text()).toContain('已退出常规复习队列')

    await wrapper.get('[data-testid="next-question"]').trigger('click')
    await flushPromises()

    expect(mockedGetNextDue).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="empty-queue"]').text()).toContain(
      '今天没有待复习错题',
    )
  })

  it('blocks a duplicate rating click while the first POST is pending', async () => {
    const pendingAction = deferred<ReviewActionResponse>()
    mockedGetNextDue.mockResolvedValue(dueResponse)
    mockedGetQuestion.mockResolvedValue(questionDetail)
    mockedSubmitEvaluation.mockReturnValue(pendingAction.promise)
    const wrapper = mountView()
    await flushPromises()
    await revealAnswer(wrapper)

    const button = wrapper.get('[data-testid="rating-FUZZY"]')
    await button.trigger('click')
    await button.trigger('click')
    await nextTick()

    expect(mockedSubmitEvaluation).toHaveBeenCalledTimes(1)
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.getComponent({ name: 'ElSelect' }).props('disabled')).toBe(true)

    pendingAction.resolve({
      ...masteredAction,
      rating: 'FUZZY',
      reviewStatus: 'ACTIVE',
      nextReviewDate: '2026-09-07',
      consecutiveProficientCount: 0,
    })
    await flushPromises()
  })

  it('does not retry an evaluation after a proxy 500 and safely synchronizes changed progress', async () => {
    mockedGetNextDue.mockResolvedValue(dueResponse)
    mockedGetQuestion
      .mockResolvedValueOnce(questionDetail)
      .mockResolvedValueOnce({
        ...questionDetail,
        nextReviewDate: '2026-09-07',
        consecutiveProficientCount: 0,
        lastReviewedAt: '2026-09-04T02:00:00Z',
      })
    mockedSubmitEvaluation.mockRejectedValue(axiosError(500))
    const wrapper = mountView()
    await flushPromises()
    await revealAnswer(wrapper)

    await wrapper.get('[data-testid="rating-FUZZY"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="uncertain-result"]').exists()).toBe(true)
    expect(mockedSubmitEvaluation).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="sync-progress"]').trigger('click')
    await flushPromises()

    expect(mockedGetQuestion).toHaveBeenCalledTimes(2)
    expect(mockedSubmitEvaluation).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="review-result"]').text()).toContain(
      '服务器状态已更新',
    )
    expect(wrapper.text()).toContain('2026-09-07')
  })

  it('allows a new evaluation only after synchronization confirms unchanged progress', async () => {
    mockedGetNextDue.mockResolvedValue(dueResponse)
    mockedGetQuestion.mockResolvedValue(questionDetail)
    mockedSubmitEvaluation
      .mockRejectedValueOnce(axiosError())
      .mockResolvedValueOnce({
        ...masteredAction,
        rating: 'NOT_KNOWN',
        reviewStatus: 'ACTIVE',
        nextReviewDate: '2026-09-05',
        consecutiveProficientCount: 0,
      })
    const wrapper = mountView()
    await flushPromises()
    await revealAnswer(wrapper)

    await wrapper.get('[data-testid="rating-NOT_KNOWN"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="sync-progress"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="evaluation-error"]').text()).toContain(
      '服务器复习状态尚未变化',
    )

    await wrapper.get('[data-testid="rating-NOT_KNOWN"]').trigger('click')
    await flushPromises()

    expect(mockedSubmitEvaluation).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="review-result"]').exists()).toBe(true)
  })

  it.each([
    [409, 'REVIEW_NOT_DUE', '错题尚未到复习日期'],
    [409, 'REVIEW_ALREADY_MASTERED', '已掌握错题不能提交普通复习评价'],
    [409, 'REVIEW_CONCURRENT_MODIFICATION', '复习状态已被其他请求修改'],
    [404, 'QUESTION_NOT_FOUND', '错题不存在'],
  ])(
    'shows %s %s and reloads the dynamic queue without a local success result',
    async (status, code, message) => {
      mockedGetNextDue
        .mockResolvedValueOnce(dueResponse)
        .mockResolvedValueOnce(emptyResponse)
      mockedGetQuestion.mockResolvedValue(questionDetail)
      mockedSubmitEvaluation.mockRejectedValue(axiosError(status, code, message))
      const wrapper = mountView()
      await flushPromises()
      await revealAnswer(wrapper)

      await wrapper.get('[data-testid="rating-BASICALLY_MASTERED"]').trigger('click')
      await flushPromises()

      expect(wrapper.get('[data-testid="queue-notice"]').text()).toContain(message)
      expect(mockedGetNextDue).toHaveBeenCalledTimes(2)
      expect(wrapper.find('[data-testid="review-result"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="empty-queue"]').exists()).toBe(true)
    },
  )

  it('reloads the queue when the question disappears while revealing the answer', async () => {
    mockedGetNextDue
      .mockResolvedValueOnce(dueResponse)
      .mockResolvedValueOnce(emptyResponse)
    mockedGetQuestion.mockRejectedValue(
      axiosError(404, 'QUESTION_NOT_FOUND', '错题不存在'),
    )
    const wrapper = mountView()
    await flushPromises()

    await revealAnswer(wrapper)

    expect(mockedGetNextDue).toHaveBeenCalledTimes(2)
    expect(wrapper.get('[data-testid="queue-notice"]').text()).toContain('错题不存在')
    expect(wrapper.find('[data-testid="answer-panel"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="empty-queue"]').exists()).toBe(true)
  })

  it('stays uncertain and permits another GET when progress synchronization fails', async () => {
    mockedGetNextDue.mockResolvedValue(dueResponse)
    mockedGetQuestion
      .mockResolvedValueOnce(questionDetail)
      .mockRejectedValueOnce(new Error('同步查询失败'))
      .mockResolvedValueOnce({
        ...questionDetail,
        nextReviewDate: '2026-09-07',
        lastReviewedAt: '2026-09-04T02:00:00Z',
      })
    mockedSubmitEvaluation.mockRejectedValue(axiosError())
    const wrapper = mountView()
    await flushPromises()
    await revealAnswer(wrapper)

    await wrapper.get('[data-testid="rating-FUZZY"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="sync-progress"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="uncertain-result"]').text()).toContain('同步查询失败')
    expect(mockedSubmitEvaluation).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="sync-progress"]').trigger('click')
    await flushPromises()

    expect(mockedGetQuestion).toHaveBeenCalledTimes(3)
    expect(wrapper.find('[data-testid="review-result"]').exists()).toBe(true)
  })

  it('keeps a validation failure in the answer phase with the backend field message', async () => {
    mockedGetNextDue.mockResolvedValue(dueResponse)
    mockedGetQuestion.mockResolvedValue(questionDetail)
    mockedSubmitEvaluation.mockRejectedValue(
      axiosError(400, 'VALIDATION_FAILED', '请求参数校验失败', {
        rating: '复习评价不能为空',
      }),
    )
    const wrapper = mountView()
    await flushPromises()
    await revealAnswer(wrapper)

    await wrapper.get('[data-testid="rating-FUZZY"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="evaluation-error"]').text()).toContain(
      '复习评价不能为空',
    )
    expect(wrapper.find('[data-testid="rating-panel"]').exists()).toBe(true)
    expect(mockedGetNextDue).toHaveBeenCalledTimes(1)
  })

  it('switches subjects immediately and clears the previous answer state', async () => {
    mockedGetTree.mockResolvedValue([
      { id: 1, name: '408', parentId: null, children: [] },
      { id: 10, name: '数学', parentId: null, children: [] },
    ])
    mockedGetNextDue
      .mockResolvedValueOnce(dueResponse)
      .mockResolvedValueOnce({
        dueCount: 1,
        question: {
          ...dueQuestion,
          id: 88,
          questionText: '计算定积分',
          subject: '数学',
        },
      })
    mockedGetQuestion.mockResolvedValue(questionDetail)
    const wrapper = mountView()
    await flushPromises()
    await revealAnswer(wrapper)

    expect(wrapper.text()).toContain(questionDetail.correctAnswer)

    const select = wrapper.getComponent({ name: 'ElSelect' })
    select.vm.$emit('update:modelValue', '数学')
    await nextTick()
    select.vm.$emit('change', '数学')
    await flushPromises()

    expect(mockedGetNextDue).toHaveBeenLastCalledWith('数学')
    expect(wrapper.text()).toContain('计算定积分')
    expect(wrapper.text()).not.toContain(questionDetail.correctAnswer)
    expect(wrapper.find('[data-testid="rating-panel"]').exists()).toBe(false)
  })

  it('lets a single-subject empty state return to the all-subject queue', async () => {
    mockedGetTree.mockResolvedValue([
      { id: 10, name: '数学', parentId: null, children: [] },
    ])
    mockedGetNextDue
      .mockResolvedValueOnce(dueResponse)
      .mockResolvedValueOnce(emptyResponse)
      .mockResolvedValueOnce(dueResponse)
    const wrapper = mountView()
    await flushPromises()

    const select = wrapper.getComponent({ name: 'ElSelect' })
    select.vm.$emit('update:modelValue', '数学')
    await nextTick()
    select.vm.$emit('change', '数学')
    await flushPromises()

    expect(wrapper.get('[data-testid="empty-queue"]').text()).toContain(
      '“数学”今天没有待复习错题',
    )

    await wrapper.get('[data-testid="clear-subject"]').trigger('click')
    await flushPromises()

    expect(mockedGetNextDue).toHaveBeenLastCalledWith(undefined)
    expect(wrapper.text()).toContain(dueQuestion.questionText)
  })

  it('ignores an older all-subject response that arrives after a subject response', async () => {
    const allSubjects = deferred<DueReviewResponse>()
    const mathSubject = deferred<DueReviewResponse>()
    mockedGetTree.mockResolvedValue([
      { id: 1, name: '408', parentId: null, children: [] },
      { id: 10, name: '数学', parentId: null, children: [] },
    ])
    mockedGetNextDue
      .mockReturnValueOnce(allSubjects.promise)
      .mockReturnValueOnce(mathSubject.promise)
    const wrapper = mountView()
    await flushPromises()

    const select = wrapper.getComponent({ name: 'ElSelect' })
    select.vm.$emit('update:modelValue', '数学')
    await nextTick()
    select.vm.$emit('change', '数学')
    await nextTick()

    mathSubject.resolve({
      dueCount: 1,
      question: {
        ...dueQuestion,
        id: 88,
        questionText: '计算定积分',
        subject: '数学',
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('计算定积分')
    expect(mockedGetNextDue).toHaveBeenNthCalledWith(2, '数学')

    allSubjects.resolve(dueResponse)
    await flushPromises()

    expect(wrapper.text()).toContain('计算定积分')
    expect(wrapper.text()).not.toContain(dueQuestion.questionText)
  })

  it('keeps the all-subject queue usable when knowledge point loading fails', async () => {
    mockedGetTree.mockRejectedValue(new Error('知识点服务暂时不可用'))
    mockedGetNextDue.mockResolvedValue(dueResponse)
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('科目筛选暂不可用')
    expect(wrapper.text()).toContain('知识点服务暂时不可用')
    expect(wrapper.text()).toContain(dueQuestion.questionText)
    expect(mockedGetNextDue).toHaveBeenCalledWith(undefined)
  })

  it('rejects an inconsistent empty queue response instead of showing a false empty state', async () => {
    mockedGetNextDue.mockResolvedValue({ dueCount: 1, question: null })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="queue-error"]').text()).toContain(
      '待复习队列数据不一致',
    )
    expect(wrapper.find('[data-testid="empty-queue"]').exists()).toBe(false)
  })
})
