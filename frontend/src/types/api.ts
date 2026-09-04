export interface HealthResponse {
  status: string
}

export interface MessageResponse {
  message: string
}

export interface ApiErrorResponse {
  timestamp?: string
  status?: number
  code?: string
  message?: string
  path?: string
  fieldErrors?: Record<string, string>
}

export interface NormalizedApiError {
  status?: number
  code?: string
  message: string
  fieldErrors: Record<string, string>
}
