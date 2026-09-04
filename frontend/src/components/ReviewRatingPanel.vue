<template>
  <el-card class="page-card rating-panel" shadow="never" data-testid="rating-panel">
    <template #header>
      <div>
        <strong>掌握程度评价</strong>
        <p>以下间隔是固定规则说明，实际结果以后端响应为准。</p>
      </div>
    </template>

    <div class="rating-grid">
      <el-button
        v-for="option in ratingOptions"
        :key="option.value"
        :type="option.buttonType"
        :plain="option.value !== 'PROFICIENT'"
        :disabled="disabled"
        :loading="submittingRating === option.value"
        :data-testid="`rating-${option.value}`"
        class="rating-button"
        @click="emit('rate', option.value)"
      >
        <span class="rating-button__label">{{ option.label }}</span>
        <span class="rating-button__rule">{{ option.rule }}</span>
      </el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { ReviewRating } from '../types/review'

type RatingButtonType = 'primary' | 'success' | 'warning' | 'danger'

interface RatingOption {
  value: ReviewRating
  label: string
  rule: string
  buttonType: RatingButtonType
}

const ratingOptions: RatingOption[] = [
  { value: 'NOT_KNOWN', label: '不会', rule: '1 天后', buttonType: 'danger' },
  { value: 'FUZZY', label: '模糊', rule: '3 天后', buttonType: 'warning' },
  {
    value: 'BASICALLY_MASTERED',
    label: '基本掌握',
    rule: '7 天后',
    buttonType: 'primary',
  },
  {
    value: 'PROFICIENT',
    label: '熟练',
    rule: '14 天后；连续第 2 次进入已掌握',
    buttonType: 'success',
  },
]

defineProps<{
  disabled: boolean
  submittingRating: ReviewRating | null
}>()

const emit = defineEmits<{
  rate: [rating: ReviewRating]
}>()
</script>

<style scoped>
.rating-panel :deep(.el-card__header) p {
  margin: 5px 0 0;
  color: var(--app-muted);
  font-size: 0.86rem;
}

.rating-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.rating-button {
  width: 100%;
  min-height: 72px;
  margin: 0 !important;
  white-space: normal;
}

.rating-button :deep(span) {
  display: grid;
  gap: 5px;
}

.rating-button__label {
  font-weight: 700;
}

.rating-button__rule {
  font-size: 0.78rem;
  font-weight: 400;
  line-height: 1.35;
  opacity: 0.82;
}

@media (max-width: 980px) {
  .rating-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .rating-grid {
    grid-template-columns: 1fr;
  }
}
</style>
