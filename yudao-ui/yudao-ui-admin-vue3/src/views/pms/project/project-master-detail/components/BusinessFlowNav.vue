<template>
  <!-- 全流程业务阶段导航（DemoV2 左侧六组导航口径）：点击项由父页面在右侧承接完整业务操作界面 -->
  <div class="flow-nav">
    <div v-for="group in groups" :key="group.key" class="flow-group">
      <div class="flow-group-title">{{ group.title }}</div>
      <template v-for="item in visibleItems(group)" :key="item.key">
        <button
          class="flow-item"
          :class="{ 'flow-item--active': active === item.key }"
          :data-testid="`flow-nav-${item.key}`"
          @click="emit('select', item.key)"
        >
          <Icon :icon="item.icon" class="flow-icon" />
          <span class="flow-label">{{ item.label }}</span>
          <span v-if="item.code" class="flow-code">{{ item.code }}</span>
        </button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { checkPermi } from '@/utils/permission'
import type { BusinessFlowGroup, BusinessNavItem } from './businessModules'

defineOptions({ name: 'BusinessFlowNav' })

defineProps<{ groups: BusinessFlowGroup[]; active: string }>()
const emit = defineEmits<{ (e: 'select', key: string): void }>()

const visibleItems = (group: BusinessFlowGroup): BusinessNavItem[] =>
  group.items.filter((item) => !item.permission || checkPermi(item.permission))
</script>

<style lang="scss" scoped>
.flow-group {
  margin-bottom: 10px;

  &:last-child {
    margin-bottom: 0;
  }
}

.flow-group-title {
  padding: 4px 10px 2px;
  margin-left: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  border-left: 2px solid var(--el-border-color);
}

.flow-item {
  display: flex;
  width: 100%;
  padding: 6px 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: none;
  border-radius: 4px;
  transition: all 0.15s ease;
  align-items: center;
  gap: 8px;

  &:hover {
    background: var(--el-color-primary-light-9);
  }

  &--active {
    font-weight: 600;
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
}

.flow-icon {
  flex-shrink: 0;
  font-size: 15px;
}

.flow-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-code {
  flex: none;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-placeholder);
}
</style>
