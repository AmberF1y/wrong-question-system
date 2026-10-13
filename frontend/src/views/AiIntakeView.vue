<template>
  <div>
    <AppPageHeader title="AI 图片辅助录入" description="从题目截图生成可编辑草稿，核对原图后再保存为错题。">
      <template #actions><el-button @click="router.push('/ai/tasks')">查看识别任务</el-button></template>
    </AppPageHeader>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="ai-gap" />
    <el-alert v-if="configuration?.demo" title="当前为演示模式：生成模拟草稿，不会识别图片内容或调用外部模型。" type="warning" :closable="false" show-icon class="ai-gap" />
    <el-alert v-else-if="configuration && !configuration.enabled" title="AI 识别尚未配置，可以继续手工录入错题。" type="info" :closable="false" show-icon class="ai-gap" />
    <el-card v-if="pendingId" shadow="never" class="ai-gap" data-testid="pending-task">
      <p>上次提交的响应尚未确认。先查找服务器上的任务，避免重复识别。</p>
      <el-button type="primary" :loading="busy" @click="recover">查找上次任务</el-button>
      <el-button v-if="confirmedMissing" :disabled="busy" @click="clearPending">任务不存在，重新选择图片</el-button>
    </el-card>
    <el-card shadow="never" class="ai-gap">
      <el-form label-position="top">
        <el-form-item label="科目范围（可选）">
          <el-select v-model="rootId" clearable placeholder="全部科目" :disabled="busy || !!pendingId">
            <el-option v-for="root in knowledgeStore.roots" :key="root.id" :label="root.name" :value="root.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目截图">
          <QuestionImageField :disabled="busy || !!pendingId" @change="file = $event.file" />
        </el-form-item>
      </el-form>
      <p class="ai-note">识别请求会把这张图片和候选知识点名称发送给已配置的模型服务。未出现在图片中的答案和解析需要自行补充。</p>
      <p v-if="configuration" class="ai-note">今日执行次数 {{ configuration.callsToday }} / {{ configuration.dailyCallLimit }}；失败后的自动重试也占用额度。</p>
      <el-button type="primary" :loading="busy" :disabled="!file || !configuration?.enabled || !!pendingId" data-testid="start-recognition" @click="submit">生成识别草稿</el-button>
      <el-button :disabled="busy" @click="loadConfiguration">刷新服务状态</el-button>
      <el-button :disabled="busy" @click="router.push('/questions/new')">手工录入</el-button>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createAiTask, getAiConfiguration, getAiTask } from '../api/ai'
import AppPageHeader from '../components/AppPageHeader.vue'
import QuestionImageField from '../components/QuestionImageField.vue'
import { useKnowledgePointStore } from '../stores/knowledge-points'
import type { AiConfiguration } from '../types/ai'
import { isNotFoundError, isResponseUnavailableError, normalizeApiError } from '../utils/api-error'

const PENDING_KEY = 'wrongquestion.ai.pending-task'
const router = useRouter()
const knowledgeStore = useKnowledgePointStore()
const configuration = ref<AiConfiguration>()
const rootId = ref<number>()
const file = ref<File | null>(null)
const busy = ref(false)
const error = ref('')
const pendingId = ref('')
const confirmedMissing = ref(false)

function remember(id: string): void {
  pendingId.value = id
  try { localStorage.setItem(PENDING_KEY, id) } catch { /* Server task remains accessible in the task center. */ }
}
function clearPending(): void {
  pendingId.value = ''; confirmedMissing.value = false
  try { localStorage.removeItem(PENDING_KEY) } catch { /* Storage may be disabled. */ }
}
async function loadConfiguration(): Promise<void> {
  error.value = ''
  try {
    configuration.value = await getAiConfiguration()
    await knowledgeStore.load()
  } catch (cause) { error.value = normalizeApiError(cause).message }
}
async function submit(): Promise<void> {
  if (busy.value || pendingId.value || !file.value || !configuration.value?.enabled) return
  busy.value = true; error.value = ''; confirmedMissing.value = false
  const id = crypto.randomUUID()
  remember(id)
  try {
    const task = await createAiTask(id, file.value, rootId.value)
    clearPending()
    await router.push(`/ai/tasks/${task.id}`)
  } catch (cause) {
    error.value = normalizeApiError(cause).message
    if (!isResponseUnavailableError(cause)) clearPending()
    else error.value = '提交结果尚未确认，请查找上次任务；系统不会自动重新提交图片。'
  } finally { busy.value = false }
}
async function recover(): Promise<void> {
  if (busy.value || !pendingId.value) return
  busy.value = true; error.value = ''; confirmedMissing.value = false
  try {
    const task = await getAiTask(pendingId.value)
    clearPending()
    await router.push(`/ai/tasks/${task.id}`)
  } catch (cause) {
    confirmedMissing.value = isNotFoundError(cause)
    error.value = confirmedMissing.value ? '服务器未找到这个任务，请确认后端已恢复，并在任务中心核对后再重新提交。' : normalizeApiError(cause).message
  } finally { busy.value = false }
}
onMounted(() => {
  try { const saved = localStorage.getItem(PENDING_KEY); if (saved && /^[0-9a-f-]{36}$/.test(saved)) pendingId.value = saved } catch { /* Optional local recovery. */ }
  void loadConfiguration()
})
</script>

<style scoped>
.ai-gap { margin-bottom: 18px; }
.ai-note { color: var(--app-muted); line-height: 1.7; }
</style>
