<template>
  <!-- 人员指派（S0 阶段冻结绑定）在当前工作区内嵌项目成员面板，不再跳离当前界面。 -->
  <ProjectMembersPanel ref="membersRef" :project="project" :readonly="readonly" @updated="emit('changed')" />
</template>
<script setup lang="ts">
import { ref } from 'vue'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import ProjectMembersPanel from '@/views/pms/project/inheritance/members/ProjectMembersPanel.vue'

defineProps<{ project: ProjectMasterVO; readonly?: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const membersRef = ref<InstanceType<typeof ProjectMembersPanel>>()
defineExpose({ refresh: () => membersRef.value?.refresh(), requestLeave: () => membersRef.value?.requestLeave() ?? Promise.resolve(true) })
</script>
