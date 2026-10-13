<template>
  <div>
    <AppPageHeader title="错题库学习助手" description="从自己的错题、答案和解析中查找依据，带着来源理解知识点。">
      <template #actions><el-button @click="router.push('/ai/tasks')">查看 AI 调用明细</el-button></template>
    </AppPageHeader>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="rag-gap" />
    <el-alert v-if="configuration && !configuration.generationEnabled" :title="configuration.demo ? '演示模式：仅检索真实错题原文，未调用回答模型。' : '尚未配置回答模型：可以检索自己的错题原文，配置模型后可获得带来源的回答。'" type="info" :closable="false" class="rag-gap" />
    <el-alert v-if="pendingId" title="上次请求的响应尚未确认。先同步处理结果，避免重复调用。" type="warning" :closable="false" class="rag-gap" data-testid="rag-pending">
      <el-button :loading="busy" @click="synchronize">同步结果</el-button>
      <el-button v-if="pendingMissing" @click="clearPending">清除未找到的请求</el-button>
    </el-alert>
    <div class="rag-grid">
      <aside>
        <el-card shadow="never">
          <template #header><div class="rag-row"><strong>学习对话</strong><el-button :disabled="busy || !!pendingId" @click="newSession">新对话</el-button></div></template>
          <p v-if="!sessions.items.length" class="rag-note">提问后会保存对话，可随时回来继续。</p>
          <button v-for="item in sessions.items" :key="item.id" class="rag-session" :class="{ selected: item.id === sessionId }" :disabled="busy || !!pendingId" @click="selectSession(item.id)">{{ item.title }}</button>
          <el-pagination v-if="sessions.totalPages > 1" small :current-page="sessionPage + 1" :page-size="20" :total="sessions.totalElements" layout="prev, next" @current-change="changeSessionPage" />
          <el-button v-if="sessionId" link type="danger" :disabled="busy || active || !!pendingId" @click="removeSession">删除当前对话</el-button>
        </el-card>
        <el-card shadow="never" class="rag-gap">
          <template #header><strong>错题资料索引</strong></template>
          <p v-if="indexState" class="rag-note">当前科目 {{ indexState.questionCount }} 道题，已索引 {{ indexState.indexedQuestionCount }} 道，待更新 {{ indexState.staleQuestionCount }} 道。向量片段 {{ indexState.embeddedChunkCount }} / {{ indexState.chunkCount }}。</p>
          <p class="rag-note">提问时自动刷新文本索引。启用语义检索后，更新向量会占用 AI 调用额度。</p>
          <el-button :disabled="busy || !!pendingId || (indexRequest && isRagActive(indexRequest))" data-testid="rag-build" @click="buildIndex">更新索引</el-button>
          <div v-if="indexRequest" class="rag-note"><p>{{ ragStatus[indexRequest.status] }} · {{ ragPhase[indexRequest.phase] }}</p><p>{{ indexRequest.answer ?? indexRequest.errorMessage }}</p><p v-for="warning in indexRequest.warnings" :key="warning">{{ warning }}</p>
            <el-button v-if="isRagActive(indexRequest)" link @click="mutate(indexRequest, 'cancel')">取消索引任务</el-button>
            <el-button v-if="canRetry(indexRequest)" link @click="mutate(indexRequest, 'retry')">重试索引任务</el-button>
          </div>
        </el-card>
      </aside>
      <section>
        <el-card shadow="never">
          <div class="rag-row rag-filters">
            <el-select v-model="rootId" clearable placeholder="全部科目" :disabled="busy || !!pendingId" @change="scopeChanged"><el-option v-for="root in roots" :key="root.id" :label="root.name" :value="root.id" /></el-select>
            <el-select v-model="reviewStatus" clearable placeholder="全部掌握状态" :disabled="busy || !!pendingId" @change="scopeChanged"><el-option label="复习中" value="ACTIVE" /><el-option label="已掌握" value="MASTERED" /></el-select>
            <span v-if="configuration" class="rag-note">今日调用 {{ configuration.callsToday }} / {{ configuration.dailyCallLimit }}</span>
          </div>
          <el-alert v-if="focusId" :title="`围绕错题 #${focusId} 提问`" type="info" :closable="!busy && !pendingId" @close="focusId = undefined" class="rag-gap" />
          <el-input v-model="question" type="textarea" :rows="4" maxlength="2000" show-word-limit placeholder="例如：我的二分查找错题中，边界条件容易错在哪里？" :disabled="busy || !!pendingId" data-testid="rag-question" />
          <div class="rag-row rag-gap">
            <el-checkbox v-model="followUp" :disabled="busy || active || !!pendingId || !history.items.some(r => r.status === 'SUCCEEDED')">联系上一问题进行追问</el-checkbox>
            <el-button type="primary" :loading="busy" :disabled="!question.trim() || active || !!pendingId" data-testid="rag-ask" @click="submit">{{ configuration?.generationEnabled ? '检索并回答' : '检索错题' }}</el-button>
            <el-button :disabled="busy" @click="refresh">刷新</el-button>
          </div>
          <p class="rag-note">只使用当前范围内的错题文本。相关片段会发送到已配置的模型服务；图片不会用于问答。资料不足时会说明原因，生成解释请结合原题核对。</p>
        </el-card>
        <el-card v-if="!history.items.length" shadow="never" class="rag-gap"><p class="rag-note">可以查找同类错题、比较正确答案与错误原因，或从错题详情进入并追问。回答来源会显示在这里。</p></el-card>
        <article v-for="request in history.items" :key="request.id" class="rag-gap">
          <el-card shadow="never">
            <div class="rag-row"><strong><MathText :text="request.question" /></strong><el-tag>{{ ragStatus[request.status] }}</el-tag></div>
            <p class="rag-note">{{ formatDateTime(request.createdAt) }} · {{ ragPhase[request.phase] }}<span v-if="request.followUp"> · 联系上一问题</span></p>
            <el-alert v-if="request.errorMessage" :title="request.errorMessage" type="error" :closable="false" />
            <div v-if="request.answer" class="rag-answer" data-testid="rag-answer"><el-tag v-if="request.answerType === 'RETRIEVAL_ONLY'" type="info">错题原文检索</el-tag><el-tag v-else-if="request.answerType === 'GENERATED'" type="success">依据错题生成</el-tag><MathText :text="request.answer" /></div>
            <p v-for="warning in request.warnings" :key="warning" class="rag-warning">{{ warning }}</p>
            <div v-if="request.sources.length" class="rag-sources"><strong>参考错题 · {{ ragMethods[request.retrievalMethod ?? 'KEYWORD'] }}</strong>
              <details v-for="source in request.sources" :key="source.reference" class="rag-source" :open="request.sources.length === 1">
                <summary>{{ source.reference }} · #{{ source.questionId }} · {{ source.subject }} · {{ source.knowledgePoints.join(' / ') }} <span v-if="request.citations.includes(source.reference)">（回答引用）</span><span v-if="!source.available" class="rag-warning">（原题已删除）</span><span v-else-if="source.changed" class="rag-warning">（原题已修改）</span></summary>
                <p v-if="!source.available" class="rag-warning">原错题已删除，以下保留的是回答时的资料。</p><p v-else-if="source.changed" class="rag-warning">原错题已修改，以下为历史资料，请查看最新原题。</p>
                <p class="rag-note">{{ ragMethods[source.method] }} · 片段 {{ source.chunkNumber + 1 }} · {{ formatDateTime(source.updatedAt) }}</p>
                <div class="rag-excerpt"><MathText :text="source.excerpt" /></div>
                <el-button v-if="source.available" link type="primary" @click="router.push(`/questions/${source.questionId}`)">查看完整错题 #{{ source.questionId }}</el-button>
              </details>
            </div>
            <el-button v-if="isRagActive(request)" :disabled="busy" @click="mutate(request, 'cancel')">取消处理</el-button>
            <el-button v-if="canRetry(request)" :disabled="busy || active || !!pendingId" @click="mutate(request, 'retry')">人工重试（可能再次计费）</el-button>
          </el-card>
        </article>
        <el-pagination v-if="history.totalPages > 1" :current-page="historyPage + 1" :page-size="20" :total="history.totalElements" layout="total, prev, pager, next" @current-change="changeHistoryPage" />
      </section>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppPageHeader from '../components/AppPageHeader.vue'
