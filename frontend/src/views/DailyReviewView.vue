<template>
  <div>
    <AppPageHeader
      title="每日复习"
      description="先完成到期复习，再抽查一道已掌握题，按真实掌握情况评价。"
    />

    <el-card class="page-card filter-card" shadow="never">
      <div class="filter-row">
        <div>
          <span class="filter-label">复习范围</span>
          <el-select
            v-model="selectedSubject"
            class="subject-filter"
            placeholder="全部科目"
            clearable
            :loading="knowledgeStore.loading"
            :disabled="phase === 'SUBMITTING'"
            data-testid="subject-filter"
            @change="handleSubjectChange"
          >
            <el-option
              v-for="root in knowledgeStore.roots"
              :key="root.id"
              :label="root.name"
              :value="root.name"
            />
          </el-select>
        </div>
        <span class="filter-description">
          {{ selectedSubject ? `仅复习“${selectedSubject}”` : '复习全部科目的到期错题' }}
        </span>
      </div>
    </el-card>

    <el-alert
      v-if="knowledgeStore.errorMessage"
      :title="`科目筛选暂不可用：${knowledgeStore.errorMessage}`"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    >
      <template #default>
        <el-button
          link
          type="primary"
          :loading="knowledgeStore.loading"
          data-testid="retry-subjects"
          @click="retryKnowledgePoints"
        >
          重新加载科目
        </el-button>
      </template>
    </el-alert>

    <el-alert
      v-if="queueNotice"
      :title="queueNotice"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
      data-testid="queue-notice"
    />

    <el-card
      v-if="phase === 'LOADING_QUEUE'"
      class="page-card state-card"
      shadow="never"
      data-testid="queue-loading"
    >
      <el-skeleton :rows="7" animated />
    </el-card>

    <el-alert
      v-else-if="phase === 'QUEUE_ERROR'"
      :title="queueError"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
      data-testid="queue-error"
    >
      <template #default>
        <el-button link type="primary" data-testid="retry-queue" @click="retryQueue">
          重新加载待复习题
        </el-button>
      </template>
    </el-alert>

    <el-card
      v-else-if="phase === 'EMPTY'"
      class="page-card state-card"
      shadow="never"
      data-testid="empty-queue"
    >
      <AppEmptyState :description="emptyDescription">
        <el-button @click="retryQueue">刷新队列</el-button>
        <el-button
          v-if="selectedSubject"
          type="primary"
          data-testid="clear-subject"
          @click="clearSubject"
        >
          查看全部科目
        </el-button>
      </AppEmptyState>
    </el-card>

    <div v-else-if="currentQuestion" class="review-flow">
      <el-alert
        v-if="reviewMode === 'SPOT_CHECK'"
        title="今日已掌握题随机抽查"
        type="info"
        show-icon
        :closable="false"
        class="flow-alert"
        data-testid="spot-check-notice"
      >
        熟练表示继续保持已掌握；不会、模糊或基本掌握会分别在 1、3 或 7 天后重新进入常规复习。
      </el-alert>

      <ReviewQuestionCard
        :question="currentQuestion"
        :due-count="reviewMode === 'SPOT_CHECK' ? (spotCheckReview?.eligibleCount ?? 0) : (dueReview?.dueCount ?? 0)"
        :mode="reviewMode"
        :loading-answer="phase === 'LOADING_ANSWER'"
        :answer-revealed="Boolean(questionDetail)"
        :image-url="currentQuestionImageUrl"
        :disabled="phase !== 'QUESTION' && phase !== 'ANSWER_ERROR'"
        @show-answer="loadAnswer"
      />

      <el-alert
        v-if="phase === 'ANSWER_ERROR'"
        :title="answerError"
        type="error"
        show-icon
        :closable="false"
        class="flow-alert"
        data-testid="answer-error"
      >
        <template #default>
          <el-button link type="primary" data-testid="retry-answer" @click="loadAnswer">
            重新加载答案
          </el-button>
        </template>
      </el-alert>

      <ReviewAnswerPanel v-if="questionDetail" :question="questionDetail" />

      <el-alert
        v-if="actionError && phase === 'ANSWER'"
        :title="actionError"
        type="error"
        show-icon
        :closable="false"
        class="flow-alert"
        data-testid="evaluation-error"
      />

      <el-alert
        v-if="phase === 'UNCERTAIN'"
        title="评价结果暂时无法确认"
        type="warning"
        show-icon
        :closable="false"
        class="flow-alert"
        data-testid="uncertain-result"
      >
        <p class="uncertain-copy">
          请求可能已经在服务器提交成功。系统不会自动重发评价，请先同步服务器状态。
        </p>
        <p v-if="uncertainSyncError" class="uncertain-error">{{ uncertainSyncError }}</p>
        <el-button
          type="primary"
          plain
          :loading="syncing"
          data-testid="sync-progress"
          @click="syncProgress"
        >
          同步复习进度
        </el-button>
      </el-alert>

      <ReviewRatingPanel
        v-if="questionDetail && (phase === 'ANSWER' || phase === 'SUBMITTING')"
        :disabled="phase === 'SUBMITTING'"
        :submitting-rating="submittingRating"
        @rate="submitRating"
      />

      <ReviewResultCard
        v-if="phase === 'RESULT' && resultSummary"
        :rating="resultSummary.rating"
        :mode="reviewMode"
        :review-status="resultSummary.reviewStatus"
        :next-review-date="resultSummary.nextReviewDate"
        :consecutive-proficient-count="resultSummary.consecutiveProficientCount"
        :synchronized="resultSummary.synchronized"
        @next="loadNextQuestion"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getQuestion, getQuestionImageUrl } from '../api/questions'
