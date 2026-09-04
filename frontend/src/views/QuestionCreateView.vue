<template>
  <div>
    <AppPageHeader
      title="录入错题"
      description="填写题目、答案与复盘信息，并关联同一科目下的知识点。"
    />

    <el-alert
      v-if="knowledgeError"
      :title="knowledgeError"
      type="error"
      show-icon
      :closable="false"
      class="page-alert"
    >
      <template #default>
        <el-button link type="primary" @click="loadKnowledgeTree">重新加载知识树</el-button>
      </template>
    </el-alert>

    <el-skeleton v-if="knowledgeStore.loading && !knowledgeStore.loaded" :rows="10" animated />

    <QuestionForm
      v-else
      :knowledge-tree="knowledgeStore.tree"
      :submitting="submitting"
      :field-errors="fieldErrors"
      submit-label="保存错题"
      @submit="saveQuestion"
      @cancel="router.push('/questions')"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { createQuestion } from '../api/questions'
import AppPageHeader from '../components/AppPageHeader.vue'
import QuestionForm from '../components/QuestionForm.vue'
import { useKnowledgePointStore } from '../stores/knowledge-points'
import type { QuestionFormPayload } from '../types/question'
import { normalizeApiError } from '../utils/api-error'

const router = useRouter()
const knowledgeStore = useKnowledgePointStore()
const knowledgeError = ref('')
const submitting = ref(false)
const fieldErrors = ref<Record<string, string>>({})

async function loadKnowledgeTree(): Promise<void> {
  knowledgeError.value = ''
  try {
    await knowledgeStore.load()
  } catch (error) {
    knowledgeError.value = normalizeApiError(error).message
  }
}

async function saveQuestion(payload: QuestionFormPayload): Promise<void> {
  submitting.value = true
  fieldErrors.value = {}

  try {
    const question = await createQuestion(payload)
    ElMessage.success('错题保存成功')
    await router.push(`/questions/${question.id}`)
  } catch (error) {
    const normalized = normalizeApiError(error)
    fieldErrors.value = normalized.fieldErrors
    ElMessage.error(normalized.message)
  } finally {
    submitting.value = false
  }
}

onMounted(loadKnowledgeTree)
</script>

<style scoped>
.page-alert {
  margin-bottom: 18px;
}
</style>
