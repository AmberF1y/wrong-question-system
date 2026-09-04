import { describe, expect, it } from 'vitest'
import type { KnowledgePointTreeNode } from '../types/knowledge-point'
import {
  areKnowledgePointsInSameRoot,
  buildValidParentTree,
  collectNodeAndDescendantIds,
  createKnowledgeNodeMap,
  findKnowledgeNode,
  findRootForNode,
  getKnowledgePath,
} from './knowledge-tree'

const tree: KnowledgePointTreeNode[] = [
  {
    id: 1,
    name: '408',
    parentId: null,
    children: [
      {
        id: 2,
        name: '计算机网络',
        parentId: 1,
        children: [
          {
            id: 3,
            name: 'TCP',
            parentId: 2,
            children: [],
          },
        ],
      },
      {
        id: 4,
        name: '操作系统',
        parentId: 1,
        children: [],
      },
    ],
  },
  {
    id: 10,
    name: '数学',
    parentId: null,
    children: [
      {
        id: 11,
        name: '高等数学',
        parentId: 10,
        children: [],
      },
    ],
  },
]

describe('knowledge-tree', () => {
  it('creates an id-to-node map for every level', () => {
    const nodeMap = createKnowledgeNodeMap(tree)

    expect(nodeMap.size).toBe(6)
    expect(nodeMap.get(3)?.name).toBe('TCP')
  })

  it('finds a node and its root', () => {
    expect(findKnowledgeNode(tree, 3)?.name).toBe('TCP')
    expect(findRootForNode(tree, 3)?.name).toBe('408')
    expect(findKnowledgeNode(tree, 999)).toBeUndefined()
  })

  it('accepts parent and child selections under the same root', () => {
    expect(areKnowledgePointsInSameRoot(tree, [1, 2, 3])).toBe(true)
    expect(areKnowledgePointsInSameRoot(tree, [3, 4])).toBe(true)
  })

  it('rejects empty, missing and cross-root selections', () => {
    expect(areKnowledgePointsInSameRoot(tree, [])).toBe(false)
    expect(areKnowledgePointsInSameRoot(tree, [3, 11])).toBe(false)
    expect(areKnowledgePointsInSameRoot(tree, [3, 999])).toBe(false)
  })

  it('collects the selected node and all descendants', () => {
    const node = findKnowledgeNode(tree, 2)
    expect(node).toBeDefined()
    expect([...collectNodeAndDescendantIds(node!)]).toEqual([2, 3])
  })

  it('keeps only legal same-tree parents when moving a node', () => {
    const candidates = buildValidParentTree(tree, 2)
    const candidateMap = createKnowledgeNodeMap(candidates)

    expect(candidateMap.has(1)).toBe(true)
    expect(candidateMap.has(4)).toBe(true)
    expect(candidateMap.has(2)).toBe(false)
    expect(candidateMap.has(3)).toBe(false)
    expect(candidateMap.has(10)).toBe(false)
  })

  it('does not provide parent candidates for a root node', () => {
    expect(buildValidParentTree(tree, 1)).toEqual([])
  })

  it('builds the full display path', () => {
    expect(getKnowledgePath(tree, 3).map((node) => node.name)).toEqual([
      '408',
      '计算机网络',
      'TCP',
    ])
    expect(getKnowledgePath(tree, 999)).toEqual([])
  })
})
