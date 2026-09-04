import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getKnowledgePointTree } from '../api/knowledge-points'
import type { KnowledgePointTreeNode } from '../types/knowledge-point'
import { normalizeApiError } from '../utils/api-error'
import { createKnowledgeNodeMap } from '../utils/knowledge-tree'

export const useKnowledgePointStore = defineStore('knowledge-points', () => {
  const tree = ref<KnowledgePointTreeNode[]>([])
  const loading = ref(false)
  const loaded = ref(false)
  const errorMessage = ref('')

  const roots = computed(() => tree.value)
  const nodeMap = computed(() => createKnowledgeNodeMap(tree.value))

  async function load(force = false): Promise<void> {
    if (loaded.value && !force) {
      return
    }

    loading.value = true
    errorMessage.value = ''

    try {
      tree.value = await getKnowledgePointTree()
      loaded.value = true
    } catch (error) {
      errorMessage.value = normalizeApiError(error).message
      throw error
    } finally {
      loading.value = false
    }
  }

  async function refresh(): Promise<void> {
    await load(true)
  }

  return {
    tree,
    loading,
    loaded,
    errorMessage,
    roots,
    nodeMap,
    load,
    refresh,
  }
})
