<script setup lang="ts">
/**
 * 微流节点（X6 自定义 Vue 节点，通过 @antv/x6-vue-shape 注册）。
 *
 * <p>由 x6-vue-shape 以 props.node（X6 Node 实例）注入；节点数据从 node.getData() 读取，
 * 监听 change:data 以响应选中态、执行状态（RUNNING/SUCCESS/FAILED）与日志高亮变更。</p>
 *
 * <p>渲染：类型图标 + 节点名称 + 状态指示点。边框颜色随执行状态变化，
 * 便于执行后在画布上直观看到每个节点的执行结果。</p>
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import type { Node } from '@antv/x6'
import type { MicroflowNodeType } from '@/api/lowcode-microflow'

/** 节点渲染数据（由父组件写入 node.data） */
export interface MicroflowNodeData {
  nodeId: string
  type: MicroflowNodeType
  label: string
  /** 执行状态：来自执行日志，用于画布同步高亮 */
  status?: 'RUNNING' | 'SUCCESS' | 'FAILED'
  /** 是否被日志面板点击高亮 */
  highlighted?: boolean
  /** 调试模式：是否设为断点 */
  breakpoint?: boolean
  /** 调试模式：是否为当前暂停节点 */
  debugCurrent?: boolean
}

const props = defineProps<{ node: Node; graph?: unknown }>()

/** 响应式读取节点数据（监听 change:data 以响应 setData 触发的状态/高亮变更） */
const nodeData = ref<MicroflowNodeData>((props.node.getData() as MicroflowNodeData) || {})
const onDataChange = () => {
  nodeData.value = (props.node.getData() as MicroflowNodeData) || {}
}
props.node.on('change:data', onDataChange)
onBeforeUnmount(() => {
  props.node.off('change:data', onDataChange)
})

/** 节点类型元信息（图标 + 主题色） */
const META: Record<MicroflowNodeType, { icon: string; color: string }> = {
  START: { icon: '▶', color: '#67c23a' },
  END: { icon: '■', color: '#f56c6c' },
  ASSIGN: { icon: '=', color: '#409eff' },
  CONDITION: { icon: '?', color: '#e6a23c' },
  LOOP: { icon: '↻', color: '#9c27b0' },
  CALL_SERVICE: { icon: '⚙', color: '#00bcd4' },
  CALL_MICROFLOW: { icon: '✦', color: '#00bcd4' },
  CALL_RULE: { icon: '§', color: '#00bcd4' },
  CALL_CONNECTOR: { icon: '⇄', color: '#00bcd4' },
  THROW_EXCEPTION: { icon: '!', color: '#ff9800' },
  RETURN: { icon: '←', color: '#67c23a' }
}

const meta = computed(() => META[nodeData.value.type] || { icon: '•', color: '#909399' })

/** 边框色：调试当前节点优先，其次执行状态，未执行节点使用默认灰色边框 */
const borderColor = computed(() => {
  // 调试中当前暂停节点 → 紫色突出
  if (nodeData.value.debugCurrent) return '#9c27b0'
  const s = nodeData.value.status
  if (s === 'SUCCESS') return '#67c23a'
  if (s === 'FAILED') return '#f56c6c'
  if (s === 'RUNNING') return '#409eff'
  // 未执行 → 默认灰色边框
  return '#c0c4cc'
})
</script>

<template>
  <div
    class="microflow-node"
    :class="{
      highlighted: nodeData.highlighted,
      'is-running': nodeData.status === 'RUNNING',
      'is-debug-current': nodeData.debugCurrent
    }"
    :style="{ borderColor: borderColor }"
  >
    <!-- 断点标记：右上角红点 -->
    <span v-if="nodeData.breakpoint" class="mn-breakpoint" title="断点"></span>
    <span class="mn-icon" :style="{ background: meta.color }">{{ meta.icon }}</span>
    <span class="mn-label">{{ nodeData.label || nodeData.type }}</span>
    <span v-if="nodeData.status" class="mn-status" :class="`st-${nodeData.status.toLowerCase()}`"></span>
  </div>
</template>

<script lang="ts">
export default { name: 'MicroflowNode' }
</script>

<style scoped lang="scss">
.microflow-node {
  position: relative;
  width: 100%;
  height: 100%;
  background: #fff;
  border: 2px solid #c0c4cc;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  box-sizing: border-box;
  font-size: 13px;
  transition: box-shadow 0.2s, border-color 0.2s;

  &.highlighted {
    box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.35);
  }

  // 执行中：蓝色脉冲光晕动画（边框颜色由内联 style 设为蓝色）
  &.is-running {
    animation: mn-border-pulse 1s infinite;
  }

  // 调试当前暂停节点：紫色脉冲光晕
  &.is-debug-current {
    animation: mn-debug-pulse 1s infinite;
  }

  // 断点标记：右上角红色实心圆
  .mn-breakpoint {
    position: absolute;
    top: -5px;
    right: -5px;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: #f56c6c;
    border: 2px solid #fff;
    box-shadow: 0 0 2px rgba(0, 0, 0, 0.3);
    z-index: 2;
  }

  .mn-icon {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 12px;
    font-weight: 700;
    flex-shrink: 0;
  }

  .mn-label {
    flex: 1;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mn-status {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;

    &.st-success {
      background: #67c23a;
    }
    &.st-failed {
      background: #f56c6c;
    }
    &.st-running {
      background: #409eff;
      animation: mn-pulse 1s infinite;
    }
  }
}

@keyframes mn-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

// 执行中节点边框光晕脉冲（蓝色扩散与回缩）
@keyframes mn-border-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(64, 158, 255, 0.55);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(64, 158, 255, 0.15);
  }
}

// 调试当前暂停节点紫色脉冲光晕
@keyframes mn-debug-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(156, 39, 176, 0.55);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(156, 39, 176, 0.15);
  }
}
</style>
