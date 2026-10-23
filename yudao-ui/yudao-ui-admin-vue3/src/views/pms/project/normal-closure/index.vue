<template>
  <ProjectNormalClosurePanel v-if="projectId" :project-id="projectId" />
  <el-alert v-else title="请从项目工作台进入正常闭环" type="info" :closable="false" />
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { isBusinessViewId } from '@/api/pms/platform/business-view/ids'
import ProjectNormalClosurePanel from '../project-master-detail/components/ProjectNormalClosurePanel.vue'
const route = useRoute()
const projectId = computed(() => typeof route.query.projectId === 'string' && isBusinessViewId(route.query.projectId)
  ? route.query.projectId : undefined)
</script>
