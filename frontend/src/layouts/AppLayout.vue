<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <div class="brand">
        <div class="brand__mark" aria-hidden="true">错</div>
        <div>
          <strong>错题复习</strong>
          <span>个人学习工作台</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        class="app-navigation"
        router
      >
        <el-menu-item index="/reviews">
          <el-icon><Calendar /></el-icon>
          <span>每日复习</span>
        </el-menu-item>
        <el-menu-item index="/questions">
          <el-icon><Notebook /></el-icon>
          <span>错题管理</span>
        </el-menu-item>
        <el-menu-item index="/knowledge-points">
          <el-icon><Collection /></el-icon>
          <span>知识点管理</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <section class="app-body">
      <header class="app-topbar">
        <span class="app-topbar__context">个人学习工作台</span>
        <button
          class="health-status"
          type="button"
          :class="`health-status--${healthState}`"
          :disabled="healthState === 'checking'"
          @click="checkBackend"
        >
          <span class="health-status__dot" aria-hidden="true" />
          {{ healthLabel }}
        </button>
      </header>

      <main class="app-content">
        <RouterView />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Calendar, Collection, Notebook } from '@element-plus/icons-vue'
import { RouterView, useRoute } from 'vue-router'
import { getHealth } from '../api/health'

type HealthState = 'checking' | 'online' | 'offline'

const route = useRoute()
const healthState = ref<HealthState>('checking')

const activeMenu = computed(() => {
  if (route.path.startsWith('/reviews')) {
    return '/reviews'
  }

  if (route.path.startsWith('/knowledge-points')) {
    return '/knowledge-points'
  }

  return '/questions'
})

const healthLabel = computed(() => {
  if (healthState.value === 'checking') {
    return '正在检查后端'
  }

  return healthState.value === 'online' ? '后端已连接' : '后端未连接'
})

async function checkBackend(): Promise<void> {
  healthState.value = 'checking'

  try {
    const response = await getHealth()
    healthState.value = response.status.toLowerCase() === 'ok' ? 'online' : 'offline'
  } catch {
    healthState.value = 'offline'
  }
}

onMounted(checkBackend)
</script>

<style scoped>
.app-shell {
  display: flex;
  min-height: 100vh;
}

.app-sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 20;
  width: 224px;
  background: #132544;
  color: #ffffff;
}

.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  height: 82px;
  padding: 0 22px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.brand__mark {
  display: grid;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 11px;
  background: #ffffff;
  color: #1d4ed8;
  font-size: 1.1rem;
  font-weight: 800;
}

.brand strong,
.brand span {
  display: block;
}

.brand strong {
  font-size: 1rem;
}

.brand span {
  margin-top: 3px;
  color: #aebbd0;
  font-size: 0.78rem;
}

.app-navigation {
  border-right: 0;
  background: transparent;
  padding: 16px 12px;
}

.app-navigation :deep(.el-menu-item) {
  height: 48px;
  margin-bottom: 6px;
  border-radius: 9px;
  color: #c8d3e3;
  font-size: 0.94rem;
}

.app-navigation :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.app-navigation :deep(.el-menu-item.is-active) {
  background: #2563eb;
  color: #ffffff;
}

.app-body {
  min-width: 0;
  width: calc(100% - 224px);
  margin-left: 224px;
}

.app-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 66px;
  padding: 0 32px;
  border-bottom: 1px solid var(--app-border);
  background: rgba(255, 255, 255, 0.93);
  backdrop-filter: blur(10px);
}

.app-topbar__context {
  color: var(--app-muted);
  font-size: 0.875rem;
}

.health-status {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 7px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #475569;
  cursor: pointer;
}

.health-status:disabled {
  cursor: wait;
}

.health-status__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #94a3b8;
}

.health-status--online .health-status__dot {
  background: #16a34a;
}

.health-status--offline .health-status__dot {
  background: #dc2626;
}

.app-content {
  width: min(1380px, 100%);
  margin: 0 auto;
  padding: 30px 32px 48px;
}

@media (max-width: 760px) {
  .app-shell {
    display: block;
  }

  .app-sidebar {
    position: static;
    width: 100%;
  }

  .brand {
    height: 68px;
  }

  .app-navigation {
    display: flex;
    padding: 8px 12px 12px;
  }

  .app-navigation :deep(.el-menu-item) {
    flex: 1;
    justify-content: center;
    margin: 0 4px;
  }

  .app-body {
    width: 100%;
    margin-left: 0;
  }

  .app-topbar {
    height: 58px;
    padding: 0 16px;
  }

  .app-topbar__context {
    display: none;
  }

  .app-content {
    padding: 22px 16px 36px;
  }
}
</style>
