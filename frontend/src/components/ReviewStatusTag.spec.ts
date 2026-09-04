import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ReviewStatusTag from './ReviewStatusTag.vue'

const global = {
  stubs: {
    ElTag: {
      props: ['type', 'effect'],
      template: '<span :data-type="type"><slot /></span>',
    },
  },
}

describe('ReviewStatusTag', () => {
  it('renders an active question as reviewing', () => {
    const wrapper = mount(ReviewStatusTag, {
      props: { status: 'ACTIVE' },
      global,
    })

    expect(wrapper.text()).toBe('复习中')
    expect(wrapper.attributes('data-type')).toBe('primary')
  })

  it('renders a mastered question as mastered', () => {
    const wrapper = mount(ReviewStatusTag, {
      props: { status: 'MASTERED' },
      global,
    })

    expect(wrapper.text()).toBe('已掌握')
    expect(wrapper.attributes('data-type')).toBe('success')
  })
})
