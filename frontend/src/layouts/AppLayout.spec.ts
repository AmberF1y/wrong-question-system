import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import {
  createMemoryHistory,
  createRouter,
  type Router,
} from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getHealth } from '../api/health'
import AppLayout from './AppLayout.vue'

vi.mock('../api/health', () => ({
  getHealth: vi.fn(),
}))

const mockedGetHealth = vi.mocked(getHealth)

async function createTestRouter(path: string): Promise<Router> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/:pathMatch(.*)*',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push(path)
  await router.isReady()
  return router
}

describe('AppLayout', () => {
  beforeEach(() => {
    mockedGetHealth.mockReset()
    mockedGetHealth.mockResolvedValue({ status: 'OK' })
  })

  it('places daily review first and marks it active on /reviews', async () => {
    const router = await createTestRouter('/reviews')
    const wrapper = mount(AppLayout, {
      global: {
        plugins: [router, ElementPlus],
      },
    })
    await flushPromises()

    const menu = wrapper.getComponent({ name: 'ElMenu' })
    const items = wrapper.findAllComponents({ name: 'ElMenuItem' })

    expect(menu.props('defaultActive')).toBe('/reviews')
    expect(items.map((item) => item.props('index'))).toEqual([
      '/reviews',
      '/questions',
      '/knowledge-points',
    ])
    expect(wrapper.text()).toContain('每日复习')
  })

  it.each([
    ['/questions/42', '/questions'],
    ['/knowledge-points', '/knowledge-points'],
  ])('keeps %s mapped to the correct navigation item', async (path, expectedMenu) => {
    const router = await createTestRouter(path)
    const wrapper = mount(AppLayout, {
      global: {
        plugins: [router, ElementPlus],
      },
    })
    await flushPromises()

    expect(wrapper.getComponent({ name: 'ElMenu' }).props('defaultActive')).toBe(
      expectedMenu,
    )
  })
})
