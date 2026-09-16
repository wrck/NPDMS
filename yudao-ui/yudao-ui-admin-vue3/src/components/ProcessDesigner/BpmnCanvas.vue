<!-- src/components/ProcessDesigner/BpmnCanvas.vue -->
<script setup lang="ts">
/**
 * BPMN 流程设计器画布组件。
 *
 * <p>封装 bpmn-js 的 BpmnModeler：在 onMounted 中初始化 modeler，导入现有
 * BPMN XML（或创建空白图），监听 selection.changed / shape.added 等事件并
 * 向父级 emit；onBeforeUnmount 销毁 modeler 释放资源。</p>
 *
 * <p>父级通过 ref 调用 expose 的方法（newDiagram / importXml / exportXml /
 * zoomToFit / getModeler）完成保存、导入、部署等操作。</p>
 */
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import type BpmnModeler from 'bpmn-js/lib/Modeler'
import type { ModdleElement } from 'bpmn-js/lib/model/Types'
import {
  createModeler,
  createNewDiagram,
  exportXml,
  importXml
} from './bpmn-helper'

defineOptions({ name: 'BpmnCanvas' })

const props = defineProps<{
  /** 初始 BPMN XML（为空则创建空白图） */
  bpmnXml?: string
}>()

const emit = defineEmits<{
  (e: 'ready', modeler: BpmnModeler): void
  (e: 'selection-changed', element: ModdleElement | null): void
  (e: 'changed'): void
  (e: 'import-error', message: string): void
}>()

const containerRef = ref<HTMLDivElement>()
const modelerRef = shallowRef<BpmnModeler | null>(null)
const ready = ref(false)

/** 初始化 modeler 并导入 XML */
async function init() {
  if (!containerRef.value) return
  const modeler = createModeler(containerRef.value)
  modelerRef.value = modeler

  const eventBus = modeler.get<{ on: (e: string, cb: (ev: Record<string, unknown>) => void) => void }>('eventBus')
  // 选中变化：通知父级刷新属性面板
  eventBus.on('selection.changed', (event) => {
    const newSelection = (event.newSelection as ModdleElement[]) || []
    emit('selection-changed', newSelection.length === 1 ? newSelection[0] : null)
  })
  // 画布变更（增删改）：通知父级标记脏状态
  eventBus.on('commandStack.changed', () => {
    emit('changed')
  })

  try {
    if (props.bpmnXml) {
      await importXml(modeler, props.bpmnXml)
    } else {
      await createNewDiagram(modeler)
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    // 导入失败则降级为空白图，避免画布空白
    try {
      await createNewDiagram(modeler)
    } catch {
      /* ignore */
    }
    emit('import-error', msg)
  }

  ready.value = true
  emit('ready', modeler)
}

onMounted(async () => {
  await nextTick()
  await init()
})

onBeforeUnmount(() => {
  try {
    modelerRef.value?.destroy()
  } catch {
    /* ignore */
  }
  modelerRef.value = null
})

// 父级传入新 XML 时重新导入（例如切换流程绑定）
watch(
  () => props.bpmnXml,
  async (xml) => {
    if (!modelerRef.value || !ready.value) return
    if (!xml) {
      await createNewDiagram(modelerRef.value)
      return
    }
    try {
      await importXml(modelerRef.value, xml)
    } catch (e) {
      emit('import-error', e instanceof Error ? e.message : String(e))
    }
  }
)

defineExpose({
  /** 创建空白图 */
  async newDiagram() {
    if (!modelerRef.value) return
    await createNewDiagram(modelerRef.value)
  },
  /** 导入 BPMN XML */
  async importXml(xml: string) {
    if (!modelerRef.value) return
    await importXml(modelerRef.value, xml)
  },
  /** 导出当前画布的 BPMN XML */
  async exportXml(): Promise<string> {
    if (!modelerRef.value) return ''
    return exportXml(modelerRef.value)
  },
  /** 获取底层 modeler 实例 */
  getModeler(): BpmnModeler | null {
    return modelerRef.value
  },
  /** 缩放至适应视口 */
  zoomToFit() {
    if (!modelerRef.value) return
    const canvas = modelerRef.value.get<{ zoomToFit?: (o?: Record<string, unknown>) => void }>('canvas')
    canvas.zoomToFit?.({ padding: 20 })
  }
})
</script>

<template>
  <div class="bpmn-canvas-wrap">
    <div ref="containerRef" class="bpmn-canvas-container"></div>
  </div>
</template>

<style scoped lang="scss">
.bpmn-canvas-wrap {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
  background: #fff;
}

.bpmn-canvas-container {
  width: 100%;
  height: 100%;
}

/* bpmn-js 画布内部使用绝对定位，确保容器有尺寸 */
.bpmn-canvas-container :deep(.djs-container) {
  background: #fbfbfb;
}
</style>
