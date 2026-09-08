import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { KnowledgePointTreeNode } from '../types/knowledge-point'
import type { QuestionFormPayload } from '../types/question'
import QuestionForm from './QuestionForm.vue'

const tree: KnowledgePointTreeNode[] = [
  {
    id: 1,
    name: '408',
    parentId: null,
    children: [{ id: 2, name: '计网', parentId: 1, children: [] }],
  },
  {
    id: 10,
    name: '数学',
    parentId: null,
    children: [{ id: 11, name: '高数', parentId: 10, children: [] }],
  },
]

const knowledgeSelectorStub = {
  name: 'KnowledgePointSelector',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div class="knowledge-selector-stub" />',
}

const questionImageFieldStub = {
  name: 'QuestionImageField',
  props: ['currentImageUrl', 'disabled'],
  emits: ['change'],
  template: '<div class="question-image-field-stub" />',
}

function mountForm() {
  return mount(QuestionForm, {
    props: { knowledgeTree: tree },
    global: {
      plugins: [ElementPlus],
      stubs: {
        KnowledgePointSelector: knowledgeSelectorStub,
        QuestionImageField: questionImageFieldStub,
      },
    },
  })
}

async function fillTextFields(wrapper: ReturnType<typeof mountForm>): Promise<void> {
  const values = [' 题目 ', ' 未作答 ', ' 正确答案 ', ' 解析 ', ' 错误原因 ']
  const textareas = wrapper.findAll('textarea')

  expect(textareas).toHaveLength(5)
  await Promise.all(textareas.map((textarea, index) => textarea.setValue(values[index])))
}

describe('QuestionForm', () => {
  it('shows a folded formula guide and previews raw LaTeX without changing the input', async () => {
    const wrapper = mountForm()
    const formulaText = '当 $x\\to0$ 时，$\\frac{\\sin x}{x}\\to1$。'

    expect(wrapper.get('[data-testid="formula-help"]').attributes('open')).toBeUndefined()
    expect(wrapper.get('[data-testid="formula-help"]').text()).toContain('独立公式')
    expect(wrapper.get('[data-testid="formula-help"]').text()).toContain('\\sum')

    await wrapper.findAll('textarea')[0].setValue(formulaText)

    expect(wrapper.findAllComponents({ name: 'MathText' })).toHaveLength(1)
    expect(wrapper.getComponent({ name: 'MathText' }).props('text')).toBe(formulaText)
    expect(wrapper.findAll('.katex')).toHaveLength(2)
    expect(wrapper.findAll('textarea')[0].element.value).toBe(formulaText)
  })

  it('submits the original LaTeX text instead of generated HTML', async () => {
    const wrapper = mountForm()
    const values = [
      '计算 $x^2$',
      '误写为 $x$',
      '答案是 $x^2$',
      '使用 $$x\\cdot x=x^2$$',
      '遗漏指数',
    ]

    await Promise.all(
      wrapper.findAll('textarea').map((textarea, index) => textarea.setValue(values[index])),
    )
    wrapper.findComponent({ name: 'KnowledgePointSelector' }).vm.$emit('update:modelValue', [10, 11])
    await nextTick()
    await wrapper.find('form').trigger('submit')

    const payload = wrapper.emitted('submit')?.[0]?.[0] as QuestionFormPayload
    expect(payload.questionText).toBe(values[0])
    expect(payload.analysis).toBe(values[3])
    expect(payload.questionText).not.toContain('<span')
  })

  it('emits a trimmed payload after valid input', async () => {
    const wrapper = mountForm()
    await fillTextFields(wrapper)

    wrapper.findComponent({ name: 'KnowledgePointSelector' }).vm.$emit('update:modelValue', [1, 2])
    await nextTick()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await nextTick()

    expect(wrapper.emitted('submit')).toEqual([
      [
        {
          questionText: '题目',
          wrongAnswer: '未作答',
          correctAnswer: '正确答案',
          analysis: '解析',
          errorReason: '错误原因',
          knowledgePointIds: [1, 2],
        },
        {
          file: null,
          removeExisting: false,
        },
      ],
    ])
  })

  it('blocks a cross-subject knowledge selection', async () => {
    const wrapper = mountForm()
    await fillTextFields(wrapper)

    wrapper.findComponent({ name: 'KnowledgePointSelector' }).vm.$emit('update:modelValue', [2, 11])
    await nextTick()
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await nextTick()

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text().match(/所选知识点必须属于同一科目/g)).toHaveLength(1)
  })

  it('shows every required-field error after an empty submit', async () => {
    const wrapper = mountForm()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('题目内容不能为空')
    expect(wrapper.text()).toContain('错误答案不能为空')
    expect(wrapper.text()).toContain('正确答案不能为空')
    expect(wrapper.text()).toContain('题目解析不能为空')
    expect(wrapper.text()).toContain('错误原因不能为空')
    expect(wrapper.text()).toContain('至少选择一个知识点')
  })
})
