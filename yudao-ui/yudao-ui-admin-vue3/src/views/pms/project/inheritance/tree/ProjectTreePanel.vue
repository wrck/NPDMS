<template>
  <ContentWrap class="project-tree-panel">
    <header class="panel-header"><span class="panel-title"><Icon icon="ep:share" /> 项目树</span><div class="panel-header-actions">
      <el-button v-hasPermi="['pms:project:update']" :disabled="moving" @click="openMove">移动当前项目</el-button>
      <el-button :loading="tree.state.root.loading" @click="load"><Icon icon="ep:refresh" />刷新</el-button>
    </div></header>
    <el-alert v-if="tree.state.root.error" :title="tree.state.root.error" type="error" :closable="false" />
    <el-alert v-if="tree.state.updating" title="项目树正在更新，当前显示上一完整版本" type="info" :closable="false" />
    <p v-if="mobile" class="tree-scroll-hint">左右滑动查看办事处、负责人及进度</p>
    <el-table v-loading="tree.state.root.loading" :data="rows" row-key="rowKey" :tree-props="{ children: 'children' }" default-expand-all :indent="24" empty-text="当前项目暂无可见关系">
      <el-table-column label="项目名称 / 编码" :min-width="mobile ? 250 : 340"><template #default="{ row }">
        <div class="project-identity">
          <div class="project-identity-title">
            <router-link v-if="row.visibility === 'FULL'" class="project-link" :to="projectLocation(row.projectId)">{{ row.projectName || '未命名项目' }}</router-link>
            <span v-else>{{ row.visibility === 'PATH_PLACEHOLDER' ? '受限项目' : row.projectName || '项目概要' }}</span>
            <el-tag v-if="row.projectId === props.projectId" size="small" effect="plain" class="current-project">当前项目</el-tag>
            <el-tag v-if="row.visibility !== 'FULL'" size="small" type="info" effect="plain" class="current-project">{{ visibilityLabel[row.visibility] }}</el-tag>
          </div>
          <div v-if="row.visibility !== 'PATH_PLACEHOLDER' && row.projectCode" class="project-code">{{ row.projectCode }}</div>
        </div>
      </template></el-table-column>
      <el-table-column label="办事处" min-width="110" show-overflow-tooltip><template #default="{ row }">
        <span>{{ row.visibility === 'FULL' ? row.displayDetails?.office || '—' : '—' }}</span>
      </template></el-table-column>
      <el-table-column v-for="role in managerColumns" :key="role.key" :label="role.label" min-width="110" show-overflow-tooltip><template #default="{ row }">
        <span>{{ row.visibility === 'FULL' ? row.displayDetails?.[role.key] || '未指派' : '—' }}</span>
      </template></el-table-column>
      <el-table-column label="阶段 / 状态" width="110"><template #default="{ row }"><ProjectStatusTag :project="row" /></template></el-table-column>
      <el-table-column label="项目进度" min-width="140"><template #default="{ row }">
        <el-progress v-if="projectProgressDisplay(row).percentage != null" :percentage="projectProgressDisplay(row).percentage!" :stroke-width="8" />
        <span v-else class="progress-empty">{{ projectProgressDisplay(row).label }}</span>
        <div v-if="row.progressRecordedAt && row.visibility === 'FULL'" class="progress-time">记录于 {{ formatDate(row.progressRecordedAt) }}</div>
      </template></el-table-column>
    </el-table>
  </ContentWrap>
  <Dialog v-model="moveVisible" title="移动当前项目" :width="mobile ? '96%' : '560px'">
    <el-form label-position="top" @submit.prevent="submitMove">
      <el-form-item label="目标父项目" required><PmsEntitySelect v-model="parentId" :api="searchProjects" :label-field="['projectCode', 'projectName']"
        value-field="id" query-field="keyword" placeholder="按项目名称或编码搜索" :disabled="moving" @change="loadParentPath" /></el-form-item>
      <p v-if="parentPath" class="tree-help">目标路径：{{ parentPath }}</p>
      <el-form-item label="移动原因"><el-input v-model="reason" type="textarea" maxlength="500" :disabled="moving" /></el-form-item>
      <el-alert v-if="moveError" :title="moveError" type="error" :closable="false" />
    </el-form>
    <template #footer><el-button :disabled="moving" @click="moveVisible = false">取消</el-button>
      <el-button type="primary" :loading="moving" :disabled="!parentId || pathLoading || moveVersion == null" @click="submitMove">确认移动</el-button></template>
  </Dialog>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectsApi from '@/api/pms/project/projects'
