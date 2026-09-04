import http from './http'
import type { HealthResponse } from '../types/api'

export async function getHealth(): Promise<HealthResponse> {
  const response = await http.get<HealthResponse>('/health')
  return response.data
}
