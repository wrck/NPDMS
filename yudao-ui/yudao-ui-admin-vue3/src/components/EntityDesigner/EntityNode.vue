<!-- src/components/EntityDesigner/EntityNode.vue -->
<script setup lang="ts">
/**
 * 实体节点（X6 自定义 Vue 节点，通过 @antv/x6-vue-shape 注册）。
 *
 * <p>由 x6-vue-shape 以 props.node（X6 Node 实例）+ props.graph 注入；
 * 实体数据从 node.getData() 读取，监听 change:data 实现选中态等响应式更新。</p>
 *
 * <p>渲染：实体名标题 + 物理表名 + 字段列表（PK 字段标红徽章）。
 * 选中态（data.selected）时边框高亮，供多实体画布点击高亮使用。</p>
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import type { Node } from '@antv/x6'
import type { LowCodeField } from '@/api/lowcode-entity'

export interface EntityNodeData {
  entityId?: number
  entityName: string
  tableName: string
  fields: LowCodeField[]
  selected?: boolean
  status?: string
}

const props = defineProps<{ node: Node; graph?: unknown }>()

/** 响应式读取节点数据（监听 change:data 以响应 setData 触发的选中态变更） */
const nodeData = ref<EntityNodeData>((props.node.getData() as EntityNodeData) || {})
const onDataChange = () => {
  nodeData.value = (props.node.getData() as EntityNodeData) || {}
}
props.node.on('change:data', onDataChange)
onBeforeUnmount(() => {
  props.node.off('change:data', onDataChange)
})

const fields = computed(() => nodeData.value.fields || [])
const isSelected = computed(() => !!nodeData.value.selected)
</script>

<template>
  <div class="entity-node" :class="{ selected: isSelected }">
    <div class="entity-header">
      <span class="entity-name">{{ nodeData.entityName }}</span>
      <span v-if="nodeData.status" class="entity-status">{{ nodeData.status }}</span>
    </div>
    <div class="entity-table">{{ nodeData.tableName }}</div>
    <div class="entity-fields">
      <div v-for="field in fields" :key="field.name" class="field-row">
        <span class="field-key" v-if="field.primaryKey === 1">PK</span>
        <span class="field-name">{{ field.name }}</span>
        <span class="field-type">{{ field.fieldType }}</span>
      </div>
      <div v-if="fields.length === 0" class="field-empty">（暂无字段）</div>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'EntityNode' }
</script>

<style scoped lang="scss">
.entity-node {
  width: 100%;
  height: 100%;
  background: #fff;
  border: 2px solid #409eff;
  border-radius: 4px;
  font-size: 12px;
  box-sizing: border-box;
  overflow: hidden;
  transition: box-shadow 0.2s, border-color 0.2s;

  &.selected {
    border-color: #f56c6c;
    box-shadow: 0 0 0 3px rgba(245, 108, 108, 0.25);
  }

  .entity-header {
    background: #409eff;
    color: #fff;
    padding: 6px 10px;
    font-weight: 600;
    font-size: 13px;
    display: flex;
    align-items: center;
    justify-content: space-between;

    .entity-status {
      font-size: 10px;
      font-weight: 400;
      opacity: 0.85;
    }
  }

  .entity-table {
    padding: 2px 10px;
    color: #909399;
    font-size: 11px;
    border-bottom: 1px solid #ebeef5;
  }

  .entity-fields {
    max-height: 180px;
    overflow-y: auto;

    .field-row {
      display: flex;
      align-items: center;
      padding: 3px 10px;
      border-bottom: 1px solid #f5f5f5;

      .field-key {
        background: #f56c6c;
        color: #fff;
        border-radius: 2px;
        padding: 0 4px;
        font-size: 10px;
        margin-right: 6px;
      }

      .field-name {
        flex: 1;
        color: #303133;
      }

      .field-type {
        color: #909399;
        font-size: 11px;
      }
    }

    .field-empty {
      padding: 6px 10px;
      color: #c0c4cc;
      font-size: 11px;
    }
  }
}
</style>
