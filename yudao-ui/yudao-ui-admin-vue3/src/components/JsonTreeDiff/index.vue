<template>
  <div class="json-tree-diff">
    <div class="diff-container">
      <div class="diff-side diff-left">
        <div class="diff-header">旧版本</div>
        <pre class="diff-content" v-html="leftHtml"></pre>
      </div>
      <div class="diff-side diff-right">
        <div class="diff-header">新版本</div>
        <pre class="diff-content" v-html="rightHtml"></pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { create } from 'jsondiffpatch'
import { format as formatHtml } from 'jsondiffpatch/formatters/html'
import DOMPurify from 'dompurify'

const props = defineProps<{
  oldData: any
  newData: any
}>()

const jsondiffpatch = create()

const delta = computed(() => jsondiffpatch.diff(props.oldData, props.newData))

// jsondiffpatch 的 HTML formatter 输出会通过 v-html 渲染，存在 XSS 风险，
// 此处用 DOMPurify 消毒后再交给模板，采用严格白名单（仅保留 diff 展示所需标签与样式类）。
function sanitize(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['div', 'span', 'ul', 'li', 'ins', 'del', 'i', 'b', 'small'],
    ALLOWED_ATTR: ['class', 'style']
  })
}

const leftHtml = computed(() => {
  const raw = formatHtml(delta.value, props.oldData) || '<i>无数据</i>'
  return sanitize(raw)
})

const rightHtml = computed(() => {
  const reversedDelta = jsondiffpatch.diff(props.newData, props.oldData)
  const raw = formatHtml(reversedDelta, props.newData) || '<i>无数据</i>'
  return sanitize(raw)
})
</script>

<style scoped>
.json-tree-diff {
  width: 100%;
}
.diff-container {
  display: flex;
  gap: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
}
.diff-side {
  flex: 1;
  min-width: 0;
}
.diff-header {
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
  font-size: 13px;
}
.diff-content {
  padding: 12px;
  margin: 0;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 12px;
  line-height: 1.5;
  max-height: 500px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
.diff-content :deep(.jsondiffpatch-added) {
  background: #e6ffed;
  color: #22863a;
}
.diff-content :deep(.jsondiffpatch-deleted) {
  background: #ffeef0;
  color: #cb2431;
  text-decoration: line-through;
}
.diff-content :deep(.jsondiffpatch-modified) {
  background: #fff8c5;
  color: #b08800;
}
</style>
