import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getKnowledgePointTree } from '../api/knowledge-points'
import {
  getQuestion,
  getQuestionImageUrl,
  removeQuestionImage,
  updateQuestion,
  uploadQuestionImage,
} from '../api/questions'
import type {
  QuestionDetail,
  QuestionFormPayload,
  QuestionImageChange,
} from '../types/question'
import QuestionEditView from './QuestionEditView.vue'

const mocks = vi.hoisted(() => ({
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
  }
})

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '42' } }),
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('../api/knowledge-points', () => ({
  getKnowledgePointTree: vi.fn(),
}))

vi.mock('../api/questions', () => ({
  getQuestion: vi.fn(),
  getQuestionImageUrl: vi.fn(),
  removeQuestionImage: vi.fn(),
  updateQuestion: vi.fn(),
  uploadQuestionImage: vi.fn(),
}))

const mockedGetTree = vi.mocked(getKnowledgePointTree)
const mockedGetQuestion = vi.mocked(getQuestion)
const mockedGetQuestionImageUrl = vi.mocked(getQuestionImageUrl)
const mockedRemoveQuestionImage = vi.mocked(removeQuestionImage)
const mockedUpdateQuestion = vi.mocked(updateQuestion)
const mockedUploadQuestionImage = vi.mocked(uploadQuestionImage)

const payload: QuestionFormPayload = {
  questionText: '修改后的题目',
  wrongAnswer: '错误答案',
  correctAnswer: '正确答案',
  analysis: '解析',
  errorReason: '错误原因',
  knowledgePointIds: [2],
}

const question: QuestionDetail = {
  id: 42,
  questionText: '原题目',
  wrongAnswer: payload.wrongAnswer,
  correctAnswer: payload.correctAnswer,
  analysis: payload.analysis,
  errorReason: payload.errorReason,
  subject: '408',
  imagePath: 'questions/42/original.png',
  knowledgePoints: [{ id: 2, name: '存储系统', parentId: 1 }],
  createdTime: '2026-09-05T10:00:00',
  updatedTime: '2026-09-05T10:00:00',
  reviewStatus: 'ACTIVE',
  nextReviewDate: '2026-09-06',
  consecutiveProficientCount: 0,
  lastReviewedAt: null,
}

const updatedQuestion: QuestionDetail = {
  ...question,
  ...payload,
  updatedTime: '2026-09-05T11:00:00',
}

const questionFormStub = {
  name: 'QuestionForm',
  props: ['currentImageUrl'],
  emits: ['submit', 'cancel'],
  template: '<div data-testid="question-form-stub" />',
}

function mountView() {
  return mount(QuestionEditView, {
    global: {
      plugins: [createPinia(), ElementPlus],
      stubs: { QuestionForm: questionFormStub },
    },
  })
}

async function submit(
  wrapper: ReturnType<typeof mountView>,
  imageChange: QuestionImageChange,
): Promise<void> {
  wrapper.findComponent({ name: 'QuestionForm' }).vm.$emit(
    'submit',
    payload,
    imageChange,
  )
  await flushPromises()
}

