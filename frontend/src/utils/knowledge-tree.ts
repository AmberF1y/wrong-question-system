import type { KnowledgePointTreeNode } from '../types/knowledge-point'

export function flattenKnowledgeTree(
  tree: KnowledgePointTreeNode[],
): KnowledgePointTreeNode[] {
  return tree.flatMap((node) => [node, ...flattenKnowledgeTree(node.children)])
}

export function createKnowledgeNodeMap(
  tree: KnowledgePointTreeNode[],
): Map<number, KnowledgePointTreeNode> {
  return new Map(flattenKnowledgeTree(tree).map((node) => [node.id, node]))
}

export function findKnowledgeNode(
  tree: KnowledgePointTreeNode[],
  id: number,
): KnowledgePointTreeNode | undefined {
  for (const node of tree) {
    if (node.id === id) {
      return node
    }

    const found = findKnowledgeNode(node.children, id)
    if (found) {
      return found
    }
  }

  return undefined
}

export function findRootForNode(
  tree: KnowledgePointTreeNode[],
  id: number,
): KnowledgePointTreeNode | undefined {
  for (const root of tree) {
    if (findKnowledgeNode([root], id)) {
      return root
    }
  }

  return undefined
}

export function areKnowledgePointsInSameRoot(
  tree: KnowledgePointTreeNode[],
  ids: number[],
): boolean {
  if (ids.length === 0) {
    return false
  }

  const rootIds = ids.map((id) => findRootForNode(tree, id)?.id)

  if (rootIds.some((id) => id === undefined)) {
    return false
  }

  return new Set(rootIds).size === 1
}

export function collectNodeAndDescendantIds(node: KnowledgePointTreeNode): Set<number> {
  return new Set([
    node.id,
    ...node.children.flatMap((child) => [...collectNodeAndDescendantIds(child)]),
  ])
}

function cloneWithoutExcludedNodes(
  nodes: KnowledgePointTreeNode[],
  excludedIds: Set<number>,
): KnowledgePointTreeNode[] {
  return nodes
    .filter((node) => !excludedIds.has(node.id))
    .map((node) => ({
      ...node,
      children: cloneWithoutExcludedNodes(node.children, excludedIds),
    }))
}

export function buildValidParentTree(
  tree: KnowledgePointTreeNode[],
  nodeId: number,
): KnowledgePointTreeNode[] {
  const node = findKnowledgeNode(tree, nodeId)
  const root = findRootForNode(tree, nodeId)

  if (!node || !root || node.parentId === null) {
    return []
  }

  return cloneWithoutExcludedNodes(
    [root],
    collectNodeAndDescendantIds(node),
  )
}

export function getKnowledgePath(
  tree: KnowledgePointTreeNode[],
  id: number,
): KnowledgePointTreeNode[] {
  function visit(
    nodes: KnowledgePointTreeNode[],
    path: KnowledgePointTreeNode[],
  ): KnowledgePointTreeNode[] | undefined {
    for (const node of nodes) {
      const nextPath = [...path, node]
      if (node.id === id) {
        return nextPath
      }

      const found = visit(node.children, nextPath)
      if (found) {
        return found
      }
    }

    return undefined
  }

  return visit(tree, []) ?? []
}
