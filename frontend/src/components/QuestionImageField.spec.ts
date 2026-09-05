import ElementPlus from 'element-plus'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuestionImageField from './QuestionImageField.vue'

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(),
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessageBox: { confirm: mocks.confirm },
  }
})

const createObjectUrl = vi.fn(() => 'blob:question-image')
const revokeObjectUrl = vi.fn()

function mountField(currentImageUrl = '') {
  return mount(QuestionImageField, {
    props: { currentImageUrl },
    global: { plugins: [ElementPlus] },
  })
}

async function chooseFile(
  wrapper: ReturnType<typeof mountField>,
  file: File,
): Promise<void> {
  const input = wrapper.get('[data-testid="question-image-input"]')
  Object.defineProperty(input.element, 'files', {
    value: [file],
    configurable: true,
  })
  await input.trigger('change')
}

describe('QuestionImageField', () => {
  beforeEach(() => {
    mocks.confirm.mockReset()
    createObjectUrl.mockClear()
    revokeObjectUrl.mockClear()
    Object.defineProperty(URL, 'createObjectURL', {
      value: createObjectUrl,
      configurable: true,
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: revokeObjectUrl,
      configurable: true,
    })
  })

  it('previews a supported local file and releases its object URL', async () => {
    const wrapper = mountField()
    const file = new File(['png'], 'question.png', { type: 'image/png' })

    await chooseFile(wrapper, file)

    expect(createObjectUrl).toHaveBeenCalledWith(file)
    expect(wrapper.get('img').attributes('src')).toBe('blob:question-image')
    expect(wrapper.text()).toContain('question.png')
    expect(wrapper.emitted('change')?.slice(-1)[0]).toEqual([
      { file, removeExisting: false },
    ])

    await wrapper.get('[data-testid="clear-selected-image"]').trigger('click')
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:question-image')
    expect(wrapper.emitted('change')?.slice(-1)[0]).toEqual([
      { file: null, removeExisting: false },
    ])
  })

  it('rejects unsupported and oversized files before creating a preview', async () => {
    const wrapper = mountField()
    await chooseFile(
      wrapper,
      new File(['<svg/>'], 'question.svg', { type: 'image/svg+xml' }),
    )
    expect(wrapper.text()).toContain('仅支持 PNG、JPEG、WebP 或 GIF 图片')

    const oversized = new File(['x'], 'large.png', { type: 'image/png' })
    Object.defineProperty(oversized, 'size', { value: 20 * 1024 * 1024 + 1 })
    await chooseFile(wrapper, oversized)

    expect(wrapper.text()).toContain('题目图片不能超过 20 MiB')
    expect(createObjectUrl).not.toHaveBeenCalled()
    expect(wrapper.emitted('change')).toBeUndefined()
  })

  it('requires confirmation before marking an existing image for removal', async () => {
    mocks.confirm.mockResolvedValue('confirm')
    const wrapper = mountField('/api/questions/42/image')

    await wrapper.get('[data-testid="remove-existing-image"]').trigger('click')
    await Promise.resolve()

    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="question-image-remove-pending"]').exists()).toBe(true)
    expect(wrapper.emitted('change')?.slice(-1)[0]).toEqual([
      { file: null, removeExisting: true },
    ])

    await wrapper.get('[data-testid="undo-remove-image"]').trigger('click')
    expect(wrapper.get('img').attributes('src')).toBe('/api/questions/42/image')
    expect(wrapper.emitted('change')?.slice(-1)[0]).toEqual([
      { file: null, removeExisting: false },
    ])
  })
})
