<script setup lang="ts">
/**
 * 低代码关联页设计器。
 *
 * <p>三栏布局：</p>
 * <ul>
 *   <li>左侧：组件库面板（Section 组件，按 type 分组：form/list/tab/custom）</li>
 *   <li>中间：画布（关联页顶层配置 + Section 列表，可拖拽排序，
 *       每个卡片显示 title/type/pageCode/span/order）</li>
 *   <li>右侧：属性面板（选中 Section 的 title/type/pageCode/pageUrl/span/order/visible/props）</li>
 * </ul>
 *
 * <p>顶部操作栏：保存草稿 / 发布 / 归档 / 导入 / 导出 / 预览 / 重置，以及元信息编辑。</p>
 *
 * <p>使用原生 HTML5 拖拽 API（draggable + dragstart/dragover/drop）实现，
 * 支持组件库 → 画布拖拽、画布内 Section 排序拖拽两种交互。Section ID 自动生成
 * （section_N），默认 span=24、order=100，点击"预览"打开 LowCodeRelatedPageRenderer 弹窗。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  SectionType,
  RelatedPageLayout,
  archiveRelatedPage,
  createRelatedPage,
  deleteRelatedPage,
  exportRelatedPage,
  getRelatedPage,
  importRelatedPage,
  normalizeRelatedPageConfig,
  publishRelatedPage,
  updateRelatedPage,
  type LowCodeRelatedPageConfig,
  type LowCodeRelatedPageQuery,
  type RelatedPageConfig,
  type RelatedPageSectionConfig,
  type ResponsiveSpan
} from '@/api/lowcode'
import LowCodeRelatedPageRenderer from '@/components/LowCodeRelatedPageRenderer/index.vue'
import { useUndoRedo } from '@/composables/useUndoRedo'

const route = useRoute()
const router = useRouter()

// ===================== 组件库定义 =====================

interface ComponentDef {
  /** 组件 type，与 SectionType 对应 */
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
    title: '区块组件（按引用页面类型）',
    items: [
      {
        type: SectionType.FORM,
        label: '表单区块',
        icon: 'Document',
        defaultProps: { type: SectionType.FORM, span: 24, order: 100 }
      },
      {
        type: SectionType.LIST,
        label: '列表区块',
        icon: 'List',
        defaultProps: { type: SectionType.LIST, span: 24, order: 100 }
      },
      {
        type: SectionType.TAB,
        label: '标签页区块',
        icon: 'Files',
        defaultProps: { type: SectionType.TAB, span: 24, order: 100 }
      },
      {
        type: SectionType.CUSTOM,
        label: '自定义区块',
        icon: 'Setting',
        defaultProps: { type: SectionType.CUSTOM, span: 24, order: 100, pageUrl: '' }
      }
    ]
  }
])

// ===================== 元信息 + 配置状态 =====================

/** 关联页元信息（对应 LowCodeRelatedPageConfig 的非 relatedConfig 字段） */
const metaForm = reactive<LowCodeRelatedPageConfig>({
  code: '',
  name: '',
  description: '',
  relatedConfig: '',
  status: 'DRAFT',
  bizType: '',
  version: 1
})

/** 设计器内部维护的 RelatedPageConfig 对象 */
const relatedConfig = reactive<RelatedPageConfig>({
  title: '',
  description: '',
  mainEntity: '',
  sections: [],
  layout: RelatedPageLayout.GRID,
  gutter: 16
})

/** 当前选中项 id（section 的 id） */
const selectedId = ref<string>('')

/** Section ID 计数器 */
let sectionSeq = 0

/** 元信息表单 ref */
const metaFormRef = ref<FormInstance>()
const metaRules: FormRules = {
  code: [{ required: true, message: '请输入关联页编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入关联页名称', trigger: 'blur' }]
}

const loading = ref(false)
const previewVisible = ref(false)

// ===================== 选中项计算 =====================

/** 当前选中的区块 */
const selectedSection = computed<RelatedPageSectionConfig | null>(() =>
  relatedConfig.sections.find((s) => s.id === selectedId.value) || null
)

// ===================== 响应式栅格断点（xs/sm/md/lg/xl） =====================

