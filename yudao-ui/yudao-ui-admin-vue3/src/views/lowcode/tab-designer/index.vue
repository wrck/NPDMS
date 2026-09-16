<script setup lang="ts">
/**
 * 低代码标签页设计器。
 *
 * <p>三栏布局：</p>
 * <ul>
 *   <li>左侧：组件库面板（Tab Item 组件，按 pageType 分组）</li>
 *   <li>中间：画布（Tab 顶层配置 + Tab 列表，可拖拽排序，
 *       每个卡片显示 title/name/pageCode/pageType）</li>
 *   <li>右侧：属性面板（选中 Tab 的 title/name/icon/pageCode/pageType/pageUrl/lazy/disabled/visible/props）</li>
 * </ul>
 *
 * <p>顶部操作栏：保存草稿 / 发布 / 归档 / 导入 / 导出 / 预览 / 重置，以及元信息编辑。</p>
 *
 * <p>使用原生 HTML5 拖拽 API（draggable + dragstart/dragover/drop）实现，
 * 支持组件库 → 画布拖拽、画布内 Tab 排序拖拽两种交互。Tab ID 自动生成
 * （tab_N），点击"预览"打开 LowCodeTabRenderer 弹窗。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  TabPageType,
  TabsType,
  TabPosition,
  archiveTab,
  createTab,
  deleteTab,
  exportTab,
  getTab,
  importTab,
  normalizeTabConfig,
  publishTab,
  updateTab,
  type LowCodeTabConfig,
  type LowCodeTabQuery,
  type TabConfig,
  type TabItemConfig
} from '@/api/lowcode'
import LowCodeTabRenderer from '@/components/LowCodeTabRenderer/index.vue'
import { useUndoRedo } from '@/composables/useUndoRedo'

const route = useRoute()
const router = useRouter()

// ===================== 组件库定义 =====================

interface ComponentDef {
  /** 组件 type，与 TabPageType 对应 */
  type: string
  /** 显示标签 */
  label: string
  /** Element Plus 图标名 */
  icon: string
  /** 默认属性 */
  defaultProps?: Record<string, unknown>
}

/** 组件库分组 */
const componentGroups = ref<Array<{ title: string; items: ComponentDef[] }>>([
  {
    title: '标签页组件（按引用页面类型）',
    items: [
      {
        type: TabPageType.FORM,
        label: '表单 Tab',
        icon: 'Document',
        defaultProps: { pageType: TabPageType.FORM, lazy: true }
      },
      {
        type: TabPageType.LIST,
        label: '列表 Tab',
        icon: 'List',
        defaultProps: { pageType: TabPageType.LIST, lazy: true }
      },
      {
        type: TabPageType.RELATED_PAGE,
        label: '关联页 Tab',
        icon: 'Share',
        defaultProps: { pageType: TabPageType.RELATED_PAGE, lazy: true }
      },
      {
        type: TabPageType.CUSTOM,
        label: '自定义 Tab',
        icon: 'Setting',
        defaultProps: { pageType: TabPageType.CUSTOM, lazy: true, pageUrl: '' }
      }
    ]
  }
])

// ===================== 元信息 + 配置状态 =====================

/** 标签页元信息（对应 LowCodeTabConfig 的非 tabConfig 字段） */
const metaForm = reactive<LowCodeTabConfig>({
  code: '',
  name: '',
  description: '',
  tabConfig: '',
  status: 'DRAFT',
  bizType: '',
  version: 1
})

/** 设计器内部维护的 TabConfig 对象 */
const tabConfig = reactive<TabConfig>({
  title: '',
  description: '',
  type: TabsType.BORDER_CARD,
  tabPosition: TabPosition.TOP,
  closable: false,
  addable: false,
  editable: false,
  tabs: []
})

/** 当前选中项 id（tab 的 id） */
const selectedId = ref<string>('')

/** 各类型 ID 计数器 */
let tabSeq = 0

/** 元信息表单 ref */
const metaFormRef = ref<FormInstance>()
const metaRules: FormRules = {
  code: [{ required: true, message: '请输入标签页编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入标签页名称', trigger: 'blur' }]
}

const loading = ref(false)
const previewVisible = ref(false)

// ===================== 选中项计算 =====================

/** 当前选中的标签项 */
const selectedTab = computed<TabItemConfig | null>(() =>
  tabConfig.tabs.find((t) => t.id === selectedId.value) || null
)

