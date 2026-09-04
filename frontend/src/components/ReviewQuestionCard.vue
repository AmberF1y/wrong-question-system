<template>
  <el-card class="page-card review-question-card" shadow="never">
    <template #header>
      <div class="question-header">
        <div>
          <span class="question-header__eyebrow">当前题目</span>
          <h2>{{ question.subject }}</h2>
        </div>
        <div class="question-meta">
          <el-tag effect="plain">到期：{{ formatDate(question.nextReviewDate) }}</el-tag>
          <span data-testid="due-count">
            本次获取时待复习 {{ dueCount }} 道（包含本题）
          </span>
        </div>
      </div>
    </template>

    <div class="content-prose question-content" data-testid="review-question-text">
      {{ question.questionText }}
    </div>

    <div v-if="!answerRevealed" class="question-actions">
      <p>请先在纸上重新作答，系统不会保存本次作答内容。</p>
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
import type { DueQuestion } from '../types/review'
import { formatDate } from '../utils/date-time'

defineProps<{
  question: DueQuestion
  dueCount: number
  loadingAnswer: boolean
  answerRevealed: boolean
  disabled?: boolean
}>()

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
