import ElementPlus from 'element-plus'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import QuestionImageDisplay from './QuestionImageDisplay.vue'

const dialogStub = {
  name: 'ElDialog',
  props: ['modelValue'],
  template: '<div><slot /></div>',
}

describe('QuestionImageDisplay', () => {
  it('shows a local retry state without removing surrounding content', async () => {
    const wrapper = mount(QuestionImageDisplay, {
      props: { src: '/api/questions/42/image?v=first' },
      global: {
        plugins: [ElementPlus],
        stubs: { ElDialog: dialogStub },
      },
    })

    expect(wrapper.get('[data-testid="question-image"]').attributes('src')).toBe(
      '/api/questions/42/image?v=first',
    )

    await wrapper.get('[data-testid="question-image"]').trigger('error')
    expect(wrapper.find('[data-testid="question-image-error"]').exists()).toBe(true)

    await wrapper.get('[data-testid="retry-question-image"]').trigger('click')
    expect(wrapper.get('[data-testid="question-image"]').attributes('src')).toBe(
      '/api/questions/42/image?v=first&retry=1',
    )
  })

  it('opens the large-image dialog after clicking the image', async () => {
    const wrapper = mount(QuestionImageDisplay, {
      props: { src: '/api/questions/42/image' },
      global: {
        plugins: [ElementPlus],
        stubs: { ElDialog: dialogStub },
      },
    })

    await wrapper.get('button[aria-label="查看题目大图"]').trigger('click')

    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
  })
})
