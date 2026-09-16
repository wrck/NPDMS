<script setup lang="ts">
/**
 * 低代码流程设计器主视图。
 *
 * <p>三入口：
 * <ul>
 *   <li>「设计新流程」—— 全屏 bpmn-js 设计器（左调色板 + 中画布 + 右低代码属性面板），
 *       支持导入/导出 XML、导出 SVG、部署到 Flowable、保存为流程绑定。</li>
 *   <li>「新建流程绑定」—— 选择已部署流程定义 + 节点表单绑定可视化表格（替代 JSON 文本框）。</li>
 *   <li>「预览」—— 只读 bpmn-js Viewer，支持流程实例当前节点高亮。</li>
 * </ul>
 * 借鉴 Appian Process Modeler / Camunda Modeler 的三栏布局。</p>
 */
import { computed, onMounted, ref, shallowRef } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import type BpmnModeler from 'bpmn-js/lib/Modeler'
import type { ModdleElement } from 'bpmn-js/lib/model/Types'
import BpmnCanvas from '@/components/ProcessDesigner/BpmnCanvas.vue'
import BpmnPalette from '@/components/ProcessDesigner/BpmnPalette.vue'
import LowCodeBpmnProperties from '@/components/ProcessDesigner/LowCodeBpmnProperties.vue'
import ProcessPreview from '@/components/ProcessDesigner/ProcessPreview.vue'
import NodeFormBindingPanel from '@/components/ProcessDesigner/NodeFormBindingPanel.vue'
import { exportSvg } from '@/components/ProcessDesigner/bpmn-helper'
import {
  deployBpmnXml,
  getProcessBindings,
  getProcessDefinitionBpmnXml,
  getProcessDefinitions,
  getProcessDiagram,
  getProcessInstanceActivityIds,
  getProcessInstances,
  saveProcessBinding,
  terminateInstance,
  type LowCodeProcessBinding,
  type ProcessInstance
} from '@/api/lowcode-process'

defineOptions({ name: 'ProcessDesignerView' })

const route = useRoute()

/** 当前激活的标签页：bindings=流程绑定 / instances=流程实例 */
const activeTab = ref<'bindings' | 'instances'>('bindings')

/** 已部署流程定义下拉项 */
interface ProcessDefinitionOption {
  id: string
  name?: string
  key: string
  version?: number
}

// ===== 列表 =====
const list = ref<LowCodeProcessBinding[]>([])

async function load() {
  list.value = await getProcessBindings()
}

// ===== 流程实例列表 =====
const instances = ref<ProcessInstance[]>([])
const instanceStatusFilter = ref<string>('running')
const instanceLoading = ref(false)

async function loadInstances() {
  instanceLoading.value = true
  try {
    instances.value = await getProcessInstances({
      status: instanceStatusFilter.value || undefined
    })
  } catch (e) {
    instances.value = []
    ElMessage.error('加载流程实例失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    instanceLoading.value = false
  }
}

/** 切换 Tab 时按需加载数据 */
function onTabChange(name: string | number) {
  if (name === 'instances' && instances.value.length === 0) {
    loadInstances()
  }
}

/** 实例状态 → tag type */
function instanceStatusTagType(status?: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === '运行中') return 'success'
  if (status === '挂起') return 'warning'
  if (status === '已完成') return 'info'
  if (status === '已终止') return 'danger'
  return 'info'
}

// ===== 流程图查看对话框 =====
const diagramDialogVisible = ref(false)
const diagramUrl = ref<string>('')
const diagramInstance = ref<ProcessInstance | null>(null)
const diagramLoading = ref(false)

