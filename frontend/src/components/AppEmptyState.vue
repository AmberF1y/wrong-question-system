<template>
  <div class="empty-state">
    <el-empty :description="description">
      <slot />
    </el-empty>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  description: string
}>()
</script>

<style scoped>
.empty-state {
  padding: 28px 12px;
}
</style>