// ===================== 创建新项 =====================

/** 生成标签项对象 */
function createTabItem(comp: ComponentDef): TabItemConfig {
  tabSeq++
  const idx = tabSeq
  return {
    id: `tab_${idx}`,
    title: `标签${idx}`,
    name: `tab${idx}`,
    lazy: true,
    disabled: false,
    pageType: comp.type,
    ...(comp.defaultProps || {})
  }
}

// ===================== 添加 / 删除 / 复制 / 移动 =====================

/** 添加项（点击组件库或拖拽放置） */
function addComponent(comp: ComponentDef) {
  const tab = createTabItem(comp)
  tabConfig.tabs.push(tab)
  selectedId.value = tab.id
}

/** 删除项 */
function removeItem(id: string) {
  tabConfig.tabs = tabConfig.tabs.filter((t) => t.id !== id)
  if (selectedId.value === id) selectedId.value = ''
}

/** 复制项 */
function duplicateItem(id: string) {
  const tab = tabConfig.tabs.find((t) => t.id === id)
  if (!tab) return
  tabSeq++
  const copy: TabItemConfig = JSON.parse(JSON.stringify(tab))
  copy.id = `tab_${tabSeq}`
  copy.name = `${tab.name}_copy`
  copy.title = `${tab.title}_副本`
  const idx = tabConfig.tabs.findIndex((t) => t.id === id)
  tabConfig.tabs.splice(idx + 1, 0, copy)
  selectedId.value = copy.id
}

/** 上下移动 */
function moveItem(id: string, direction: -1 | 1) {
  const arr = tabConfig.tabs
  const idx = arr.findIndex((t) => t.id === id)
  if (idx < 0) return
  const newIdx = idx + direction
  if (newIdx < 0 || newIdx >= arr.length) return
  const tmp = arr[idx]
  arr[idx] = arr[newIdx]
  arr[newIdx] = tmp
}

/** 选中项 */
function selectItem(id: string) {
  selectedId.value = id
}

// ===================== 拖拽 =====================

let dragType = ''
let dragItemId = ''

/** 组件库 dragstart */
function onCompDragStart(event: DragEvent, comp: ComponentDef) {
  dragType = comp.type
  dragItemId = ''
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'copy'
    event.dataTransfer.setData('text/plain', `comp:${comp.type}`)
  }
}

/** 画布项 dragstart（用于排序） */
function onItemDragStart(event: DragEvent, id: string) {
  dragItemId = id
  dragType = ''
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', `item:${id}`)
  }
}

/** 画布 dragover */
function onCanvasDragOver(event: DragEvent) {
  if (event.dataTransfer) event.dataTransfer.dropEffect = dragType ? 'copy' : 'move'
  event.preventDefault()
}

/** 画布 drop：组件库 → 添加 */
function onCanvasDrop(event: DragEvent) {
  event.preventDefault()
  const raw = event.dataTransfer?.getData('text/plain') || ''
  if (raw.startsWith('comp:')) {
    const type = raw.slice(5)
    for (const g of componentGroups.value) {
      const comp = g.items.find((c) => c.type === type)
      if (comp) {
        addComponent(comp)
        return
      }
    }
  }
}

/** 项 drop：将 dragItemId 移动到 targetId 之前 */
function onItemDrop(event: DragEvent, targetId: string) {
  event.preventDefault()
  if (!dragItemId || dragItemId === targetId) return
  const arr = tabConfig.tabs
  const fromIdx = arr.findIndex((t) => t.id === dragItemId)
  const toIdx = arr.findIndex((t) => t.id === targetId)
  if (fromIdx < 0 || toIdx < 0) return
  const [moved] = arr.splice(fromIdx, 1)
  arr.splice(toIdx, 0, moved)
  dragItemId = ''
}

// ===================== props 编辑（Tab 项 props） =====================

function addTabProp(tab: TabItemConfig) {
  if (!tab.props) tab.props = {}
  const key = `prop_${Object.keys(tab.props).length + 1}`
  tab.props[key] = ''
}

function removeTabProp(tab: TabItemConfig, key: string) {
  if (tab.props) {
    delete tab.props[key]
  }
}

// ===================== 元信息 & 配置序列化 =====================

function syncTabConfigToStr() {
  metaForm.tabConfig = JSON.stringify(tabConfig, null, 2)
}

