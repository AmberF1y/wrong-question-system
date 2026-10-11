<template>
  <div>
    <AppPageHeader title="识别结果与草稿" description="对照原图核验识别内容，补充个人作答与错误原因，再确认保存。">
      <template #actions><el-button @click="router.push('/ai/tasks')">返回任务中心</el-button></template>
    </AppPageHeader>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="ai-gap" />
    <el-skeleton v-if="loading && !task" :rows="8" animated />
    <template v-if="task">
      <el-card shadow="never" class="ai-gap">
        <div class="ai-toolbar">
          <el-tag :type="task.status === 'FAILED' ? 'danger' : 'info'">{{ aiStatusLabels[task.status] }}</el-tag>
          <span>{{ task.fileName }}</span>
          <el-button :loading="loading" @click="load">刷新任务状态</el-button>
          <el-button v-if="isAiTaskActive(task)" :disabled="actionBusy" @click="mutate('cancel')">取消识别</el-button>
          <el-button v-if="['FAILED', 'CANCELLED'].includes(task.status)" :disabled="actionBusy || !configuration?.enabled || task.retryCount >= (configuration?.maxManualRetries ?? 0)" @click="mutate('retry')">重新识别</el-button>
        </div>
        <p class="ai-note">{{ task.provider === 'demo' ? '演示执行' : task.model }} · 执行 {{ task.attemptCount }} 次 · {{ formatDateTime(task.createdAt) }}</p>
        <el-alert v-if="task.provider === 'demo'" title="这是演示草稿，未进行真实图片识别。" type="warning" :closable="false" show-icon class="ai-gap" />
        <el-alert v-if="task.errorMessage" :title="task.errorMessage" :type="task.status === 'RETRY_WAIT' ? 'warning' : 'error'" :closable="false" show-icon />
        <p v-if="task.status === 'RETRY_WAIT'" class="ai-note">下次尝试时间：{{ formatDateTime(task.availableAt) }}；自动重试最多 {{ configuration?.maxAttempts ?? '有限' }} 次。</p>
        <p v-if="isAiTaskActive(task)" class="ai-note">处理中每 2 秒更新，可以离开页面后从任务中心继续。</p>
        <p v-if="task.status === 'CANCELLED'" class="ai-note">已发出的模型请求可能仍会计费；其结果不会应用到草稿。</p>
        <div v-if="task.status === 'APPLIED'" data-testid="applied-task">
          <p>这份草稿已确认保存。</p>
          <el-button v-if="task.questionId" type="primary" @click="router.push(`/questions/${task.questionId}`)">查看已保存错题</el-button>
          <p v-else>已保存的题目现已删除。</p>
        </div>
      </el-card>
      <el-card shadow="never" class="ai-gap">
        <template #header><strong>识别原图</strong></template>
        <QuestionImageDisplay :src="aiImageUrl(task.id)" />
      </el-card>
      <template v-if="task.status === 'SUCCEEDED' && formValue">
        <el-card v-if="task.draft?.warnings.length" shadow="never" class="ai-gap">
          <strong>识别提示</strong>
          <ul><li v-for="(warning, index) in task.draft.warnings" :key="index">{{ warning }}</li></ul>
        </el-card>
        <el-alert v-if="uncertain" title="保存结果尚未确认。先同步服务器状态，避免重复保存错题。" type="warning" :closable="false" show-icon class="ai-gap" data-testid="apply-uncertain">
          <template #default><el-button :loading="actionBusy" data-testid="sync-apply" @click="syncApply">同步保存结果</el-button></template>
        </el-alert>
        <el-alert v-if="knowledgeError" :title="knowledgeError" type="error" :closable="false" class="ai-gap">
          <template #default><el-button link @click="loadKnowledge">重新加载知识点</el-button></template>
        </el-alert>
        <p class="ai-note">草稿修改会在本浏览器暂存。确认保存时会同时保存题目和原图，并进入现有滚动复习流程。</p>
        <QuestionForm :initial-value="formValue" :knowledge-tree="knowledgeStore.tree" :show-image-field="false"
          :submitting="actionBusy || uncertain" :field-errors="fieldErrors" submit-label="已核对原图，保存错题"
          @change="rememberDraft" @submit="save" @cancel="router.push('/ai/tasks')" />
      </template>
      <el-card shadow="never" class="ai-gap ai-call-card">
        <template #header><strong>调用记录</strong></template>
        <el-alert v-if="callsError" :title="callsError" type="warning" :closable="false" />
        <el-table :data="calls" empty-text="尚未执行模型调用">
          <el-table-column prop="attempt" label="次数" width="70" />
          <el-table-column label="状态" min-width="120"><template #default="{ row }">{{ callLabels[row.status] ?? row.status }}</template></el-table-column>
          <el-table-column label="耗时" width="110"><template #default="{ row }">{{ row.durationMs == null ? '未完成' : `${(row.durationMs / 1000).toFixed(2)} 秒` }}</template></el-table-column>
          <el-table-column label="输入 / 输出 token" min-width="150"><template #default="{ row }">{{ row.inputTokens ?? '未报告' }} / {{ row.outputTokens ?? '未报告' }}</template></el-table-column>
          <el-table-column label="成本估算" min-width="120"><template #default="{ row }">{{ row.estimatedCost ?? '未知' }}</template></el-table-column>
          <el-table-column prop="errorCode" label="错误代码" min-width="190" />
        </el-table>
        <p class="ai-note">模型未报告用量或未配置单价时，成本显示为未知；估算不等于账单。</p>
      </el-card>
    </template>
    <el-button v-else-if="error" @click="load">重新加载</el-button>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { aiImageUrl, applyAiDraft, cancelAiTask, getAiCalls, getAiConfiguration, getAiTask, retryAiTask } from '../api/ai'
