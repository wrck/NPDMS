<template>
  <ContentWrap>
    <div class="tree-heading"><h3>项目树</h3><div>
      <el-button v-hasPermi="['pms:project:update']" :disabled="moving" @click="openMove">移动当前项目</el-button>
      <el-button :loading="tree.state.root.loading" @click="load">刷新</el-button>
    </div></div>
    <el-alert v-if="tree.state.root.error" :title="tree.state.root.error" type="error" :closable="false" />
    <el-alert v-if="tree.state.updating" title="项目树正在更新，当前显示上一完整版本" type="info" :closable="false" />
    <el-table v-loading="tree.state.root.loading" :data="rows" row-key="rowKey" :tree-props="{ children: 'children' }" default-expand-all :indent="24" empty-text="当前项目暂无可见关系">
      <el-table-column label="项目名称 / 编码" min-width="280" show-overflow-tooltip><template #default="{ row }">
        <template v-if="row.pagerFor != null">
          <el-button link type="primary" :loading="tree.pageFor(row.pagerFor).loading" @click="tree.children(row.pagerFor)">
            {{ tree.pageFor(row.pagerFor).error ? '加载失败，重试下级' : '加载更多下级' }}</el-button>
          <span v-if="tree.pageFor(row.pagerFor).error" role="alert">{{ tree.pageFor(row.pagerFor).error }}</span>
        </template><template v-else><span>{{ projectLabel(row) }}</span>
          <el-tag v-if="row.projectId === props.projectId" size="small" class="current-project">当前项目</el-tag>
          <el-tag v-if="row.visibility !== 'FULL'" size="small" type="info" class="current-project">{{ visibilityLabel[row.visibility] }}</el-tag>
        </template>
      </template></el-table-column>
      <el-table-column label="阶段 / 状态" width="110"><template #default="{ row }"><ProjectStatusTag v-if="row.pagerFor == null" :project="row" /></template></el-table-column>
      <el-table-column label="项目进度" min-width="150"><template #default="{ row }">
        <template v-if="row.pagerFor == null">
          <el-progress v-if="projectProgressDisplay(row).percentage != null" :percentage="projectProgressDisplay(row).percentage!" :stroke-width="8" />
          <span v-else>{{ projectProgressDisplay(row).label }}</span>
          <div v-if="row.progressRecordedAt && row.visibility === 'FULL'" class="progress-time">记录于 {{ formatDate(row.progressRecordedAt) }}</div>
        </template>
      </template></el-table-column>
      <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }">
        <el-button v-if="row.pagerFor == null && tree.canLoadChildren(row.projectId) && !tree.pageFor(row.projectId).loaded && !tree.pageFor(row.projectId).error" link type="primary"
          :loading="tree.pageFor(row.projectId).loading" @click="tree.children(row.projectId)">加载下级</el-button>
      </template></el-table-column>
    </el-table>
    <el-button v-if="tree.state.root.cursor" class="load-more" :loading="tree.state.root.loading" @click="loadMorePath">加载更多当前项目路径</el-button>
    <p class="tree-help">仅展示当前项目的上下级关系；上级仅作路径上下文，不展开其他分支。下级按需分页加载。进度为最近形成的进度事实或汇总记录，未形成记录不显示为0%。</p>
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
const visibilityLabel: Record<string, string> = { FULL: '可见项目', ROOT_SUMMARY: '根项目概要', PATH_PLACEHOLDER: '路径占位' }
const loadCurrentChildren = async () => {
  if (!tree.state.root.error && tree.state.nodes.has(props.projectId) && !tree.pageFor(props.projectId).loaded) {
    await tree.children(props.projectId)
  }
}
const load = async () => {
  const id = props.projectId
  await tree.load(id, 'LOCATE')
  if (id === props.projectId) await loadCurrentChildren()
}
const loadMorePath = async () => { await tree.more(); await loadCurrentChildren() }
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
.tree-heading { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; }
.tree-heading h3 { margin: 0; font-size: 16px; }
.tree-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.current-project { margin-left: 8px; }
.progress-time { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.load-more { margin-top: 12px; width: 100%; }
</style>