import MathText from '../components/MathText.vue'
import { getKnowledgePointTree } from '../api/knowledge-points'
import { askRag, buildRagIndex, cancelRagRequest, createRagSession, deleteRagSession, getRagConfiguration, getRagHistory, getRagIndex, getRagRequest, getRagSessions, retryRagRequest } from '../api/rag'
import { isRagActive, ragMethods, ragPhase, ragStatus, type RagConfiguration, type RagIndexStatus, type RagPage, type RagRequest, type RagSession } from '../types/rag'
import type { KnowledgePointTreeNode } from '../types/knowledge-point'
import { normalizeApiError, isResponseUnavailableError } from '../utils/api-error'
import { formatDateTime } from '../utils/date-time'
const route = useRoute(); const router = useRouter()
const KEY = 'wrongquestion.rag.pending-request'; const SESSION = 'wrongquestion.rag.session'; const INDEX = 'wrongquestion.rag.index-request'
const SCOPE = 'wrongquestion.rag.scope'
const PENDING_SESSION = 'wrongquestion.rag.pending-session'
function saved(key: string): string { try { return localStorage.getItem(key) ?? '' } catch { return '' } }
function save(key: string, value: string): void { try { if (value) localStorage.setItem(key, value); else localStorage.removeItem(key) } catch { /* The view still works when browser storage is unavailable. */ } }
const pendingId = ref(saved(KEY)); const pendingMissing = ref(false); const sessionId = ref(saved(SESSION)); const error = ref(''); const busy = ref(false)
const configuration = ref<RagConfiguration>(); const indexState = ref<RagIndexStatus>(); const roots = ref<KnowledgePointTreeNode[]>([])
const empty = <T,>(): RagPage<T> => ({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
const sessions = ref<RagPage<RagSession>>(empty()); const history = ref<RagPage<RagRequest>>(empty()); const indexRequest = ref<RagRequest>()
function savedScope(): { rootId?: number; reviewStatus?: string } { try { const value = JSON.parse(saved(SCOPE) || '{}') as Record<string, unknown>; return { rootId: typeof value.rootId === 'number' && Number.isSafeInteger(value.rootId) && value.rootId > 0 ? value.rootId : undefined, reviewStatus: value.reviewStatus === 'ACTIVE' || value.reviewStatus === 'MASTERED' ? value.reviewStatus : undefined } } catch { return {} } }
const scope = savedScope()
const sessionPage = ref(0); const historyPage = ref(0); const rootId = ref<number | undefined>(scope.rootId); const reviewStatus = ref<string | undefined>(scope.reviewStatus); const focusId = ref<number>(); const followUp = ref(false); const question = ref('')
const active = computed(() => history.value.items.some(isRagActive))
let disposed = false; let timer: ReturnType<typeof setTimeout> | undefined; let sequence = 0
function clearPending(): void { pendingId.value = ''; pendingMissing.value = false; save(KEY, '') }
function canRetry(r: RagRequest): boolean { return r.status === 'FAILED' && r.retryCount < (configuration.value?.maxManualRetries ?? 0) }
async function loadMetadata(): Promise<void> { const [config, state] = await Promise.all([getRagConfiguration(), getRagIndex(rootId.value)]); if (!disposed) { configuration.value = config; indexState.value = state } }
function schedule(): void { if (timer) clearTimeout(timer); if (!disposed && (active.value || (indexRequest.value && isRagActive(indexRequest.value)))) timer = setTimeout(() => void refresh(), 2000) }
async function refresh(): Promise<void> {
  const version = ++sequence; const selected = sessionId.value
  try {
    const [sessionData, historyData, indexData] = await Promise.all([getRagSessions(sessionPage.value), selected ? getRagHistory(selected, historyPage.value) : Promise.resolve(empty<RagRequest>()), indexRequest.value ? getRagRequest(indexRequest.value.id) : Promise.resolve(undefined)])
    if (disposed || version !== sequence) return
    sessions.value = sessionData; history.value = historyData; indexRequest.value = indexData
    if (historyPage.value > 0 && !historyData.items.length) { historyPage.value = 0; await refresh(); return }
    if (indexData && !isRagActive(indexData)) save(INDEX, '')
    await loadMetadata()
  } catch (cause) { if (disposed || version !== sequence) return; const failure = normalizeApiError(cause); error.value = failure.message; if (failure.status === 404) { sessionId.value = ''; save(SESSION, ''); history.value = empty(); indexRequest.value = undefined; save(INDEX, '') } }
  finally { schedule() }
}
async function synchronize(): Promise<void> {
  if (!pendingId.value) return; busy.value = true; error.value = ''; pendingMissing.value = false
  try { const request = await getRagRequest(pendingId.value); if (disposed) return; accept(request); clearPending(); await refresh() }
  catch (cause) { if (!disposed) { const failure = normalizeApiError(cause); error.value = failure.status === 404 ? '服务器尚未找到这个请求。不会自动重发；可以稍后再次同步，或清除记录后重新提问。' : failure.message; pendingMissing.value = failure.status === 404 } }
  finally { if (!disposed) busy.value = false }
}
function accept(request: RagRequest): void { if (request.kind === 'INDEX') { indexRequest.value = request; if (isRagActive(request)) save(INDEX, request.id) } else { sessionId.value = request.sessionId ?? ''; save(SESSION, sessionId.value); historyPage.value = 0; question.value = '' } }
async function submit(): Promise<void> {
  if (busy.value || active.value || pendingId.value || !question.value.trim()) return
  busy.value = true; error.value = ''
  try {
    if (!sessionId.value) { const id = saved(PENDING_SESSION) || crypto.randomUUID(); save(PENDING_SESSION, id); const session = await createRagSession(id); if (disposed) return; sessionId.value = session.id; save(SESSION, session.id); save(PENDING_SESSION, '') }
    const id = crypto.randomUUID(); pendingId.value = id; save(KEY, id)
    try { const response = await askRag(id, sessionId.value, { question: question.value.trim(), rootId: rootId.value, reviewStatus: reviewStatus.value, focusQuestionId: focusId.value, followUp: followUp.value }); if (!disposed) { accept(response); clearPending() } }
    catch (cause) { if (!isResponseUnavailableError(cause)) clearPending(); throw cause }
    await refresh()
  } catch (cause) { if (!disposed) error.value = normalizeApiError(cause).message }
  finally { if (!disposed) busy.value = false }
}
async function buildIndex(): Promise<void> { if (busy.value || pendingId.value) return; busy.value = true; error.value = ''; const id = crypto.randomUUID(); pendingId.value = id; save(KEY, id)
  try { const request = await buildRagIndex(id, rootId.value); if (!disposed) { accept(request); clearPending(); await refresh() } }
  catch (cause) { if (!disposed) { if (!isResponseUnavailableError(cause)) clearPending(); error.value = normalizeApiError(cause).message } }
  finally { if (!disposed) busy.value = false }
}
async function mutate(request: RagRequest, action: 'cancel' | 'retry'): Promise<void> { if (busy.value) return; busy.value = true; error.value = ''
  try { const result = action === 'cancel' ? await cancelRagRequest(request.id) : await retryRagRequest(request.id, request.retryCount); if (!disposed) { accept(result); await refresh() } }
  catch (cause) { if (!disposed) { error.value = normalizeApiError(cause).message; await refresh() } }
  finally { if (!disposed) busy.value = false }
}
function newSession(): void { sessionId.value = ''; save(SESSION, ''); history.value = empty(); historyPage.value = 0; followUp.value = false; question.value = ''; void router.replace({ path: '/learning-assistant' }) }
async function selectSession(id: string): Promise<void> { sessionId.value = id; save(SESSION, id); historyPage.value = 0; focusId.value = undefined; followUp.value = false; error.value = ''; await router.replace({ path: '/learning-assistant', query: { session: id } }); await refresh() }
async function removeSession(): Promise<void> { if (busy.value || !sessionId.value) return; busy.value = true; try { await deleteRagSession(sessionId.value); newSession(); await refresh() } catch (cause) { error.value = normalizeApiError(cause).message } finally { busy.value = false } }
function scopeChanged(): void { followUp.value = false; focusId.value = undefined; void loadMetadata().catch(cause => { error.value = normalizeApiError(cause).message }) }
function changeHistoryPage(page: number): void { historyPage.value = page - 1; void refresh() }
function changeSessionPage(page: number): void { sessionPage.value = page - 1; void refresh() }
async function routeContext(): Promise<void> {
  const focus = Number(route.query.questionId); focusId.value = Number.isSafeInteger(focus) && focus > 0 ? focus : undefined
  if (focusId.value) { rootId.value = undefined; reviewStatus.value = undefined }
  if (typeof route.query.session === 'string') { sessionId.value = route.query.session; save(SESSION, sessionId.value) }
  if (typeof route.query.request === 'string') { const request = await getRagRequest(route.query.request); if (!disposed) accept(request) }
}
watch(() => route.query.request, () => void routeContext().then(refresh).catch(cause => { error.value = normalizeApiError(cause).message }))
watch([rootId, reviewStatus], () => save(SCOPE, JSON.stringify({ rootId: rootId.value, reviewStatus: reviewStatus.value })))
onMounted(async () => { try { roots.value = await getKnowledgePointTree(); await routeContext(); const id = saved(INDEX); if (id && !indexRequest.value) indexRequest.value = await getRagRequest(id); await refresh(); if (pendingId.value) await synchronize() } catch (cause) { error.value = normalizeApiError(cause).message } })
onBeforeUnmount(() => { disposed = true; sequence += 1; if (timer) clearTimeout(timer) })
</script>
<style scoped>
.rag-grid { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 20px; margin-top: 18px; }
.rag-gap { margin-top: 16px; }
.rag-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }
.rag-filters { margin-bottom: 16px; justify-content: flex-start; }.rag-filters .el-select { width: 180px; }
.rag-note { color: var(--app-muted); font-size: 13px; line-height: 1.8; }.rag-warning { color: #996214; line-height: 1.8; }
.rag-session { display: block; width: 100%; text-align: left; border: 0; background: transparent; padding: 12px; border-radius: 8px; cursor: pointer; color: #233858; line-height: 1.6; overflow-wrap: anywhere; }.rag-session.selected { background: #edf3fc; }
.rag-answer { margin: 20px 0; line-height: 1.9; overflow-wrap: anywhere; }.rag-answer .el-tag { display: block; width: fit-content; margin-bottom: 12px; }
.rag-sources { margin: 20px 0; }.rag-source { margin: 12px 0; border: 1px solid #dde5ef; border-radius: 10px; padding: 14px; }.rag-source summary { cursor: pointer; line-height: 1.8; overflow-wrap: anywhere; }
.rag-excerpt { background: #f5f8fc; padding: 14px; margin-bottom: 12px; line-height: 1.8; overflow-wrap: anywhere; }
@media (max-width: 1000px) { .rag-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
