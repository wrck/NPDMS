<template>
  <!-- 人员指派（S0 阶段冻结绑定）在当前工作区内嵌项目成员面板，不再跳离当前界面。 -->
  <ProjectMembersPanel :project="project" @updated="emit('changed')" />
</template>
<script setup lang="ts">
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import ProjectMembersPanel from '@/views/pms/project/inheritance/members/ProjectMembersPanel.vue'

defineProps<{ project: ProjectMasterVO }>()
const emit = defineEmits<{ changed: [] }>()
</script>