import {
  getNextDueReview,
  getTodayMasteredSpotCheck,
  submitMasteredSpotCheckEvaluation,
  submitReviewEvaluation,
} from '../api/reviews'
import AppEmptyState from '../components/AppEmptyState.vue'
import AppPageHeader from '../components/AppPageHeader.vue'
import ReviewAnswerPanel from '../components/ReviewAnswerPanel.vue'
import ReviewQuestionCard from '../components/ReviewQuestionCard.vue'
import ReviewRatingPanel from '../components/ReviewRatingPanel.vue'
import ReviewResultCard from '../components/ReviewResultCard.vue'
import { useKnowledgePointStore } from '../stores/knowledge-points'
import type { QuestionDetail } from '../types/question'
import type {
  DueReviewResponse,
  MasteredSpotCheckResponse,
  ReviewActionResponse,
  ReviewRating,
  ReviewStatus,
} from '../types/review'
import {
  isNotFoundError,
  isResponseUnavailableError,
  normalizeApiError,
} from '../utils/api-error'

type ReviewPagePhase =
  | 'LOADING_QUEUE'
  | 'QUEUE_ERROR'
  | 'EMPTY'
  | 'QUESTION'
  | 'LOADING_ANSWER'
  | 'ANSWER_ERROR'
  | 'ANSWER'
  | 'SUBMITTING'
  | 'RESULT'
  | 'UNCERTAIN'

interface ReviewProgressSnapshot {
  reviewStatusValue: ReviewStatus
  nextReviewDate: string | null
  consecutiveProficientCount: number
  lastReviewedAt: string | null
}

interface ReviewResultSummary {
  rating: ReviewRating | null
  reviewStatus: ReviewStatus
  nextReviewDate: string | null
  consecutiveProficientCount: number
  synchronized: boolean
}

const knowledgeStore = useKnowledgePointStore()
const selectedSubject = ref('')
const reviewMode = ref<'DUE' | 'SPOT_CHECK'>('DUE')
const phase = ref<ReviewPagePhase>('LOADING_QUEUE')
const dueReview = ref<DueReviewResponse>()
const spotCheckReview = ref<MasteredSpotCheckResponse>()
const questionDetail = ref<QuestionDetail>()
const resultSummary = ref<ReviewResultSummary>()
const preSubmissionSnapshot = ref<ReviewProgressSnapshot>()
const queueError = ref('')
const queueNotice = ref('')
const answerError = ref('')
const actionError = ref('')
const uncertainSyncError = ref('')
const submittingRating = ref<ReviewRating | null>(null)
const syncing = ref(false)

let queueRequestSequence = 0
let detailRequestSequence = 0
let evaluationInFlight = false

const currentQuestion = computed(() =>
  reviewMode.value === 'SPOT_CHECK'
    ? spotCheckReview.value?.question ?? null
    : dueReview.value?.question ?? null,
)
const currentQuestionImageUrl = computed(() => {
  const question = currentQuestion.value
  return question?.imagePath ? getQuestionImageUrl(question.id) : ''
})
const emptyDescription = computed(() => {
  const scope = selectedSubject.value ? `“${selectedSubject.value}”` : ''
  if (spotCheckReview.value?.completedToday) {
    return `${scope}今天没有待复习错题，且今日已完成已掌握题抽查`
  }
  return `${scope}今天没有待复习错题，也没有可抽查的已掌握题`
})

function readableError(error: unknown): string {
  const normalized = normalizeApiError(error)
  const fieldMessage = Object.values(normalized.fieldErrors)[0]
  return fieldMessage ? `${normalized.message}：${fieldMessage}` : normalized.message
}

