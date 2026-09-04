<template>
  <div>
    <AppPageHeader
      title="知识点管理"
      description="维护可扩展的知识树；错题所属科目由根知识点自动确定。"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="store.loading" @click="loadTree">
          刷新
        </el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateRoot">
          新建根知识点
        </el-button>
      </template>
    </AppPageHeader>

    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-card class="page-card" shadow="never">
      <div class="knowledge-layout">
        <section class="knowledge-tree-panel" aria-label="知识树">
          <div class="panel-heading">
            <strong>知识树</strong>
            <span>{{ store.nodeMap.size }} 个节点</span>
          </div>

          <el-skeleton v-if="store.loading && !store.loaded" :rows="7" animated />

          <AppEmptyState
            v-else-if="store.tree.length === 0"
            description="还没有知识点，请先创建一个科目根节点"
          >
            <el-button type="primary" @click="openCreateRoot">新建根知识点</el-button>
          </AppEmptyState>

          <el-tree
            v-else
            ref="treeRef"
            :data="store.tree"
            :props="treeProps"
            node-key="id"
            highlight-current
            default-expand-all
            :expand-on-click-node="false"
            @node-click="selectNode"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <span>{{ data.name }}</span>
                <el-tag v-if="data.parentId === null" size="small" effect="plain">
                  科目
                </el-tag>
              </span>
            </template>
          </el-tree>
        </section>

        <section class="knowledge-detail-panel" aria-label="选中知识点详情">
          <template v-if="selectedNode">
            <div class="panel-heading">
              <strong>节点详情</strong>
              <span>ID {{ selectedNode.id }}</span>
            </div>

            <dl class="node-details">
              <div>
                <dt>名称</dt>
                <dd>{{ selectedNode.name }}</dd>
              </div>
              <div>
                <dt>类型</dt>
                <dd>{{ selectedNode.parentId === null ? '科目根节点' : '普通知识点' }}</dd>
              </div>
              <div>
                <dt>路径</dt>
                <dd>{{ selectedPath }}</dd>
              </div>
              <div>
                <dt>直接子节点</dt>
                <dd>{{ selectedNode.children.length }}</dd>
              </div>
            </dl>

            <div class="node-actions">
              <el-button type="primary" :icon="Plus" @click="openCreateChild">
                新建子节点
              </el-button>
              <el-button :icon="Edit" @click="openEdit">修改</el-button>
              <el-button
                type="danger"
                plain
                :icon="Delete"
                :loading="deleting"
                @click="removeSelectedNode"
              >
                删除
              </el-button>
            </div>

            <el-alert
              title="知识点存在子节点或被错题引用时，后端会拒绝删除。"
              type="info"
              :closable="false"
              show-icon
            />
          </template>

          <AppEmptyState v-else description="请从左侧选择一个知识点" />
        </section>
      </div>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="min(520px, calc(100vw - 32px))"
      destroy-on-close
      @closed="resetDialog"
    >
      <el-form
        ref="dialogFormRef"
        :model="dialogForm"
        label-position="top"
        @submit.prevent="saveDialog"
      >
        <el-form-item label="名称" :error="dialogFieldError">
          <el-input
            v-model="dialogForm.name"
            :maxlength="dialogNameLimit"
            show-word-limit
            autofocus
            :disabled="saving"
            placeholder="输入知识点名称"
          />
        </el-form-item>

        <el-form-item
          v-if="dialogMode === 'edit' && selectedNode?.parentId !== null"
          label="父节点"
          :error="dialogParentError"
        >
          <el-tree-select
            v-model="dialogForm.parentId"
            :data="validParentTree"
            :props="treeProps"
            node-key="id"
            check-strictly
            default-expand-all
            :render-after-expand="false"
            :disabled="saving"
            class="parent-selector"
          />
        </el-form-item>

        <p v-if="dialogParentDescription" class="dialog-description">
          {{ dialogParentDescription }}
        </p>
      </el-form>

      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDialog">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, TreeInstance } from 'element-plus'
import {
  createKnowledgePoint,
  deleteKnowledgePoint,
  updateKnowledgePoint,
} from '../api/knowledge-points'
import AppEmptyState from '../components/AppEmptyState.vue'
import AppPageHeader from '../components/AppPageHeader.vue'
import { useKnowledgePointStore } from '../stores/knowledge-points'
import type { KnowledgePointTreeNode } from '../types/knowledge-point'
import { normalizeApiError } from '../utils/api-error'
import { buildValidParentTree, getKnowledgePath } from '../utils/knowledge-tree'

type DialogMode = 'create-root' | 'create-child' | 'edit'

const store = useKnowledgePointStore()
const treeRef = ref<TreeInstance>()
const dialogFormRef = ref<FormInstance>()
const selectedId = ref<number>()
const pageError = ref('')
const dialogVisible = ref(false)
const dialogMode = ref<DialogMode>('create-root')
const dialogFieldError = ref('')
const dialogParentError = ref('')
const saving = ref(false)
const deleting = ref(false)

const treeProps = {
  value: 'id',
  label: 'name',
  children: 'children',
}

const dialogForm = reactive({
  name: '',
  parentId: null as number | null,
})

const selectedNode = computed(() => {
  return selectedId.value === undefined ? undefined : store.nodeMap.get(selectedId.value)
})

const selectedPath = computed(() => {
  if (!selectedNode.value) {
    return ''
  }

  return getKnowledgePath(store.tree, selectedNode.value.id)
    .map((node) => node.name)
    .join(' / ')
})

const validParentTree = computed(() => {
  if (!selectedNode.value) {
    return []
  }

  return buildValidParentTree(store.tree, selectedNode.value.id)
})

