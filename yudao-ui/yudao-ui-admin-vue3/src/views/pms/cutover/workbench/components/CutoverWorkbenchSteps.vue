<template>
  <nav class="cut-progress" aria-label="割接办理流程">
    <ol>
      <li v-for="(step, index) in steps" :key="step.stage" :class="[`is-${step.state.toLowerCase()}`, { 'is-selected': selected === step.stage }]">
        <button type="button" :disabled="!step.isAccessible || step.state === 'SKIPPED'"
          :aria-current="selected === step.stage ? 'step' : undefined" :title="stateLabel[step.state]" @click="emit('select', step.stage)">
          <span class="step-circle" aria-hidden="true">{{ step.state === 'COMPLETED' ? '✓' : index + 1 }}</span>
          <span class="step-label">{{ labels[step.stage] }}<small v-if="step.state === 'SKIPPED'">D级跳过</small></span>
        </button>
        <span v-if="index < steps.length - 1" class="step-connector" aria-hidden="true" />
      </li>
    </ol>
  </nav>
</template>

<script setup lang="ts">
import type { CutoverTaskDetail } from '@/api/pms/cutover/workbench'

defineProps<{ steps: CutoverTaskDetail['workbenchSteps']; selected?: string | null }>()
const emit = defineEmits<{ select: [stage: 'P2' | 'P3' | 'P4' | 'P5' | 'P6'] }>()

const labels = { P2: '割接等级确认', P3: '调研及风险考察', P4: '割接方案', P5: '割接审批', P6: '割接跟踪及闭环' }
const stateLabel = { CURRENT: '当前办理阶段', COMPLETED: '查看已完成步骤', FUTURE: '尚未进入此步骤', SKIPPED: 'D级简易割接跳过调研步骤' }
</script>

<style scoped>
.cut-progress { margin-bottom: 20px; padding: 16px 20px; overflow-x: auto; border-radius: 8px; background: #fff; box-shadow: 0 1px 3px rgb(0 0 0 / 4%); }
ol { display: flex; align-items: center; min-width: 820px; margin: 0; padding: 0; list-style: none; }
li { display: flex; flex: 1; align-items: center; min-width: 0; color: var(--cut-future, #ff4d4f); }
li:last-child { flex: 0 0 auto; }
button { display: flex; align-items: center; gap: 8px; padding: 0; border: 0; background: transparent; color: inherit; font: inherit; font-size: 13px; white-space: nowrap; cursor: pointer; }
button:disabled { cursor: default; }
.step-circle { display: grid; width: 28px; height: 28px; flex: 0 0 28px; place-items: center; border: 2px solid currentColor; border-radius: 50%; font-size: 13px; line-height: 1; }
.step-connector { height: 2px; flex: 1; min-width: 18px; margin: 0 8px; background: #e6eaf1; }
.step-label small { display: block; color: #888; font-size: 11px; font-weight: 400; }
.is-completed { color: var(--cut-complete, #52c41a); }
.is-completed .step-circle { background: var(--cut-complete, #52c41a); color: #fff; border-color: var(--cut-complete, #52c41a); }
.is-completed .step-connector { background: var(--cut-complete, #52c41a); }
.is-current { color: var(--cut-current, #1677ff); font-weight: 600; }
.is-current .step-circle { border-color: var(--cut-current, #1677ff); background: var(--cut-current, #1677ff); color: #fff; box-shadow: 0 0 0 4px #d6e4ff; }
.is-skipped { color: #a7adb5; }
.is-selected:not(.is-current) .step-label { text-decoration: underline; text-underline-offset: 5px; }
@media (width <= 767px) { .cut-progress { padding: 16px 14px; } }
</style>
