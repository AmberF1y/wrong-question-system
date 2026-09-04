<template>
  <el-card class="page-card answer-panel" shadow="never" data-testid="answer-panel">
    <template #header>
      <div>
        <strong>答案与复盘</strong>
        <p>对照纸上的作答，再根据实际掌握情况评价。</p>
      </div>
    </template>

    <section class="answer-section answer-section--danger">
      <h3>我的错误答案</h3>
      <div class="content-prose">{{ question.wrongAnswer }}</div>
    </section>
    <section class="answer-section answer-section--success">
      <h3>正确答案</h3>
      <div class="content-prose">{{ question.correctAnswer }}</div>
    </section>
    <section class="answer-section">
      <h3>解析</h3>
      <div class="content-prose">{{ question.analysis }}</div>
    </section>
    <section class="answer-section answer-section--warning">
      <h3>错误原因</h3>
      <div class="content-prose">{{ question.errorReason }}</div>
    </section>
  </el-card>
</template>

<script setup lang="ts">
import type { QuestionDetail } from '../types/question'

defineProps<{
  question: QuestionDetail
}>()
</script>

<style scoped>
.answer-panel :deep(.el-card__header) p {
  margin: 5px 0 0;
  color: var(--app-muted);
  font-size: 0.86rem;
}

.answer-panel :deep(.el-card__body) {
  padding: 0 24px;
}

.answer-section {
  padding: 22px 0;
  border-bottom: 1px solid var(--app-border);
}

.answer-section:last-child {
  border-bottom: 0;
}

.answer-section h3 {
  margin: 0 0 10px;
  color: var(--app-navy);
  font-size: 0.98rem;
}

.answer-section--danger,
.answer-section--success,
.answer-section--warning {
  padding-left: 16px;
  border-left: 3px solid;
}

.answer-section--danger {
  border-left-color: #dc2626;
}

.answer-section--success {
  border-left-color: #16a34a;
}

.answer-section--warning {
  border-left-color: #d97706;
}
</style>
