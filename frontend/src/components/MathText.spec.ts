import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MathText from './MathText.vue'

const taylorSeries = [
  'e^x=1+x+\\frac{x^2}{2}+\\frac{x^3}{6}+\\cdots',
  '\\sin x=x-\\frac{x^3}{6}+\\frac{x^5}{120}+\\cdots',
  '\\cos x=1-\\frac{x^2}{2}+\\frac{x^4}{24}+\\cdots',
  '\\ln(1+x)=x-\\frac{x^2}{2}+\\frac{x^3}{3}-\\frac{x^4}{4}+\\cdots',
  '\\frac{1}{1-x}=1+x+x^2+x^3+\\cdots',
  '(1+x)^\\alpha=1+\\alpha x+\\frac{\\alpha(\\alpha-1)}{2}x^2+\\cdots',
  '\\arctan x=x-\\frac{x^3}{3}+\\frac{x^5}{5}+\\cdots',
  '\\arcsin x=x+\\frac{x^3}{6}+\\frac{3x^5}{40}+\\cdots',
]

const postgraduateMathExamples = [
  'x^2+a_n+x^{n+1}',
  '\\frac{a}{b}+\\sqrt{x}+\\sqrt[n]{x}',
  '\\lim_{x\\to0}\\frac{\\sin x}{x}=1',
  '\\int_a^b f(x)\\,dx+\\iint_D f(x,y)\\,d\\sigma',
  '\\sum_{n=1}^{\\infty}a_n+\\prod_{k=1}^n k',
  "\\frac{\\partial f}{\\partial x}+f'(x)+f''(x)+\\vec{a}",
  '\\alpha+\\beta+\\theta+\\lambda+\\mu',
  'a\\le b,\\ b\\ge c,\\ a\\ne c,\\ x\\in A,\\ A\\subset B',
  '\\begin{bmatrix}1&2\\\\3&4\\end{bmatrix}',
  '\\begin{vmatrix}1&2\\\\3&4\\end{vmatrix}',
  'f(x)=\\begin{cases}x,&x\\ge0\\\\-x,&x<0\\end{cases}',
]

describe('MathText', () => {
  it('renders ordinary text without creating KaTeX markup', () => {
    const source = '普通中文内容\n第二行仍是普通文本'
    const wrapper = mount(MathText, { props: { text: source } })

    expect(wrapper.text()).toBe(source)
    expect(wrapper.find('.katex').exists()).toBe(false)
  })

  it('renders an inline formula with HTML and MathML output', () => {
    const wrapper = mount(MathText, {
      props: { text: '当 $x^2+y^2=1$ 时' },
    })

    expect(wrapper.findAll('[data-math-inline="true"]')).toHaveLength(1)
    expect(wrapper.find('.katex-html').exists()).toBe(true)
    expect(wrapper.find('.katex-mathml').exists()).toBe(true)
  })

  it('renders an independent display formula', () => {
    const wrapper = mount(MathText, {
      props: { text: '结论：$$\\lim_{x\\to0}\\frac{\\sin x}{x}=1$$' },
    })

    expect(wrapper.findAll('[data-math-display="true"]')).toHaveLength(1)
    expect(wrapper.find('.katex-display').exists()).toBe(true)
  })

  it('mixes Chinese text, line breaks, inline formulas and display formulas', () => {
    const source =
      '当 $x\\to0$ 时，$\\frac{\\sin x}{x}\\to1$。\n' +
      '结论如下：\n' +
      '$$\\lim_{x\\to0}\\frac{\\sin x}{x}=1$$'
    const wrapper = mount(MathText, { props: { text: source } })

    expect(wrapper.findAll('[data-math-inline="true"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-math-display="true"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('当 ')
    expect(wrapper.text()).toContain('结论如下：')
    expect(wrapper.get('.math-text').text()).toContain('\n结论如下：\n')
  })

  it('shows an escaped dollar sign as ordinary text', () => {
    const wrapper = mount(MathText, {
      props: { text: '价格为 \\$5，变量为 $x$' },
    })

    expect(wrapper.text()).toContain('价格为 $5')
    expect(wrapper.text()).not.toContain('\\$5')
    expect(wrapper.findAll('[data-math-inline="true"]')).toHaveLength(1)
  })

  it.each(['未配对行内公式 $x+1', '未配对独立公式 $$x+1'])(
    'keeps unmatched delimiters as original text: %s',
    (source) => {
      const wrapper = mount(MathText, { props: { text: source } })

      expect(wrapper.text()).toBe(source)
      expect(wrapper.find('.katex').exists()).toBe(false)
    },
  )

  it('keeps invalid LaTeX visible without throwing', () => {
    const source = '错误公式：$\\frac{1}{$ 后续文本'
    const wrapper = mount(MathText, { props: { text: source } })

    expect(wrapper.text()).toBe(source)
    expect(wrapper.find('.katex').exists()).toBe(false)
  })

  it('never turns user HTML, scripts, styles or event attributes into DOM nodes', () => {
    const source =
      '<script>globalThis.compromised=true</script>\n' +
      '<img src=x onerror="globalThis.compromised=true">\n' +
      '<style>body{display:none}</style>\n' +
      '<a href="javascript:alert(1)" onclick="alert(1)">危险链接</a>\n' +
      '$\\href{javascript:alert(1)}{x}$'
    const wrapper = mount(MathText, { props: { text: source } })

    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('style').exists()).toBe(false)
    expect(wrapper.find('a').exists()).toBe(false)
    expect(wrapper.find('[onerror]').exists()).toBe(false)
    expect(wrapper.find('[onclick]').exists()).toBe(false)
    expect(wrapper.text()).toContain('<script>')
    expect(wrapper.text()).toContain('<img')
    expect(wrapper.text()).toContain('\\href')
  })

  it.each(postgraduateMathExamples)(
    'supports common postgraduate mathematics syntax: %s',
    (formula) => {
      const wrapper = mount(MathText, { props: { text: '$$' + formula + '$$' } })

      expect(wrapper.findAll('[data-math-display="true"]')).toHaveLength(1)
      expect(wrapper.find('.katex').exists()).toBe(true)
    },
  )

  it.each(taylorSeries)('renders required Taylor expansion: %s', (formula) => {
    const wrapper = mount(MathText, { props: { text: '$$' + formula + '$$' } })

    expect(wrapper.findAll('[data-math-display="true"]')).toHaveLength(1)
    expect(wrapper.find('.katex').exists()).toBe(true)
    expect(wrapper.find('.katex-error').exists()).toBe(false)
  })
})