type Breakpoint = 'xs' | 'sm' | 'md' | 'lg' | 'xl'
/** 响应式断点折叠面板激活项（默认展开） */
const sectionResponsiveCollapse = ref<string[]>(['resp'])

/** 当前选中区块是否启用响应式断点（span 为对象） */
const isSectionResponsive = computed<boolean>({
  get: () => !!selectedSection.value && typeof selectedSection.value.span === 'object',
  set: (val: boolean) => {
    const section = selectedSection.value
    if (!section) return
    if (val) {
      const cur = typeof section.span === 'number' ? section.span : 24
      section.span = { xs: cur, sm: cur, md: cur, lg: cur, xl: cur }
    } else {
      const obj = section.span
      section.span = typeof obj === 'object' && obj ? (obj.md ?? 24) : 24
    }
  }
})

/** 非响应式模式下的栅格宽度（数字） */
const sectionSpan = computed<number>({
  get: () => (typeof selectedSection.value?.span === 'number' ? selectedSection.value.span : 24),
  set: (v: number) => {
    if (selectedSection.value) selectedSection.value.span = v
  }
})

/** 读取指定断点值（缺省回退 24） */
function getSectionBreakpoint(k: Breakpoint): number {
  const s = selectedSection.value?.span
  return typeof s === 'object' && s && s[k] !== undefined ? (s[k] as number) : 24
}

/** 设置指定断点值（自动转为响应式对象） */
function setSectionBreakpoint(k: Breakpoint, v: number): void {
  const section = selectedSection.value
  if (!section) return
  const s = section.span
  const obj: ResponsiveSpan = typeof s === 'object' && s ? { ...s } : {}
  obj[k] = v
  section.span = obj
}

/** 格式化 span 用于卡片展示：数字直接返回，对象拼接断点键值 */
function formatSpan(span: number | ResponsiveSpan | undefined): string {
  if (span === undefined) return '24'
  if (typeof span === 'number') return String(span)
  const parts: string[] = []
  if (span.xs !== undefined) parts.push(`xs:${span.xs}`)
  if (span.sm !== undefined) parts.push(`sm:${span.sm}`)
  if (span.md !== undefined) parts.push(`md:${span.md}`)
  if (span.lg !== undefined) parts.push(`lg:${span.lg}`)
  if (span.xl !== undefined) parts.push(`xl:${span.xl}`)
  return parts.length ? parts.join(' ') : '24'
}

// ===================== 创建新项 =====================

/** 生成区块对象 */
function createSection(comp: ComponentDef): RelatedPageSectionConfig {
  sectionSeq++
  const idx = sectionSeq
  return {
    id: `section_${idx}`,
    title: `区块${idx}`,
    type: comp.type,
    span: 24,
    order: 100,
    ...(comp.defaultProps || {})
  }
}

// ===================== 添加 / 删除 / 复制 / 移动 =====================

/** 添加项（点击组件库或拖拽放置） */
function addComponent(comp: ComponentDef) {
  const section = createSection(comp)
  relatedConfig.sections.push(section)
  selectedId.value = section.id
}

/** 删除项 */
function removeItem(id: string) {
  relatedConfig.sections = relatedConfig.sections.filter((s) => s.id !== id)
  if (selectedId.value === id) selectedId.value = ''
}

/** 复制项 */
function duplicateItem(id: string) {
  const section = relatedConfig.sections.find((s) => s.id === id)
  if (!section) return
  sectionSeq++
  const copy: RelatedPageSectionConfig = JSON.parse(JSON.stringify(section))
  copy.id = `section_${sectionSeq}`
  copy.title = `${section.title}_副本`
  const idx = relatedConfig.sections.findIndex((s) => s.id === id)
  relatedConfig.sections.splice(idx + 1, 0, copy)
  selectedId.value = copy.id
}

/** 上下移动 */
function moveItem(id: string, direction: -1 | 1) {
  const arr = relatedConfig.sections
  const idx = arr.findIndex((s) => s.id === id)
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
  const arr = relatedConfig.sections
  const fromIdx = arr.findIndex((s) => s.id === dragItemId)
  const toIdx = arr.findIndex((s) => s.id === targetId)
  if (fromIdx < 0 || toIdx < 0) return
  const [moved] = arr.splice(fromIdx, 1)
  arr.splice(toIdx, 0, moved)
  dragItemId = ''
}

