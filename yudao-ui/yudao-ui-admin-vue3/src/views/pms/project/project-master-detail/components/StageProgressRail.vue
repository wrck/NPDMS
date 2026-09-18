<template>
  <ContentWrap class="rail-band">
    <div v-if="sortedStages.length" class="rail-scroll">
      <div class="rail" role="tablist" aria-label="项目阶段推进轨">
        <template v-for="(stage, index) in sortedStages" :key="stage.stageCode">
          <button
            class="station"
            :class="stationClass(stage)"
            type="button"
            :aria-label="`${stage.stageCode} ${stage.name}`"
            @click="emit('select', stage.stageCode)"
          >
            <span class="st-line">
              <i class="st-dot"></i>
              <span class="st-code">{{ stage.stageCode }}</span>
              <span class="st-name">{{ stage.name }}</span>
              <span v-if="overdueDays(stage)" class="st-over">超期 {{ overdueDays(stage) }} 天</span>
            </span>
          </button>
          <div v-if="index < sortedStages.length - 1" class="gate-seg" :class="gateClass(stage)">
            <i class="gate-wire"></i>
            <i class="gate-node"></i>
            <span class="gate-label">{{ gateLabel(stage.stageCode) }}</span>
          </div>
        </template>
        <button
          class="rail-more"
          type="button"
          :aria-expanded="legendVisible"
          aria-label="推进说明与图例"
          title="推进说明与图例"
          @click="legendVisible = !legendVisible"
        >
          <Icon :icon="legendVisible ? 'ep:arrow-up' : 'ep:arrow-down'" />
        </button>
      </div>
    </div>
    <div v-else class="rail-empty">阶段实例尚未生成，创建项目计划后展示推进轨。</div>
    <div v-show="legendVisible && sortedStages.length" class="rail-detail">
      <span class="rail-hint">每阶段通过出口门禁后进入下一阶段；门禁要求与推进操作见工作区右侧栏。</span>
      <span class="lg"><i class="lg-done"></i>已完成</span>
      <span class="lg"><i class="lg-cur"></i>进行中</span>
      <span class="lg"><i class="lg-over"></i>超期</span>
      <span class="lg"><i class="lg-todo"></i>未开始</span>
      <span class="lg"><i class="lg-gate lg-gate-pass"></i>门禁已通过</span>
      <span class="lg"><i class="lg-gate"></i>门禁待评估</span>
    </div>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ProjectInstancesVO } from '@/api/pms/project/projects'

defineOptions({ name: 'StageProgressRail' })

const props = defineProps<{
  stages: ProjectInstancesVO['stages']
  gates: ProjectInstancesVO['gates']
}>()
const emit = defineEmits<{ select: [stageCode: string] }>()

const legendVisible = ref(false)
const sortedStages = computed(() => [...props.stages].sort((a, b) => a.sortOrder - b.sortOrder))

const overdueDays = (stage: ProjectInstancesVO['stages'][number]) => {
  if (stage.status !== 'ACTIVE' || !stage.planEndTime) return 0
  const days = Math.ceil((Date.now() - new Date(stage.planEndTime).getTime()) / 86400000)
  return days > 0 ? days : 0
}

const stationClass = (stage: ProjectInstancesVO['stages'][number]) => {
  if (stage.status === 'DONE') return 'done'
  if (stage.status === 'ACTIVE') return overdueDays(stage) ? 'cur overdue' : 'cur'
  if (stage.status === 'TERMINATED') return 'terminated'
  return ''
}

// 出口门禁线随上一阶段状态着色：上一阶段已完成即视为已通过
const gateClass = (stage: ProjectInstancesVO['stages'][number]) => {
  if (stage.status === 'DONE') return 'passed'
  if (stage.status === 'ACTIVE') return 'next'
  return ''
}

const gateLabel = (stageCode: string) =>
  props.gates
    .filter((gate) => gate.stageCode === stageCode && gate.gateType !== 'ENTRY')
    .map((gate) => gate.name)
    .join(' · ')
