import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { KnowledgePointTreeNode } from '../types/knowledge-point'
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

function mountForm() {
  return mount(QuestionForm, {
    props: { knowledgeTree: tree },
    global: {
      plugins: [ElementPlus],
      stubs: {
        KnowledgePointSelector: knowledgeSelectorStub,
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
