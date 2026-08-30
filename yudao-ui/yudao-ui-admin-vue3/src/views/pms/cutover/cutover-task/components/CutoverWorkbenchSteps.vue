<template>
  <ol class="step-list" aria-label="割接工作台阶段">
    <li v-for="step in steps" :key="step.stage" :class="['step-item', `is-${step.state.toLowerCase()}`]">
      <span class="step-code">{{ step.stage }}</span>
      <div>
        <strong>{{ step.label }}</strong>
        <small>{{ stateLabel[step.state] }}</small>
      </div>
    </li>
  </ol>
</template>

<script setup lang="ts">
import type { CutoverTaskDetail } from '@/api/pms/cutover/cutover-task'

defineProps<{ steps: CutoverTaskDetail['workbenchSteps'] }>()

const stateLabel = { CURRENT: '当前阶段', COMPLETED: '已完成', FUTURE: '尚未进入' }
</script>

<style scoped>
.step-list { display: grid; grid-template-columns: repeat(5, minmax(8rem, 1fr)); gap: 12px; padding: 0; list-style: none; overflow-x: auto; }
.step-item { display: flex; gap: 10px; min-width: 8rem; padding: 12px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); }
.step-code { font-weight: 700; color: var(--el-text-color-secondary); }
.step-item strong, .step-item small { display: block; }
.step-item small { margin-top: 4px; color: var(--el-text-color-secondary); }
.step-item.is-current { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.step-item.is-completed .step-code { color: var(--el-color-success); }
</style>
