<!-- src/views/lowcode/entity-designer/index.vue -->
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, shallowRef } from 'vue'
import { Graph } from '@antv/x6'
import { register } from '@antv/x6-vue-shape'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getEntityList,
  getEntityDesign,
  saveEntityDesign,
  generateDdl,
  publishEntity,
  deleteEntity,
  checkTableName,
  saveRelations,
  listDdlBackups,
  rollbackLastDdl,
  rollbackByBackupId,
  type LowCodeEntity,
  type LowCodeField,
  type LowCodeRelation,
  type EntityDesignDTO,
  type DdlResultDTO,
  type DdlBackup
} from '@/api/lowcode-entity'
import FieldPanel from '@/components/EntityDesigner/FieldPanel.vue'
import IndexPanel, { type LowCodeIndex } from '@/components/EntityDesigner/IndexPanel.vue'
import RelationConfigDialog from '@/components/EntityDesigner/RelationConfigDialog.vue'
import EntityNode from '@/components/EntityDesigner/EntityNode.vue'

/** 自定义实体节点 shape 名（通过 x6-vue-shape 注册） */
const ENTITY_NODE_SHAPE = 'entity-node'

defineOptions({ name: 'EntityDesignerView' })

const entityList = ref<LowCodeEntity[]>([])
const currentEntity = ref<LowCodeEntity>({
  code: '',
  name: '',
  tableName: '',
  description: '',
  bizType: '',
  status: 'DRAFT'
})
const currentFields = ref<LowCodeField[]>([])
const currentRelations = ref<LowCodeRelation[]>([])
/** 当前实体的复合索引列表（本地维护，后续可扩展到后端持久化） */
const currentIndexes = ref<LowCodeIndex[]>([])
const relationDialogVisible = ref(false)
const pendingRelation = ref<{ from: number; to: number } | null>(null)
const ddlDialogVisible = ref(false)
const ddlResult = ref<DdlResultDTO | null>(null)
const loading = ref(false)
const rollbackDialogVisible = ref(false)
const rollbackBackups = ref<DdlBackup[]>([])
const rollbackLoading = ref(false)

/** 备份类型显示文案映射 */
const backupTypeText: Record<string, string> = {
  CREATE: '建表',
  ALTER: '改表',
  DROP_COLUMN: '删列'
}

const graphRef = shallowRef<Graph | null>(null)
const canvasContainer = ref<HTMLDivElement>()

async function loadEntityList() {
  loading.value = true
  try {
    entityList.value = await getEntityList()
  } catch (e) {
    ElMessage.error('加载实体列表失败')
  } finally {
    loading.value = false
  }
}

async function selectEntity(entity: LowCodeEntity) {
  if (!entity.id) return
  const design = await getEntityDesign(entity.id)
  currentEntity.value = design.entity
  currentFields.value = design.fields
  currentRelations.value = design.relations || []
  currentIndexes.value = []
  renderGraph()
}

/** 构造实体节点数据（供 EntityNode vue-shape 渲染） */
function buildEntityNodeData(entity: LowCodeEntity, fields: LowCodeField[], selected = false) {
  return {
    entityId: entity.id,
    entityName: entity.name,
    tableName: entity.tableName,
    status: entity.status,
    fields,
    selected
  }
}

/** 计算实体节点高度（标题+表名+字段行） */
function entityNodeHeight(fields: LowCodeField[]): number {
  return 56 + Math.min(fields.length, 8) * 22
}

function renderGraph() {
  if (!graphRef.value) return
  graphRef.value.clearCells()

  // 渲染当前实体节点（单实体编辑模式）
  graphRef.value.addNode({
    shape: ENTITY_NODE_SHAPE,
    x: 120,
    y: 80,
    width: 240,
    height: entityNodeHeight(currentFields.value),
    data: buildEntityNodeData(currentEntity.value, currentFields.value, true)
  })
}