</script>

<style scoped lang="scss">
.rail-band :deep(.el-card__body) {
  padding: 12px 16px 10px;
}

.rail-scroll {
  overflow-x: auto;
  overflow-y: hidden;
}

.rail {
  display: flex;
  align-items: flex-start;
  min-width: 860px;
  padding-bottom: 20px;
}

.station {
  flex: 1 1 auto;
  min-width: 0;
  padding: 0 4px;
  background: none;
  border: none;
  cursor: pointer;
  text-align: center;
}

.st-line {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding-bottom: 6px;
}

.st-dot {
  width: 10px;
  height: 10px;
  flex: none;
  background: var(--el-border-color);
  border: 2px solid var(--el-border-color);
  border-radius: 50%;
}

.station.done .st-dot {
  background: var(--el-color-success);
  border-color: var(--el-color-success);
}

.station.cur .st-dot {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
}

.station.overdue .st-dot {
  background: var(--el-color-danger);
  border-color: var(--el-color-danger);
  box-shadow: 0 0 0 3px var(--el-color-danger-light-9);
}

.station.terminated .st-dot {
  background: var(--el-color-info);
  border-color: var(--el-color-info);
}

.st-code {
  flex: none;
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.st-name {
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.station:hover .st-name {
  color: var(--el-color-primary);
}

.station.cur .st-name {
  color: var(--el-color-primary-dark);
  font-weight: 600;
}

.station.cur .st-line::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  content: '';
  background: var(--el-color-primary);
  border-radius: 1px;
}

.st-over {
  flex: none;
  padding: 0 6px;
  font-size: 10.5px;
  line-height: 16px;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border-radius: 2px;
}

.gate-seg {
  position: relative;
  flex: 0 0 56px;
  height: 22px;
}

.gate-wire {
  position: absolute;
  top: 10px;
  right: -3px;
  left: -3px;
  border-top: 2px dashed var(--el-border-color-lighter);
}

.gate-seg.passed .gate-wire {
  border-top: 2px solid var(--el-color-primary);
}

.gate-node {
  position: absolute;
  top: 5px;
  left: 50%;
  width: 10px;
  height: 10px;
  background: var(--el-bg-color);
  border: 2px solid var(--el-border-color);
  transform: translateX(-50%) rotate(45deg);
}

.gate-seg.passed .gate-node {
  background: var(--el-color-success);
  border-color: var(--el-color-success);
}

.gate-seg.next .gate-node {
  border-color: var(--el-color-primary);
}

.gate-label {
  position: absolute;
  top: 26px;
  right: -16px;
  left: -16px;
  font-size: 10.5px;
  line-height: 1.3;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.rail-more {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-left: 10px;
  padding: 0;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  cursor: pointer;

  &:hover {
    color: var(--el-color-primary);
    border-color: var(--el-color-primary);
  }
}

.rail-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  align-items: center;
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--el-text-color-secondary);
}

.rail-hint {
  color: var(--el-text-color-secondary);
}

.lg {
  display: inline-flex;
  gap: 5px;
  align-items: center;

  i {
    display: inline-block;
    width: 9px;
    height: 9px;
    border-radius: 50%;
  }
}

.lg-done {
  background: var(--el-color-success);
}

.lg-cur {
  background: var(--el-color-primary);
}

.lg-todo {
  background: var(--el-border-color);
  border: 1px solid var(--el-border-color);
}

.lg-over {
  background: var(--el-color-danger);
}

.lg-gate {
  width: 8px !important;
  height: 8px !important;
  background: var(--el-bg-color);
  border: 1.5px solid var(--el-border-color);
  border-radius: 1px !important;
  transform: rotate(45deg);
}

.lg-gate-pass {
  background: var(--el-color-success);
  border-color: var(--el-color-success);
}

.rail-empty {
  padding: 6px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
