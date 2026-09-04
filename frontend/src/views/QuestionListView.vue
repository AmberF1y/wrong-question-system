<template>
  <div>
    <AppPageHeader
      title="错题管理"
      description="按录入时间浏览错题，并通过科目和掌握状态筛选。"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="router.push('/questions/new')">
          录入错题
        </el-button>
      </template>
    </AppPageHeader>

    <el-card class="page-card filter-card" shadow="never">
      <div class="filter-row">
        <el-select
          v-model="filters.subject"
          clearable
          placeholder="全部科目"
          class="filter-control"
          @change="applyFilters"
        >
          <el-option
            v-for="root in knowledgeStore.roots"
            :key="root.id"
            :label="root.name"
            :value="root.name"
          />
        </el-select>

        <el-select
          v-model="filters.reviewStatus"
          clearable
          placeholder="全部掌握状态"
          class="filter-control"
          @change="applyFilters"
        >
          <el-option label="复习中" value="ACTIVE" />
          <el-option label="已掌握" value="MASTERED" />
        </el-select>

        <el-button :icon="Refresh" @click="resetFilters">重置</el-button>

        <span class="filter-summary">共 {{ pageData.totalElements }} 道错题</span>
      </div>
    </el-card>

    <el-alert
      v-if="pageError"
      :title="pageError"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    >
      <template #default>
        <el-button link type="primary" @click="loadQuestions">重新加载</el-button>
      </template>
    </el-alert>

    <el-alert
      v-else-if="knowledgeStore.errorMessage"
      :title="`科目筛选暂不可用：${knowledgeStore.errorMessage}`"
      type="warning"
      show-icon
      :closable="false"
      class="page-alert"
    />

    <el-card class="page-card list-card" shadow="never">
      <el-skeleton v-if="loading && pageData.items.length === 0" :rows="8" animated />

      <AppEmptyState
        v-else-if="pageData.items.length === 0"
        :description="hasFilters ? '没有符合当前筛选条件的错题' : '还没有录入错题'"
      >
        <el-button v-if="hasFilters" @click="resetFilters">清除筛选</el-button>
        <el-button v-else type="primary" @click="router.push('/questions/new')">
          录入第一道错题
        </el-button>
      </AppEmptyState>

      <template v-else>
        <el-table
          v-loading="loading"
          :data="pageData.items"
          row-key="id"
          class="question-table"
        >
          <el-table-column label="题目" min-width="300">
            <template #default="{ row }">
              <button class="question-link" type="button" @click="openDetail(row.id)">
                {{ row.questionText }}
              </button>
            </template>
          </el-table-column>

          <el-table-column prop="subject" label="科目" width="120" />

          <el-table-column label="知识点" min-width="210">
            <template #default="{ row }">
              <div class="tag-list">
                <el-tag
                  v-for="point in row.knowledgePoints"
                  :key="point.id"
                  size="small"
                  effect="plain"
                >
                  {{ point.name }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="105">
            <template #default="{ row }">
              <ReviewStatusTag :status="row.reviewStatus" />
            </template>
          </el-table-column>

          <el-table-column label="下次复习" width="120">
            <template #default="{ row }">
              {{ formatDate(row.nextReviewDate) }}
            </template>
          </el-table-column>

          <el-table-column label="更新时间" width="168">
            <template #default="{ row }">
              {{ formatDateTime(row.updatedTime) }}
            </template>
          </el-table-column>

          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
              <el-button link type="primary" @click="openEdit(row.id)">修改</el-button>
              <el-button link type="danger" @click="removeQuestion(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-row">
          <el-pagination
            background
            layout="prev, pager, next"
            :current-page="pageData.page + 1"
            :page-size="pageSize"
            :total="pageData.totalElements"
            @current-change="changePage"
          />
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { deleteQuestion, getQuestions } from '../api/questions'
import AppEmptyState from '../components/AppEmptyState.vue'
import AppPageHeader from '../components/AppPageHeader.vue'
import ReviewStatusTag from '../components/ReviewStatusTag.vue'
import { useKnowledgePointStore } from '../stores/knowledge-points'
import type { QuestionPageResponse, ReviewStatus } from '../types/question'
import { normalizeApiError } from '../utils/api-error'
import { formatDate, formatDateTime } from '../utils/date-time'

const router = useRouter()
const knowledgeStore = useKnowledgePointStore()
const pageSize = 20
const loading = ref(false)
const pageError = ref('')
const filters = reactive<{
  subject: string
  reviewStatus: ReviewStatus | ''
}>({
  subject: '',
  reviewStatus: '',
})

const pageData = reactive<QuestionPageResponse>({
  items: [],
  page: 0,
  size: pageSize,
  totalElements: 0,
  totalPages: 0,
})

const hasFilters = computed(() => Boolean(filters.subject || filters.reviewStatus))

async function loadQuestions(): Promise<void> {
  loading.value = true
  pageError.value = ''

  try {
    const result = await getQuestions({
      page: pageData.page,
      size: pageSize,
      subject: filters.subject || undefined,
      reviewStatus: filters.reviewStatus || undefined,
    })

    if (result.items.length === 0 && result.totalPages > 0 && pageData.page >= result.totalPages) {
      pageData.page = result.totalPages - 1
      await loadQuestions()
      return
    }

    Object.assign(pageData, result)
  } catch (error) {
    pageError.value = normalizeApiError(error).message
  } finally {
    loading.value = false
  }
}

function applyFilters(): void {
  pageData.page = 0
  void loadQuestions()
}

function resetFilters(): void {
  filters.subject = ''
  filters.reviewStatus = ''
  pageData.page = 0
  void loadQuestions()
}

function changePage(page: number): void {
  pageData.page = page - 1
  void loadQuestions()
}

function openDetail(id: number): void {
  void router.push(`/questions/${id}`)
}

function openEdit(id: number): void {
  void router.push(`/questions/${id}/edit`)
}

async function removeQuestion(questionId: number): Promise<void> {
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

  try {
    const result = await deleteQuestion(questionId)
    ElMessage.success(result.message)
    if (pageData.items.length === 1 && pageData.page > 0) {
      pageData.page -= 1
    }
    await loadQuestions()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  }
}

onMounted(() => {
  void knowledgeStore.load().catch(() => undefined)
  void loadQuestions()
})
</script>

<style scoped>
.filter-card,
.page-alert {
  margin-bottom: 18px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.filter-control {
  width: 190px;
}

.filter-summary {
  margin-left: auto;
  color: var(--app-muted);
  font-size: 0.9rem;
}

.list-card :deep(.el-card__body) {
  padding: 0;
}

.question-table {
  width: 100%;
}

.question-link {
  display: -webkit-box;
  max-width: 100%;
  padding: 0;
  overflow: hidden;
  border: 0;
  background: none;
  color: #1e40af;
  font-size: 0.95rem;
  line-height: 1.55;
  text-align: left;
  cursor: pointer;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.question-link:hover {
  text-decoration: underline;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding: 18px 20px;
  border-top: 1px solid var(--app-border);
}

@media (max-width: 680px) {
  .filter-control {
    width: 100%;
  }

  .filter-summary {
    width: 100%;
    margin-left: 0;
  }
}
</style>
