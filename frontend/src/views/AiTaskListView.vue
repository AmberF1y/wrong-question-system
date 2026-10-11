<template>
  <div>
    <AppPageHeader title="AI 任务与调用中心" description="查看识别进度、失败原因和调用用量；完成的草稿可随时继续核对。">
      <template #actions><el-button type="primary" @click="router.push('/ai/intake')">新建识别任务</el-button></template>
    </AppPageHeader>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="ai-gap" />
    <el-alert v-if="stats?.configuration.demo" title="当前为演示模式。演示执行不代表真实识别效果，也不产生模型账单。" type="warning" :closable="false" class="ai-gap" />
    <div v-if="stats" class="ai-metrics ai-gap">
      <el-card shadow="never"><span>任务总数</span><strong>{{ stats.taskCount }}</strong></el-card>
      <el-card shadow="never"><span>正在处理</span><strong>{{ stats.activeCount }}</strong></el-card>
      <el-card shadow="never"><span>草稿完成 / 已保存</span><strong>{{ stats.succeededCount }}</strong></el-card>
      <el-card shadow="never"><span>失败任务</span><strong>{{ stats.failedCount }}</strong></el-card>
      <el-card shadow="never"><span>今日执行额度</span><strong>{{ stats.configuration.callsToday }} / {{ stats.configuration.dailyCallLimit }}</strong></el-card>
    </div>
    <el-card shadow="never" class="ai-gap">
      <div class="ai-toolbar">
        <el-select v-model="statusFilter" clearable placeholder="全部状态" @change="changeFilter">
          <el-option v-for="(label, status) in aiStatusLabels" :key="status" :label="label" :value="status" />
        </el-select>
        <el-button :loading="loading" @click="load">刷新任务</el-button>
        <span class="ai-note">处理中每 5 秒更新</span>
      </div>
      <el-table v-loading="loading" :data="pageData.items" row-key="id" empty-text="暂无识别任务">
        <el-table-column label="图片" min-width="200"><template #default="{ row }"><el-button link type="primary" @click="router.push(`/ai/tasks/${row.id}`)">{{ row.fileName }}</el-button></template></el-table-column>
        <el-table-column label="状态" width="145"><template #default="{ row }"><el-tag :type="tagType(row.status)">{{ aiStatusLabels[row.status as AiTaskStatus] }}</el-tag></template></el-table-column>
        <el-table-column label="模型" min-width="160"><template #default="{ row }">{{ row.provider === 'demo' ? '演示 · ' : '' }}{{ row.model }}</template></el-table-column>
        <el-table-column prop="attemptCount" label="执行次数" width="95" />
        <el-table-column label="创建时间" min-width="165"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="175"><template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/ai/tasks/${row.id}`)">查看</el-button>
          <el-button v-if="isAiTaskActive(row as AiTask)" link type="warning" :disabled="!!actionId" @click="mutate(row as AiTask, 'cancel')">取消</el-button>
          <el-button v-else-if="['FAILED', 'CANCELLED'].includes(row.status)" link type="primary" :disabled="!!actionId || !stats?.configuration.enabled || row.retryCount >= (stats?.configuration.maxManualRetries ?? 0)" @click="mutate(row as AiTask, 'retry')">重试</el-button>
          <el-button v-if="!isAiTaskActive(row as AiTask)" link type="danger" :disabled="!!actionId" @click="remove(row as AiTask)">删除</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination v-if="pageData.totalElements" class="ai-pagination" :current-page="page + 1" :page-size="20" :total="pageData.totalElements" layout="total, prev, pager, next" @current-change="changePage" />
    </el-card>
    <el-card v-if="stats" shadow="never">
      <strong>累计调用记录</strong>
      <p class="ai-note">执行 {{ stats.callCount }} 次，失败或中断 {{ stats.failedCallCount }} 次；已报告输入 / 输出 token：{{ stats.inputTokens ?? '未报告' }} / {{ stats.outputTokens ?? '未报告' }}。</p>
      <p class="ai-note">有记录的平均耗时：{{ stats.averageDurationMs == null ? '暂无' : `${(stats.averageDurationMs / 1000).toFixed(2)} 秒` }}。已知用量的累计成本估算：{{ stats.estimatedCost ?? '未配置单价或未报告用量' }}。单价币种由服务端配置约定，估算不等于账单。</p>
    </el-card>
    <AiCallHistory :refresh-key="callsRefresh" @completed="load" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { cancelAiTask, deleteAiTask, getAiStatistics, getAiTasks, retryAiTask } from '../api/ai'
import AppPageHeader from '../components/AppPageHeader.vue'
import AiCallHistory from '../components/AiCallHistory.vue'
import { aiStatusLabels, isAiTaskActive, type AiStatistics, type AiTask, type AiTaskPage, type AiTaskStatus } from '../types/ai'
import { normalizeApiError, isResponseUnavailableError } from '../utils/api-error'
import { formatDateTime } from '../utils/date-time'

const router = useRouter()
const page = ref(0)
const statusFilter = ref<AiTaskStatus>()
const pageData = ref<AiTaskPage>({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const stats = ref<AiStatistics>()
const loading = ref(false)
const actionId = ref('')
const error = ref('')
const callsRefresh = ref(0)
let sequence = 0
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false
function tagType(status: AiTaskStatus): 'success' | 'danger' | 'warning' | 'info' {
  return status === 'FAILED' ? 'danger' : ['APPLIED', 'SUCCEEDED'].includes(status) ? 'success' : status === 'CANCELLED' ? 'info' : 'warning'
}
async function load(): Promise<void> {
  const request = ++sequence
  if (timer) clearTimeout(timer)
  loading.value = true; error.value = ''
  try {
    const [tasks, summary] = await Promise.all([getAiTasks(page.value, statusFilter.value), getAiStatistics()])
    if (disposed || request !== sequence) return
    pageData.value = tasks; stats.value = summary; callsRefresh.value += 1
    if (page.value > 0 && !tasks.items.length) { page.value = 0; await load(); return }
  } catch (cause) { if (request === sequence) error.value = normalizeApiError(cause).message }
  finally {
    if (!disposed && request === sequence) {
      loading.value = false
      if (stats.value?.activeCount) timer = setTimeout(() => void load(), 5000)
    }
  }
}
function changeFilter(): void { page.value = 0; void load() }
function changePage(value: number): void { page.value = value - 1; void load() }
async function mutate(task: AiTask, action: 'retry' | 'cancel'): Promise<void> {
  if (actionId.value) return
  actionId.value = task.id
  try {
    await (action === 'retry' ? retryAiTask(task.id, task.retryCount) : cancelAiTask(task.id))
    ElMessage.success(action === 'retry' ? '任务已重新排队' : '已取消任务；已发出的模型调用可能仍会计费')
  } catch (cause) { ElMessage.error(isResponseUnavailableError(cause) ? '操作响应尚未确认，请核对最新任务状态后再操作' : normalizeApiError(cause).message) }
  finally { await load(); actionId.value = '' }
}
async function remove(task: AiTask): Promise<void> {
  try { await ElMessageBox.confirm('将删除任务、识别原图与调用记录，已经保存的错题会保留。', '删除识别任务', { type: 'warning' }) } catch { return }
  if (actionId.value) return
  actionId.value = task.id
  try { await deleteAiTask(task.id); ElMessage.success('任务已删除') }
  catch (cause) { ElMessage.error(normalizeApiError(cause).message) }
  finally { await load(); actionId.value = '' }
}
onMounted(() => void load())
onBeforeUnmount(() => { disposed = true; sequence += 1; if (timer) clearTimeout(timer) })
</script>

<style scoped>
.ai-gap { margin-bottom: 18px; }
.ai-metrics { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.ai-metrics span { color: var(--app-muted); font-size: .85rem; }
.ai-metrics strong { display: block; margin-top: 10px; font-size: 1.6rem; color: var(--app-navy); }
.ai-toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 18px; flex-wrap: wrap; }
.ai-toolbar .el-select { width: 200px; }
.ai-note { color: var(--app-muted); line-height: 1.7; }
.ai-pagination { margin-top: 20px; justify-content: flex-end; }
@media (max-width: 1100px) { .ai-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
</style>
