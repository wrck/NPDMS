<template>
  <ContentWrap :body-style="{ padding: '20px', overflow: 'hidden' }" class="satisfaction-content">
    <header class="satisfaction-heading"><h2>满意度调查</h2><p>邀请客户填写问卷，查看评价结果并跟进整改。</p></header>
    <el-tabs v-model="activeTab">
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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
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
const message = useMessage()
const requestLeave = () => {
  if (!taskDirty.value && !resultDirty.value) return true
  message.warning('请先完成或关闭满意度操作，再切换页面。')
  return false
}
// https://router.vuejs.org/guide/advanced/composition-api.html#navigation-guards
onBeforeRouteLeave(requestLeave)
onBeforeRouteUpdate(requestLeave)
const beforeUnload = (event: BeforeUnloadEvent) => {
  if (!taskDirty.value && !resultDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
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
  requestLeave,
  isDirty: () => taskDirty.value || resultDirty.value,
  discardChanges: () => {
    if (taskRef.value?.discardChanges() === false) return false
    return resultRef.value?.discardChanges() !== false
  }
})
</script>

<style scoped>
.satisfaction-heading { margin-bottom: 20px; }
.satisfaction-heading h2 { margin: 0 0 6px; font-size: 16px; font-weight: 600; }
.satisfaction-heading p { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }
</style>
