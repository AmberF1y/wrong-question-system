<template>
  <div>
    <AppPageHeader
      title="修改错题"
      description="提交时会整体替换文本和知识点关联，复习进度由后端保留。"
    />

    <el-alert
      v-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    >
      <template #default>
        <el-button link type="primary" @click="loadPage">重新加载</el-button>
      </template>
    </el-alert>

    <el-skeleton v-if="loading" :rows="10" animated />

    <AppEmptyState v-else-if="notFound" description="这道错题不存在或已经被删除">
      <el-button type="primary" @click="router.push('/questions')">返回错题列表</el-button>
    </AppEmptyState>

    <QuestionForm
      v-else-if="initialValue"
      :initial-value="initialValue"
      :knowledge-tree="knowledgeStore.tree"
      :submitting="submitting"
      :field-errors="fieldErrors"
      submit-label="保存修改"
      @submit="saveQuestion"
      @cancel="router.push(`/questions/${questionId}`)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getQuestion, updateQuestion } from '../api/questions'
import AppEmptyState from '../components/AppEmptyState.vue'
import AppPageHeader from '../components/AppPageHeader.vue'
import QuestionForm from '../components/QuestionForm.vue'
import { useKnowledgePointStore } from '../stores/knowledge-points'
import type { QuestionDetail, QuestionFormPayload } from '../types/question'
import { isNotFoundError, normalizeApiError } from '../utils/api-error'

const route = useRoute()
const router = useRouter()
const knowledgeStore = useKnowledgePointStore()
const loading = ref(true)
const submitting = ref(false)
const notFound = ref(false)
const loadError = ref('')
const fieldErrors = ref<Record<string, string>>({})
const question = ref<QuestionDetail>()

const questionId = Number(route.params.id)

const initialValue = computed<QuestionFormPayload | undefined>(() => {
  if (!question.value) {
    return undefined
  }

  return {
    questionText: question.value.questionText,
    wrongAnswer: question.value.wrongAnswer,
    correctAnswer: question.value.correctAnswer,
    analysis: question.value.analysis,
    errorReason: question.value.errorReason,
    knowledgePointIds: question.value.knowledgePoints.map((point) => point.id),
  }
})

async function loadPage(): Promise<void> {
  loadError.value = ''
  notFound.value = false
  loading.value = true

  if (!Number.isInteger(questionId) || questionId <= 0) {
    notFound.value = true
    loading.value = false
    return
  }

  try {
    const [loadedQuestion] = await Promise.all([
      getQuestion(questionId),
      knowledgeStore.load(),
    ])
    question.value = loadedQuestion
  } catch (error) {
    if (isNotFoundError(error)) {
      notFound.value = true
    } else {
      loadError.value = normalizeApiError(error).message
    }
  } finally {
    loading.value = false
  }
}

async function saveQuestion(payload: QuestionFormPayload): Promise<void> {
  submitting.value = true
  fieldErrors.value = {}

  try {
    const updated = await updateQuestion(questionId, payload)
    ElMessage.success('错题修改成功')
    await router.push(`/questions/${updated.id}`)
  } catch (error) {
    const normalized = normalizeApiError(error)
    fieldErrors.value = normalized.fieldErrors
    ElMessage.error(normalized.message)
  } finally {
    submitting.value = false
  }
}

onMounted(loadPage)
</script>

<style scoped>
.page-alert {
  margin-bottom: 18px;
}
</style>
