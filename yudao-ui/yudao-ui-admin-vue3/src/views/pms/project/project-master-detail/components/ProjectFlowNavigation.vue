<template>
  <div v-loading="loading" class="flow-nav">
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button link @click="load">重新加载导航</el-button>
    </el-alert>
    <el-empty v-else-if="!stages.length && !loading" description="暂无阶段任务导航" :image-size="40" />
    <ProjectFlowStageNavigation v-for="stage in stages" :key="`${projectId}:${stage.stageCode}`"
      :project-id="projectId" :stage="stage" :selection="selection" :refresh-version="refreshVersion"
      @select="emit('select', $event)" />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getProjectWorkspace, type StageTaskNavigation } from '@/api/pms/project/task-workbench'
import type { ProjectFlowSelection } from './project-flow'
import ProjectFlowStageNavigation from '@/views/pms/project/inheritance/detail/ProjectFlowStageNavigation.vue'

defineOptions({ name: 'ProjectFlowNavigation' })
const props = defineProps<{ projectId: number; selection?: ProjectFlowSelection }>()
const emit = defineEmits<{ select: [payload: ProjectFlowSelection] }>()
const stages = ref<StageTaskNavigation[]>([])
const loading = ref(false)
const error = ref('')
const refreshVersion = ref(0)
let sequence = 0
const load = async () => {
  const token = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await getProjectWorkspace(props.projectId)
    if (token !== sequence) return
    stages.value = result.stageTaskNavigation
    refreshVersion.value++
  } catch {
    if (token === sequence) error.value = '阶段任务导航加载失败，请重试。'
  } finally {
    if (token === sequence) loading.value = false
  }
}
watch(() => props.projectId, () => { stages.value = []; void load() }, { immediate: true })
onBeforeUnmount(() => { ++sequence })
defineExpose({ reload: load })
</script>
