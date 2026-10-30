<template>
  <div v-if="loading" v-loading="true" class="inspection-detail-loading">正在加载巡检任务</div>
  <el-alert v-else-if="loadError" :title="loadError" type="error" :closable="false" show-icon>
    <el-button link type="primary" @click="load">重新加载</el-button>
  </el-alert>
  <PlatformDetail v-else-if="page === 'legacy'" :key="taskId" />
  <UmcDetail v-else-if="page === 'umc'" :key="taskId" />
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getTask } from '@/api/pms/inspection/tasks'
import { checkPermi } from '@/utils/permission'
import PlatformDetail from './task-detail/index.vue'
import UmcDetail from './umc-task-detail/index.vue'
const route = useRoute()
const taskId = computed(() => String(route.params.taskId || ''))
const page = ref<'legacy' | 'umc'>()
const loading = ref(false)
const loadError = ref('')
let sequence = 0
const load = async () => {
  const current = ++sequence
  page.value = undefined
  loadError.value = ''
  loading.value = true
  try {
    if (!/^\d+$/.test(taskId.value)) throw new Error('任务编号无效')
    // The existing page also supports assignees who can read an issue but not its whole task.
    if (!checkPermi(['pms:inspection-task:query'])) {
      page.value = 'legacy'
      return
    }
    const task = await getTask(taskId.value)
    if (current !== sequence) return
    if (task.currentAttemptId != null) {
      const attempt = task.attempts.find((row) => String(row.id) === String(task.currentAttemptId))
      if (!attempt) throw new Error('当前执行尝试缺失')
      page.value = attempt.providerCode === 'UMC' ? 'umc' : 'legacy'
    } else {
      page.value = task.attempts.length || task.selections.length ? 'legacy' : 'umc'
    }
  } catch {
    if (current !== sequence) return
    if (typeof route.query.issueId === 'string' && route.query.issueId) page.value = 'legacy'
    else loadError.value = '任务详情加载失败或无权访问，请重新加载。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
watch(taskId, load, { immediate: true })
onBeforeUnmount(() => { sequence++ })
</script>
<style scoped>
.inspection-detail-loading {
  padding: 32px 20px;
  color: var(--el-text-color-secondary);
}
</style>
