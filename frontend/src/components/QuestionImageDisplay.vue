<template>
  <section class="question-image-display" data-testid="question-image-display">
    <el-alert
      v-if="failed"
      title="题目图片加载失败"
      type="warning"
      :closable="false"
      show-icon
      data-testid="question-image-error"
    >
      <template #default>
        <el-button link type="primary" data-testid="retry-question-image" @click="retry">
          重新加载图片
        </el-button>
      </template>
    </el-alert>

    <button
      v-else
      type="button"
      class="question-image-display__button"
      aria-label="查看题目大图"
      @click="previewVisible = true"
    >
      <img
        :src="resolvedSrc"
        alt="题目图片"
        class="question-image-display__image"
        data-testid="question-image"
        @error="failed = true"
      />
    </button>

    <el-dialog v-model="previewVisible" title="题目图片" width="min(92vw, 1100px)">
      <img :src="resolvedSrc" alt="题目大图" class="question-image-display__preview" />
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = defineProps<{
  src: string
}>()

const failed = ref(false)
const previewVisible = ref(false)
const retryVersion = ref(0)

const resolvedSrc = computed(() => {
  if (retryVersion.value === 0) {
    return props.src
  }
  const separator = props.src.includes('?') ? '&' : '?'
  return `${props.src}${separator}retry=${retryVersion.value}`
})

function retry(): void {
  retryVersion.value += 1
  failed.value = false
}

watch(
  () => props.src,
  () => {
    failed.value = false
    previewVisible.value = false
    retryVersion.value = 0
  },
)
</script>

<style scoped>
.question-image-display {
  margin-top: 18px;
}

.question-image-display__button {
  display: block;
  width: 100%;
  padding: 0;
  overflow: hidden;
  background: #f8fafc;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  cursor: zoom-in;
}

.question-image-display__image,
.question-image-display__preview {
  display: block;
  width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.question-image-display__preview {
  margin: 0 auto;
}
</style>
