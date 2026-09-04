<template>
  <el-card class="page-card" shadow="never">
    <AppEmptyState description="页面不存在">
      <el-button type="primary" @click="router.push('/questions')">返回错题列表</el-button>
    </AppEmptyState>
  </el-card>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppEmptyState from '../components/AppEmptyState.vue'

const router = useRouter()
</script>