function parseTabConfigFromStr() {
  try {
    if (!metaForm.tabConfig) {
      tabConfig.tabs = []
      return
    }
    const parsed = normalizeTabConfig(JSON.parse(metaForm.tabConfig) as TabConfig)
    tabConfig.title = parsed.title ?? ''
    tabConfig.description = parsed.description ?? ''
    tabConfig.type = parsed.type ?? TabsType.BORDER_CARD
    tabConfig.tabPosition = parsed.tabPosition ?? TabPosition.TOP
    tabConfig.closable = parsed.closable ?? false
    tabConfig.addable = parsed.addable ?? false
    tabConfig.editable = parsed.editable ?? false
    tabConfig.tabs = parsed.tabs || []
    // 重置计数器
    tabSeq = 0
    tabConfig.tabs.forEach((t) => {
      const m = /tab_(\d+)/.exec(t.id)
      if (m) {
        const n = parseInt(m[1], 10)
        if (n > tabSeq) tabSeq = n
      }
    })
  } catch (e) {
    ElMessage.error('标签页配置 JSON 解析失败：' + (e as Error).message)
  }
}

// ===================== 撤销/重做 =====================

/**
 * 撤销/重做历史栈：对整个 tabConfig 做 JSON 快照。
 *
 * <p>采用 watch 深度监听 tabConfig 自动推历史（400ms 防抖合并连续输入，
 * 避免 50 步栈被逐字符吞掉）；undo/redo 时反向同步快照回 reactive，
 * 保持 tabConfig 引用不变以兼容现有 UI 双向绑定。</p>
 */
const history = useUndoRedo<TabConfig>(JSON.parse(JSON.stringify(tabConfig)))
const { present: historyPresent, canUndo, canRedo } = history

/** 抑制标志：undo/redo 同步快照回 tabConfig 时关闭 watch 推历史，避免循环 */
let suppressHistory = false
/** 防抖计时器：连续输入合并为一次历史入栈 */
let historyDebounce: ReturnType<typeof setTimeout> | null = null
const HISTORY_DEBOUNCE_MS = 400

/** 立即提交待入栈的变更（undo/redo 前调用，避免丢失未入栈编辑） */
function commitPendingHistory() {
  if (historyDebounce) {
    clearTimeout(historyDebounce)
    historyDebounce = null
    history.set(JSON.parse(JSON.stringify(tabConfig)))
  }
}

// 深度监听 tabConfig，自动推历史（flush: sync 便于精确抑制）
watch(
  tabConfig,
  () => {
    if (suppressHistory) return
    if (historyDebounce) clearTimeout(historyDebounce)
    historyDebounce = setTimeout(() => {
      historyDebounce = null
      history.set(JSON.parse(JSON.stringify(tabConfig)))
    }, HISTORY_DEBOUNCE_MS)
  },
  { deep: true, flush: 'sync' }
)

/** 将历史当前快照同步回 reactive tabConfig（保持引用不变，UI 自动更新） */
function applyHistoryToTabConfig() {
  const snap = historyPresent.value
  suppressHistory = true
  try {
    const target = tabConfig as unknown as Record<string, unknown>
    const src = snap as unknown as Record<string, unknown>
    // 删除快照中没有的 key
    for (const key of Object.keys(target)) {
      if (!(key in src)) delete target[key]
    }
    // 写入快照中的所有 key（深拷贝避免共享引用）
    for (const key of Object.keys(src)) {
      target[key] = JSON.parse(JSON.stringify(src[key]))
    }
    // 重算 tabSeq，避免 undo/redo 后新增标签 ID 冲突
    tabSeq = 0
    for (const t of tabConfig.tabs) {
      const m = /tab_(\d+)/.exec(t.id)
      if (m) {
        const n = parseInt(m[1], 10)
        if (n > tabSeq) tabSeq = n
      }
    }
  } finally {
    nextTick(() => {
      suppressHistory = false
    })
  }
}

/** 撤销 */
function undo() {
  commitPendingHistory()
  if (!canUndo.value) return
  history.undo()
  applyHistoryToTabConfig()
}

/** 重做 */
function redo() {
  commitPendingHistory()
  if (!canRedo.value) return
  history.redo()
  applyHistoryToTabConfig()
}