import { createProjectTreeState, projectLabel, projectProgressDisplay, PROJECT_TREE_PAGE_SIZE, readProjectPath, searchProjects } from './projectTree'
import ProjectStatusTag from '@/views/pms/project/projects/ProjectStatusTag.vue'
import { formatDate } from '@/utils/formatTime'
import { createSubmissionIdempotencyState } from '@/views/pms/project/projects/submissionIdempotency'
const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ treeVersion: [value: number]; updated: [] }>()
const message = useMessage(), mobile = useMediaQuery('(max-width: 767px)')
const tree = createProjectTreeState(), rows = computed(tree.forest)
const projectLocation = (projectId: number) => ({
  path: '/pms/project-management/project-master-detail', query: { projectId: String(projectId) }
})
const managerColumns = [
  { key: 'serviceManager' as const, label: '服务经理' },
  { key: 'projectManager' as const, label: '项目经理' }
]
const visibilityLabel: Record<string, string> = { FULL: '可见项目', ROOT_SUMMARY: '根项目概要', PATH_PLACEHOLDER: '路径占位' }
const load = async () => {
  await tree.load(props.projectId, 'FULL')
}
watch(() => tree.state.version, version => { if (version) emit('treeVersion', version) })
watch(() => props.projectId, () => { void load() }, { immediate: true })
const moveVisible = ref(false), moving = ref(false), parentId = ref<number>(), moveVersion = ref<number>()
const reason = ref(''), moveError = ref(''), parentPath = ref(''), pathLoading = ref(false)
const submission = createSubmissionIdempotencyState()
let pathSequence = 0
watch(() => props.projectId, () => { moveVisible.value = false; pathSequence++ })
const loadParentPath = async () => {
  const request = ++pathSequence, id = parentId.value
  parentPath.value = ''; moveError.value = ''; pathLoading.value = Boolean(id)
  if (!id) return
  try {
    const path = await readProjectPath(id)
    if (request === pathSequence) parentPath.value = path.nodes.map(projectLabel).join(' / ')
  } catch (error: any) {
    if (request === pathSequence) { parentId.value = undefined; moveError.value = error?.message || '目标路径加载失败，请重选' }
  } finally { if (request === pathSequence) pathLoading.value = false }
}
const openMove = async () => {
  const id = props.projectId
  parentId.value = undefined; moveVersion.value = undefined; reason.value = ''; moveError.value = ''; parentPath.value = ''
  pathSequence++; pathLoading.value = false; submission.reset(); moveVisible.value = true
  try {
    const source = await ProjectsApi.queryTree(id, { queryType: 'LOCATE', pageSize: PROJECT_TREE_PAGE_SIZE })
    if (id !== props.projectId) return
    if (source.updating) throw new Error('项目树正在更新，请稍后重试')
    moveVersion.value = source.treeVersion
  } catch (error: any) { if (id === props.projectId) moveError.value = error?.message || '当前项目版本读取失败，请重新打开' }
}
const submitMove = async () => {
  if (moving.value || !parentId.value || pathLoading.value || moveVersion.value == null) return
  if (parentId.value === props.projectId) return message.warning('不能移动到项目自身')
  moving.value = true; moveError.value = ''
  const payload = { projectId: props.projectId, newParentId: parentId.value, reason: reason.value, version: moveVersion.value }
  try {
    await ProjectsApi.moveSubtree(payload.projectId, { newParentId: payload.newParentId, reason: payload.reason }, payload.version, submission.keyFor(payload))
    message.success('项目移动成功，关系历史已保留'); moveVisible.value = false; emit('updated'); await load()
  } catch (error: any) { moveError.value = error?.message || '移动未成功，请检查项目范围和版本后重试' }
  finally { moving.value = false }
}
</script>
<style scoped>
.panel-header, .panel-header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.panel-header { justify-content: space-between; padding-bottom: 8px; margin-bottom: 12px; border-bottom: 1px solid var(--el-border-color-lighter); }
.panel-title { display: inline-flex; align-items: center; gap: 6px; font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.tree-scroll-hint { margin: 0 0 10px; font-size: 12px; color: var(--el-text-color-secondary); }
.project-tree-panel :deep(.el-alert) { margin-bottom: 12px; }
.project-identity { display: inline-block; max-width: calc(100% - 24px); vertical-align: top; }
.project-identity-title { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6px 8px; line-height: 1.6; }
.project-code { margin-top: 4px; font-size: 12px; line-height: 1.5; color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
.tree-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.current-project { flex-shrink: 0; }
.project-link, .project-link:hover { color: var(--el-color-primary); text-decoration: none; font-weight: 500; overflow-wrap: anywhere; }
.project-link:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 3px; border-radius: 2px; }
.progress-empty { color: var(--el-text-color-secondary); font-size: 13px; }
.progress-time { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
@media (max-width: 767px) {
  .panel-header { align-items: flex-start; }
}
</style>
