<template>
  <ContentWrap>
    <div class="panel-heading">
      <div>
        <h3>版本化项目树</h3>
        <span>当前完整版本 v{{ treeVersion || '—' }}</span>
        <el-tag v-if="updating" type="warning" size="small">新版本构建中</el-tag>
      </div>
      <div class="actions">
        <el-button @click="moveVisible = true" v-hasPermi="['pms:project:update']">移动当前节点</el-button>
        <el-button type="primary" @click="load">刷新</el-button>
      </div>
    </div>
    <el-form class="query-bar" label-position="top">
      <el-form-item label="查询方式">
        <el-select v-model="queryType" @change="load">
          <el-option v-for="item in queryOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="queryType === 'BUSINESS_LEVEL'" label="业务层级编码">
        <el-input v-model="businessLevelCode" clearable @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="定位项目ID">
        <el-input-number v-model="anchorId" :min="1" controls-position="right" />
      </el-form-item>
    </el-form>
    <el-skeleton v-if="loading" :rows="5" animated />
    <template v-else>
      <el-tree-v2
        v-if="treeData.length && !mobile"
        :data="treeData"
        :props="{ value: 'projectId', label: 'label', children: 'children' }"
        :height="360"
        :item-size="42"
      >
        <template #default="{ data }">
          <div class="tree-row">
            <span>{{ data.label }}</span>
            <div class="tree-row-actions">
              <span class="tree-meta">{{ data.currentStage || '—' }} · {{ data.lifecycleStatus || '—' }}</span>
              <el-button link type="primary" :loading="data.loadingChildren" @click.stop="loadChildren(data)">
                {{ data.childrenLoaded ? '刷新下级' : '加载下级' }}
              </el-button>
            </div>
          </div>
        </template>
      </el-tree-v2>
      <div v-else-if="treeData.length" class="node-cards">
        <article v-for="node in mobileNodes" :key="node.projectId" class="node-card">
          <strong>{{ '—'.repeat(node.depth) }}{{ node.label }}</strong>
          <span>{{ node.currentStage || '—' }} · {{ node.lifecycleStatus || '—' }}</span>
          <el-tag size="small">{{ node.visibility }}</el-tag>
          <el-button link type="primary" :loading="node.loadingChildren" @click="loadChildren(node)">加载下级</el-button>
        </article>
      </div>
      <el-empty v-else description="当前查询无可见项目" />
      <el-button v-if="nextCursor" class="load-more" @click="loadMore">加载更多</el-button>
    </template>
  </ContentWrap>

  <Dialog v-model="moveVisible" title="移动当前项目" :width="dialogWidth">
    <el-form label-position="top">
      <el-form-item label="目标父项目ID" required>
        <el-input-number v-model="moveForm.newParentId" :min="1" class="full-control" />
      </el-form-item>
      <el-form-item label="移动原因">
        <el-input v-model="moveForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="moveVisible = false">取消</el-button>
      <el-button type="primary" :loading="moving" @click="submitMove">确认移动</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectsApi from '@/api/pms/project/projects'
import type { ProjectTreeNodeVO, ProjectTreeQueryType } from '@/api/pms/project/projects'