/** 键盘快捷键：Ctrl/Cmd+Z 撤销，Ctrl+Y 或 Ctrl/Cmd+Shift+Z 重做 */
function onUndoRedoKeydown(event: KeyboardEvent) {
  const isMac =
    typeof navigator !== 'undefined' &&
    navigator.platform.toUpperCase().indexOf('MAC') >= 0
  const ctrlOrCmd = isMac ? event.metaKey : event.ctrlKey
  if (!ctrlOrCmd) return
  const key = event.key.toLowerCase()
  if (key === 'z' && !event.shiftKey) {
    event.preventDefault()
    undo()
  } else if ((key === 'z' && event.shiftKey) || key === 'y') {
    event.preventDefault()
    redo()
  }
}

// ===================== 加载已有标签页（编辑模式） =====================

async function loadTab(id: number) {
  loading.value = true
  try {
    const data = await getTab(id)
    Object.assign(metaForm, data)
    parseTabConfigFromStr()
    // 编辑模式加载完成后重置历史栈，使加载的配置成为初始状态（不可 undo 回空状态）
    if (historyDebounce) {
      clearTimeout(historyDebounce)
      historyDebounce = null
    }
    history.reset(JSON.parse(JSON.stringify(tabConfig)))
  } catch {
    /* handled by interceptor */
  } finally {
    loading.value = false
  }
}

// ===================== 保存 / 发布 / 归档 =====================

async function handleSave() {
  if (!metaFormRef.value) return
  await metaFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (tabConfig.tabs.length === 0) {
      ElMessage.warning('请至少添加一个标签')
      return
    }
    syncTabConfigToStr()
    loading.value = true
    try {
      if (metaForm.id) {
        await updateTab(metaForm.id, metaForm)
        ElMessage.success('保存成功')
      } else {
        const created = await createTab(metaForm)
        metaForm.id = created.id
        metaForm.status = created.status
        ElMessage.success('创建成功')
      }
    } catch {
      /* handled by interceptor */
    } finally {
      loading.value = false
    }
  })
}

async function handlePublish() {
  if (!metaForm.id) {
    ElMessage.warning('请先保存草稿')
    return
  }
  syncTabConfigToStr()
  loading.value = true
  try {
    await updateTab(metaForm.id, metaForm)
    await publishTab(metaForm.id)
    metaForm.status = 'PUBLISHED'
    ElMessage.success('发布成功')
  } catch {
    /* handled by interceptor */
  } finally {
    loading.value = false
  }
}

async function handleArchive() {
  if (!metaForm.id) return
  try {
    await ElMessageBox.confirm('确认归档此标签页？归档后不可再使用', '确认', { type: 'warning' })
    await archiveTab(metaForm.id)
    metaForm.status = 'ARCHIVED'
    ElMessage.success('归档成功')
  } catch {
    /* cancelled or error */
  }
}

// ===================== 导入 / 导出 =====================

