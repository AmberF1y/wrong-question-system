<template>
  <el-card class="page-card review-question-card" shadow="never">
    <template #header>
      <div class="question-header">
        <div>
          <span class="question-header__eyebrow">
            {{ mode === 'SPOT_CHECK' ? '已掌握题抽查' : '当前题目' }}
          </span>
          <h2>{{ question.subject }}</h2>
        </div>
        <div class="question-meta">
          <el-tag v-if="mode === 'SPOT_CHECK'" type="success" effect="plain">
            随机抽查
          </el-tag>
          <el-tag v-else effect="plain">到期：{{ formatDate(dueDate) }}</el-tag>
          <span v-if="mode === 'SPOT_CHECK'" data-testid="spot-check-count">
            当前有 {{ dueCount }} 道符合抽查条件
          </span>
          <span v-else data-testid="due-count">
            本次获取时待复习 {{ dueCount }} 道（包含本题）
          </span>
        </div>
      </div>
    </template>

    <MathText
      :text="question.questionText"
      class="content-prose question-content"
      data-testid="review-question-text"
    />

    <QuestionImageDisplay v-if="imageUrl" :src="imageUrl" class="review-question-image" />

    <div v-if="!answerRevealed" class="question-actions">
      <p>
        {{
          mode === 'SPOT_CHECK'
            ? '请先重新作答，确认自己是否仍然掌握这道题。'
            : '请先在纸上重新作答，系统不会保存本次作答内容。'
        }}
      </p>
      <el-button
        type="primary"
        :loading="loadingAnswer"
        :disabled="disabled"
        data-testid="show-answer"
        @click="emit('show-answer')"
      >
        查看答案
      </el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DueQuestion, SpotCheckQuestion } from '../types/review'
import { formatDate } from '../utils/date-time'
import MathText from './MathText.vue'
import QuestionImageDisplay from './QuestionImageDisplay.vue'

const props = withDefaults(defineProps<{
  question: DueQuestion | SpotCheckQuestion
  dueCount: number
  mode?: 'DUE' | 'SPOT_CHECK'
  loadingAnswer: boolean
  answerRevealed: boolean
  imageUrl?: string
  disabled?: boolean
}>(), {
  mode: 'DUE',
})

const dueDate = computed(() =>
  'nextReviewDate' in props.question ? props.question.nextReviewDate : null,
)

const emit = defineEmits<{
  'show-answer': []
}>()
</script>

<style scoped>
.question-header {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
}

.question-header__eyebrow {
  color: var(--app-muted);
  font-size: 0.8rem;
}

h2 {
  margin: 4px 0 0;
  color: var(--app-navy);
  font-size: 1.15rem;
}

.question-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: flex-end;
  color: var(--app-muted);
  font-size: 0.86rem;
}

.question-content {
  min-height: 120px;
  padding: 8px 2px 24px;
  color: #1e293b;
  font-size: 1.08rem;
}

.question-actions {
  display: flex;
  gap: 18px;
  align-items: center;
  justify-content: space-between;
  padding-top: 18px;
  border-top: 1px solid var(--app-border);
}

.review-question-image {
  margin: 0 0 24px;
}

.question-actions p {
  margin: 0;
  color: var(--app-muted);
  font-size: 0.9rem;
}

@media (max-width: 680px) {
  .question-header,
  .question-actions {
    align-items: flex-start;
    flex-direction: column;
  }

  .question-meta {
    justify-content: flex-start;
  }
}
</style>
