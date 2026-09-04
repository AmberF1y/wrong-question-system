<template>
  <header class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
}

h1 {
  margin: 0;
  color: var(--app-navy);
  font-size: 1.75rem;
  line-height: 1.25;
  letter-spacing: -0.02em;
}

p {
  margin: 7px 0 0;
  color: var(--app-muted);
  font-size: 0.95rem;
  line-height: 1.6;
}

.page-header__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

@media (max-width: 680px) {
  .page-header {
    flex-direction: column;
    gap: 14px;
  }

  .page-header__actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
