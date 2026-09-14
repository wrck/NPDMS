<template>
  <el-drawer
    v-model="visible"
    append-to-body
    size="min(1180px, 100vw)"
    :before-close="beforeClose"
    destroy-on-close
    :title="selection?.kind === 'stage' ? '阶段办理' : '任务办理'"
  >
    <ProjectFlowPanel
      v-if="visible && project.id != null && selection?.stageCode && (selection.kind === 'stage' || selection.taskId != null)"
      ref="flow"
      :project-id="project.id"
      :project="project"
      :selection="selection"
      :show-responsibilities="showResponsibilities"
      @changed="emit('changed')"
    />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import ProjectFlowPanel from './ProjectFlowPanel.vue'
import type { ProjectFlowSelection } from './project-flow'

// Stage/task actions, business pages and leave protection share the delivery-flow implementation.
defineOptions({ name: 'ProjectNodeWorkbenchDrawer' })
withDefaults(
  defineProps<{
    project: ProjectMasterVO
    selection?: ProjectFlowSelection
    showResponsibilities?: boolean
  }>(),
  { showResponsibilities: false }
)
const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ changed: [] }>()
const flow = ref<InstanceType<typeof ProjectFlowPanel>>()
const beforeClose = async (done: () => void) => {
  if ((await flow.value?.requestLeave()) !== false) done()
}
</script>
