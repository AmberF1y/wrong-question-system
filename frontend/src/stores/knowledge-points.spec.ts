import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { getKnowledgePointTree } from '../api/knowledge-points'
import { useKnowledgePointStore } from './knowledge-points'

vi.mock('../api/knowledge-points', () => ({
  getKnowledgePointTree: vi.fn(),
}))

const mockedGetTree = vi.mocked(getKnowledgePointTree)

describe('knowledge point store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockedGetTree.mockReset()
  })

  it('loads and derives roots and the node map', async () => {
    mockedGetTree.mockResolvedValue([
      {
        id: 1,
        name: '408',
        parentId: null,
        children: [{ id: 2, name: '计网', parentId: 1, children: [] }],
      },
    ])
    const store = useKnowledgePointStore()

    await store.load()

    expect(store.loaded).toBe(true)
    expect(store.roots).toHaveLength(1)
    expect(store.nodeMap.get(2)?.name).toBe('计网')
  })

  it('does not reload an already loaded tree without force', async () => {
    mockedGetTree.mockResolvedValue([])
    const store = useKnowledgePointStore()

    await store.load()
    await store.load()

    expect(mockedGetTree).toHaveBeenCalledTimes(1)
  })

  it('records a readable error and allows a later retry', async () => {
    mockedGetTree
      .mockRejectedValueOnce(new Error('加载失败'))
      .mockResolvedValueOnce([])
    const store = useKnowledgePointStore()

    await expect(store.load()).rejects.toThrow('加载失败')
    expect(store.errorMessage).toBe('加载失败')
    expect(store.loaded).toBe(false)

    await store.load()
    expect(store.loaded).toBe(true)
    expect(store.errorMessage).toBe('')
  })
})
