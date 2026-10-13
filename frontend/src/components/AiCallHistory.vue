<template>
  <el-card shadow="never" class="call-history">
    <template #header><strong>调用明细</strong></template>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <div class="call-history__toolbar">
      <el-select v-model="status" clearable placeholder="全部调用状态" @change="changeFilter">
        <el-option v-for="(label, value) in aiCallStatusLabels" :key="value" :label="label" :value="value" />
      </el-select>
      <el-button :loading="loading" @click="load">刷新调用明细</el-button>
    </div>
    <el-table v-loading="loading" :data="data.items" empty-text="暂无调用记录">
      <el-table-column label="任务" min-width="180"><template #default="{ row }"><el-button link type="primary" @click="router.push(row.targetPath ?? `/ai/tasks/${row.taskId}`)">{{ row.fileName }}</el-button></template></el-table-column>
      <el-table-column label="用途" width="100"><template #default="{ row }">{{ row.purpose === 'EMBEDDING' ? '向量检索' : row.purpose === 'GENERATION' ? '学习问答' : '图片识别' }}</template></el-table-column>
      <el-table-column label="模型" min-width="150"><template #default="{ row }">{{ row.call.provider === 'demo' ? '演示 · ' : '' }}{{ row.call.model }}</template></el-table-column>
      <el-table-column label="次数" width="70"><template #default="{ row }">{{ row.call.attempt }}</template></el-table-column>
      <el-table-column label="状态" width="130"><template #default="{ row }">{{ aiCallStatusLabels[row.call.status] ?? row.call.status }}</template></el-table-column>
      <el-table-column label="耗时" width="110"><template #default="{ row }">{{ row.call.durationMs == null ? '未完成' : `${(row.call.durationMs / 1000).toFixed(2)} 秒` }}</template></el-table-column>
      <el-table-column label="输入 / 输出 token" min-width="150"><template #default="{ row }">{{ row.call.inputTokens ?? '未报告' }} / {{ row.call.outputTokens ?? '未报告' }}</template></el-table-column>
      <el-table-column label="成本估算" min-width="115"><template #default="{ row }">{{ row.call.estimatedCost ?? '未知' }}</template></el-table-column>
      <el-table-column label="错误代码" min-width="190"><template #default="{ row }">{{ row.call.errorCode ?? '—' }}</template></el-table-column>
      <el-table-column label="开始时间" min-width="170"><template #default="{ row }">{{ formatDateTime(row.call.createdAt) }}</template></el-table-column>
    </el-table>
    <el-pagination v-if="data.totalElements" :current-page="page + 1" :page-size="20" :total="data.totalElements" layout="total, prev, pager, next" class="call-history__pagination" @current-change="changePage" />
    <p class="call-history__note">仅汇总仍保留的任务和调用记录。删除记录不会返还今日额度；未知用量和成本不会按零计算。</p>
  </el-card>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getAllAiCalls } from '../api/ai'
import { aiCallStatusLabels, type AiCallPage } from '../types/ai'
import { normalizeApiError } from '../utils/api-error'
import { formatDateTime } from '../utils/date-time'

const props = defineProps<{ refreshKey: number }>()
const emit = defineEmits<{ completed: [] }>()
const router = useRouter()
const page = ref(0)
const status = ref<string>()
const data = ref<AiCallPage>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const error = ref('')
const loading = ref(false)
let sequence = 0
let disposed = false
let timer: ReturnType<typeof setTimeout> | undefined
async function load(): Promise<void> {
  const request = ++sequence
  loading.value = true; error.value = ''
  if (timer) clearTimeout(timer)
  try {
    const result = await getAllAiCalls(page.value, status.value)
    if (disposed || request !== sequence) return
    const wasRunning = data.value.items.some(entry => entry.call.status === 'RUNNING')
    data.value = result
    if (wasRunning && !result.items.some(entry => entry.call.status === 'RUNNING')) emit('completed')
    if (page.value > 0 && !result.items.length) { page.value = 0; await load() }
  } catch (cause) { if (!disposed && request === sequence) error.value = normalizeApiError(cause).message }
  finally {
    if (!disposed && request === sequence) {
      loading.value = false
      // A cancelled task may still have a sent model request in flight.
      if (data.value.items.some(entry => entry.call.status === 'RUNNING')) timer = setTimeout(() => void load(), 5000)
    }
  }
}
function changeFilter(): void { page.value = 0; void load() }
function changePage(value: number): void { page.value = value - 1; void load() }
watch(() => props.refreshKey, () => void load(), { immediate: true })
onBeforeUnmount(() => { disposed = true; sequence += 1; if (timer) clearTimeout(timer) })
</script>

<style scoped>
.call-history { margin-top: 18px; }
.call-history__toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin: 12px 0; }
.call-history__toolbar .el-select { width: 200px; }
.call-history__pagination { margin-top: 20px; justify-content: flex-end; }
.call-history__note { color: var(--app-muted); line-height: 1.7; }
</style>
