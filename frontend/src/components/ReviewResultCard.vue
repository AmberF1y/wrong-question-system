<template>
  <el-card class="page-card result-card" shadow="never" data-testid="review-result">
    <el-result
      icon="success"
      :title="synchronized ? '服务器状态已更新' : '本次评价已记录'"
      :sub-title="synchronized ? '以下是重新同步得到的最新复习状态。' : '以下结果来自后端响应。'"
    >
      <template #extra>
        <dl class="result-summary">
          <div v-if="rating">
            <dt>本次评价</dt>
            <dd>{{ ratingLabel }}</dd>
          </div>
          <div>
            <dt>当前状态</dt>
            <dd><ReviewStatusTag :status="reviewStatus" /></dd>
          </div>
          <div>
            <dt>下次复习</dt>
            <dd>
              {{ reviewStatus === 'MASTERED' ? '已退出常规复习队列' : formatDate(nextReviewDate) }}
            </dd>
          </div>
          <div>
            <dt>连续熟练</dt>
            <dd>{{ consecutiveProficientCount }} 次</dd>
          </div>
        </dl>

        <el-button type="primary" data-testid="next-question" @click="emit('next')">
          下一题
        </el-button>
      </template>
    </el-result>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ReviewRating, ReviewStatus } from '../types/review'
import { formatDate } from '../utils/date-time'
import ReviewStatusTag from './ReviewStatusTag.vue'

const props = defineProps<{
  rating: ReviewRating | null
  reviewStatus: ReviewStatus
  nextReviewDate: string | null
  consecutiveProficientCount: number
  synchronized: boolean
}>()

const emit = defineEmits<{
  next: []
}>()

const ratingLabels: Record<ReviewRating, string> = {
  NOT_KNOWN: '不会',
  FUZZY: '模糊',
  BASICALLY_MASTERED: '基本掌握',
  PROFICIENT: '熟练',
}

const ratingLabel = computed(() => (props.rating ? ratingLabels[props.rating] : '—'))
</script>

<style scoped>
.result-card {
  border-color: #bbf7d0 !important;
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  width: min(760px, 100%);
  margin: 0 auto 22px;
  text-align: left;
}

.result-summary > div {
  padding: 13px 14px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: #f8fafc;
}

dt {
  margin-bottom: 6px;
  color: var(--app-muted);
  font-size: 0.78rem;
}

dd {
  margin: 0;
  color: #334155;
  font-size: 0.92rem;
  line-height: 1.5;
}

@media (max-width: 760px) {
  .result-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 480px) {
  .result-summary {
    grid-template-columns: 1fr;
  }
}
</style>
