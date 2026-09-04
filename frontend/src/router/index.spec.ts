import { describe, expect, it } from 'vitest'
import router from './index'

describe('application router', () => {
  it('registers the daily review route with its document title metadata', () => {
    const route = router.resolve('/reviews')

    expect(route.name).toBe('daily-review')
    expect(route.meta.title).toBe('每日复习')
  })

  it('keeps question routes separate from the daily review route', () => {
    expect(router.resolve('/questions').name).toBe('question-list')
    expect(router.resolve('/questions/42').name).toBe('question-detail')
  })
})
