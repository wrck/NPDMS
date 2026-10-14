<template>
  <CutoverTaskDetail v-if="taskId" :key="taskId" :task-id="taskId" @close="close" />
  <el-result v-else icon="warning" title="任务编号无效"><template #extra><el-button @click="router.push('/pms/cutover/tasks')">返回任务列表</el-button></template></el-result>
</template>
<script setup lang="ts">
import CutoverTaskDetail from './index.vue'
defineOptions({ name: 'PmsCutoverTaskDetail' })
const route = useRoute()
const router = useRouter()
const taskId = computed(() => typeof route.params.taskId === 'string' && /^[1-9]\d*$/.test(route.params.taskId) ? route.params.taskId : null)
const close = () => {
  const previous = router.options.history.state.back
  if (typeof previous === 'string' && previous.startsWith('/pms/cutover/')) router.back()
  else void router.push('/pms/cutover/tasks')
}
</script>
