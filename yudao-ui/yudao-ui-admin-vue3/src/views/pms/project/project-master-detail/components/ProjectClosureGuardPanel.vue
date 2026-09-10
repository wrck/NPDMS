<template>
  <ContentWrap>
    <el-form inline class="-mb-15px guard-query">
      <el-form-item label="项目"><el-input :model-value="projectName || `项目 #${projectId}`" disabled class="!w-220px" /></el-form-item>
      <el-form-item><el-button :loading="loading" @click="evaluate" data-testid="evaluate-closure-tree"><Icon icon="ep:search" />重新检查</el-button></el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-alert type="info" :closable="false" class="guard-alert"
      title="检查范围仅为后代项目闭环与聚合进度；不能替代按冻结配置执行的完整闭环校验、审批或归档。" />
    <el-skeleton v-if="loading" :rows="3" animated />
    <el-alert v-else-if="errorText" :title="errorText" type="error" :closable="false" data-testid="closure-tree-error" />
    <el-alert
      v-else-if="result?.allowed"
      type="success"
      :closable="false"
      title="项目树维度检查通过，不代表完整闭环校验通过"
      :description="`项目树版本 v${result.treeVersion}；仍需办理其余适用校验和闭环审批。`"
      data-testid="closure-tree-passed"
    />
    <template v-else-if="result">
      <el-alert
        type="warning"
        :closable="false"
        title="项目树检查存在阻断项"
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
    <el-empty v-else description="尚未取得项目树检查结果" />
  </ContentWrap>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import * as ProjectsApi from '@/api/pms/project/projects'
import type { ProjectClosureGuardVO } from '@/api/pms/project/projects'

const props = defineProps<{ projectId: number; projectName?: string; treeVersion?: number }>()
const loading = ref(false)
const result = ref<ProjectClosureGuardVO>()
const resolvedTreeVersion = ref<number>()
const errorText = ref('')
let evaluationSequence = 0

const evaluate = async () => {
  const sequence = ++evaluationSequence
  const projectId = props.projectId
  const hintedVersion = props.treeVersion
  result.value = undefined
  resolvedTreeVersion.value = undefined
  errorText.value = ''
  if (!Number.isSafeInteger(projectId) || projectId <= 0 ||
      (hintedVersion !== undefined && (!Number.isSafeInteger(hintedVersion) || hintedVersion <= 0))) {
    loading.value = false
    errorText.value = '项目或项目树版本无效，未执行检查。'
    return
  }
  loading.value = true
  try {
    const version = hintedVersion ?? (await ProjectsApi.queryTree(
      projectId, { queryType: 'CHILDREN', pageSize: 1 }
    )).treeVersion
    if (sequence !== evaluationSequence) return
    if (!Number.isSafeInteger(version) || version <= 0) throw new Error('Invalid tree version')
    resolvedTreeVersion.value = version
    const checked = await ProjectsApi.getClosureGuard(projectId, version)
    if (sequence === evaluationSequence) result.value = checked
  } catch {
    if (sequence === evaluationSequence) errorText.value = '闭环树检查未完成，请确认项目范围与树版本后重试；不能沿用上次通过结果。'
  } finally {
    if (sequence === evaluationSequence) loading.value = false
  }
}

watch(() => [props.projectId, props.treeVersion], () => {
  result.value = undefined
  evaluate()
}, { immediate: true })
onBeforeUnmount(() => { evaluationSequence++ })
</script>

<style scoped lang="scss">
.guard-alert, .pending-alert { margin-bottom: 12px; }
.pending-alert { margin-top: 12px; }
.table-scroll { max-width: 100%; overflow-x: auto; }
@media (max-width: 767px) {
  .guard-query :deep(.el-form-item) { width: 100%; }
}
</style>