/** 将指定 entityId 的节点设为选中，其余取消选中 */
function highlightNode(entityId: number) {
  if (!graphRef.value) return
  graphRef.value.getNodes().forEach((node) => {
    const data = node.getData() || {}
    const isTarget = data.entityId === entityId
    if (data.selected !== isTarget) {
      node.setData({ ...data, selected: isTarget })
    }
  })
}

/** 画布节点点击：加载该实体到右侧编辑面板并高亮（多实体画布下生效） */
async function onNodeClick({ node }: { node: { getData: () => any } }) {
  const data = node.getData()
  if (!data || !data.entityId) return
  const entity = entityList.value.find((e) => e.id === data.entityId)
  if (!entity) return
  try {
    const design = await getEntityDesign(data.entityId)
    currentEntity.value = design.entity
    currentFields.value = design.fields
    currentRelations.value = design.relations || []
    currentIndexes.value = []
    highlightNode(data.entityId)
  } catch {
    ElMessage.error('加载实体设计失败')
  }
}

/** 加载全部实体到画布，形成多实体 ER 全景图（自动网格布局 + 关联边） */
async function loadAllEntities() {
  if (!graphRef.value) return
  loading.value = true
  try {
    const list = await getEntityList()
    const designs: Array<{ entity: LowCodeEntity; fields: LowCodeField[]; relations: LowCodeRelation[] }> = []
    for (const e of list) {
      if (!e.id) continue
      const design = await getEntityDesign(e.id)
      designs.push({ entity: design.entity, fields: design.fields, relations: design.relations || [] })
    }
    graphRef.value.clearCells()

    // 简单网格布局（按列数 = ceil(sqrt(n)) 排布）
    const cols = Math.max(1, Math.ceil(Math.sqrt(designs.length)))
    const gapX = 300
    const gapY = 280
    designs.forEach((d, i) => {
      const col = i % cols
      const row = Math.floor(i / cols)
      graphRef.value!.addNode({
        shape: ENTITY_NODE_SHAPE,
        x: col * gapX + 40,
        y: row * gapY + 40,
        width: 240,
        height: entityNodeHeight(d.fields),
        data: buildEntityNodeData(d.entity, d.fields, false)
      })
    })

    // 渲染实体间关联边（按 fromEntityId→toEntityId 去重）
    const addedEdges = new Set<string>()
    for (const d of designs) {
      for (const rel of d.relations) {
        const key = `${rel.fromEntityId}->${rel.toEntityId}`
        if (addedEdges.has(key)) continue
        addedEdges.add(key)
        const sourceNode = graphRef.value!.getNodes().find((n) => n.getData()?.entityId === rel.fromEntityId)
        const targetNode = graphRef.value!.getNodes().find((n) => n.getData()?.entityId === rel.toEntityId)
        if (sourceNode && targetNode) {
          graphRef.value!.addEdge({
            source: sourceNode,
            target: targetNode,
            attrs: { line: { stroke: '#67c23a', strokeWidth: 2, targetMarker: { name: 'classic', size: 6 } } },
            labels: rel.relationType ? [{ text: rel.relationType, fontSize: 10, fill: '#909399' }] : []
          })
        }
      }
    }

    // 适配画布视口
    graphRef.value.zoomToFit({ padding: 20, maxScale: 1 })
    ElMessage.success(`已加载 ${designs.length} 个实体`)
  } catch {
    ElMessage.error('加载全部实体失败')
  } finally {
    loading.value = false
  }
}

