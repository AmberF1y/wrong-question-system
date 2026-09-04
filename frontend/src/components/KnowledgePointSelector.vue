<template>
  <div class="knowledge-selector">
    <el-tree-select
      :model-value="modelValue"
      :data="tree"
      :props="treeProps"
      node-key="id"
      multiple
      show-checkbox
      check-strictly
      filterable
      clearable
      collapse-tags
      collapse-tags-tooltip
      :render-after-expand="false"
      :disabled="disabled"
      placeholder="请选择同一科目下的一个或多个知识点"
      class="knowledge-selector__control"
      @update:model-value="updateValue"
    />
    <p v-if="selectedSubject" class="knowledge-selector__subject">
      所属科目：{{ selectedSubject }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { KnowledgePointTreeNode } from '../types/knowledge-point'
import {
  areKnowledgePointsInSameRoot,
  findRootForNode,
} from '../utils/knowledge-tree'

const props = defineProps<{
  modelValue: number[]
  tree: KnowledgePointTreeNode[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
}>()

const treeProps = {
  value: 'id',
  label: 'name',
  children: 'children',
}

const selectedSubject = computed(() => {
  if (!areKnowledgePointsInSameRoot(props.tree, props.modelValue)) {
    return ''
  }

  return findRootForNode(props.tree, props.modelValue[0])?.name ?? ''
})

function updateValue(value: unknown): void {
  if (!Array.isArray(value)) {
    emit('update:modelValue', [])
    return
  }

  emit(
    'update:modelValue',
    value.filter((id): id is number => typeof id === 'number'),
  )
}
</script>

<style scoped>
.knowledge-selector__control {
  width: 100%;
}

.knowledge-selector__subject {
  margin: 7px 0 0;
  font-size: 0.86rem;
  line-height: 1.5;
}

.knowledge-selector__subject {
  color: var(--app-muted);
}

</style>
