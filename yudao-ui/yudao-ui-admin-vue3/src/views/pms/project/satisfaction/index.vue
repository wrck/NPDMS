<template>
  <ContentWrap>
    <header class="page-heading">
      <div>
        <h1>{{ scoped ? '项目满意度' : '满意度管理' }}</h1>
        <p>{{
          scoped
            ? '办理当前项目的采集任务，查看判定和归档结果。'
            : '配置问卷、推进采集任务，并查看不可变判定与归档结果。'
        }}</p>
      </div>
    </header>
    <el-tabs v-model="activeTab" class="workbench-tabs">
      <el-tab-pane v-if="!scoped && !props.readonly" label="问卷模板" name="templates"
        ><TemplatePanel
      /></el-tab-pane>
      <el-tab-pane label="采集任务" name="tasks"
        ><TaskPanel
          ref="taskRef"
          :project-id="props.projectId"
          :readonly="props.readonly"
          @dirty-change="taskDirty = $event"
          @changed="emit('changed')"
      /></el-tab-pane>
      <el-tab-pane label="判定结果" name="results"
        ><ResultPanel
          ref="resultRef"
          :project-id="props.projectId"
          :readonly="props.readonly"
          @dirty-change="resultDirty = $event"
          @changed="emit('changed')"
      /></el-tab-pane>
    </el-tabs>
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import TemplatePanel from './TemplatePanel.vue'
import TaskPanel from './TaskPanel.vue'
import ResultPanel from './ResultPanel.vue'
import type { SatisfactionViewProps } from './projectContext'

defineOptions({ name: 'PmsSatisfactionWorkbench' })
const props = defineProps<SatisfactionViewProps>()
const emit = defineEmits<{ 'dirty-change': [value: boolean]; changed: [] }>()
const scoped = computed(() => props.projectId !== undefined)
const activeTab = ref('tasks')
const taskRef = ref<InstanceType<typeof TaskPanel>>()
const resultRef = ref<InstanceType<typeof ResultPanel>>()
const taskDirty = ref(false)
const resultDirty = ref(false)
watch(
  () => taskDirty.value || resultDirty.value,
  (value) => emit('dirty-change', value)
)
watch(
  () => scoped.value || props.readonly,
  (restricted) => {
    if (restricted && activeTab.value === 'templates') activeTab.value = 'tasks'
  }
)
defineExpose({
  isDirty: () => taskDirty.value || resultDirty.value,
  discardChanges: () => {
    if (taskRef.value?.discardChanges() === false) return false
    return resultRef.value?.discardChanges() !== false
  }
})
</script>

<style scoped lang="scss">
.page-heading h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 24px;
}
.page-heading p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}
.workbench-tabs {
  margin-top: 16px;
}
</style>