async function viewDiagram(row: ProcessInstance) {
  diagramInstance.value = row
  diagramUrl.value = ''
  diagramDialogVisible.value = true
  diagramLoading.value = true
  try {
    const blob = await getProcessDiagram(row.id)
    // 释放上一次的 ObjectURL，避免内存泄漏
    if (diagramUrl.value) URL.revokeObjectURL(diagramUrl.value)
    diagramUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    ElMessage.error('加载流程图失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    diagramLoading.value = false
  }
}

function closeDiagram() {
  if (diagramUrl.value) {
    URL.revokeObjectURL(diagramUrl.value)
    diagramUrl.value = ''
  }
  diagramInstance.value = null
}

/** 终止流程实例（带确认） */
async function terminateRow(row: ProcessInstance) {
  try {
    await ElMessageBox.confirm(
      `确认终止流程实例「${row.processDefinitionName || row.processDefinitionKey}」吗？终止后不可恢复。`,
      '终止确认',
      { type: 'warning', confirmButtonText: '终止', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await terminateInstance(row.id, '手动终止')
    ElMessage.success('已终止')
    await loadInstances()
  } catch (e) {
    ElMessage.error('终止失败：' + (e instanceof Error ? e.message : String(e)))
  }
}

// ===== 流程绑定对话框 =====
const bindingDialogVisible = ref(false)
const current = ref<LowCodeProcessBinding | null>(null)
const processDefinitions = ref<ProcessDefinitionOption[]>([])

async function loadDefinitions() {
  try {
    const res = (await getProcessDefinitions(1, 200)) as { records?: ProcessDefinitionOption[] }
    processDefinitions.value = res.records || []
  } catch {
    processDefinitions.value = []
  }
}

function openNewBinding() {
  current.value = {
    processDefinitionKey: '',
    processDefinitionName: '',
    nodeFormBindings: '[]',
    status: 'ACTIVE'
  }
  loadDefinitions()
  bindingDialogVisible.value = true
}

function openEditBinding(row: LowCodeProcessBinding) {
  current.value = { ...row }
  loadDefinitions()
  bindingDialogVisible.value = true
}

function onDefinitionChange(key: string) {
  if (!current.value) return
  current.value.processDefinitionKey = key
  const def = processDefinitions.value.find((d) => d.key === key)
  current.value.processDefinitionName = def?.name || key
}

async function saveBinding() {
  if (!current.value) return
  if (!current.value.processDefinitionKey) {
    ElMessage.warning('请选择流程定义')
    return
  }
  await saveProcessBinding(current.value)
  ElMessage.success('保存成功')
  bindingDialogVisible.value = false
  await load()
}

// ===== BPMN 设计器对话框 =====
const designerDialogVisible = ref(false)
const dialogOpened = ref(false)
const designerName = ref('')
const bpmnCanvasRef = ref<InstanceType<typeof BpmnCanvas> | null>(null)
const modelerRef = shallowRef<BpmnModeler | null>(null)
const selectedElement = shallowRef<ModdleElement | null>(null)
const lastDeployedKey = ref('')
const fileInputRef = ref<HTMLInputElement | null>(null)

function openDesigner() {
  designerName.value = ''
  lastDeployedKey.value = ''
  selectedElement.value = null
  modelerRef.value = null
  dialogOpened.value = false
  designerDialogVisible.value = true
}

function onDialogOpened() {
  dialogOpened.value = true
}

function onDialogClosed() {
  dialogOpened.value = false
  modelerRef.value = null
  selectedElement.value = null
}

function onCanvasReady(modeler: BpmnModeler) {
  modelerRef.value = modeler
}

function onSelectionChanged(element: ModdleElement | null) {
  selectedElement.value = element
}

/** 从当前画布根元素读取流程定义 key（BPMN process id） */
function getProcessKey(): string {
  const modeler = modelerRef.value
  if (!modeler) return ''
  const canvas = modeler.get<{ getRootElement: () => ModdleElement }>('canvas')
  const root = canvas.getRootElement()
  const bo = (root.businessObject as ModdleElement) || root
  return String(bo.id || '')
}

/** 部署当前画布 BPMN XML 到 Flowable，返回流程定义 key */
async function deploy(): Promise<string> {
  const xml = await bpmnCanvasRef.value?.exportXml()
  if (!xml) {
    ElMessage.warning('画布为空，无法部署')
    return ''
  }
  const key = getProcessKey()
  const name = designerName.value || key || 'process'
  const res = await deployBpmnXml(xml, name)
  lastDeployedKey.value = key
  ElMessage.success(`部署成功：${res.name || name}`)
  return key
}

/** 仅部署 */
async function onDeploy() {
  await deploy()
}

/** 部署（若未部署）并跳转到流程绑定对话框 */
async function onSaveFromDesigner() {
  const key = lastDeployedKey.value || (await deploy())
  if (!key) return
  current.value = {
    processDefinitionKey: key,
    processDefinitionName: designerName.value || key,
    nodeFormBindings: '[]',
    status: 'ACTIVE'
  }
  designerDialogVisible.value = false
  await loadDefinitions()
  bindingDialogVisible.value = true
}

/** 导出 BPMN XML 并触发下载 */
async function onExportXml() {
  const xml = await bpmnCanvasRef.value?.exportXml()
  if (!xml) return
  triggerDownload(xml, `${designerName.value || getProcessKey() || 'process'}.bpmn20.xml`, 'application/xml')
}

/** 导出 SVG 并触发下载 */
async function onExportSvg() {
  const modeler = modelerRef.value
  if (!modeler) return
  const svg = await exportSvg(modeler)
  triggerDownload(svg, `${designerName.value || getProcessKey() || 'process'}.svg`, 'image/svg+xml')
}

/** 触发隐藏文件选择器导入 BPMN XML */
function onImportClick() {
  fileInputRef.value?.click()
}

async function onImportFile(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  const text = await file.text()
  try {
    await bpmnCanvasRef.value?.importXml(text)
    ElMessage.success('导入成功')
  } catch (err) {
    ElMessage.error('导入失败：' + (err instanceof Error ? err.message : String(err)))
  } finally {
    target.value = ''
  }
}

/** 通用文件下载 */
function triggerDownload(content: string, filename: string, mime: string) {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

// ===== 流程预览对话框 =====
const previewDialogVisible = ref(false)
const previewBinding = ref<LowCodeProcessBinding | null>(null)
const previewXml = ref('')
const previewActivityIds = ref<string[]>([])
const previewLoading = ref(false)

async function openPreview(row: LowCodeProcessBinding) {
  previewBinding.value = row
  previewXml.value = ''
  previewActivityIds.value = []
  previewDialogVisible.value = true
  previewLoading.value = true
  try {
    previewXml.value = await getProcessDefinitionBpmnXml(row.processDefinitionKey)
  } catch (e) {
    ElMessage.error('加载流程图失败：' + (e instanceof Error ? e.message : String(e)))
  } finally {
    previewLoading.value = false
  }
  // 若路由携带 processInstanceId，则高亮当前活动节点
  const pid = route.query.processInstanceId
  if (pid && typeof pid === 'string') {
    try {
      previewActivityIds.value = await getProcessInstanceActivityIds(pid)
    } catch {
      previewActivityIds.value = []
    }
  }
}

const previewKey = computed(() => previewBinding.value?.id ?? previewBinding.value?.processDefinitionKey ?? 'none')

onMounted(load)
</script>

<template>
  <div style="padding: 16px">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- 流程绑定 Tab -->
      <el-tab-pane label="流程绑定" name="bindings">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>流程设计器</span>
              <div>
                <el-button type="primary" @click="openDesigner">设计新流程</el-button>
                <el-button @click="openNewBinding">新建流程绑定</el-button>
              </div>
            </div>
          </template>
          <el-table :data="list">
            <el-table-column label="流程定义 Key" prop="processDefinitionKey" min-width="160" show-overflow-tooltip />
            <el-table-column label="流程名称" prop="processDefinitionName" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" prop="status" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button size="small" @click="openEditBinding(row)">编辑绑定</el-button>
                <el-button size="small" type="primary" link @click="openPreview(row)">预览</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 流程实例 Tab -->
      <el-tab-pane label="流程实例" name="instances">
        <el-card shadow="never">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>流程实例</span>
              <div style="display: flex; align-items: center; gap: 8px">
                <el-select
                  v-model="instanceStatusFilter"
                  placeholder="状态"
                  style="width: 140px"
                  @change="loadInstances"
                >
                  <el-option label="运行中" value="running" />
                  <el-option label="已完成" value="completed" />
                  <el-option label="全部" value="all" />
                </el-select>
                <el-button @click="loadInstances">刷新</el-button>
              </div>
            </div>
          </template>
          <el-table v-loading="instanceLoading" :data="instances">
            <el-table-column label="实例 ID" prop="id" min-width="160" show-overflow-tooltip />
            <el-table-column
              label="流程定义"
              prop="processDefinitionName"
              min-width="160"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.processDefinitionName || row.processDefinitionKey }}
              </template>
            </el-table-column>
            <el-table-column label="发起人" prop="startUserId" width="120" show-overflow-tooltip />
            <el-table-column label="开始时间" prop="startTime" width="170" />
            <el-table-column label="当前任务" prop="currentTaskName" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.currentTaskName || '—' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" prop="status" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="instanceStatusTagType(row.status)">{{ row.status || '—' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" align="center">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="viewDiagram(row)">查看流程图</el-button>
                <el-button
                  size="small"
                  type="danger"
                  link
                  :disabled="row.status !== '运行中' && row.status !== '挂起'"
                  @click="terminateRow(row)"
                >终止</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 流程绑定对话框 -->
    <el-dialog v-model="bindingDialogVisible" title="流程绑定编辑" width="900px" :close-on-click-modal="false">
      <el-form v-if="current" label-width="120px">
        <el-form-item label="流程定义">
          <el-select
            :model-value="current.processDefinitionKey"
            placeholder="选择已部署的流程定义"
            filterable
            style="width: 100%"
            @change="onDefinitionChange"
          >
            <el-option
              v-for="def in processDefinitions"
              :key="def.id"
              :label="`${def.name || def.key} (v${def.version ?? 1})`"
              :value="def.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="流程名称">
          <el-input v-model="current.processDefinitionName" placeholder="留空则取流程定义名称" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="current.status" style="width: 200px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="节点表单绑定">
          <NodeFormBindingPanel
            v-model="current.nodeFormBindings"
            :process-definition-key="current.processDefinitionKey"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindingDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveBinding">保存</el-button>
      </template>
    </el-dialog>

    <!-- BPMN 设计器全屏对话框 -->
    <el-dialog
      v-model="designerDialogVisible"
      title="流程设计器"
      fullscreen
      :close-on-click-modal="false"
      class="designer-dialog"
      @opened="onDialogOpened"
      @closed="onDialogClosed"
    >
      <div v-if="dialogOpened" class="designer-layout">
        <div class="designer-toolbar">
          <span class="toolbar-label">流程名称：</span>
          <el-input
            v-model="designerName"
            placeholder="部署名称（如：项目立项审批）"
            style="width: 240px"
            size="small"
          />
          <el-button size="small" @click="onImportClick">导入 XML</el-button>
          <el-button size="small" @click="onExportXml">导出 XML</el-button>
          <el-button size="small" @click="onExportSvg">导出 SVG</el-button>
          <el-button size="small" type="warning" @click="onDeploy">部署到 Flowable</el-button>
          <el-button size="small" type="primary" @click="onSaveFromDesigner">保存</el-button>
          <input
            ref="fileInputRef"
            type="file"
            accept=".xml,.bpmn,.bpmn20.xml"
            style="display: none"
            @change="onImportFile"
          />
          <span class="toolbar-tip">提示：选中根流程可设置流程 Key（部署后即流程定义 key），选中 UserTask 可绑定表单/审批人。</span>
        </div>
        <div class="designer-body">
          <div class="designer-palette">
            <BpmnPalette :modeler="modelerRef" />
          </div>
          <div class="designer-canvas">
            <BpmnCanvas
              ref="bpmnCanvasRef"
              @ready="onCanvasReady"
              @selection-changed="onSelectionChanged"
              @import-error="(m: string) => ElMessage.error('导入失败：' + m)"
            />
          </div>
          <div class="designer-props">
            <LowCodeBpmnProperties :element="selectedElement" :modeler="modelerRef" />
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 流程预览对话框 -->
    <el-dialog v-model="previewDialogVisible" title="流程预览" fullscreen class="preview-dialog">
      <div v-loading="previewLoading" class="preview-body">
        <ProcessPreview
          v-if="previewXml"
          :key="previewKey"
          :bpmn-xml="previewXml"
          :current-activity-ids="previewActivityIds"
        />
        <el-empty v-else-if="!previewLoading" description="无流程图数据" />
      </div>
    </el-dialog>

    <!-- 流程图查看对话框（实例运行图） -->
    <el-dialog
      v-model="diagramDialogVisible"
      title="流程实例图"
      width="900px"
      @close="closeDiagram"
    >
      <div v-if="diagramInstance" class="diagram-meta">
        <span><b>实例 ID：</b>{{ diagramInstance.id }}</span>
        <span><b>流程：</b>{{ diagramInstance.processDefinitionName || diagramInstance.processDefinitionKey }}</span>
        <span><b>当前任务：</b>{{ diagramInstance.currentTaskName || '—' }}</span>
      </div>
      <div v-loading="diagramLoading" class="diagram-image-wrap">
        <img v-if="diagramUrl" :src="diagramUrl" alt="流程图" class="diagram-image" />
        <el-empty v-else-if="!diagramLoading" description="无流程图数据" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
/* 全屏设计器对话框：去除 body 内边距，使三栏布局占满 */
:deep(.designer-dialog .el-dialog__body) {
  padding: 0;
}

.designer-layout {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 55px);
  background: #f5f7fa;
}

.designer-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  flex-shrink: 0;

  .toolbar-label {
    font-size: 13px;
    color: #606266;
    white-space: nowrap;
  }

  .toolbar-tip {
    margin-left: auto;
    font-size: 12px;
    color: #909399;
  }
}

.designer-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.designer-palette {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #ebeef5;
  overflow: auto;
  background: #fafafa;
}

.designer-canvas {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.designer-props {
  width: 340px;
  flex-shrink: 0;
  border-left: 1px solid #ebeef5;
  overflow: auto;
  background: #fff;
}

:deep(.preview-dialog .el-dialog__body) {
  padding: 0;
}

.preview-body {
  width: 100%;
  height: calc(100vh - 55px);
  background: #fff;
}

/* 流程实例图对话框 */
.diagram-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 12px;
  font-size: 13px;
}

.diagram-image-wrap {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
}

.diagram-image {
  max-width: 100%;
  height: auto;
}
</style>
