export interface KnowledgePoint {
  id: number
  name: string
  parentId: number | null
}

export interface KnowledgePointTreeNode extends KnowledgePoint {
  children: KnowledgePointTreeNode[]
}

export interface CreateKnowledgePointRequest {
  name: string
  parentId: number | null
}

export interface UpdateKnowledgePointRequest {
  name: string
  parentId: number | null
}
