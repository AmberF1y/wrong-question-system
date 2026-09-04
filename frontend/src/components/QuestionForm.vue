<template>
  <el-form
    :model="form"
    label-position="top"
    class="question-form"
    @submit.prevent="submit"
  >
    <el-card class="page-card form-section" shadow="never">
      <template #header>
        <div class="form-section__header">
          <strong>题目信息</strong>
          <span>保留必要换行，系统不会把正文当作 HTML 执行</span>
        </div>
      </template>

      <el-form-item
        label="题目"
        prop="questionText"
        :error="fieldErrors.questionText"
      >
        <el-input
          v-model="form.questionText"
          type="textarea"
          :rows="6"
          maxlength="10000"
          show-word-limit
          placeholder="输入完整题目内容"
          :disabled="submitting"
          @input="clearClientFieldError('questionText')"
        />
        <p v-if="clientFieldErrors.questionText" class="question-form__field-error">
          {{ clientFieldErrors.questionText }}
        </p>
      </el-form-item>

      <el-form-item
        label="我的错误答案"
        prop="wrongAnswer"
        :error="fieldErrors.wrongAnswer"
      >
        <el-input
          v-model="form.wrongAnswer"
          type="textarea"
          :rows="4"
          maxlength="5000"
          show-word-limit
          placeholder="没有作答时填写“未作答”"
          :disabled="submitting"
          @input="clearClientFieldError('wrongAnswer')"
        />
        <p v-if="clientFieldErrors.wrongAnswer" class="question-form__field-error">
          {{ clientFieldErrors.wrongAnswer }}
        </p>
      </el-form-item>

      <el-form-item
        label="正确答案"
        prop="correctAnswer"
        :error="fieldErrors.correctAnswer"
      >
        <el-input
          v-model="form.correctAnswer"
          type="textarea"
          :rows="4"
          maxlength="5000"
          show-word-limit
          placeholder="输入正确答案"
          :disabled="submitting"
          @input="clearClientFieldError('correctAnswer')"
        />
        <p v-if="clientFieldErrors.correctAnswer" class="question-form__field-error">
          {{ clientFieldErrors.correctAnswer }}
        </p>
      </el-form-item>
    </el-card>

    <el-card class="page-card form-section" shadow="never">
      <template #header>
        <div class="form-section__header">
          <strong>复盘信息</strong>
          <span>解析说明解法，错误原因记录本次失误的根源</span>
        </div>
      </template>

      <el-form-item
        label="解析"
        prop="analysis"
        :error="fieldErrors.analysis"
      >
        <el-input
          v-model="form.analysis"
          type="textarea"
          :rows="6"
          maxlength="10000"
          show-word-limit
          placeholder="输入解题过程或关键结论"
          :disabled="submitting"
          @input="clearClientFieldError('analysis')"
        />
        <p v-if="clientFieldErrors.analysis" class="question-form__field-error">
          {{ clientFieldErrors.analysis }}
        </p>
      </el-form-item>

      <el-form-item
        label="错误原因"
        prop="errorReason"
        :error="fieldErrors.errorReason"
      >
        <el-input
          v-model="form.errorReason"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          placeholder="例如：概念混淆、计算失误、遗漏条件"
          :disabled="submitting"
          @input="clearClientFieldError('errorReason')"
        />
        <p v-if="clientFieldErrors.errorReason" class="question-form__field-error">
          {{ clientFieldErrors.errorReason }}
        </p>
      </el-form-item>
    </el-card>

    <el-card class="page-card form-section" shadow="never">
      <template #header>
        <div class="form-section__header">
          <strong>知识点</strong>
          <span>科目由所选知识点的根节点自动确定</span>
        </div>
      </template>

      <el-form-item
        label="关联知识点"
        prop="knowledgePointIds"
        :error="fieldErrors.knowledgePointIds"
      >
        <KnowledgePointSelector
          v-model="form.knowledgePointIds"
          :tree="knowledgeTree"
          :disabled="submitting"
        />
        <p v-if="clientFieldErrors.knowledgePointIds" class="question-form__field-error">
          {{ clientFieldErrors.knowledgePointIds }}
        </p>
      </el-form-item>
    </el-card>

    <div class="question-form__actions">
      <el-button :disabled="submitting" @click="emit('cancel')">取消</el-button>
      <el-button
        type="primary"
        native-type="submit"
        :loading="submitting"
      >
        {{ submitLabel }}
      </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { KnowledgePointTreeNode } from '../types/knowledge-point'
