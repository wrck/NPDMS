<!-- src/components/ProcessDesigner/BpmnPalette.vue -->
<script setup lang="ts">
/**
 * BPMN 流程设计器左侧调色板。
 *
 * <p>借鉴 Appian Process Modeler / Camunda bpmn-js：按事件/活动/网关/泳道
 * 分组展示可拖拽 BPMN 元素。鼠标按下即通过 bpmn-js 的 create 模块启动拖拽，
 * dragging 模块在 document 级别接管后续 mousemove/mouseup，支持从外部
 * 调色板拖入画布并自动放置。</p>
 */
import {
  PALETTE_ACTIVITIES,
  PALETTE_EVENTS,
  PALETTE_GATEWAYS,
  PALETTE_SWIMLANES,
  startCreateFromPalette
} from './bpmn-helper'
import type BpmnModeler from 'bpmn-js/lib/Modeler'

defineOptions({ name: 'BpmnPalette' })

const props = defineProps<{ modeler: BpmnModeler | null }>()

interface PaletteGroup {
  title: string
  items: Array<{ type: string; label: string }>
}

const groups: PaletteGroup[] = [
  { title: '事件', items: PALETTE_EVENTS },
  { title: '活动', items: PALETTE_ACTIVITIES },
  { title: '网关', items: PALETTE_GATEWAYS },
  { title: '泳道', items: PALETTE_SWIMLANES }
]

/** 调色板项 mousedown：构造 shape 并启动 create 拖拽 */
function onItemMouseDown(event: MouseEvent, type: string) {
  if (!props.modeler) return
  // 阻止默认文本选中
  event.preventDefault()
  try {
    startCreateFromPalette(props.modeler, event, type)
  } catch (e) {
    console.error('[bpmn-palette] create start failed:', e)
  }
}
</script>

<template>
  <div class="bpmn-palette">
    <div v-for="group in groups" :key="group.title" class="palette-group">
      <div class="palette-group-title">{{ group.title }}</div>
      <div
        v-for="item in group.items"
        :key="item.type"
        class="palette-item"
        :title="`拖拽添加：${item.label}`"
        @mousedown="onItemMouseDown($event, item.type)"
      >
        <span class="palette-item-label">{{ item.label }}</span>
      </div>
    </div>
    <div class="palette-tip">
      按住元素拖入画布；选中节点后可在右侧配置表单/审批人/超时等属性。
    </div>
  </div>
</template>

<style scoped lang="scss">
.bpmn-palette {
  width: 100%;
  height: 100%;
  background: #fafafa;
  overflow-y: auto;
  padding: 8px;
  box-sizing: border-box;
  font-size: 12px;

  .palette-group {
    margin-bottom: 12px;
  }

  .palette-group-title {
    font-weight: 600;
    color: #606266;
    padding: 4px 2px;
    border-bottom: 1px solid #ebeef5;
    margin-bottom: 6px;
  }

  .palette-item {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 32px;
    margin-bottom: 6px;
    border: 1px dashed #c0c4cc;
    border-radius: 4px;
    background: #fff;
    cursor: grab;
    color: #303133;
    transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
    user-select: none;

    &:hover {
      border-color: #409eff;
      border-style: solid;
      background: #ecf5ff;
      box-shadow: 0 1px 4px rgba(64, 158, 255, 0.2);
    }

    &:active {
      cursor: grabbing;
      border-color: #409eff;
    }

    .palette-item-label {
      pointer-events: none;
    }
  }

  .palette-tip {
    margin-top: 12px;
    padding: 6px;
    color: #909399;
    line-height: 1.6;
    background: #f4f4f5;
    border-radius: 4px;
  }
}
</style>