// ===================== props 编辑（Section 项 props） =====================

function addSectionProp(section: RelatedPageSectionConfig) {
  if (!section.props) section.props = {}
  const key = `prop_${Object.keys(section.props).length + 1}`
  section.props[key] = ''
}

function removeSectionProp(section: RelatedPageSectionConfig, key: string) {
  if (section.props) {
    delete section.props[key]
  }
}

// ===================== 元信息 & 配置序列化 =====================

function syncRelatedConfigToStr() {
  metaForm.relatedConfig = JSON.stringify(relatedConfig, null, 2)
}

function parseRelatedConfigFromStr() {
  try {
    if (!metaForm.relatedConfig) {
      relatedConfig.sections = []
      return
    }
    const parsed = normalizeRelatedPageConfig(JSON.parse(metaForm.relatedConfig) as RelatedPageConfig)
    relatedConfig.title = parsed.title ?? ''
    relatedConfig.description = parsed.description ?? ''
    relatedConfig.mainEntity = parsed.mainEntity ?? ''
    relatedConfig.sections = parsed.sections || []
    relatedConfig.layout = parsed.layout ?? RelatedPageLayout.GRID
    relatedConfig.gutter = parsed.gutter ?? 16
    // 重置计数器
    sectionSeq = 0
    relatedConfig.sections.forEach((s) => {
      const m = /section_(\d+)/.exec(s.id)
      if (m) {
        const n = parseInt(m[1], 10)
        if (n > sectionSeq) sectionSeq = n
      }
    })
  } catch (e) {
    ElMessage.error('关联页配置 JSON 解析失败：' + (e as Error).message)
  }
}

// ===================== 撤销/重做 =====================

/**
 * 撤销/重做历史栈：对整个 relatedConfig 做 JSON 快照。
 *
 * <p>采用 watch 深度监听 relatedConfig 自动推历史（400ms 防抖合并连续输入，
 * 避免 50 步栈被逐字符吞掉）；undo/redo 时反向同步快照回 reactive，
 * 保持 relatedConfig 引用不变以兼容现有 UI 双向绑定。</p>
 */
const history = useUndoRedo<RelatedPageConfig>(JSON.parse(JSON.stringify(relatedConfig)))
const { present: historyPresent, canUndo, canRedo } = history

/** 抑制标志：undo/redo 同步快照回 relatedConfig 时关闭 watch 推历史，避免循环 */
let suppressHistory = false
/** 防抖计时器：连续输入合并为一次历史入栈 */
let historyDebounce: ReturnType<typeof setTimeout> | null = null
const HISTORY_DEBOUNCE_MS = 400

/** 立即提交待入栈的变更（undo/redo 前调用，避免丢失未入栈编辑） */
function commitPendingHistory() {
  if (historyDebounce) {
    clearTimeout(historyDebounce)
    historyDebounce = null
    history.set(JSON.parse(JSON.stringify(relatedConfig)))
  }
}

// 深度监听 relatedConfig，自动推历史（flush: sync 便于精确抑制）
watch(
  relatedConfig,
  () => {
    if (suppressHistory) return
    if (historyDebounce) clearTimeout(historyDebounce)
    historyDebounce = setTimeout(() => {
      historyDebounce = null
      history.set(JSON.parse(JSON.stringify(relatedConfig)))
    }, HISTORY_DEBOUNCE_MS)
  },
  { deep: true, flush: 'sync' }
)