const dialogTitle = computed(() => {
  if (dialogMode.value === 'create-root') {
    return '新建科目根节点'
  }

  if (dialogMode.value === 'create-child') {
    return '新建子知识点'
  }

  return '修改知识点'
})

const dialogNameLimit = computed(() => {
  if (dialogMode.value === 'create-root') {
    return 50
  }

  if (dialogMode.value === 'edit' && selectedNode.value?.parentId === null) {
    return 50
  }

  return 100
})

const dialogParentDescription = computed(() => {
  if (dialogMode.value === 'create-child') {
    return selectedNode.value ? `将创建在“${selectedNode.value.name}”下面。` : ''
  }

  if (dialogMode.value === 'edit' && selectedNode.value?.parentId === null) {
    return '根节点只能改名，不能变成普通节点。'
  }

  return ''
})

async function loadTree(): Promise<void> {
  pageError.value = ''
  try {
    await store.refresh()
    await nextTick()
    if (selectedId.value && store.nodeMap.has(selectedId.value)) {
      treeRef.value?.setCurrentKey(selectedId.value)
    } else {
      selectedId.value = undefined
    }
  } catch (error) {
    pageError.value = normalizeApiError(error).message
  }
}

function selectNode(node: KnowledgePointTreeNode): void {
  selectedId.value = node.id
}

function openCreateRoot(): void {
  dialogMode.value = 'create-root'
  dialogForm.name = ''
  dialogForm.parentId = null
  dialogVisible.value = true
}

function openCreateChild(): void {
  if (!selectedNode.value) {
    return
  }

  dialogMode.value = 'create-child'
  dialogForm.name = ''
  dialogForm.parentId = selectedNode.value.id
  dialogVisible.value = true
}

function openEdit(): void {
  if (!selectedNode.value) {
    return
  }

  dialogMode.value = 'edit'
  dialogForm.name = selectedNode.value.name
  dialogForm.parentId = selectedNode.value.parentId
  dialogVisible.value = true
}

function resetDialog(): void {
  dialogFieldError.value = ''
  dialogParentError.value = ''
  dialogFormRef.value?.clearValidate()
}

async function saveDialog(): Promise<void> {
  const name = dialogForm.name.trim()
  dialogFieldError.value = ''
  dialogParentError.value = ''

  if (!name) {
    dialogFieldError.value = '知识点名称不能为空'
    return
  }

  if (name.length > dialogNameLimit.value) {
    dialogFieldError.value = `知识点名称不能超过${dialogNameLimit.value}个字符`
    return
  }

  if (dialogMode.value === 'edit' && selectedNode.value?.parentId !== null && dialogForm.parentId === null) {
    dialogParentError.value = '普通知识点必须选择父节点'
    return
  }

  saving.value = true

  try {
    let savedId: number

    if (dialogMode.value === 'edit' && selectedNode.value) {
      const result = await updateKnowledgePoint(selectedNode.value.id, {
        name,
        parentId: selectedNode.value.parentId === null ? null : dialogForm.parentId,
      })
      savedId = result.id
      ElMessage.success('知识点修改成功')
    } else {
      const result = await createKnowledgePoint({
        name,
        parentId: dialogMode.value === 'create-root' ? null : selectedNode.value?.id ?? null,
      })
      savedId = result.id
      ElMessage.success('知识点创建成功')
    }

    dialogVisible.value = false
    await store.refresh()
    selectedId.value = savedId
    await nextTick()
    treeRef.value?.setCurrentKey(savedId)
  } catch (error) {
    const normalized = normalizeApiError(error)
    dialogFieldError.value = normalized.fieldErrors.name ?? ''
    ElMessage.error(normalized.message)
  } finally {
    saving.value = false
  }
}

async function removeSelectedNode(): Promise<void> {
  if (!selectedNode.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定删除“${selectedNode.value.name}”吗？有子节点或被错题引用时无法删除。`,
      '删除知识点',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  deleting.value = true

  try {
    const result = await deleteKnowledgePoint(selectedNode.value.id)
    ElMessage.success(result.message)
    selectedId.value = undefined
    await store.refresh()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    deleting.value = false
  }
}

onMounted(loadTree)
</script>

<style scoped>
.page-alert {
  margin-bottom: 18px;
}

.knowledge-layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.9fr) minmax(360px, 1.1fr);
  min-height: 560px;
}

.knowledge-tree-panel,
.knowledge-detail-panel {
  min-width: 0;
  padding: 22px;
}

.knowledge-tree-panel {
  border-right: 1px solid var(--app-border);
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.panel-heading strong {
  color: var(--app-navy);
  font-size: 1rem;
}

.panel-heading span {
  color: var(--app-muted);
  font-size: 0.84rem;
}

.tree-node {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding-right: 8px;
  font-size: 0.94rem;
}

.node-details {
  display: grid;
  gap: 0;
  margin: 0 0 22px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  overflow: hidden;
}

.node-details > div {
  display: grid;
  grid-template-columns: 120px 1fr;
  gap: 16px;
  padding: 13px 15px;
  border-bottom: 1px solid var(--app-border);
}

.node-details > div:last-child {
  border-bottom: 0;
}

dt {
  color: var(--app-muted);
}

dd {
  min-width: 0;
  margin: 0;
  color: #334155;
  overflow-wrap: anywhere;
}

.node-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 22px;
}

.parent-selector {
  width: 100%;
}

.dialog-description {
  margin: -4px 0 0;
  color: var(--app-muted);
  font-size: 0.88rem;
}

@media (max-width: 900px) {
  .knowledge-layout {
    grid-template-columns: 1fr;
  }

  .knowledge-tree-panel {
    border-right: 0;
    border-bottom: 1px solid var(--app-border);
  }
}

@media (max-width: 560px) {
  .node-details > div {
    grid-template-columns: 1fr;
    gap: 4px;
  }
}
</style>
