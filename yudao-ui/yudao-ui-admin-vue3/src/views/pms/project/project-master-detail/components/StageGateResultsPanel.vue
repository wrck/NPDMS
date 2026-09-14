<template>
  <section class="stage-gates" aria-label="阶段门禁条件" :aria-busy="loading">
    <div class="gate-heading">
      <span>阶段门禁条件</span>
      <el-button :loading="loading" :disabled="loading" @click="refresh">刷新门禁结果</el-button>
    </div>
    <p v-if="loading" role="status">正在读取本轮门禁结果…</p>
    <el-alert v-else-if="error" :title="error" type="warning" :closable="false" show-icon />
    <template v-else-if="workbench">
      <p class="hint">第 {{ workbench.executionRound }} 轮 · 当前规则结果；刷新不会推进阶段或发起审批。</p>
      <p v-if="!workbench.gates.length" role="status">当前阶段未配置门禁。</p>
      <article v-for="gate in workbench.gates" :key="gate.gateId" class="gate-result" :aria-label="gate.name">
        <div class="gate-heading">
          <span>{{ gate.name }} · {{ gate.gateType === 'ENTRY' ? '准入' : '退出' }}</span>
          <el-tag :type="gate.evaluation.outcome === 'MATCHED' ? 'success' : 'warning'">{{ label(gate.evaluation.outcome) }}</el-tag>
        </div>
        <p v-if="gate.evaluation.reasonCode" class="hint">原因：{{ gate.evaluation.reasonCode }}</p>
        <details>
          <summary>条件与引用明细</summary>
          <ul>
            <li v-for="reference in gate.references" :key="reference.gateReferenceId">
              {{ reference.refType }} · {{ reference.refCode }}<span v-if="reference.refVersion"> · 冻结版本 {{ reference.refVersion }}</span>
            </li>
          </ul>
          <ul>
            <li v-for="condition in gate.evaluation.conditions" :key="condition.key">
              {{ condition.path }} · {{ condition.component }}：{{ label(condition.outcome) }}
              <span v-if="condition.reasonCode">（{{ condition.reasonCode }}）</span>
            </li>
          </ul>
        </details>
      </article>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getStageGateWorkbench, type StageGateWorkbench } from '@/api/pms/project/stage-gates'

const props = defineProps<{ projectId: number; stageCode: string; projectVersion?: number }>()
const workbench = ref<StageGateWorkbench>()
const error = ref('')
const loading = ref(false)
let requestNo = 0
const label = (outcome: string) => ({ MATCHED: '满足', NOT_MATCHED: '不满足', UNKNOWN: '未知' }[outcome] || '未知')
const refresh = async () => {
  const current = ++requestNo
  const { projectId, stageCode } = props
  workbench.value = undefined
  error.value = ''
  loading.value = true
  try {
    const result = await getStageGateWorkbench(projectId, stageCode)
    if (current !== requestNo) return
    if (String(result.projectId) !== String(projectId) || result.stageCode !== stageCode || result.recoverableError
      || result.executionId == null || result.executionRound == null || result.planVersionId == null) {
      error.value = '当前阶段的计划或执行轮次暂不可用，请刷新后重试。'
      return
    }
    workbench.value = result
  } catch {
    if (current === requestNo) error.value = '门禁结果读取失败，请重试；不能视为条件已满足。'
  } finally {
    if (current === requestNo) loading.value = false
  }
}
// Vue watch invalidates requests on context changes; stale results never replace another stage.
// https://vuejs.org/guide/essentials/watchers.html#side-effect-cleanup
watch(() => [props.projectId, props.stageCode, props.projectVersion], refresh, { immediate: true })
onBeforeUnmount(() => { ++requestNo })
defineExpose({ refresh })
</script>

<style scoped>
.stage-gates { margin-top: 16px; min-width: 0; }
.gate-heading { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.gate-result { padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); overflow-wrap: anywhere; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; }
summary { cursor: pointer; margin-top: 8px; }
</style>
