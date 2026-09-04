import http from './http'
import type { MessageResponse } from '../types/api'
import type {
  CreateKnowledgePointRequest,
  KnowledgePoint,
  KnowledgePointTreeNode,
  UpdateKnowledgePointRequest,
} from '../types/knowledge-point'

export async function getKnowledgePointTree(): Promise<KnowledgePointTreeNode[]> {
  const response = await http.get<KnowledgePointTreeNode[]>('/knowledge-points/tree')
  return response.data
}

export async function createKnowledgePoint(
  request: CreateKnowledgePointRequest,
): Promise<KnowledgePoint> {
  const response = await http.post<KnowledgePoint>('/knowledge-points', request)
  return response.data
}

export async function updateKnowledgePoint(
  id: number,
  request: UpdateKnowledgePointRequest,
): Promise<KnowledgePoint> {
  const response = await http.put<KnowledgePoint>(`/knowledge-points/${id}`, request)
  return response.data
}

export async function deleteKnowledgePoint(id: number): Promise<MessageResponse> {
  const response = await http.delete<MessageResponse>(`/knowledge-points/${id}`)
  return response.data
}
