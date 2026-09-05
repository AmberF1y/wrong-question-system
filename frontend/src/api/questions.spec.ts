import { beforeEach, describe, expect, it, vi } from 'vitest'
import http from './http'
import {
  getQuestionImageUrl,
  removeQuestionImage,
  uploadQuestionImage,
} from './questions'
import type { QuestionImageResponse } from '../types/question'

vi.mock('./http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockedPut = vi.mocked(http.put)
const mockedDelete = vi.mocked(http.delete)

describe('question image API', () => {
  beforeEach(() => {
    mockedPut.mockReset()
    mockedDelete.mockReset()
  })

  it('uploads the selected file as the multipart file field', async () => {
    const file = new File(['image'], 'question.png', { type: 'image/png' })
    const response: QuestionImageResponse = {
      questionId: 42,
      imagePath: 'questions/42/generated.png',
      contentType: 'image/png',
      size: file.size,
    }
    mockedPut.mockResolvedValue({ data: response } as never)

    await expect(uploadQuestionImage(42, file)).resolves.toEqual(response)

    expect(mockedPut).toHaveBeenCalledTimes(1)
    expect(mockedPut.mock.calls[0]?.[0]).toBe('/questions/42/image')
    const body = mockedPut.mock.calls[0]?.[1]
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
  })

  it('removes the image through the dedicated endpoint', async () => {
    mockedDelete.mockResolvedValue({
      data: { message: '题目图片移除成功' },
    } as never)

    await expect(removeQuestionImage(42)).resolves.toEqual({
      message: '题目图片移除成功',
    })
    expect(mockedDelete).toHaveBeenCalledWith('/questions/42/image')
  })

  it('builds same-origin image URLs with an optional cache key', () => {
    expect(getQuestionImageUrl(42)).toBe('/api/questions/42/image')
    expect(getQuestionImageUrl(42, '2026-09-05T10:00:00+08:00')).toBe(
      '/api/questions/42/image?v=2026-09-05T10%3A00%3A00%2B08%3A00',
    )
  })
})
