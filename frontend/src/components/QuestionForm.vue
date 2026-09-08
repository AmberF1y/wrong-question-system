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

      <details class="formula-help" data-testid="formula-help">
        <summary>公式输入说明与预览</summary>
        <div class="formula-help__body">
          <p>
            普通文字直接输入；行内公式使用 <code>$...$</code>，独立公式使用
            <code>$$...$$</code>，需要显示美元符号时输入 <code>\$</code>。
          </p>
          <ul>
            <li>分式：<code>$\frac{a}{b}$</code></li>
            <li>根号：<code>$\sqrt{x}$</code> 或 <code>$\sqrt[n]{x}$</code></li>
            <li>极限：<code>$\lim_{x\to0}\frac{\sin x}{x}=1$</code></li>
            <li>积分：<code>$\int_a^b f(x)\,dx$</code></li>
            <li>求和：<code>$\sum_{n=1}^{\infty} a_n$</code></li>
          </ul>

          <div class="formula-preview">
            <h3>当前内容预览</h3>
            <p v-if="previewFields.length === 0" class="formula-preview__empty">
              输入题目、答案或复盘内容后，这里会显示排版效果。
            </p>
            <template v-else>
              <section
                v-for="field in previewFields"
                :key="field.key"
                class="formula-preview__section"
              >
                <h4>{{ field.label }}</h4>
                <MathText :text="field.text" class="content-prose" />
              </section>
            </template>
          </div>
        </div>
      </details>
    </el-card>

    <el-card class="page-card form-section" shadow="never">
      <template #header>
        <div class="form-section__header">
          <strong>题目图片（可选）</strong>
          <span>图片用于保留公式、图表或原题排版，不进行 OCR</span>
        </div>
      </template>

      <QuestionImageField
        :current-image-url="currentImageUrl"
        :disabled="submitting"
        @change="imageChange = $event"
      />
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
import { computed, reactive, ref, watch } from 'vue'
import type { KnowledgePointTreeNode } from '../types/knowledge-point'
import type { QuestionFormPayload, QuestionImageChange } from '../types/question'
import { areKnowledgePointsInSameRoot } from '../utils/knowledge-tree'
import KnowledgePointSelector from './KnowledgePointSelector.vue'
import MathText from './MathText.vue'
import QuestionImageField from './QuestionImageField.vue'

const props = withDefaults(
  defineProps<{
    initialValue?: QuestionFormPayload
    knowledgeTree: KnowledgePointTreeNode[]
    submitting?: boolean
    submitLabel?: string
    fieldErrors?: Record<string, string>
    currentImageUrl?: string
  }>(),
  {
    initialValue: undefined,
    submitting: false,
    submitLabel: '保存错题',
    fieldErrors: () => ({}),
    currentImageUrl: '',
  },
)

const emit = defineEmits<{
  submit: [value: QuestionFormPayload, imageChange: QuestionImageChange]
  cancel: []
}>()

const form = reactive<QuestionFormPayload>(emptyForm())
const imageChange = ref<QuestionImageChange>({
  file: null,
  removeExisting: false,
})
const clientFieldErrors = reactive<Record<keyof QuestionFormPayload, string>>(
  emptyClientFieldErrors(),
)
const previewFields = computed(() =>
  [
    { key: 'questionText', label: '题目', text: form.questionText },
    { key: 'wrongAnswer', label: '我的错误答案', text: form.wrongAnswer },
    { key: 'correctAnswer', label: '正确答案', text: form.correctAnswer },
    { key: 'analysis', label: '解析', text: form.analysis },
    { key: 'errorReason', label: '错误原因', text: form.errorReason },
  ].filter((field) => field.text.length > 0),
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

  emit(
    'submit',
    {
      questionText: form.questionText.trim(),
      wrongAnswer: form.wrongAnswer.trim(),
      correctAnswer: form.correctAnswer.trim(),
      analysis: form.analysis.trim(),
      errorReason: form.errorReason.trim(),
      knowledgePointIds: [...new Set(form.knowledgePointIds)],
    },
    { ...imageChange.value },
  )
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

.formula-help {
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: #f8fafc;
}

.formula-help summary {
  padding: 12px 14px;
  color: var(--app-blue);
  font-weight: 600;
  cursor: pointer;
}

.formula-help__body {
  padding: 0 14px 14px;
  color: #475569;
  font-size: 0.88rem;
  line-height: 1.7;
}

.formula-help__body > p {
  margin: 0 0 8px;
}

.formula-help__body ul {
  margin: 0;
  padding-left: 22px;
}

.formula-help code {
  padding: 1px 5px;
  border-radius: 4px;
  background: #e8eef7;
  color: #334155;
  font-family: Consolas, "Courier New", monospace;
}

.formula-preview {
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--app-border);
}

.formula-preview h3,
.formula-preview h4 {
  margin: 0;
  color: var(--app-navy);
}

.formula-preview h3 {
  margin-bottom: 10px;
  font-size: 0.92rem;
}

.formula-preview h4 {
  margin-bottom: 4px;
  font-size: 0.8rem;
}

.formula-preview__empty {
  margin: 0;
  color: var(--app-muted);
}

.formula-preview__section {
  min-width: 0;
  padding: 10px 0;
  border-top: 1px dashed #d7e0eb;
}

.formula-preview__section:first-of-type {
  border-top: 0;
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
