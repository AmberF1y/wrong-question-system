<template>
  <span class="math-text">
    <template v-for="(segment, index) in segments" :key="index">
      <span v-if="segment.kind === 'text'" class="math-text__plain">{{ segment.text }}</span>
      <span
        v-else-if="segment.display"
        class="math-text__formula math-text__formula--display"
        data-math-display="true"
        v-html="segment.html"
      />
      <span
        v-else
        class="math-text__formula math-text__formula--inline"
        data-math-inline="true"
        v-html="segment.html"
      />
    </template>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import katex from 'katex'

interface TextSegment {
  kind: 'text'
  text: string
}

interface FormulaSegment {
  kind: 'formula'
  display: boolean
  html: string
}

type MathTextSegment = TextSegment | FormulaSegment

const props = defineProps<{
  text: string
}>()

const segments = computed(() => parseMathText(props.text))

function parseMathText(source: string): MathTextSegment[] {
  const result: MathTextSegment[] = []
  let plainStart = 0
  let cursor = 0

  while (cursor < source.length) {
    if (source[cursor] !== '$' || isEscaped(source, cursor)) {
      cursor += 1
      continue
    }

    const delimiter = source[cursor + 1] === '$' ? '$$' : '$'
    const closingIndex = findClosingDelimiter(source, cursor + delimiter.length, delimiter)

    if (closingIndex === -1) {
      cursor += delimiter.length
      continue
    }

    pushPlainText(result, source.slice(plainStart, cursor))

    const rawFormula = source.slice(cursor, closingIndex + delimiter.length)
    const latex = source.slice(cursor + delimiter.length, closingIndex)

    try {
      result.push({
        kind: 'formula',
        display: delimiter === '$$',
        html: katex.renderToString(latex, {
          displayMode: delimiter === '$$',
          output: 'htmlAndMathml',
          throwOnError: true,
          trust: false,
        }),
      })
    } catch {
      pushPlainText(result, rawFormula, false)
    }

    cursor = closingIndex + delimiter.length
    plainStart = cursor
  }

  pushPlainText(result, source.slice(plainStart))
  return result
}

function findClosingDelimiter(source: string, start: number, delimiter: '$' | '$$'): number {
  for (let index = start; index < source.length; index += 1) {
    if (source[index] !== '$' || isEscaped(source, index)) {
      continue
    }

    if (delimiter === '$$') {
      if (source[index + 1] === '$') {
        return index
      }
      continue
    }

    if (source[index + 1] !== '$' && source[index - 1] !== '$') {
      return index
    }
  }

  return -1
}

function isEscaped(source: string, index: number): boolean {
  let backslashCount = 0

  for (let cursor = index - 1; cursor >= 0 && source[cursor] === '\\'; cursor -= 1) {
    backslashCount += 1
  }

  return backslashCount % 2 === 1
}

function pushPlainText(
  result: MathTextSegment[],
  text: string,
  unescapeDollar = true,
): void {
  if (!text) {
    return
  }

  const plainText = unescapeDollar ? text.replace(/\\\$/g, '$') : text
  const previous = result[result.length - 1]

  if (previous?.kind === 'text') {
    previous.text += plainText
  } else {
    result.push({ kind: 'text', text: plainText })
  }
}
</script>

<style scoped>
.math-text {
  display: block;
  width: 100%;
  min-width: 0;
  white-space: pre-wrap;
}

.math-text__plain {
  white-space: pre-wrap;
}

.math-text__formula--inline {
  display: inline-block;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  vertical-align: middle;
}

.math-text__formula--display {
  display: block;
  max-width: 100%;
  padding: 0.35em 0;
  overflow-x: auto;
  overflow-y: hidden;
  white-space: normal;
  scrollbar-width: thin;
}

.math-text__formula--display :deep(.katex-display) {
  width: max-content;
  min-width: 100%;
  margin: 0.35em 0;
}
</style>
