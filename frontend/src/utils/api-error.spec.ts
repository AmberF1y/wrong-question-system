import { describe, expect, it } from 'vitest'
import {
  isNotFoundError,
  isResponseUnavailableError,
  normalizeApiError,
} from './api-error'

function axiosError(status?: number, data?: unknown): unknown {
  return {
    isAxiosError: true,
    response:
      status === undefined
        ? undefined
        : {
            status,
            data,
          },
  }
}

describe('normalizeApiError', () => {
  it('preserves the backend error code, message and valid field errors', () => {
    expect(
      normalizeApiError(
        axiosError(400, {
          status: 400,
          code: 'VALIDATION_FAILED',
          message: '请求参数校验失败',
          fieldErrors: {
            questionText: '题目内容不能为空',
            ignored: 42,
          },
        }),
      ),
    ).toEqual({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: '请求参数校验失败',
      fieldErrors: {
        questionText: '题目内容不能为空',
      },
    })
  })

  it('uses the HTTP status when the response body is not an API error', () => {
    expect(normalizeApiError(axiosError(500, '<html>error</html>'))).toEqual({
      status: 500,
      code: undefined,
      message: '请求处理失败，请稍后重试',
      fieldErrors: {},
    })
  })

  it('distinguishes a connection failure', () => {
    expect(normalizeApiError(axiosError()).message).toContain('无法连接后端服务')
  })

  it('preserves a normal Error message', () => {
    expect(normalizeApiError(new Error('本地校验失败')).message).toBe('本地校验失败')
  })

  it('recognizes a 404 response', () => {
    expect(isNotFoundError(axiosError(404, { status: 404 }))).toBe(true)
    expect(isNotFoundError(axiosError(409, { status: 409 }))).toBe(false)
  })

  it('distinguishes an unavailable response from an HTTP error response', () => {
    expect(isResponseUnavailableError(axiosError())).toBe(true)
    expect(isResponseUnavailableError(axiosError(500))).toBe(false)
    expect(isResponseUnavailableError(new Error('local error'))).toBe(false)
  })
})