import AppPageHeader from '../components/AppPageHeader.vue'
import QuestionForm from '../components/QuestionForm.vue'
import QuestionImageDisplay from '../components/QuestionImageDisplay.vue'
import { useKnowledgePointStore } from '../stores/knowledge-points'
import { aiCallStatusLabels as callLabels, aiStatusLabels, draftToForm, isAiTaskActive, type AiCall, type AiConfiguration, type AiTask } from '../types/ai'
import type { QuestionFormPayload } from '../types/question'
import { isNotFoundError, isResponseUnavailableError, normalizeApiError } from '../utils/api-error'
import { formatDateTime } from '../utils/date-time'

const route = useRoute()
const router = useRouter()
const knowledgeStore = useKnowledgePointStore()
const task = ref<AiTask>()
const configuration = ref<AiConfiguration>()
const calls = ref<AiCall[]>([])
const formValue = ref<QuestionFormPayload>()
const fieldErrors = ref<Record<string, string>>({})
const loading = ref(false)
const actionBusy = ref(false)
const uncertain = ref(false)
const error = ref('')
const callsError = ref('')
const knowledgeError = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
let sequence = 0
let disposed = false
function draftKey(id: string): string { return `wrongquestion.ai.draft.${id}` }
function pendingKey(id: string): string { return `wrongquestion.ai.apply-pending.${id}` }
function validForm(value: unknown): value is QuestionFormPayload {
  if (!value || typeof value !== 'object') return false
  const record = value as Record<string, unknown>
  return ['questionText', 'correctAnswer', 'analysis', 'wrongAnswer', 'errorReason'].every(key => typeof record[key] === 'string') &&
    Array.isArray(record.knowledgePointIds) && record.knowledgePointIds.every(id => Number.isSafeInteger(id) && id > 0)
}
function rememberDraft(value: QuestionFormPayload): void {
  if (!task.value || task.value.status !== 'SUCCEEDED') return
  try { localStorage.setItem(draftKey(task.value.id), JSON.stringify(value)) } catch { /* Server original draft remains available. */ }
}
function clearSaved(id: string): void {
  try { localStorage.removeItem(draftKey(id)); localStorage.removeItem(pendingKey(id)) } catch { /* Optional local storage. */ }
}
function acceptTask(value: AiTask): void {
  task.value = value
  if (value.status === 'APPLIED') { clearSaved(value.id); uncertain.value = false; return }
  if (value.status === 'SUCCEEDED' && value.draft && !formValue.value) {
    let initial = draftToForm(value.draft)
    try { const saved = JSON.parse(localStorage.getItem(draftKey(value.id)) ?? 'null'); if (validForm(saved)) initial = saved } catch { /* Use server draft if browser cache is invalid. */ }
    formValue.value = initial
  }
}
async function loadKnowledge(): Promise<void> {
  knowledgeError.value = ''
  try { await knowledgeStore.load() } catch (cause) { knowledgeError.value = normalizeApiError(cause).message }
}
async function load(): Promise<void> {
  const id = String(route.params.id ?? '')
  if (!/^[0-9a-f-]{36}$/.test(id)) { error.value = '任务标识不正确'; return }
  const request = ++sequence
  if (timer) clearTimeout(timer)
  loading.value = true; error.value = ''
  try {
    const value = await getAiTask(id)
    if (disposed || request !== sequence) return
    acceptTask(value)
    try {
      const latestCalls = await getAiCalls(id)
      if (!disposed && request === sequence) { calls.value = latestCalls; callsError.value = '' }
    } catch (cause) { if (!disposed && request === sequence) callsError.value = normalizeApiError(cause).message }
  } catch (cause) {
    if (!disposed && request === sequence) {
      error.value = normalizeApiError(cause).message
      if (isNotFoundError(cause)) { task.value = undefined; formValue.value = undefined; calls.value = [] }
    }
  }
  finally {
    if (!disposed && request === sequence) {
      loading.value = false
      if (task.value && (isAiTaskActive(task.value) || calls.value.some(call => call.status === 'RUNNING'))) timer = setTimeout(() => void load(), error.value ? 5000 : 2000)
    }
  }
}
async function mutate(action: 'retry' | 'cancel'): Promise<void> {
  const current = task.value
  if (!current || actionBusy.value) return
  actionBusy.value = true
  try {
    const value = await (action === 'retry' ? retryAiTask(current.id, current.retryCount) : cancelAiTask(current.id))
    if (action === 'retry') { formValue.value = undefined; clearSaved(current.id) }
    acceptTask(value)
  } catch (cause) { ElMessage.error(isResponseUnavailableError(cause) ? '操作结果尚未确认，请核对最新任务状态后再操作' : normalizeApiError(cause).message) }
  finally { await load(); actionBusy.value = false }
}
async function save(payload: QuestionFormPayload): Promise<void> {
  const current = task.value
  if (!current || current.status !== 'SUCCEEDED' || actionBusy.value || uncertain.value) return
  actionBusy.value = true; fieldErrors.value = {}; error.value = ''
  rememberDraft(payload)
  try { localStorage.setItem(pendingKey(current.id), 'true') } catch { /* Server apply operation is independently idempotent. */ }
  try {
    const question = await applyAiDraft(current.id, payload)
    clearSaved(current.id)
    ElMessage.success('错题和原图已保存')
    await router.push(`/questions/${question.id}`)
  } catch (cause) {
    const normalized = normalizeApiError(cause)
    if (isResponseUnavailableError(cause)) uncertain.value = true
    else {
      try { localStorage.removeItem(pendingKey(current.id)) } catch { /* Optional storage. */ }
      fieldErrors.value = normalized.fieldErrors; error.value = normalized.message
    }
  } finally { actionBusy.value = false }
}
async function syncApply(): Promise<void> {
  const current = task.value
  if (!current || actionBusy.value) return
  actionBusy.value = true
  try {
    const latest = await getAiTask(current.id)
    acceptTask(latest)
    if (latest.status === 'APPLIED') {
      ElMessage.success('服务器已保存这份草稿')
      if (latest.questionId) await router.push(`/questions/${latest.questionId}`)
    } else if (latest.status === 'SUCCEEDED') {
      uncertain.value = false
      try { localStorage.removeItem(pendingKey(current.id)) } catch { /* Optional storage. */ }
      error.value = '服务器尚未保存这份草稿，可以核对后重新保存。'
    }
  } catch (cause) { error.value = normalizeApiError(cause).message }
  finally { actionBusy.value = false }
}
watch(() => route.params.id, () => {
  sequence += 1; task.value = undefined; formValue.value = undefined; calls.value = []; error.value = ''; uncertain.value = false
  try { uncertain.value = localStorage.getItem(pendingKey(String(route.params.id))) === 'true' } catch { /* Optional storage. */ }
  void load(); void loadKnowledge()
  void getAiConfiguration().then(value => { configuration.value = value }).catch(() => undefined)
}, { immediate: true })
onBeforeUnmount(() => { disposed = true; sequence += 1; if (timer) clearTimeout(timer) })
</script>

<style scoped>
.ai-gap { margin-bottom: 18px; }
.ai-toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.ai-note { color: var(--app-muted); line-height: 1.7; }
li { line-height: 1.8; }
.ai-call-card { margin-top: 18px; }
</style>
