<template>
  <ContentWrap class="task-entry">
    <el-result
      icon="info"
      title="任务 WBS 已迁移"
      sub-title="请选择项目后进入项目详情中的任务工作台"
    >
      <template #extra>
        <div class="task-entry__actions">
          <PmsEntitySelect
            v-model="projectId"
            :api="ProjectApi.getProjectPage"
            :label-field="['code', 'name']"
            value-field="id"
            query-field="name"
            placeholder="请选择项目"
            class="task-entry__select"
          />
          <el-button type="primary" :disabled="!projectId" @click="openTaskWorkbench">
            进入项目任务
          </el-button>
        </div>
      </template>
    </el-result>
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as ProjectApi from '@/api/pms/project/project'

defineOptions({ name: 'PmsProjectTask' })

const route = useRoute()
const router = useRouter()
const projectId = ref<number>()

const openTaskWorkbench = () => {
  if (!projectId.value) return
  router.push({
    path: '/pms/project-management/project-master-detail',
    query: { projectId: String(projectId.value), tab: 'tasks' }
  })
}

onMounted(() => {
  const requestedProjectId = Number(route.query.projectId)
  if (!Number.isSafeInteger(requestedProjectId) || requestedProjectId <= 0) return
  projectId.value = requestedProjectId
  openTaskWorkbench()
})
</script>

<style scoped lang="scss">
.task-entry {
  min-height: 360px;

  &__actions {
    display: flex;
    gap: 12px;
    justify-content: center;
    width: min(520px, calc(100vw - 64px));
  }

  &__select {
    width: min(360px, 100%);
  }
}

@media (width <= 480px) {
  .task-entry__actions {
    flex-direction: column;
    width: calc(100vw - 56px);
  }
}
</style>