async function saveDesign() {
  if (!currentEntity.value.code || !currentEntity.value.tableName) {
    ElMessage.warning('请填写实体编码和物理表名')
    return
  }
  // 校验表名格式
  if (!/^pms_lc_[a-z][a-z0-9_]*$/.test(currentEntity.value.tableName)) {
    ElMessage.warning('物理表名必须以 pms_lc_ 开头，小写字母+数字+下划线')
    return
  }
  // 校验表名唯一
  const exists = await checkTableName(currentEntity.value.tableName, currentEntity.value.id)
  if (exists) {
    ElMessage.error('物理表名已存在')
    return
  }

  const design: EntityDesignDTO = {
    entity: currentEntity.value,
    fields: currentFields.value,
    relations: currentRelations.value
  }
  try {
    const saved = await saveEntityDesign(design)
    currentEntity.value = saved
    ElMessage.success('保存成功')
    await loadEntityList()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function previewDdl() {
  if (!currentEntity.value.id) {
    ElMessage.warning('请先保存实体')
    return
  }
  try {
    ddlResult.value = await generateDdl(currentEntity.value.id)
    ddlDialogVisible.value = true
  } catch (e) {
    ElMessage.error('生成 DDL 失败')
  }
}

/** 打开 DDL 回滚对话框并加载备份列表 */
async function openRollbackDialog() {
  if (!currentEntity.value.id) {
    ElMessage.warning('请先保存实体')
    return
  }
  rollbackDialogVisible.value = true
  await loadRollbackBackups()
}

async function loadRollbackBackups() {
  if (!currentEntity.value.id) return
  rollbackLoading.value = true
  try {
    rollbackBackups.value = await listDdlBackups(currentEntity.value.id)
  } catch (e) {
    ElMessage.error('加载 DDL 备份列表失败')
    rollbackBackups.value = []
  } finally {
    rollbackLoading.value = false
  }
}

/** 回滚最近一次 DDL 操作（二次确认） */
async function rollbackLast() {
  if (!currentEntity.value.id) return
  try {
    await ElMessageBox.confirm(
      '确认回滚最近一次 DDL 操作？该操作可能删除/重建物理表，请谨慎确认。',
      '回滚最近 DDL',
      { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' }
    )
    const type = await rollbackLastDdl(currentEntity.value.id)
    ElMessage.success(`已回滚最近一次 DDL（${backupTypeText[type] || type}）`)
    await loadRollbackBackups()
  } catch (e) {
    // 用户取消或回滚失败（失败时 request 拦截器已提示）
  }
}

/** 按备份记录 ID 回滚（二次确认） */
async function rollbackByBackup(backup: DdlBackup) {
  if (!backup.id) return
  const typeText = backupTypeText[backup.backupType || ''] || backup.backupType
  try {
    await ElMessageBox.confirm(
      `确认回滚该备份记录（${typeText}，表 ${backup.tableName}）？该操作可能删除/重建物理表，请谨慎确认。`,
      '按备份回滚',
      { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' }
    )
    await rollbackByBackupId(backup.id)
    ElMessage.success('回滚成功')
    await loadRollbackBackups()
  } catch (e) {
    // 用户取消或回滚失败
  }
}

async function publish() {
  if (!currentEntity.value.id) {
    ElMessage.warning('请先保存实体')
    return
  }
  try {
    const { value: changeLog } = await ElMessageBox.prompt('请输入变更说明', '发布实体', {
      confirmButtonText: '发布',
      cancelButtonText: '取消'
    })
    await publishEntity(currentEntity.value.id, changeLog || '')
    ElMessage.success('发布成功')
    await loadEntityList()
    await selectEntity(currentEntity.value)
  } catch (e) {
    // 用户取消
  }
}

async function removeEntity(entity: LowCodeEntity) {
  if (!entity.id) return
  try {
    await ElMessageBox.confirm(`确认删除实体 ${entity.name}？`, '提示', { type: 'warning' })
    await deleteEntity(entity.id)
    ElMessage.success('删除成功')
    await loadEntityList()
  } catch (e) {
    // 取消
  }
}

function newEntity() {
  currentEntity.value = {
    code: '',
    name: '',
    tableName: '',
    description: '',
    bizType: '',
    status: 'DRAFT'
  }
  currentFields.value = []
  currentRelations.value = []
  currentIndexes.value = []
  if (graphRef.value) graphRef.value.clearCells()
}

function onEdgeConnected({ source, target }: { source: { cell: any }; target: { cell: any } }) {
  const fromEntityId = source.cell.getData()?.entityId
  const toEntityId = target.cell.getData()?.entityId
  if (fromEntityId && toEntityId) {
    pendingRelation.value = { from: fromEntityId, to: toEntityId }
    relationDialogVisible.value = true
  }
}

async function onRelationConfirm(relation: LowCodeRelation) {
  if (!pendingRelation.value) return
  try {
    await saveRelations(pendingRelation.value.from, [relation])
    ElMessage.success('关联已保存')
    const entity = entityList.value.find(x => x.id === pendingRelation.value!.from)
    if (entity) await selectEntity(entity)
  } catch (e) {
    ElMessage.error('保存关联失败')
  }
}

function onEntityDragStart(e: DragEvent, entity: LowCodeEntity) {
  e.dataTransfer?.setData('entityId', String(entity.id))
}

async function onCanvasDrop(e: DragEvent) {
  e.preventDefault()
  const entityId = Number(e.dataTransfer?.getData('entityId'))
  if (!entityId) return
  const entity = entityList.value.find(x => x.id === entityId)
  if (!entity?.id) return
  try {
    const design = await getEntityDesign(entity.id)
    const rect = canvasContainer.value?.getBoundingClientRect()
    const x = (e.clientX - (rect?.left || 0)) - 120
    const y = (e.clientY - (rect?.top || 0)) - 50
    graphRef.value?.addNode({
      shape: ENTITY_NODE_SHAPE,
      x: Math.max(0, x),
      y: Math.max(0, y),
      width: 240,
      height: entityNodeHeight(design.fields),
      data: buildEntityNodeData(entity, design.fields, false)
    })
  } catch (err) {
    ElMessage.error('加载实体设计失败')
  }
}

function initGraph() {
  if (!canvasContainer.value) return
  graphRef.value = new Graph({
    container: canvasContainer.value,
    background: { color: '#f5f5f5' },
    grid: { visible: true, size: 10 },
    interacting: { nodeMovable: true, edgeMovable: true },
    mousewheel: { enabled: true, modifiers: ['ctrl'] },
    connecting: {
      allowBlank: false,
      allowLoop: false,
      allowMulti: true,
      router: 'orth',
      connector: 'rounded',
      createEdge() {
        return this.createEdge({
          shape: 'edge',
          attrs: {
            line: { stroke: '#409eff', strokeWidth: 2 }
          }
        })
      }
    }
  })

  graphRef.value.on('edge:connected', ({ edge }) => {
    const sourceCell = edge.getSourceCell()
    const targetCell = edge.getTargetCell()
    if (sourceCell && targetCell) {
      onEdgeConnected({ source: { cell: sourceCell }, target: { cell: targetCell } })
    }
  })

  // 节点点击：加载该实体到右侧编辑面板并高亮（多实体画布场景）
  graphRef.value.on('node:click', ({ node }) => {
    onNodeClick({ node })
  })
}

onMounted(() => {
  // 注册 EntityNode 自定义 Vue 节点（x6-vue-shape）
  register({
    shape: ENTITY_NODE_SHAPE,
    width: 240,
    height: 120,
    component: EntityNode
  })
  initGraph()
  loadEntityList()
})

onBeforeUnmount(() => {
  graphRef.value?.dispose()
})
</script>

<template>
  <div class="entity-designer" v-loading="loading">
    <!-- 左侧：实体列表 -->
    <div class="entity-list-panel">
      <div class="panel-header">
        <span>实体列表</span>
        <el-button type="primary" size="small" @click="newEntity">新建</el-button>
      </div>
      <el-scrollbar>
        <div
          v-for="entity in entityList"
          :key="entity.id"
          class="entity-item"
          :class="{ active: entity.id === currentEntity.id }"
          draggable="true"
          @dragstart="onEntityDragStart($event, entity)"
          @click="selectEntity(entity)"
        >
          <div class="entity-item-name">{{ entity.name }}</div>
          <div class="entity-item-code">{{ entity.code }}</div>
          <el-tag size="small" :type="entity.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ entity.status }}
          </el-tag>
          <el-button
            type="danger"
            size="small"
            link
            @click.stop="removeEntity(entity)"
          >删除</el-button>
        </div>
      </el-scrollbar>
    </div>

    <!-- 中间：X6 画布 -->
    <div class="canvas-panel">
      <div class="toolbar">
        <el-button type="primary" size="small" @click="saveDesign">保存</el-button>
        <el-button size="small" @click="previewDdl">DDL 预览</el-button>
        <el-button type="success" size="small" @click="publish">发布</el-button>
        <el-button type="warning" size="small" @click="loadAllEntities">加载全部实体</el-button>
        <el-button type="danger" size="small" @click="openRollbackDialog">回滚 DDL</el-button>
      </div>
      <div ref="canvasContainer" class="canvas-container" @drop="onCanvasDrop" @dragover.prevent></div>
    </div>

    <!-- 右侧：属性面板 -->
    <div class="property-panel">
      <FieldPanel
        :entity="currentEntity"
        :fields="currentFields"
        :indexes="currentIndexes"
        @update:entity="currentEntity = $event"
        @update:fields="currentFields = $event"
        @update:indexes="currentIndexes = $event"
      />
    </div>

    <!-- DDL 预览对话框 -->
    <el-dialog v-model="ddlDialogVisible" title="DDL 预览" width="700px">
      <div v-if="ddlResult">
        <el-alert
          v-if="ddlResult.hasJunctionTable"
          type="info"
          title="检测到多对多关联，已自动生成中间表 DDL"
          :closable="false"
          style="margin-bottom: 10px"
        />
        <pre v-for="(sql, i) in ddlResult.ddlStatements" :key="i" class="ddl-block">{{ sql }};</pre>
      </div>
    </el-dialog>

    <!-- DDL 回滚对话框 -->
    <el-dialog v-model="rollbackDialogVisible" title="DDL 回滚" width="720px">
      <el-alert
        type="warning"
        title="回滚会直接修改物理表结构（可能 DROP/重建表），属于高危操作，请谨慎确认。"
        :closable="false"
        style="margin-bottom: 12px"
      />
      <div style="margin-bottom: 10px">
        <el-button type="danger" size="small" @click="rollbackLast">回滚最近一次</el-button>
        <el-button size="small" @click="loadRollbackBackups">刷新</el-button>
      </div>
      <el-table v-loading="rollbackLoading" :data="rollbackBackups" border size="small" empty-text="暂无 DDL 备份记录">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.backupType === 'CREATE' ? 'success' : (row.backupType === 'ALTER' ? 'warning' : 'danger')">
              {{ backupTypeText[row.backupType] || row.backupType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tableName" label="物理表" width="180" />
        <el-table-column prop="createTime" label="备份时间" width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" size="small" link @click="rollbackByBackup(row)">回滚此备份</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <RelationConfigDialog
      v-model="relationDialogVisible"
      :from-entity-id="pendingRelation?.from || 0"
      :to-entity-id="pendingRelation?.to || 0"
      @confirm="onRelationConfirm"
    />
  </div>
</template>

<style scoped lang="scss">
.entity-designer {
  display: flex;
  height: calc(100vh - 84px);
  gap: 1px;
  background: #dcdfe6;
}

.entity-list-panel {
  width: 220px;
  background: #fff;
  display: flex;
  flex-direction: column;
  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px;
    border-bottom: 1px solid #ebeef5;
    font-weight: 600;
  }
  .entity-item {
    padding: 8px 10px;
    border-bottom: 1px solid #f5f5f5;
    cursor: pointer;
    &:hover { background: #f5f7fa; }
    &.active { background: #ecf5ff; border-left: 3px solid #409eff; }
    .entity-item-name { font-size: 13px; font-weight: 500; }
    .entity-item-code { font-size: 11px; color: #909399; margin: 2px 0; }
  }
}

.canvas-panel {
  flex: 1;
  background: #fff;
  display: flex;
  flex-direction: column;
  .toolbar {
    padding: 8px 10px;
    border-bottom: 1px solid #ebeef5;
    display: flex;
    gap: 8px;
  }
  .canvas-container {
    flex: 1;
    overflow: hidden;
  }
}

.property-panel {
  width: 380px;
  background: #fff;
  overflow-y: auto;
}

.ddl-block {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  margin-bottom: 10px;
}
</style>