describe('QuestionEditView', () => {
  beforeEach(() => {
    mocks.messageSuccess.mockReset()
    mocks.messageError.mockReset()
    mocks.push.mockReset()
    mockedGetTree.mockReset()
    mockedGetQuestion.mockReset()
    mockedGetQuestionImageUrl.mockReset()
    mockedRemoveQuestionImage.mockReset()
    mockedUpdateQuestion.mockReset()
    mockedUploadQuestionImage.mockReset()
    mockedGetTree.mockResolvedValue([])
    mockedGetQuestion.mockResolvedValue(question)
    mockedGetQuestionImageUrl.mockReturnValue('/api/questions/42/image?v=initial')
    mockedUpdateQuestion.mockResolvedValue(updatedQuestion)
  })

  it('preserves the existing image when only text is modified', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findComponent({ name: 'QuestionForm' }).props('currentImageUrl')).toBe(
      '/api/questions/42/image?v=initial',
    )
    await submit(wrapper, { file: null, removeExisting: false })

    expect(mockedUpdateQuestion).toHaveBeenCalledWith(42, payload)
    expect(mockedUploadQuestionImage).not.toHaveBeenCalled()
    expect(mockedRemoveQuestionImage).not.toHaveBeenCalled()
    expect(mocks.push).toHaveBeenCalledWith('/questions/42')
  })

  it('updates text before replacing the image', async () => {
    const order: string[] = []
    const file = new File(['jpeg'], 'replacement.jpg', { type: 'image/jpeg' })
    mockedUpdateQuestion.mockImplementation(async () => {
      order.push('update')
      return updatedQuestion
    })
    mockedUploadQuestionImage.mockImplementation(async () => {
      order.push('upload')
      return {
        questionId: 42,
        imagePath: 'questions/42/replacement.jpg',
        contentType: 'image/jpeg',
        size: file.size,
      }
    })
    const wrapper = mountView()
    await flushPromises()

    await submit(wrapper, { file, removeExisting: false })

    expect(order).toEqual(['update', 'upload'])
    expect(mockedUploadQuestionImage).toHaveBeenCalledWith(42, file)
    expect(mockedRemoveQuestionImage).not.toHaveBeenCalled()
  })

  it('can retry only the image when the text is unchanged', async () => {
    const file = new File(['png'], 'retry.png', { type: 'image/png' })
    mockedUploadQuestionImage.mockResolvedValue({
      questionId: 42,
      imagePath: 'questions/42/retry.png',
      contentType: 'image/png',
      size: file.size,
    })
    const unchangedPayload: QuestionFormPayload = {
      questionText: question.questionText,
      wrongAnswer: question.wrongAnswer,
      correctAnswer: question.correctAnswer,
      analysis: question.analysis,
      errorReason: question.errorReason,
      knowledgePointIds: question.knowledgePoints.map((point) => point.id),
    }
    const wrapper = mountView()
    await flushPromises()

    wrapper.findComponent({ name: 'QuestionForm' }).vm.$emit(
      'submit',
      unchangedPayload,
      { file, removeExisting: false },
    )
    await flushPromises()

    expect(mockedUpdateQuestion).not.toHaveBeenCalled()
    expect(mockedUploadQuestionImage).toHaveBeenCalledWith(42, file)
    expect(mocks.push).toHaveBeenCalledWith('/questions/42')
  })

  it('uses the independent remove endpoint after saving text', async () => {
    mockedRemoveQuestionImage.mockResolvedValue({ message: '题目图片移除成功' })
    const wrapper = mountView()
    await flushPromises()

    await submit(wrapper, { file: null, removeExisting: true })

    expect(mockedUpdateQuestion).toHaveBeenCalledWith(42, payload)
    expect(mockedRemoveQuestionImage).toHaveBeenCalledWith(42)
    expect(mockedUploadQuestionImage).not.toHaveBeenCalled()
  })

  it('reports partial success and does not resend text after image failure', async () => {
    const file = new File(['png'], 'question.png', { type: 'image/png' })
    mockedUploadQuestionImage.mockRejectedValue(new Error('图片服务不可用'))
    const wrapper = mountView()
    await flushPromises()

    await submit(wrapper, { file, removeExisting: false })

    expect(mockedUpdateQuestion).toHaveBeenCalledTimes(1)
    expect(mockedUploadQuestionImage).toHaveBeenCalledTimes(1)
    expect(mockedGetQuestion).toHaveBeenCalledTimes(2)
    expect(mocks.messageError).toHaveBeenCalledWith(
      '文字和知识点已保存，但图片上传失败：图片服务不可用',
    )
    expect(mocks.push).not.toHaveBeenCalled()
  })
})