function resetQuestionFlow(): void {
  questionDetail.value = undefined
  resultSummary.value = undefined
  preSubmissionSnapshot.value = undefined
  answerError.value = ''
  actionError.value = ''
  uncertainSyncError.value = ''
  submittingRating.value = null
  syncing.value = false
}

function isConsistentQueueResponse(response: DueReviewResponse): boolean {
  if (!Number.isInteger(response.dueCount) || response.dueCount < 0) {
    return false
  }

  return (
    (response.dueCount === 0 && response.question === null) ||
    (response.dueCount > 0 && response.question !== null)
  )
}

function isConsistentSpotCheckResponse(
  response: MasteredSpotCheckResponse,
): boolean {
  if (!Number.isInteger(response.eligibleCount) || response.eligibleCount < 0) {
    return false
  }
  return response.cooldownDays > 0 && (
    (response.completedToday && response.eligibleCount === 0 && response.question === null) ||
    (!response.completedToday && response.eligibleCount === 0 && response.question === null) ||
    (!response.completedToday && response.eligibleCount > 0 && response.question !== null)
  )
}

async function loadQueue(notice = ''): Promise<void> {
  const requestSequence = ++queueRequestSequence
  detailRequestSequence += 1
  phase.value = 'LOADING_QUEUE'
  reviewMode.value = 'DUE'
  dueReview.value = undefined
  spotCheckReview.value = undefined
  queueError.value = ''
  queueNotice.value = notice
  resetQuestionFlow()

  const requestedSubject = selectedSubject.value || undefined

  try {
    const response = await getNextDueReview(requestedSubject)

    if (requestSequence !== queueRequestSequence) {
      return
    }

    if (!isConsistentQueueResponse(response)) {
      throw new Error('服务器返回的待复习队列数据不一致，请重新加载')
    }

    dueReview.value = response
    if (response.question) {
      phase.value = 'QUESTION'
      return
    }

    const spotCheckResponse = await getTodayMasteredSpotCheck(requestedSubject)
    if (requestSequence !== queueRequestSequence) {
      return
    }
    if (!isConsistentSpotCheckResponse(spotCheckResponse)) {
      throw new Error('服务器返回的已掌握题抽查数据不一致，请重新加载')
    }

    spotCheckReview.value = spotCheckResponse
    reviewMode.value = 'SPOT_CHECK'
    phase.value = spotCheckResponse.question ? 'QUESTION' : 'EMPTY'
  } catch (error) {
    if (requestSequence !== queueRequestSequence) {
      return
    }

    queueError.value = readableError(error)
    phase.value = 'QUEUE_ERROR'
  }
}

function handleSubjectChange(): void {
  void loadQueue()
}

function clearSubject(): void {
  selectedSubject.value = ''
  void loadQueue()
}

function retryQueue(): void {
  void loadQueue(queueNotice.value)
}

function retryKnowledgePoints(): void {
  void knowledgeStore.refresh().catch(() => undefined)
}

async function loadAnswer(): Promise<void> {
  const question = currentQuestion.value
  if (!question || (phase.value !== 'QUESTION' && phase.value !== 'ANSWER_ERROR')) {
    return
  }

  const requestSequence = ++detailRequestSequence
  phase.value = 'LOADING_ANSWER'
  answerError.value = ''
  actionError.value = ''

  try {
    const detail = await getQuestion(question.id)

    if (
      requestSequence !== detailRequestSequence ||
      currentQuestion.value?.id !== question.id
    ) {
      return
    }

    if (detail.id !== question.id) {
      throw new Error('服务器返回的错题详情与当前题目不一致')
    }

    questionDetail.value = detail
    phase.value = 'ANSWER'
  } catch (error) {
    if (requestSequence !== detailRequestSequence) {
      return
    }

    if (isNotFoundError(error)) {
      await loadQueue(readableError(error))
      return
    }

    answerError.value = readableError(error)
    phase.value = 'ANSWER_ERROR'
  }
}

function toProgressSnapshot(detail: QuestionDetail): ReviewProgressSnapshot {
  return {
    reviewStatusValue: detail.reviewStatus,
    nextReviewDate: detail.nextReviewDate,
    consecutiveProficientCount: detail.consecutiveProficientCount,
    lastReviewedAt: detail.lastReviewedAt,
  }
}

function hasProgressChanged(
  before: ReviewProgressSnapshot,
  after: QuestionDetail,
): boolean {
  return (
    before.reviewStatusValue !== after.reviewStatus ||
    before.nextReviewDate !== after.nextReviewDate ||
    before.consecutiveProficientCount !== after.consecutiveProficientCount ||
    before.lastReviewedAt !== after.lastReviewedAt
  )
}

