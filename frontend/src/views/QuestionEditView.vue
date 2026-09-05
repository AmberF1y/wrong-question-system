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
      :current-image-url="currentImageUrl"
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
import {
  getQuestion,
  getQuestionImageUrl,
  removeQuestionImage,
  updateQuestion,
  uploadQuestionImage,
} from '../api/questions'
import AppEmptyState from '../components/AppEmptyState.vue'
import AppPageHeader from '../components/AppPageHeader.vue'
import QuestionForm from '../components/QuestionForm.vue'
import { useKnowledgePointStore } from '../stores/knowledge-points'
import type {
  QuestionDetail,
  QuestionFormPayload,
  QuestionImageChange,
} from '../types/question'
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

const currentImageUrl = computed(() => {
  if (!question.value?.imagePath) {
    return ''
  }
  return getQuestionImageUrl(questionId, question.value.updatedTime)
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

async function saveQuestion(
  payload: QuestionFormPayload,
  imageChange: QuestionImageChange,
): Promise<void> {
  if (!question.value) {
    return
  }

  submitting.value = true
  fieldErrors.value = {}

  try {
    let updated = question.value
    let textUpdated = false

    if (!matchesCurrentQuestion(payload, question.value)) {
      updated = await updateQuestion(questionId, payload)
      question.value = updated
      textUpdated = true
    }

    try {
      if (imageChange.file) {
        const uploaded = await uploadQuestionImage(questionId, imageChange.file)
        question.value = { ...updated, imagePath: uploaded.imagePath }
      } else if (imageChange.removeExisting) {
        await removeQuestionImage(questionId)
        question.value = { ...updated, imagePath: null }
      }
    } catch (error) {
      const action = imageChange.file ? '上传' : '移除'
      const prefix = textUpdated
        ? '文字和知识点已保存，但'
        : ''
      ElMessage.error(`${prefix}图片${action}失败：${normalizeApiError(error).message}`)

      try {
        question.value = await getQuestion(questionId)
      } catch {
        // 保留已确认成功的文字响应，避免把部分成功误报为整体失败。
      }
      return
    }

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

function matchesCurrentQuestion(
  payload: QuestionFormPayload,
  current: QuestionDetail,
): boolean {
  const currentIds = current.knowledgePoints.map((point) => point.id)
  return (
    payload.questionText === current.questionText &&
    payload.wrongAnswer === current.wrongAnswer &&
    payload.correctAnswer === current.correctAnswer &&
    payload.analysis === current.analysis &&
    payload.errorReason === current.errorReason &&
    payload.knowledgePointIds.length === currentIds.length &&
    payload.knowledgePointIds.every((id, index) => id === currentIds[index])
  )
}

onMounted(loadPage)
</script>

<style scoped>
.page-alert {
  margin-bottom: 18px;
}
</style>