const props = defineProps<{ projectId: number }>()
const emit = defineEmits<{ treeVersion: [value: number] }>()
const message = useMessage()
const mobile = useMediaQuery('(max-width: 767px)')
const dialogWidth = computed(() => mobile.value ? '96%' : '520px')
const loading = ref(false)
const moving = ref(false)
const queryType = ref<ProjectTreeQueryType>('CHILDREN')
const businessLevelCode = ref('')
const anchorId = ref(props.projectId)
type TreeNode = ProjectTreeNodeVO & {
  label: string
  children: TreeNode[]
  childrenLoaded?: boolean
  loadingChildren?: boolean
}
const treeData = ref<TreeNode[]>([])
const mobileNodes = computed(() => {
  const result: (TreeNode & { depth: number })[] = []
  const append = (nodes: TreeNode[], depth: number) => nodes.forEach((node) => {
    result.push({ ...node, depth })
    append(node.children, depth + 1)
  })
  append(treeData.value, 0)
  return result
})
const treeVersion = ref(0)
const updating = ref(false)
const nextCursor = ref<string>()
const moveVisible = ref(false)
const moveForm = ref({ newParentId: undefined as number | undefined, reason: '' })
const queryOptions = [
  { label: '直接下级', value: 'CHILDREN' }, { label: '全部后代', value: 'DESCENDANTS' },
  { label: '完整上级链', value: 'ANCESTORS' }, { label: '定位路径', value: 'LOCATE' },
  { label: '业务层级', value: 'BUSINESS_LEVEL' }
] as const
const toTreeNode = (node: ProjectTreeNodeVO): TreeNode => ({
  ...node,
  label: `${node.projectName || '受限项目'} (#${node.projectId})`,
  children: []
})

const query = async (cursor?: string) => ProjectsApi.queryTree(anchorId.value, {
  queryType: queryType.value,
  businessLevelCode: queryType.value === 'BUSINESS_LEVEL' ? businessLevelCode.value : undefined,
  pageSize: 100,
  cursor
})
const load = async () => {
  loading.value = true
  try {
    const result = await query()
    treeData.value = result.items.map(toTreeNode)
    treeVersion.value = result.treeVersion
    updating.value = result.updating
    nextCursor.value = result.nextCursor
    emit('treeVersion', result.treeVersion)
  } finally { loading.value = false }
}
const loadChildren = async (node: TreeNode) => {
  node.loadingChildren = true
  try {
    const result = await ProjectsApi.queryTree(node.projectId, { queryType: 'CHILDREN', pageSize: 100 })
    node.children = result.items.map(toTreeNode)
    node.childrenLoaded = true
    treeVersion.value = result.treeVersion
    emit('treeVersion', result.treeVersion)
    treeData.value = [...treeData.value]
  } finally { node.loadingChildren = false }
}
const loadMore = async () => {
  if (!nextCursor.value) return
  const result = await query(nextCursor.value)
  treeData.value.push(...result.items.map(toTreeNode))
  nextCursor.value = result.nextCursor
}
const submitMove = async () => {
  if (!moveForm.value.newParentId || !treeVersion.value) return message.warning('请选择目标父项目')
  moving.value = true
  try {
    await ProjectsApi.moveSubtree(props.projectId, {
      newParentId: moveForm.value.newParentId,
      reason: moveForm.value.reason
    }, treeVersion.value, crypto.randomUUID())
    message.success('项目树移动成功')
    moveVisible.value = false
    await load()
  } finally { moving.value = false }
}
watch(() => props.projectId, (value) => { anchorId.value = value; load() }, { immediate: true })
</script>

<style scoped lang="scss">
.panel-heading, .actions, .tree-row, .tree-row-actions { display: flex; align-items: center; }
.panel-heading { justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.panel-heading h3 { margin: 0 8px 4px 0; display: inline-block; font-size: 15px; color: var(--el-text-color-primary); }
.panel-heading span, .tree-meta, .node-card span { color: var(--el-text-color-secondary); font-size: 12px; }
.actions { gap: 8px; }
.query-bar { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.query-bar :deep(.el-form-item) { margin-bottom: 12px; }
.query-bar :deep(.el-select), .full-control { width: 100%; }
.tree-row { width: 100%; justify-content: space-between; gap: 16px; padding-right: 12px; }
.tree-row-actions { gap: 8px; }
.node-cards { display: grid; gap: 8px; }
.node-card { display: grid; gap: 6px; padding: 12px; border: 1px solid var(--el-border-color); border-radius: var(--el-border-radius-base); }
.node-card .el-tag { justify-self: start; }
.load-more { width: 100%; margin-top: 12px; }
@media (max-width: 991px) { .query-bar { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 767px) {
  .panel-heading { align-items: stretch; flex-direction: column; }
  .actions, .query-bar { display: grid; grid-template-columns: 1fr; }
}
</style>
