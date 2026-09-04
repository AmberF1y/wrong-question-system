import axios from 'axios'
import type { ApiErrorResponse, NormalizedApiError } from '../types/api'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function readFieldErrors(value: unknown): Record<string, string> {
  if (!isRecord(value)) {
    return {}
  }

  return Object.fromEntries(
    Object.entries(value).filter((entry): entry is [string, string] => {
      return typeof entry[1] === 'string'
    }),
  )
}

function readApiError(value: unknown): ApiErrorResponse | null {
  if (!isRecord(value)) {
    return null
  }

  return {
    status: typeof value.status === 'number' ? value.status : undefined,
    code: typeof value.code === 'string' ? value.code : undefined,
    message: typeof value.message === 'string' ? value.message : undefined,
    fieldErrors: readFieldErrors(value.fieldErrors),
  }
}

export function normalizeApiError(error: unknown): NormalizedApiError {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      return {
        message: '无法连接后端服务，请确认后端已经启动',
        fieldErrors: {},
      }
    }

    const apiError = readApiError(error.response.data)

    return {
      status: apiError?.status ?? error.response.status,
      code: apiError?.code,
      message: apiError?.message || '请求处理失败，请稍后重试',
      fieldErrors: apiError?.fieldErrors ?? {},
    }
  }

  if (error instanceof Error && error.message) {
    return {
      message: error.message,
      fieldErrors: {},
    }
  }

  return {
    message: '发生未知错误，请稍后重试',
    fieldErrors: {},
  }
}

export function isNotFoundError(error: unknown): boolean {
  const normalized = normalizeApiError(error)
  return normalized.status === 404
}
