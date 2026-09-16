<!-- src/components/ProcessDesigner/NodeFormBindingPanel.vue -->
<script setup lang="ts">
/**
 * 节点表单绑定可视化表格。
 *
 * <p>替代原 nodeFormBindings JSON 文本框：根据所选流程定义拉取 BPMN XML，
 * 解析其中 UserTask 节点（id + name）作为「节点 ID」下拉选项；表单下拉来自
 * 表单列表，微流下拉来自微流列表。双向绑定一个 JSON 字符串
 * [{ nodeId, formCode, microflowCode }]，与后端 nodeFormBindings 字段格式一致。</p>
 *
 * <p>借鉴钉钉宜搭审批表单绑定：每行一个节点，可选择该节点审批时渲染的表单
 * 与完成回调微流，避免手写 JSON 出错。</p>
 */
import { computed, ref, watch } from 'vue'
import {
  getProcessDefinitionBpmnXml
} from '@/api/lowcode-process'
import { listForms, type LowCodeFormConfig } from '@/api/lowcode'
import { getMicroflowList, type LowCodeMicroflow } from '@/api/lowcode-microflow'

defineOptions({ name: 'NodeFormBindingPanel' })

const props = defineProps<{
  /** 流程定义 key，用于拉取 BPMN XML 解析节点列表 */
  processDefinitionKey: string
  /** nodeFormBindings JSON 字符串（v-model） */
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

/** 单条节点-表单绑定 */
interface NodeFormBinding {
  nodeId: string
  formCode: string
  microflowCode: string
}

interface OptionItem {
  label: string
  value: string
}

/** 从 BPMN XML 解析出的 UserTask 节点 */
const nodes = ref<OptionItem[]>([])
const forms = ref<OptionItem[]>([])
const microflows = ref<OptionItem[]>([])
const loadingXml = ref(false)

const rows = ref<NodeFormBinding[]>([])

/** 安全解析 JSON 字符串为数组，失败返回空数组 */
function parseBindings(raw: string): NodeFormBinding[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.map((r: Record<string, unknown>) => ({
      nodeId: String(r.nodeId ?? ''),
      formCode: String(r.formCode ?? ''),
      microflowCode: String(r.microflowCode ?? '')
    }))
  } catch {
    return []
  }
}

/** 节点 ID 下拉选项：BPMN 解析出的节点 ∪ 当前行已存在但不在列表中的 nodeId */
const nodeOptions = computed<OptionItem[]>(() => {
  const map = new Map<string, OptionItem>()
  nodes.value.forEach((n) => map.set(n.value, n))
  rows.value.forEach((r) => {
    if (r.nodeId && !map.has(r.nodeId)) {
      map.set(r.nodeId, { label: r.nodeId, value: r.nodeId })
    }
  })
  return Array.from(map.values())
})

/** 从 BPMN XML 中提取所有 UserTask 节点（id + name） */
function extractUserTasks(xml: string): OptionItem[] {
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  const result: OptionItem[] = []
  const seen = new Set<string>()
  const collect = (list: HTMLCollection | Element[]) => {
    Array.from(list).forEach((el) => {
      const id = el.getAttribute('id')
      if (!id || seen.has(id)) return
      seen.add(id)
      const name = el.getAttribute('name') || id
      result.push({ label: `${name} (${id})`, value: id })
    })
  }
  const BPMN_NS = 'http://www.omg.org/spec/BPMN/20100524/MODEL'
  collect(doc.getElementsByTagNameNS(BPMN_NS, 'userTask'))
  if (!result.length) collect(doc.getElementsByTagName('bpmn:userTask'))
  if (!result.length) collect(doc.getElementsByTagName('userTask'))
  return result
}

/** 拉取流程定义 BPMN XML 并解析节点 */
async function loadNodes() {
  if (!props.processDefinitionKey) {
    nodes.value = []
    return
  }
  loadingXml.value = true
  try {
    const xml = await getProcessDefinitionBpmnXml(props.processDefinitionKey)
    nodes.value = extractUserTasks(xml)
  } catch (e) {
    // 流程未部署或读取失败时清空节点选项，但保留手动输入能力
    nodes.value = []
    console.warn('[node-binding] 加载流程节点失败:', e)
  } finally {
    loadingXml.value = false
  }
}

