<template>
  <div class="question-image-field" data-testid="question-image-field">
    <input
      ref="fileInput"
      class="question-image-field__input"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif,.png,.jpg,.jpeg,.webp,.gif"
      :disabled="disabled"
      data-testid="question-image-input"
      @change="selectFile"
    />

    <div v-if="previewSource" class="question-image-field__preview-wrap">
      <img :src="previewSource" alt="题目图片预览" class="question-image-field__preview" />
    </div>

    <el-alert
      v-else-if="removeExisting"
      title="保存后将移除当前图片"
      type="warning"
      :closable="false"
      show-icon
      data-testid="question-image-remove-pending"
    />

    <div v-else class="question-image-field__empty">未选择图片</div>

    <div v-if="selectedFile" class="question-image-field__metadata">
      <strong>{{ selectedFile.name }}</strong>
      <span>{{ formatFileSize(selectedFile.size) }}</span>
    </div>

    <p v-if="validationError" class="question-image-field__error">
      {{ validationError }}
    </p>

    <div class="question-image-field__actions">
      <el-button :disabled="disabled" @click="openFilePicker">
        {{ selectedFile || currentImageUrl ? '选择替换图片' : '选择图片' }}
      </el-button>
      <el-button
        v-if="selectedFile"
        :disabled="disabled"
        data-testid="clear-selected-image"
        @click="clearSelectedFile"
      >
        清除选择
      </el-button>
      <el-button
        v-if="currentImageUrl && !removeExisting && !selectedFile"
        type="danger"
        plain
        :disabled="disabled"
        data-testid="remove-existing-image"
        @click="markExistingForRemoval"
      >
        移除当前图片
      </el-button>
      <el-button
        v-if="removeExisting"
        :disabled="disabled"
        data-testid="undo-remove-image"
        @click="undoRemoval"
      >
        保留当前图片
      </el-button>
    </div>

    <p class="question-image-field__hint">
      支持 PNG、JPEG、WebP、GIF，单张不超过 20 MiB。服务器会再次校验文件内容。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { QuestionImageChange } from '../types/question'

const MAX_IMAGE_SIZE_BYTES = 20 * 1024 * 1024
const allowedMimeTypes = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif'])
const allowedExtensions = new Set(['png', 'jpg', 'jpeg', 'webp', 'gif'])

const props = withDefaults(
  defineProps<{
    currentImageUrl?: string
    disabled?: boolean
  }>(),
  {
    currentImageUrl: '',
    disabled: false,
  },
)

const emit = defineEmits<{
  change: [value: QuestionImageChange]
}>()

const fileInput = ref<HTMLInputElement>()
const selectedFile = ref<File | null>(null)
const objectUrl = ref('')
const removeExisting = ref(false)
const validationError = ref('')

const previewSource = computed(() => {
  if (objectUrl.value) {
    return objectUrl.value
  }
  return removeExisting.value ? '' : props.currentImageUrl
})

function openFilePicker(): void {
  fileInput.value?.click()
}

function selectFile(event: Event): void {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  validationError.value = ''

  if (!file) {
    return
  }
  if (!isSupportedFile(file)) {
    validationError.value = '仅支持 PNG、JPEG、WebP 或 GIF 图片'
    return
  }
  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    validationError.value = '题目图片不能超过 20 MiB'
    return
  }

  revokeObjectUrl()
  selectedFile.value = file
  objectUrl.value = URL.createObjectURL(file)
  removeExisting.value = false
  emitChange()
}

function clearSelectedFile(): void {
  revokeObjectUrl()
  selectedFile.value = null
  validationError.value = ''
  emitChange()
}

async function markExistingForRemoval(): Promise<void> {
  try {
    await ElMessageBox.confirm(
      '保存修改后将删除当前题目图片，且无法恢复。',
      '移除题目图片',
      {
        confirmButtonText: '确认移除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }

  revokeObjectUrl()
  selectedFile.value = null
  removeExisting.value = true
  validationError.value = ''
  emitChange()
}

function undoRemoval(): void {
  removeExisting.value = false
  emitChange()
}

function emitChange(): void {
  emit('change', {
    file: selectedFile.value,
    removeExisting: removeExisting.value,
  })
}

function isSupportedFile(file: File): boolean {
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  return allowedMimeTypes.has(file.type) || allowedExtensions.has(extension)
}

function formatFileSize(size: number): string {
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KiB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MiB`
}

function revokeObjectUrl(): void {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
}

watch(
  () => props.currentImageUrl,
  () => {
    if (!selectedFile.value) {
      removeExisting.value = false
      validationError.value = ''
      emitChange()
    }
  },
)

onBeforeUnmount(revokeObjectUrl)
</script>

<style scoped>
.question-image-field {
  display: grid;
  gap: 12px;
  width: 100%;
}

.question-image-field__input {
  display: none;
}

.question-image-field__preview-wrap,
.question-image-field__empty {
  display: grid;
  min-height: 180px;
  place-items: center;
  overflow: hidden;
  background: #f8fafc;
  border: 1px dashed var(--app-border);
  border-radius: 10px;
}

.question-image-field__preview {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: contain;
}

.question-image-field__empty,
.question-image-field__hint,
.question-image-field__metadata span {
  color: var(--app-muted);
}

.question-image-field__metadata,
.question-image-field__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.question-image-field__metadata {
  justify-content: space-between;
}

.question-image-field__error {
  margin: 0;
  color: var(--el-color-danger);
  font-size: 0.82rem;
}

.question-image-field__hint {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.5;
}
</style>
