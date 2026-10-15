<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
  <el-button v-if="error" @click="load">重新加载</el-button>
  <ProjectMembersPanel v-if="project" :key="String(project.id)" :project="project" :readonly="readonly"
    initial-role="END_USER" @updated="updated" />
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import * as Projects from '@/api/pms/project/projects'
import ProjectMembersPanel from '@/views/pms/project/inheritance/members/ProjectMembersPanel.vue'

defineOptions({ name: 'ProjectEndUsersWorkbench' })
const props = defineProps<{ projectId: number | string; readonly?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const project = ref<Projects.ProjectMasterVO>()
const error = ref('')
let generation = 0
const load = async () => {
  const request = ++generation, id = props.projectId
  error.value = ''
  try {
    const value = await Projects.getProject(id)
    if (request !== generation) return false
    if (!value || String(value.id) !== String(id)) throw new Error('Project unavailable')
    project.value = value
    return true
  } catch {
    if (request === generation) { project.value = undefined; error.value = '项目成员加载失败，请重新加载。' }
    return false
  }
}
const updated = async () => {
  const id = props.projectId
  if (await load() && String(id) === String(props.projectId)) emit('changed')
}
watch(() => props.projectId, () => { project.value = undefined; void load() }, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => ++generation)
</script>