async function handleExport() {
  if (!metaForm.code) {
    ElMessage.warning('请先填写标签页编码')
    return
  }
  syncTabConfigToStr()
  if (metaForm.id) {
    try {
      await exportTab(metaForm.code)
      ElMessage.success('导出成功')
    } catch {
      /* handled by interceptor */
    }
  } else {
    // 本地导出
    const blob = new Blob([JSON.stringify(metaForm, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `tab-${metaForm.code}.json`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    setTimeout(() => URL.revokeObjectURL(url), 0)
    ElMessage.success('本地导出成功')
  }
}

async function handleImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json,application/json'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    const text = await file.text()
    try {
      const imported = await importTab(text)
      ElMessage.success(`导入成功，编码：${imported.code}`)
      Object.assign(metaForm, imported)
      parseTabConfigFromStr()
    } catch {
      // 后端导入失败时本地解析
      try {
        const parsed = JSON.parse(text) as Partial<LowCodeTabConfig> & { tabs?: unknown }
        if (parsed.tabConfig && typeof parsed.tabConfig === 'string') {
          Object.assign(metaForm, parsed)
          parseTabConfigFromStr()
          ElMessage.success('已加载到画布（本地解析，未提交后端）')
        } else if (Array.isArray(parsed.tabs)) {
          metaForm.tabConfig = text
          parseTabConfigFromStr()
          ElMessage.success('已加载到画布')
        } else {
          ElMessage.error('无法识别的 JSON 结构')
        }
      } catch (e) {
        ElMessage.error('JSON 解析失败：' + (e as Error).message)
      }
    }
  }
  input.click()
}

// ===================== 预览 / 重置 =====================

function handlePreview() {
  syncTabConfigToStr()
  previewVisible.value = true
}

function handleReset() {
  ElMessageBox.confirm('确认清空画布所有配置？此操作不可恢复', '确认', { type: 'warning' })
    .then(() => {
      tabConfig.tabs = []
      selectedId.value = ''
      tabSeq = 0
      ElMessage.success('已重置画布')
    })
    .catch(() => {})
}

function goToList() {
  router.push('/lowcode/tab-list')
}

// ===================== 初始化 =====================

const editId = route.query.id ? Number(route.query.id) : 0
if (editId > 0) {
  loadTab(editId)
} else {
  tabConfig.title = '未命名标签页'
  // 重置历史栈，使初始标题作为干净的起点（不可 undo 回空标题）
  if (historyDebounce) {
    clearTimeout(historyDebounce)
    historyDebounce = null
  }
  history.reset(JSON.parse(JSON.stringify(tabConfig)))
}

// 启用撤销/重做键盘快捷键
onMounted(() => {
  window.addEventListener('keydown', onUndoRedoKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onUndoRedoKeydown)
  if (historyDebounce) clearTimeout(historyDebounce)
})
</script>

<template>
  <div class="tab-designer">
    <!-- ============ 顶部操作栏 ============ -->
    <el-card shadow="never" class="toolbar-card" :body-style="{ padding: '12px 16px' }">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="primary" :icon="'Document'" :loading="loading" @click="handleSave">保存草稿</el-button>
          <el-button type="success" :icon="'Promotion'" @click="handlePublish">发布</el-button>
          <el-button :icon="'Download'" @click="handleExport">导出</el-button>
          <el-button :icon="'Upload'" @click="handleImport">导入</el-button>
          <el-button :icon="'View'" @click="handlePreview">预览</el-button>
          <el-button :icon="'RefreshLeft'" :disabled="!canUndo" @click="undo">撤销</el-button>
          <el-button :icon="'RefreshRight'" :disabled="!canRedo" @click="redo">重做</el-button>
          <el-button :icon="'Refresh'" @click="handleReset">重置</el-button>
          <el-button v-if="metaForm.status === 'PUBLISHED'" :icon="'FolderOpened'" @click="handleArchive">归档</el-button>
        </div>
        <div class="toolbar-right">
          <el-tag :type="metaForm.status === 'PUBLISHED' ? 'success' : metaForm.status === 'ARCHIVED' ? 'info' : 'warning'">
            {{ metaForm.status || 'DRAFT' }}
          </el-tag>
          <el-button link type="primary" @click="goToList">返回列表</el-button>
        </div>
      </div>
    </el-card>

    <!-- ============ 元信息编辑区 ============ -->
    <el-card shadow="never" class="meta-card" :body-style="{ padding: '12px 16px' }">
      <el-form ref="metaFormRef" :model="metaForm" :rules="metaRules" inline label-width="90px">
        <el-form-item label="标签页编码" prop="code">
          <el-input v-model="metaForm.code" placeholder="如：tpl_project_detail_tabs" :disabled="!!metaForm.id" style="width: 220px" />
        </el-form-item>
        <el-form-item label="标签页名称" prop="name">
          <el-input v-model="metaForm.name" placeholder="请输入标签页名称" style="width: 220px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="metaForm.bizType" placeholder="如：PROJECT" style="width: 160px" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="metaForm.description" placeholder="标签页描述" style="width: 320px" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ============ 主体三栏 ============ -->
    <div class="designer-body">
      <!-- 左侧：组件库 -->
      <el-card shadow="never" class="panel panel-left" :body-style="{ padding: '8px' }">
        <template #header>
          <span class="panel-title">组件库</span>
        </template>
        <div v-for="group in componentGroups" :key="group.title" class="comp-group">
          <div class="comp-group-title">{{ group.title }}</div>
          <div class="comp-items">
            <div
              v-for="comp in group.items"
              :key="comp.type"
              class="comp-item"
              draggable="true"
              @dragstart="onCompDragStart($event, comp)"
              @click="addComponent(comp)"
            >
              <el-icon><component :is="comp.icon" /></el-icon>
              <span>{{ comp.label }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 中间：画布 -->
      <el-card shadow="never" class="panel panel-center" :body-style="{ padding: '12px' }">
        <template #header>
          <div class="canvas-header">
            <span class="panel-title">画布</span>
            <el-form inline size="small" class="canvas-config">
              <el-form-item label="el-tabs type">
                <el-select v-model="tabConfig.type" style="width: 130px">
                  <el-option label="卡片 card" :value="TabsType.CARD" />
                  <el-option label="边框卡片 border-card" :value="TabsType.BORDER_CARD" />
                  <el-option label="无样式 plain" :value="TabsType.PLAIN" />
                </el-select>
              </el-form-item>
              <el-form-item label="标签位置">
                <el-select v-model="tabConfig.tabPosition" style="width: 90px">
                  <el-option label="顶部" :value="TabPosition.TOP" />
                  <el-option label="右" :value="TabPosition.RIGHT" />
                  <el-option label="底部" :value="TabPosition.BOTTOM" />
                  <el-option label="左" :value="TabPosition.LEFT" />
                </el-select>
              </el-form-item>
              <el-form-item label="可关闭">
                <el-switch v-model="tabConfig.closable" />
              </el-form-item>
              <el-form-item label="可新增">
                <el-switch v-model="tabConfig.addable" />
              </el-form-item>
              <el-form-item label="可编辑">
                <el-switch v-model="tabConfig.editable" />
              </el-form-item>
            </el-form>
          </div>
        </template>

        <!-- 拖拽放置区 -->
        <div
          class="canvas-dropzone"
          :class="{ empty: tabConfig.tabs.length === 0 }"
          @dragover="onCanvasDragOver"
          @drop="onCanvasDrop"
        >
          <div v-if="tabConfig.tabs.length === 0" class="empty-tip">
            <el-icon :size="40"><Plus /></el-icon>
            <p>从左侧拖拽标签组件到此处</p>
          </div>

          <div v-else class="item-list">
            <div
              v-for="(tab, idx) in tabConfig.tabs"
              :key="tab.id"
              class="item-card"
              :class="{ active: selectedId === tab.id }"
              draggable="true"
              @dragstart="onItemDragStart($event, tab.id)"
              @dragover="onCanvasDragOver"
              @drop="onItemDrop($event, tab.id)"
              @click="selectItem(tab.id)"
            >
              <div class="item-card-header">
                <el-tag size="small" type="info">{{ tab.pageType }}</el-tag>
                <span class="item-label">{{ tab.title }}</span>
                <span class="item-prop">name: {{ tab.name }}</span>
                <span v-if="tab.pageCode" class="item-prop">pageCode: {{ tab.pageCode }}</span>
                <div class="item-actions">
                  <el-button-group size="small">
                    <el-button :icon="'Top'" :disabled="idx === 0" @click.stop="moveItem(tab.id, -1)" />
                    <el-button :icon="'Bottom'" :disabled="idx === tabConfig.tabs.length - 1" @click.stop="moveItem(tab.id, 1)" />
                    <el-button :icon="'CopyDocument'" @click.stop="duplicateItem(tab.id)" />
                    <el-button :icon="'Delete'" type="danger" @click.stop="removeItem(tab.id)" />
                  </el-button-group>
                </div>
              </div>
              <div class="item-card-meta">
                <el-tag v-if="tab.lazy" size="small">lazy</el-tag>
                <el-tag v-if="tab.disabled" size="small" type="warning">disabled</el-tag>
                <el-tag v-if="tab.icon" size="small" type="success">icon: {{ tab.icon }}</el-tag>
                <el-tag v-if="tab.visible" size="small" type="info">visible: {{ tab.visible }}</el-tag>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 右侧：属性面板 -->
      <el-card shadow="never" class="panel panel-right" :body-style="{ padding: '12px' }">
        <template #header>
          <span class="panel-title">属性面板</span>
        </template>

        <div v-if="!selectedTab" class="empty-prop">
          <el-empty description="请选择一个标签项" :image-size="80" />
        </div>

        <el-form v-else :model="selectedTab" label-width="100px" size="small">
          <el-divider content-position="left">基础属性</el-divider>
          <el-form-item label="标签标题">
            <el-input v-model="selectedTab.title" />
          </el-form-item>
          <el-form-item label="标签标识 name">
            <el-input v-model="selectedTab.name" placeholder="用于 v-model 绑定，必填且唯一" />
          </el-form-item>
          <el-form-item label="Element 图标">
            <el-input v-model="selectedTab.icon" placeholder="Element Plus 图标名，如 Document" />
          </el-form-item>
          <el-form-item label="懒加载 lazy">
            <el-switch v-model="selectedTab.lazy" />
          </el-form-item>
          <el-form-item label="禁用 disabled">
            <el-switch v-model="selectedTab.disabled" />
          </el-form-item>

          <el-divider content-position="left">页面引用</el-divider>
          <el-form-item label="页面类型">
            <el-select v-model="selectedTab.pageType" style="width: 100%">
              <el-option label="表单 form" :value="TabPageType.FORM" />
              <el-option label="列表 list" :value="TabPageType.LIST" />
              <el-option label="关联页 related-page" :value="TabPageType.RELATED_PAGE" />
              <el-option label="自定义 custom" :value="TabPageType.CUSTOM" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="selectedTab.pageType !== 'custom'" label="页面编码">
            <el-input v-model="selectedTab.pageCode" placeholder="引用的低代码页面编码" />
          </el-form-item>
          <el-form-item v-if="selectedTab.pageType === 'custom'" label="页面 URL">
            <el-input v-model="selectedTab.pageUrl" placeholder="/project/{id}/basic 或 http(s)://..." />
          </el-form-item>

          <el-divider content-position="left">显示条件</el-divider>
          <el-form-item label="visible 表达式">
            <el-input
              v-model="selectedTab.visible"
              type="textarea"
              :rows="2"
              placeholder="如：row.status === 'IN_PROGRESS'，留空表示始终显示"
            />
          </el-form-item>

          <el-divider content-position="left">props 传参</el-divider>
          <div class="props-list">
            <div
              v-for="key in Object.keys(selectedTab.props || {})"
              :key="key"
              class="prop-row"
            >
              <el-input v-model="(selectedTab.props as Record<string, unknown>)[key]" :placeholder="key" size="small" />
              <el-input :model-value="key" disabled size="small" style="width: 30%" />
              <el-button :icon="'Delete'" type="danger" size="small" @click="removeTabProp(selectedTab, key)" />
            </div>
            <el-button :icon="'Plus'" size="small" @click="addTabProp(selectedTab)">添加 props</el-button>
          </div>
        </el-form>
      </el-card>
    </div>

    <!-- ============ 预览弹窗 ============ -->
    <el-dialog
      v-model="previewVisible"
      title="标签页预览"
      width="90%"
      top="3vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <LowCodeTabRenderer
        :config="tabConfig"
        :allow-draft="true"
        :context-data="{
          row: { id: 1, status: 'IN_PROGRESS', projectId: 'P2025001', warrantyStatus: 'ACTIVE' },
          context: {}
        }"
      />
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tab-designer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 110px);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.designer-body {
  display: grid;
  grid-template-columns: 220px 1fr 320px;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
}