/** 加载表单与微流下拉选项 */
async function loadOptions() {
  try {
    const page = await listForms({ page: 1, size: 1000 })
    forms.value = (page.records || []).map((f: LowCodeFormConfig) => ({
      label: f.name,
      value: f.code
    }))
  } catch {
    forms.value = []
  }
  try {
    const list = await getMicroflowList()
    microflows.value = (list || []).map((m: LowCodeMicroflow) => ({
      label: m.name,
      value: m.code
    }))
  } catch {
    microflows.value = []
  }
}

/** 同步行数据到父级（JSON 字符串） */
function emitChange() {
  emit('update:modelValue', JSON.stringify(rows.value))
}

function addRow() {
  rows.value.push({ nodeId: '', formCode: '', microflowCode: '' })
  emitChange()
}

function removeRow(index: number) {
  rows.value.splice(index, 1)
  emitChange()
}

function onRowChange() {
  emitChange()
}

/** 根据 BPMN 节点列表自动补全：若绑定行为空且存在节点，则预填一行 */
function autoFillIfEmpty() {
  if (rows.value.length === 0 && nodes.value.length > 0) {
    rows.value.push({ nodeId: '', formCode: '', microflowCode: '' })
    emitChange()
  }
}

// 外部 modelValue 变化时回填行（避免与自身 emit 形成回环：仅在不一致时回填）
watch(
  () => props.modelValue,
  (v) => {
    const parsed = parseBindings(v)
    if (JSON.stringify(parsed) !== JSON.stringify(rows.value)) {
      rows.value = parsed
    }
  },
  { immediate: true }
)

// 流程定义变化时重新拉取节点
watch(
  () => props.processDefinitionKey,
  async () => {
    await loadNodes()
    autoFillIfEmpty()
  },
  { immediate: true }
)

loadOptions()
</script>

<template>
  <div class="node-form-binding-panel">
    <div class="panel-toolbar">
      <el-button size="small" type="primary" @click="addRow">添加节点绑定</el-button>
      <span v-if="loadingXml" class="panel-hint">正在加载流程节点…</span>
      <span v-else-if="nodes.length === 0 && processDefinitionKey" class="panel-hint">
        未解析到 UserTask 节点（可手动输入 nodeId）。
      </span>
      <span v-else-if="!processDefinitionKey" class="panel-hint">
        请先选择已部署的流程定义。
      </span>
    </div>
    <el-table :data="rows" border size="small" empty-text="暂无节点绑定，点击「添加节点绑定」">
      <el-table-column label="节点 ID (nodeId)" min-width="220">
        <template #default="{ row }">
          <el-select
            v-model="row.nodeId"
            placeholder="选择流程节点"
            filterable
            allow-create
            default-first-option
            @change="onRowChange"
          >
            <el-option
              v-for="opt in nodeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="绑定表单 (formCode)" min-width="200">
        <template #default="{ row }">
          <el-select
            v-model="row.formCode"
            placeholder="选择审批表单"
            filterable
            clearable
            @change="onRowChange"
          >
            <el-option v-for="f in forms" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="回调微流 (microflowCode)" min-width="200">
        <template #default="{ row }">
          <el-select
            v-model="row.microflowCode"
            placeholder="可选：完成时回调微流"
            filterable
            clearable
            @change="onRowChange"
          >
            <el-option v-for="m in microflows" :key="m.value" :label="m.label" :value="m.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ $index }">
          <el-button size="small" type="danger" link @click="removeRow($index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped lang="scss">
.node-form-binding-panel {
  width: 100%;

  .panel-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;
  }

  .panel-hint {
    font-size: 12px;
    color: #909399;
  }

  :deep(.el-select) {
    width: 100%;
  }
}
</style>