function applyActionResponse(response: ReviewActionResponse): void {
  if (questionDetail.value) {
    questionDetail.value = {
      ...questionDetail.value,
      reviewStatus: response.reviewStatus,
      nextReviewDate: response.nextReviewDate,
      consecutiveProficientCount: response.consecutiveProficientCount,
      lastReviewedAt: response.lastReviewedAt,
    }
  }

  resultSummary.value = {
    rating: response.rating,
    reviewStatus: response.reviewStatus,
    nextReviewDate: response.nextReviewDate,
    consecutiveProficientCount: response.consecutiveProficientCount,
    synchronized: false,
  }
}

async function submitRating(rating: ReviewRating): Promise<void> {
  const question = currentQuestion.value
  const detail = questionDetail.value

  if (!question || !detail || phase.value !== 'ANSWER' || evaluationInFlight) {
    return
  }

  evaluationInFlight = true
  submittingRating.value = rating
  actionError.value = ''
  uncertainSyncError.value = ''
  preSubmissionSnapshot.value = toProgressSnapshot(detail)
  phase.value = 'SUBMITTING'

  try {
    const response = reviewMode.value === 'SPOT_CHECK'
      ? await submitMasteredSpotCheckEvaluation(
          question.id,
          rating,
          selectedSubject.value || undefined,
        )
      : await submitReviewEvaluation(question.id, rating)

    if (response.questionId !== question.id) {
      throw new Error('服务器返回的评价结果与当前题目不一致')
    }

    applyActionResponse(response)
    phase.value = 'RESULT'
  } catch (error) {
    if (isResponseUnavailableError(error)) {
      phase.value = 'UNCERTAIN'
      return
    }

    const normalized = normalizeApiError(error)
    if (normalized.status === 404 || normalized.status === 409) {
      await loadQueue(readableError(error))
      return
    }

    actionError.value = readableError(error)
    phase.value = 'ANSWER'
  } finally {
    submittingRating.value = null
    evaluationInFlight = false
  }
}

async function syncProgress(): Promise<void> {
  const question = currentQuestion.value
  const before = preSubmissionSnapshot.value
  if (!question || !before || phase.value !== 'UNCERTAIN' || syncing.value) {
    return
  }

  const requestSequence = ++detailRequestSequence
  syncing.value = true
  uncertainSyncError.value = ''

  try {
    const latest = await getQuestion(question.id)

    if (
      requestSequence !== detailRequestSequence ||
      currentQuestion.value?.id !== question.id
    ) {
      return
    }

    if (latest.id !== question.id) {
      throw new Error('服务器返回的错题详情与当前题目不一致')
    }

    questionDetail.value = latest

    if (hasProgressChanged(before, latest)) {
      resultSummary.value = {
        rating: null,
        reviewStatus: latest.reviewStatus,
        nextReviewDate: latest.nextReviewDate,
        consecutiveProficientCount: latest.consecutiveProficientCount,
        synchronized: true,
      }
      phase.value = 'RESULT'
      return
    }

    actionError.value = '服务器复习状态尚未变化，可以重新提交评价。'
    preSubmissionSnapshot.value = undefined
    phase.value = 'ANSWER'
  } catch (error) {
    if (requestSequence !== detailRequestSequence) {
      return
    }

    if (isNotFoundError(error)) {
      await loadQueue(readableError(error))
      return
    }

    uncertainSyncError.value = readableError(error)
  } finally {
    if (requestSequence === detailRequestSequence) {
      syncing.value = false
    }
  }
}

function loadNextQuestion(): void {
  void loadQueue()
}

onMounted(() => {
  void knowledgeStore.load().catch(() => undefined)
  void loadQueue()
})
</script>

<style scoped>
.filter-card,
.page-alert {
  margin-bottom: 18px;
}

.filter-row {
  display: flex;
  gap: 20px;
  align-items: flex-end;
  justify-content: space-between;
}

.filter-label {
  display: block;
  margin-bottom: 7px;
  color: var(--app-muted);
  font-size: 0.8rem;
}

.subject-filter {
  width: 220px;
}

.filter-description {
  color: var(--app-muted);
  font-size: 0.9rem;
}

.state-card {
  min-height: 270px;
}

.review-flow {
  display: grid;
  gap: 18px;
}

.flow-alert {
  margin: 0;
}

.uncertain-copy,
.uncertain-error {
  margin: 0 0 12px;
  line-height: 1.6;
}

.uncertain-error {
  color: #b45309;
}

@media (max-width: 680px) {
  .filter-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .subject-filter {
    width: 100%;
  }
}
</style>
