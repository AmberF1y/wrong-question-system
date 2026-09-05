import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getKnowledgePointTree } from '../api/knowledge-points'
import { createQuestion, uploadQuestionImage } from '../api/questions'
import type {
  QuestionDetail,
  QuestionFormPayload,
  QuestionImageChange,
} from '../types/question'
import QuestionCreateView from './QuestionCreateView.vue'

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
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('../api/knowledge-points', () => ({
  getKnowledgePointTree: vi.fn(),
}))

vi.mock('../api/questions', () => ({
  createQuestion: vi.fn(),
  uploadQuestionImage: vi.fn(),
}))

const mockedGetTree = vi.mocked(getKnowledgePointTree)
const mockedCreateQuestion = vi.mocked(createQuestion)
const mockedUploadQuestionImage = vi.mocked(uploadQuestionImage)

const payload: QuestionFormPayload = {
  questionText: '题目',
  wrongAnswer: '错误答案',
  correctAnswer: '正确答案',
  analysis: '解析',
  errorReason: '错误原因',
  knowledgePointIds: [2],
}

const question: QuestionDetail = {
  id: 42,
  ...payload,
  subject: '408',
  imagePath: null,
  knowledgePoints: [{ id: 2, name: '存储系统', parentId: 1 }],
  createdTime: '2026-09-05T10:00:00',
  updatedTime: '2026-09-05T10:00:00',
  reviewStatus: 'ACTIVE',
  nextReviewDate: '2026-09-06',
  consecutiveProficientCount: 0,
  lastReviewedAt: null,
}

const questionFormStub = {
  name: 'QuestionForm',
  emits: ['submit', 'cancel'],
  template: '<div data-testid="question-form-stub" />',
}

function mountView() {
  return mount(QuestionCreateView, {
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

describe('QuestionCreateView', () => {
  beforeEach(() => {
    mocks.messageSuccess.mockReset()
    mocks.messageError.mockReset()
    mocks.push.mockReset()
    mockedGetTree.mockReset()
    mockedCreateQuestion.mockReset()
    mockedUploadQuestionImage.mockReset()
    mockedGetTree.mockResolvedValue([])
    mockedCreateQuestion.mockResolvedValue(question)
  })

  it('keeps the existing one-request flow when no image is selected', async () => {
    const wrapper = mountView()
    await flushPromises()

    await submit(wrapper, { file: null, removeExisting: false })

    expect(mockedCreateQuestion).toHaveBeenCalledWith(payload)
    expect(mockedUploadQuestionImage).not.toHaveBeenCalled()
    expect(mocks.push).toHaveBeenCalledWith('/questions/42')
  })

  it('creates the question before uploading its selected image', async () => {
    const order: string[] = []
    const file = new File(['png'], 'question.png', { type: 'image/png' })
    mockedCreateQuestion.mockImplementation(async () => {
      order.push('create')
      return question
    })
    mockedUploadQuestionImage.mockImplementation(async () => {
      order.push('upload')
      return {
        questionId: 42,
        imagePath: 'questions/42/generated.png',
        contentType: 'image/png',
        size: file.size,
      }
    })
    const wrapper = mountView()
    await flushPromises()

    await submit(wrapper, { file, removeExisting: false })

    expect(order).toEqual(['create', 'upload'])
    expect(mockedUploadQuestionImage).toHaveBeenCalledWith(42, file)
    expect(mocks.push).toHaveBeenCalledWith('/questions/42')
  })

  it('does not recreate the question when image upload fails', async () => {
    const file = new File(['png'], 'question.png', { type: 'image/png' })
    mockedUploadQuestionImage.mockRejectedValue(new Error('图片服务不可用'))
    const wrapper = mountView()
    await flushPromises()

    await submit(wrapper, { file, removeExisting: false })

    expect(mockedCreateQuestion).toHaveBeenCalledTimes(1)
    expect(mockedUploadQuestionImage).toHaveBeenCalledTimes(1)
    expect(mocks.messageError).toHaveBeenCalledWith(
      '错题已保存，但图片上传失败：图片服务不可用',
    )
    expect(mocks.push).toHaveBeenCalledWith('/questions/42/edit')
  })
})