import type { QuestionFormPayload } from '../types/question'
import { areKnowledgePointsInSameRoot } from '../utils/knowledge-tree'
import KnowledgePointSelector from './KnowledgePointSelector.vue'

const props = withDefaults(
  defineProps<{
    initialValue?: QuestionFormPayload
    knowledgeTree: KnowledgePointTreeNode[]
    submitting?: boolean
    submitLabel?: string
    fieldErrors?: Record<string, string>
  }>(),
  {
    initialValue: undefined,
    submitting: false,
    submitLabel: '保存错题',
    fieldErrors: () => ({}),
  },
)

const emit = defineEmits<{
  submit: [value: QuestionFormPayload]
  cancel: []
}>()

const form = reactive<QuestionFormPayload>(emptyForm())
const clientFieldErrors = reactive<Record<keyof QuestionFormPayload, string>>(
  emptyClientFieldErrors(),
)

function emptyForm(): QuestionFormPayload {
  return {
    questionText: '',
    wrongAnswer: '',
    correctAnswer: '',
    analysis: '',
    errorReason: '',
    knowledgePointIds: [],
  }
}

function emptyClientFieldErrors(): Record<keyof QuestionFormPayload, string> {
  return {
    questionText: '',
    wrongAnswer: '',
    correctAnswer: '',
    analysis: '',
    errorReason: '',
    knowledgePointIds: '',
  }
}

function copyInitialValue(value?: QuestionFormPayload): void {
  Object.assign(form, value ? { ...value, knowledgePointIds: [...value.knowledgePointIds] } : emptyForm())
  Object.assign(clientFieldErrors, emptyClientFieldErrors())
}

function clearClientFieldError(field: keyof QuestionFormPayload): void {
  clientFieldErrors[field] = ''
}

function validateForm(): boolean {
  Object.assign(clientFieldErrors, emptyClientFieldErrors())

  if (!form.questionText.trim()) {
    clientFieldErrors.questionText = '题目内容不能为空'
  }
  if (!form.wrongAnswer.trim()) {
    clientFieldErrors.wrongAnswer = '错误答案不能为空'
  }
  if (!form.correctAnswer.trim()) {
    clientFieldErrors.correctAnswer = '正确答案不能为空'
  }
  if (!form.analysis.trim()) {
    clientFieldErrors.analysis = '题目解析不能为空'
  }
  if (!form.errorReason.trim()) {
    clientFieldErrors.errorReason = '错误原因不能为空'
  }

  if (form.knowledgePointIds.length === 0) {
    clientFieldErrors.knowledgePointIds = '至少选择一个知识点'
  } else if (!areKnowledgePointsInSameRoot(props.knowledgeTree, form.knowledgePointIds)) {
    clientFieldErrors.knowledgePointIds = '所选知识点必须属于同一科目'
  }

  return Object.values(clientFieldErrors).every((message) => message === '')
}

function submit(): void {
  if (!validateForm()) {
    return
  }

  emit('submit', {
    questionText: form.questionText.trim(),
    wrongAnswer: form.wrongAnswer.trim(),
    correctAnswer: form.correctAnswer.trim(),
    analysis: form.analysis.trim(),
    errorReason: form.errorReason.trim(),
    knowledgePointIds: [...new Set(form.knowledgePointIds)],
  })
}

watch(
  () => props.initialValue,
  (value) => copyInitialValue(value),
  { immediate: true },
)

watch(
  () => form.knowledgePointIds,
  () => {
    clearClientFieldError('knowledgePointIds')
  },
  { deep: true },
)
</script>

<style scoped>
.question-form {
  display: grid;
  gap: 18px;
}

.form-section__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.form-section__header strong {
  color: var(--app-navy);
  font-size: 1rem;
}

.form-section__header span {
  color: var(--app-muted);
  font-size: 0.84rem;
}

.question-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 4px 0 18px;
}

.question-form__field-error {
  width: 100%;
  margin: 4px 0 0;
  color: var(--el-color-danger);
  font-size: 0.78rem;
  line-height: 1.4;
}

@media (max-width: 680px) {
  .form-section__header {
    display: block;
  }

  .form-section__header span {
    display: block;
    margin-top: 4px;
  }
}
</style>
