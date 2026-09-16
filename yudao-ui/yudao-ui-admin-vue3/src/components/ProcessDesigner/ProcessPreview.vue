<!-- src/components/ProcessDesigner/ProcessPreview.vue -->
<script setup lang="ts">
/**
 * 流程预览模式（只读 BPMN + 当前节点高亮）。
 *
 * <p>使用 bpmn-js 的 BpmnViewer 渲染只读流程图，接收 currentActivityIds 数组，
 * 通过 canvas.addMarker 为当前活动节点添加 highlight-current 标记，
 * 借鉴 Camunda Cockpit / 钉钉宜搭流程实例高亮。</p>
 *
 * <p>CSS 中 .highlight-current 重写 djs-visual 描边/填充，使活动节点以
 * 蓝色高亮显示，便于在流程实例运行时直观定位当前停留节点。</p>
 */
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import type { ModdleElement } from 'bpmn-js/lib/model/Types'
import { createViewer, getElementId, importXml } from './bpmn-helper'

defineOptions({ name: 'ProcessPreview' })

const props = defineProps<{
  /** BPMN XML */
  bpmnXml: string
  /** 当前活动节点 ID 数组（taskDefinitionKey / element id） */
  currentActivityIds?: string[]
}>()

const emit = defineEmits<{
  (e: 'import-error', message: string): void
}>()

const containerRef = ref<HTMLDivElement>()
const viewerRef = shallowRef<BpmnViewer | null>(null)
const errorMessage = ref('')

const HIGHLIGHT_MARKER = 'highlight-current'

/** 应用当前节点高亮标记 */
function applyHighlight() {
  const viewer = viewerRef.value
  if (!viewer) return
  const canvas = viewer.get<{
    addMarker: (id: string, m: string) => void
    removeMarker: (id: string, m: string) => void
    zoomToFit: (o?: Record<string, unknown>) => void
  }>('canvas')
  const elementRegistry = viewer.get<{
    getAll: () => ModdleElement[]
  }>('elementRegistry')

  // 先清除所有已有高亮
  const all = elementRegistry.getAll()
  all.forEach((el) => {
    const id = getElementId(el)
    if (id) canvas.removeMarker(id, HIGHLIGHT_MARKER)
  })

  // 再为当前活动节点添加高亮
  const ids = props.currentActivityIds || []
  ids.forEach((id) => {
    if (id) canvas.addMarker(id, HIGHLIGHT_MARKER)
  })

  // 适应视口
  try {
    canvas.zoomToFit({ padding: 40 })
  } catch {
    /* ignore */
  }
}

/** 导入 XML 并渲染 */
async function render() {
  const viewer = viewerRef.value
  if (!viewer || !props.bpmnXml) return
  try {
    errorMessage.value = ''
    await importXml(viewer, props.bpmnXml)
    applyHighlight()
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    errorMessage.value = '流程图加载失败：' + msg
    emit('import-error', msg)
  }
}

onMounted(async () => {
  if (!containerRef.value) return
  const viewer = createViewer(containerRef.value)
  viewerRef.value = viewer
  await render()
})

onBeforeUnmount(() => {
  try {
    viewerRef.value?.destroy()
  } catch {
    /* ignore */
  }
  viewerRef.value = null
})

// XML 或活动节点变化时重新渲染/高亮
watch(() => props.bpmnXml, render)
watch(() => props.currentActivityIds, applyHighlight, { deep: true })

defineExpose({
  refresh: render,
  zoomToFit() {
    const viewer = viewerRef.value
    if (!viewer) return
    const canvas = viewer.get<{ zoomToFit: (o?: Record<string, unknown>) => void }>('canvas')
    canvas.zoomToFit({ padding: 40 })
  }
})
</script>

<template>
  <div class="process-preview-wrap">
    <div ref="containerRef" class="process-preview-canvas"></div>
    <div v-if="errorMessage" class="process-preview-error">{{ errorMessage }}</div>
  </div>
</template>

<style scoped lang="scss">
.process-preview-wrap {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  background: #fff;
}

.process-preview-canvas {
  width: 100%;
  height: 100%;
}

.process-preview-error {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  padding: 8px 12px;
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fbc4c4;
  border-radius: 4px;
  font-size: 12px;
}
</style>

<!--
  非 scoped 全局样式：bpmn-js 通过 marker class 在 SVG 内部添加 class，
  需用全局选择器覆盖 djs-visual 描边/填充。放在 :deep 之外以作用到画布 SVG。
-->
<style>
/* 当前活动节点高亮：边框蓝色加粗 */
.process-preview-canvas .highlight-current:not(.djs-connection) .djs-visual {
  stroke: #1890ff !important;
  stroke-width: 2px !important;
  fill: #e6f7ff !important;
}

/* 当前活动节点中的图标/形状描边高亮 */
.process-preview-canvas .highlight-current .djs-visual > :nth-child(1) {
  stroke: #1890ff !important;
  stroke-width: 2px !important;
}

/* 当前活动连接线高亮 */
.process-preview-canvas .highlight-current.djs-connection .djs-visual > :nth-child(1) {
  stroke: #1890ff !important;
  stroke-width: 2.5px !important;
  marker-end: url(#sequenceflow-end-evt-default);
}
</style>
