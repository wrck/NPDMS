<template>
  <section :aria-label="label">
    <p>{{ label }}在项目工作台办理，完成结果会同步到当前节点。</p>
    <el-button v-if="allowedActions.includes('QUERY')" type="primary" plain @click="open">
      查看{{ label }}
    </el-button>
    <el-alert v-else title="当前没有查看该业务的权限" type="info" :closable="false" />
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { isBusinessViewId, type BusinessViewId } from '@/api/pms/platform/business-view/ids'

const props = defineProps<{
  projectId: BusinessViewId
  label: string
  tab: 'members' | 'schedule' | 'solution' | 'cutover'
  allowedActions: string[]
}>()
const router = useRouter()
const open = () => {
  if (!isBusinessViewId(props.projectId) || !props.allowedActions.includes('QUERY')) return
  return router.push({
    path: '/pms/project-management/project-master-detail',
    query: { projectId: String(props.projectId), tab: props.tab }
  })
}
</script>
