<template>
  <el-tag :type="status === 'MASTERED' ? 'success' : 'primary'" effect="light">
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ReviewStatus } from '../types/question'

const props = defineProps<{
  status: ReviewStatus
}>()

const label = computed(() => (props.status === 'MASTERED' ? '已掌握' : '复习中'))
</script>
