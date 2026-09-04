<template>
  <div>
    <AppPageHeader title="错题详情">
      <template #actions>
        <el-button @click="router.push('/questions')">返回列表</el-button>
        <el-button v-if="question" type="primary" :icon="Edit" @click="openEdit">
          修改
        </el-button>
        <el-button
          v-if="question"
          type="danger"
          plain
          :icon="Delete"
          :loading="deleting"
          @click="removeQuestion"
        >
          删除
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
    >
      <template #default>
        <el-button link type="primary" @click="loadQuestion">重新加载</el-button>
      </template>
    </el-alert>

    <el-skeleton v-if="loading" :rows="10" animated />

    <el-card v-else-if="notFound" class="page-card" shadow="never">
      <AppEmptyState description="这道错题不存在或已经被删除">
        <el-button type="primary" @click="router.push('/questions')">返回错题列表</el-button>
      </AppEmptyState>
    </el-card>

    <div v-else-if="question" class="detail-grid">
      <section class="detail-main">
        <el-card class="page-card detail-card" shadow="never">
          <DetailSection title="题目" :content="question.questionText" />
          <DetailSection title="我的错误答案" :content="question.wrongAnswer" tone="danger" />
          <DetailSection title="正确答案" :content="question.correctAnswer" tone="success" />
          <DetailSection title="解析" :content="question.analysis" />
          <DetailSection title="错误原因" :content="question.errorReason" tone="warning" />
        </el-card>
      </section>

      <aside class="detail-sidebar">
        <el-card class="page-card meta-card" shadow="never">
          <template #header><strong>分类信息</strong></template>
          <dl>
            <div>
              <dt>科目</dt>
              <dd>{{ question.subject }}</dd>
            </div>
            <div>
              <dt>知识点</dt>
              <dd class="tag-list">
                <el-tag
                  v-for="point in question.knowledgePoints"
                  :key="point.id"
                  size="small"
                  effect="plain"
                >
                  {{ point.name }}
                </el-tag>
              </dd>
            </div>
          </dl>
        </el-card>

        <el-card class="page-card meta-card" shadow="never">
          <template #header><strong>复习状态</strong></template>
          <dl>
            <div>
              <dt>当前状态</dt>
              <dd><ReviewStatusTag :status="question.reviewStatus" /></dd>
            </div>
            <div>
              <dt>下次复习</dt>
              <dd>{{ formatDate(question.nextReviewDate) }}</dd>
            </div>
            <div>
              <dt>连续熟练</dt>
              <dd>{{ question.consecutiveProficientCount }} 次</dd>
            </div>
            <div>
              <dt>最后复习</dt>
              <dd>{{ question.lastReviewedAt ? formatDateTime(question.lastReviewedAt) : '尚未复习' }}</dd>
            </div>
          </dl>
        </el-card>

        <el-card class="page-card meta-card" shadow="never">
          <template #header><strong>记录信息</strong></template>
          <dl>
            <div>
              <dt>创建时间</dt>
              <dd>{{ formatDateTime(question.createdTime) }}</dd>
            </div>
            <div>
              <dt>更新时间</dt>
              <dd>{{ formatDateTime(question.updatedTime) }}</dd>
            </div>
          </dl>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { defineComponent, h, onMounted, ref } from 'vue'
import { Delete, Edit } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { deleteQuestion, getQuestion } from '../api/questions'
import AppEmptyState from '../components/AppEmptyState.vue'
import AppPageHeader from '../components/AppPageHeader.vue'
import ReviewStatusTag from '../components/ReviewStatusTag.vue'
import type { QuestionDetail } from '../types/question'
import { isNotFoundError, normalizeApiError } from '../utils/api-error'
import { formatDate, formatDateTime } from '../utils/date-time'

const DetailSection = defineComponent({
  props: {
    title: { type: String, required: true },
    content: { type: String, required: true },
    tone: { type: String, default: 'default' },
  },
  setup(props) {
    return () =>
      h('section', { class: ['detail-section', `detail-section--${props.tone}`] }, [
        h('h2', props.title),
        h('div', { class: 'content-prose' }, props.content),
      ])
  },
})

const route = useRoute()
const router = useRouter()
const question = ref<QuestionDetail>()
const loading = ref(true)
const deleting = ref(false)
const notFound = ref(false)
const pageError = ref('')
const questionId = Number(route.params.id)

async function loadQuestion(): Promise<void> {
  pageError.value = ''
  notFound.value = false
  loading.value = true

  if (!Number.isInteger(questionId) || questionId <= 0) {
    notFound.value = true
    loading.value = false
    return
  }

  try {
    question.value = await getQuestion(questionId)
  } catch (error) {
    if (isNotFoundError(error)) {
      notFound.value = true
    } else {
      pageError.value = normalizeApiError(error).message
    }
  } finally {
    loading.value = false
  }
}

function openEdit(): void {
  void router.push(`/questions/${questionId}/edit`)
}

async function removeQuestion(): Promise<void> {
  if (!question.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      '删除后将同时清理该题的复习状态和历史，且无法恢复。',
      '删除错题',
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
    const result = await deleteQuestion(questionId)
    ElMessage.success(result.message)
    await router.push('/questions')
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    deleting.value = false
  }
}

onMounted(loadQuestion)
</script>

<style scoped>
.page-alert {
  margin-bottom: 18px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: start;
}

.detail-sidebar {
  display: grid;
  gap: 18px;
}

.detail-card :deep(.el-card__body) {
  padding: 0 26px;
}

:deep(.detail-section) {
  padding: 24px 0;
  border-bottom: 1px solid var(--app-border);
}

:deep(.detail-section:last-child) {
  border-bottom: 0;
}

:deep(.detail-section h2) {
  margin: 0 0 12px;
  color: var(--app-navy);
  font-size: 1rem;
}

:deep(.detail-section--danger) {
  border-left: 3px solid #dc2626;
  padding-left: 16px;
}

:deep(.detail-section--success) {
  border-left: 3px solid #16a34a;
  padding-left: 16px;
}

:deep(.detail-section--warning) {
  border-left: 3px solid #d97706;
  padding-left: 16px;
}

.meta-card :deep(.el-card__header) {
  color: var(--app-navy);
}

dl {
  display: grid;
  gap: 15px;
  margin: 0;
}

dl > div {
  display: grid;
  gap: 5px;
}

dt {
  color: var(--app-muted);
  font-size: 0.82rem;
}

dd {
  margin: 0;
  color: #334155;
  font-size: 0.93rem;
  line-height: 1.5;
}

@media (max-width: 980px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .detail-sidebar {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .detail-sidebar {
    grid-template-columns: 1fr;
  }
}
</style>