/** 将历史当前快照同步回 reactive relatedConfig（保持引用不变，UI 自动更新） */
function applyHistoryToRelatedConfig() {
  const snap = historyPresent.value
  suppressHistory = true
  try {
    const target = relatedConfig as unknown as Record<string, unknown>
    const src = snap as unknown as Record<string, unknown>
    // 删除快照中没有的 key
    for (const key of Object.keys(target)) {
      if (!(key in src)) delete target[key]
    }
    // 写入快照中的所有 key（深拷贝避免共享引用）
    for (const key of Object.keys(src)) {
      target[key] = JSON.parse(JSON.stringify(src[key]))
    }
    // 重算 sectionSeq，避免 undo/redo 后新增区块 ID 冲突
    sectionSeq = 0
    for (const s of relatedConfig.sections) {
      const m = /section_(\d+)/.exec(s.id)
      if (m) {
        const n = parseInt(m[1], 10)
        if (n > sectionSeq) sectionSeq = n
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
  applyHistoryToRelatedConfig()
}

/** 重做 */
function redo() {
  commitPendingHistory()
  if (!canRedo.value) return
  history.redo()
  applyHistoryToRelatedConfig()
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

// ===================== 加载已有关联页（编辑模式） =====================

async function loadRelatedPage(id: number) {
  loading.value = true
  try {
    const data = await getRelatedPage(id)
    Object.assign(metaForm, data)
    parseRelatedConfigFromStr()
    // 编辑模式加载完成后重置历史栈，使加载的配置成为初始状态（不可 undo 回空状态）
    if (historyDebounce) {
      clearTimeout(historyDebounce)
      historyDebounce = null
    }
    history.reset(JSON.parse(JSON.stringify(relatedConfig)))
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
    if (relatedConfig.sections.length === 0) {
      ElMessage.warning('请至少添加一个区块')
      return
    }
    syncRelatedConfigToStr()
    loading.value = true
    try {
      if (metaForm.id) {
        await updateRelatedPage(metaForm.id, metaForm)
        ElMessage.success('保存成功')
      } else {
        const created = await createRelatedPage(metaForm)
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
  syncRelatedConfigToStr()
  loading.value = true
  try {
    await updateRelatedPage(metaForm.id, metaForm)
    await publishRelatedPage(metaForm.id)
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
    await ElMessageBox.confirm('确认归档此关联页？归档后不可再使用', '确认', { type: 'warning' })
    await archiveRelatedPage(metaForm.id)
    metaForm.status = 'ARCHIVED'
    ElMessage.success('归档成功')
  } catch {
    /* cancelled or error */
  }
}

// ===================== 导入 / 导出 =====================

async function handleExport() {
  if (!metaForm.code) {
    ElMessage.warning('请先填写关联页编码')
    return
  }
  syncRelatedConfigToStr()
  if (metaForm.id) {
    try {
      await exportRelatedPage(metaForm.code)
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
    link.download = `related-page-${metaForm.code}.json`
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
      const imported = await importRelatedPage(text)
      ElMessage.success(`导入成功，编码：${imported.code}`)
      Object.assign(metaForm, imported)
      parseRelatedConfigFromStr()
    } catch {
      // 后端导入失败时本地解析
      try {
        const parsed = JSON.parse(text) as Partial<LowCodeRelatedPageConfig> & { sections?: unknown }
        if (parsed.relatedConfig && typeof parsed.relatedConfig === 'string') {
          Object.assign(metaForm, parsed)
          parseRelatedConfigFromStr()
          ElMessage.success('已加载到画布（本地解析，未提交后端）')
        } else if (Array.isArray(parsed.sections)) {
          metaForm.relatedConfig = text
          parseRelatedConfigFromStr()
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
  syncRelatedConfigToStr()
  previewVisible.value = true
}

function handleReset() {
  ElMessageBox.confirm('确认清空画布所有配置？此操作不可恢复', '确认', { type: 'warning' })
    .then(() => {
      relatedConfig.sections = []
      selectedId.value = ''
      sectionSeq = 0
      ElMessage.success('已重置画布')
    })
    .catch(() => {})
}

function goToList() {
  router.push('/lowcode/related-page-list')
}

// ===================== 初始化 =====================

const editId = route.query.id ? Number(route.query.id) : 0
if (editId > 0) {
  loadRelatedPage(editId)
} else {
  relatedConfig.title = '未命名关联页'
  // 重置历史栈，使初始标题作为干净的起点（不可 undo 回空标题）
  if (historyDebounce) {
    clearTimeout(historyDebounce)
    historyDebounce = null
  }
  history.reset(JSON.parse(JSON.stringify(relatedConfig)))
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
  <div class="related-page-designer">
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
        <el-form-item label="关联页编码" prop="code">
          <el-input v-model="metaForm.code" placeholder="如：tpl_project_overview_related" :disabled="!!metaForm.id" style="width: 240px" />
        </el-form-item>
        <el-form-item label="关联页名称" prop="name">
          <el-input v-model="metaForm.name" placeholder="请输入关联页名称" style="width: 220px" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-input v-model="metaForm.bizType" placeholder="如：PROJECT" style="width: 160px" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="metaForm.description" placeholder="关联页描述" style="width: 320px" />
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
              <el-form-item label="主实体">
                <el-input v-model="relatedConfig.mainEntity" placeholder="如 project/asset" style="width: 140px" />
              </el-form-item>
              <el-form-item label="布局">
                <el-select v-model="relatedConfig.layout" style="width: 110px">
                  <el-option label="栅格 grid" :value="RelatedPageLayout.GRID" />
                  <el-option label="标签页 tabs" :value="RelatedPageLayout.TABS" />
                  <el-option label="折叠面板 collapse" :value="RelatedPageLayout.COLLAPSE" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="relatedConfig.layout === RelatedPageLayout.GRID" label="栅格间距">
                <el-input-number v-model="relatedConfig.gutter" :min="0" :max="60" :step="4" style="width: 110px" />
              </el-form-item>
            </el-form>
          </div>
        </template>

        <!-- 拖拽放置区 -->
        <div
          class="canvas-dropzone"
          :class="{ empty: relatedConfig.sections.length === 0 }"
          @dragover="onCanvasDragOver"
          @drop="onCanvasDrop"
        >
          <div v-if="relatedConfig.sections.length === 0" class="empty-tip">
            <el-icon :size="40"><Plus /></el-icon>
            <p>从左侧拖拽区块组件到此处</p>
          </div>

          <div v-else class="item-list">
            <div
              v-for="(section, idx) in relatedConfig.sections"
              :key="section.id"
              class="item-card"
              :class="{ active: selectedId === section.id }"
              draggable="true"
              @dragstart="onItemDragStart($event, section.id)"
              @dragover="onCanvasDragOver"
              @drop="onItemDrop($event, section.id)"
              @click="selectItem(section.id)"
            >
              <div class="item-card-header">
                <el-tag size="small" type="info">{{ section.type }}</el-tag>
                <span class="item-label">{{ section.title }}</span>
                <span v-if="section.pageCode" class="item-prop">pageCode: {{ section.pageCode }}</span>
                <span class="item-prop">span: {{ formatSpan(section.span) }}</span>
                <span class="item-prop">order: {{ section.order ?? 100 }}</span>
                <div class="item-actions">
                  <el-button-group size="small">
                    <el-button :icon="'Top'" :disabled="idx === 0" @click.stop="moveItem(section.id, -1)" />
                    <el-button :icon="'Bottom'" :disabled="idx === relatedConfig.sections.length - 1" @click.stop="moveItem(section.id, 1)" />
                    <el-button :icon="'CopyDocument'" @click.stop="duplicateItem(section.id)" />
                    <el-button :icon="'Delete'" type="danger" @click.stop="removeItem(section.id)" />
                  </el-button-group>
                </div>
              </div>
              <div class="item-card-meta">
                <el-tag v-if="section.pageUrl" size="small" type="success">url: {{ section.pageUrl }}</el-tag>
                <el-tag v-if="section.visible" size="small" type="info">visible: {{ section.visible }}</el-tag>
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

        <div v-if="!selectedSection" class="empty-prop">
          <el-empty description="请选择一个区块" :image-size="80" />
        </div>

        <el-form v-else :model="selectedSection" label-width="100px" size="small">
          <el-divider content-position="left">基础属性</el-divider>
          <el-form-item label="区块标题">
            <el-input v-model="selectedSection.title" />
          </el-form-item>
          <el-form-item label="区块类型">
            <el-select v-model="selectedSection.type" style="width: 100%">
              <el-option label="表单 form" :value="SectionType.FORM" />
              <el-option label="列表 list" :value="SectionType.LIST" />
              <el-option label="标签页 tab" :value="SectionType.TAB" />
              <el-option label="自定义 custom" :value="SectionType.CUSTOM" />
            </el-select>
          </el-form-item>

          <el-divider content-position="left">页面引用</el-divider>
          <el-form-item v-if="selectedSection.type !== 'custom'" label="页面编码">
            <el-input v-model="selectedSection.pageCode" placeholder="引用的低代码页面编码" />
          </el-form-item>
          <el-form-item v-if="selectedSection.type === 'custom'" label="页面 URL">
            <el-input v-model="selectedSection.pageUrl" placeholder="/project/{id}/basic 或 http(s)://..." />
          </el-form-item>

          <el-divider content-position="left">布局与排序</el-divider>
          <el-form-item label="响应式栅格">
            <el-switch v-model="isSectionResponsive" />
            <span class="form-tip">开启后按 xs/sm/md/lg/xl 五档断点配置</span>
          </el-form-item>
          <el-form-item v-if="!isSectionResponsive" label="栅格宽度">
            <el-input-number v-model="sectionSpan" :min="1" :max="24" :step="1" style="width: 100%" />
            <span class="form-tip">grid 布局下生效，1-24</span>
          </el-form-item>
          <el-collapse v-else v-model="sectionResponsiveCollapse" class="resp-collapse">
            <el-collapse-item title="响应式断点（1-24）" name="resp">
              <el-form-item label="xs">
                <el-input-number :model-value="getSectionBreakpoint('xs')" :min="1" :max="24" :step="1" controls-position="right" style="width: 100%" @update:model-value="(v: number) => setSectionBreakpoint('xs', v)" />
              </el-form-item>
              <el-form-item label="sm">
                <el-input-number :model-value="getSectionBreakpoint('sm')" :min="1" :max="24" :step="1" controls-position="right" style="width: 100%" @update:model-value="(v: number) => setSectionBreakpoint('sm', v)" />
              </el-form-item>
              <el-form-item label="md">
                <el-input-number :model-value="getSectionBreakpoint('md')" :min="1" :max="24" :step="1" controls-position="right" style="width: 100%" @update:model-value="(v: number) => setSectionBreakpoint('md', v)" />
              </el-form-item>
              <el-form-item label="lg">
                <el-input-number :model-value="getSectionBreakpoint('lg')" :min="1" :max="24" :step="1" controls-position="right" style="width: 100%" @update:model-value="(v: number) => setSectionBreakpoint('lg', v)" />
              </el-form-item>
              <el-form-item label="xl">
                <el-input-number :model-value="getSectionBreakpoint('xl')" :min="1" :max="24" :step="1" controls-position="right" style="width: 100%" @update:model-value="(v: number) => setSectionBreakpoint('xl', v)" />
              </el-form-item>
            </el-collapse-item>
          </el-collapse>
          <el-form-item label="排序号">
            <el-input-number v-model="selectedSection.order" :min="0" :max="9999" :step="10" style="width: 100%" />
            <span class="form-tip">升序排列，相同 order 按数组顺序</span>
          </el-form-item>

          <el-divider content-position="left">显示条件</el-divider>
          <el-form-item label="visible 表达式">
            <el-input
              v-model="selectedSection.visible"
              type="textarea"
              :rows="2"
              placeholder="如：row.status === 'IN_PROGRESS'，留空表示始终显示"
            />
          </el-form-item>

          <el-divider content-position="left">props 传参</el-divider>
          <div class="props-list">
            <div
              v-for="key in Object.keys(selectedSection.props || {})"
              :key="key"
              class="prop-row"
            >
              <el-input v-model="(selectedSection.props as Record<string, unknown>)[key]" :placeholder="key" size="small" />
              <el-input :model-value="key" disabled size="small" style="width: 30%" />
              <el-button :icon="'Delete'" type="danger" size="small" @click="removeSectionProp(selectedSection, key)" />
            </div>
            <el-button :icon="'Plus'" size="small" @click="addSectionProp(selectedSection)">添加 props</el-button>
          </div>
        </el-form>
      </el-card>
    </div>

    <!-- ============ 预览弹窗 ============ -->
    <el-dialog
      v-model="previewVisible"
      title="关联页预览"
      width="90%"
      top="3vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <LowCodeRelatedPageRenderer
        :config="relatedConfig"
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
.related-page-designer {
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
  flex-wrap: wrap;
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

.form-tip {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
  line-height: 1.4;
}

.resp-collapse {
  margin: 4px 0 12px;
}

.resp-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 0;
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
