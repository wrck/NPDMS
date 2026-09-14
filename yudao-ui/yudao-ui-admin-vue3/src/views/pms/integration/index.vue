<template>
  <section class="integration-page" aria-label="数据迁移与同步">
    <el-tabs v-model="active" class="integration-navigation">
      <el-tab-pane label="连接管理" name="connections"
        ><ConnectionPanel v-if="active === 'connections'"
      /></el-tab-pane>
      <el-tab-pane label="迁移与同步任务" name="tasks"
        ><TaskPanel v-if="active === 'tasks'" @open-run="openRun"
      /></el-tab-pane>
      <el-tab-pane label="运行记录" name="runs"
        ><RunPanel v-if="active === 'runs'" :run-id="runId" @selected="openRun" @back="closeRun"
      /></el-tab-pane>
      <el-tab-pane label="来源映射" name="mappings"
        ><MappingPanel v-if="active === 'mappings'"
      /></el-tab-pane>
    </el-tabs>
  </section>
</template>
<script setup lang="ts">
import ConnectionPanel from './ConnectionPanel.vue'
import TaskPanel from './TaskPanel.vue'
import RunPanel from './RunPanel.vue'
import MappingPanel from './MappingPanel.vue'
import { replaceIntegrationUrlState } from './urlState'
import type { Id } from '@/api/pms/integration'
defineOptions({ name: 'PmsDataIntegration' })
const route = useRoute()
const workspaceTabs = new Set(['connections', 'tasks', 'runs', 'mappings'])
const initialTab = String(route.query.tab || 'tasks')
const activeState = ref(workspaceTabs.has(initialTab) ? initialTab : 'tasks')
const runId = ref<Id | undefined>(route.query.run ? String(route.query.run) : undefined)
const active = computed({
  get: () => activeState.value,
  set: (tab: string) => {
    activeState.value = tab
    if (tab !== 'runs') runId.value = undefined
    replaceIntegrationUrlState({ tab, task: undefined, run: runId.value?.toString() })
  }
})
const openRun = (id: Id) => {
  if (active.value === 'runs' && String(runId.value) === String(id)) return
  activeState.value = 'runs'
  runId.value = String(id)
  replaceIntegrationUrlState({ tab: 'runs', task: undefined, run: String(id) })
}
const closeRun = () => {
  runId.value = undefined
  replaceIntegrationUrlState({ tab: 'runs', run: undefined })
}
</script>
<style lang="scss">
@use './integration';
</style>
<style scoped>
@media (width <= 640px) {
  :deep(.el-pagination) {
    float: none;
    max-width: 100%;
    clear: both;
    justify-content: center;
    gap: 6px;
  }

  :deep(.el-pagination__sizes),
  :deep(.el-pagination__jump) {
    display: none;
  }
}
</style>
