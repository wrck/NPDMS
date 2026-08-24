<template>
  <ContentWrap>
    <div class="panel-heading">
      <div>
        <h3>全部后代闭环守卫</h3>
        <span>只检查进入闭环审批的资格，不执行审批或归档。</span>
      </div>
      <el-button :loading="loading" @click="evaluate">重新检查</el-button>
    </div>
    <el-skeleton v-if="loading && !result" :rows="3" animated />
    <el-result
      v-else-if="result?.allowed"
      icon="success"
      title="满足闭环前置条件"
      :sub-title="`已按完整项目树版本 v${result.treeVersion} 检查全部后代`"
    />
    <template v-else-if="result">
      <el-alert
        type="warning"
        :closable="false"
        title="暂不能进入闭环审批"
        class="guard-alert"
      />
      <div class="table-scroll">
        <el-table :data="result.blockers" size="small" border>
          <el-table-column prop="projectId" label="项目ID" min-width="120" />
          <el-table-column label="项目" min-width="180">
            <template #default="{ row }">
              {{ row.projectCode ? `${row.projectCode} ${row.projectName || ''}` : '无权查看详情' }}
            </template>
          </el-table-column>
          <el-table-column prop="blockerType" label="阻断类型" min-width="150" />
        </el-table>
      </div>
      <el-alert
        v-if="result.pendingProgressProjects.length"
        type="info"
        :closable="false"
        :title="`待计算进度项目：${result.pendingProgressProjects.join('、')}`"
        class="pending-alert"
      />
    </template>
    <el-empty v-else description="尚未执行闭环守卫检查" />
  </ContentWrap>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as ProjectsApi from '@/api/pms/project/projects'
import type { ProjectClosureGuardVO } from '@/api/pms/project/projects'

const props = defineProps<{ projectId: number; treeVersion?: number }>()
const loading = ref(false)
const result = ref<ProjectClosureGuardVO>()
const resolvedTreeVersion = ref<number>()

const evaluate = async () => {
  loading.value = true
  try {
    resolvedTreeVersion.value = props.treeVersion || (await ProjectsApi.queryTree(
      props.projectId, { queryType: 'CHILDREN', pageSize: 1 }
    )).treeVersion
    result.value = await ProjectsApi.getClosureGuard(props.projectId, resolvedTreeVersion.value)
  } finally {
    loading.value = false
  }
}

watch(() => [props.projectId, props.treeVersion], () => {
  result.value = undefined
  evaluate()
}, { immediate: true })
</script>

<style scoped lang="scss">
.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  h3 { margin: 0 0 4px; color: var(--el-text-color-primary); font-size: 15px; }
  span { color: var(--el-text-color-secondary); font-size: 13px; }
}
.guard-alert, .pending-alert { margin-bottom: 12px; }
.pending-alert { margin-top: 12px; }
.table-scroll { max-width: 100%; overflow-x: auto; }
@media (max-width: 767px) {
  .panel-heading { flex-direction: column; }
  .panel-heading .el-button { width: 100%; }
}
</style>