.panel-title {
  font-weight: 600;
  font-size: 14px;
}

/* 左侧组件库 */
.comp-group {
  margin-bottom: 8px;
}

.comp-group-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  padding: 6px 4px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  margin-bottom: 6px;
}

.comp-items {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.comp-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 4px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: grab;
  font-size: 12px;
  transition: all 0.2s;
  background: var(--el-bg-color);
}

.comp-item:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.comp-item:active {
  cursor: grabbing;
}

/* 中间画布 */
.canvas-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.canvas-config {
  margin: 0;
}

.canvas-config :deep(.el-form-item) {
  margin-bottom: 0;
  margin-right: 8px;
}

.canvas-dropzone {
  min-height: 400px;
  border: 2px dashed var(--el-border-color);
  border-radius: 6px;
  padding: 8px;
  background: var(--el-fill-color-blank);
}

.canvas-dropzone.empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-tip {
  text-align: center;
  color: var(--el-text-color-secondary);
}

.empty-tip p {
  margin: 8px 0 0;
  font-size: 13px;
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.item-card {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px;
  background: var(--el-bg-color);
  cursor: pointer;
  transition: all 0.2s;
}

.item-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.item-card.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-7);
}

.item-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.item-card-meta {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 6px;
  padding-left: 4px;
}

.item-label {
  font-weight: 600;
}

.item-prop {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-family: monospace;
}

.item-actions {
  margin-left: auto;
}

/* 右侧属性面板 */
.empty-prop {
  padding: 24px 0;
}

.props-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.prop-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 响应式：小屏堆叠 */
@media (max-width: 1200px) {
  .designer-body {
    grid-template-columns: 180px 1fr 280px;
  }
}

@media (max-width: 992px) {
  .designer-body {
    grid-template-columns: 1fr;
    grid-template-rows: auto auto auto;
  }
}
</style>
