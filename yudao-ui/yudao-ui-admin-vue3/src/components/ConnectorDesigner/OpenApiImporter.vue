<!-- src/components/ConnectorDesigner/OpenApiImporter.vue -->
<script setup lang="ts">
/**
 * OpenAPI / Swagger 导入器（Task 9）。
 *
 * <p>借鉴 Power Apps Custom Connectors 的 "从 OpenAPI 导入" 功能：
 * <ol>
 *   <li>输入 OpenAPI JSON / YAML 的 URL，或直接粘贴内容</li>
 *   <li>解析 OpenAPI 3.0 规范：paths → operations</li>
 *   <li>每个 path + method 生成一个操作（name 来自 operationId 或 summary）</li>
 *   <li>预览解析结果表格，用户勾选要导入的操作</li>
 *   <li>确认后通过 emit 将选中操作合并到操作列表</li>
 * </ol></p>
 *
 * <p>解析失败时友好提示，不阻断流程。</p>
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import yaml from 'js-yaml'
import type { HttpMethod, KeyValueItem, RestOperation } from '@/api/lowcode-connector'

const props = defineProps<{
  /** 父组件 baseUrl，用于拼装 path（仅做提示） */
  baseUrl?: string
}>()

// 显式声明 props 已使用，避免 ESLint/TS 未使用告警
void props

const emit = defineEmits<{
  /** 确认导入：将选中的操作列表回传给父组件合并 */
  import: [operations: RestOperation[]]
}>()

interface ParsedOperationRow {
  selected: boolean
  name: string
  method: HttpMethod
  path: string
  summary: string
  headers: KeyValueItem[]
  params: KeyValueItem[]
}

const visible = ref(false)
const inputMode = ref<'url' | 'content'>('content')
const urlInput = ref('')
const contentInput = ref('')
const parsing = ref(false)
const parsedOperations = ref<ParsedOperationRow[]>([])

function open() {
  visible.value = true
  urlInput.value = ''
  contentInput.value = ''
  parsedOperations.value = []
  inputMode.value = 'content'
}

defineExpose({ open })

const hasParsed = computed(() => parsedOperations.value.length > 0)
const selectedCount = computed(() => parsedOperations.value.filter((r) => r.selected).length)
const allSelected = computed(
  () => parsedOperations.value.length > 0 && selectedCount.value === parsedOperations.value.length
)

function toggleAll(val: boolean) {
  parsedOperations.value.forEach((row) => {
    row.selected = val
  })
}

function handleSelectAllChange(val: boolean) {
  toggleAll(val)
}

/**
 * 解析 OpenAPI 文档。
 *
 * <p>支持 OpenAPI 3.0 与 Swagger 2.0 的 paths 结构。parameters 转 params/headers：
 * <ul>
 *   <li>in=query / in=path → params</li>
 *   <li>in=header → headers</li>
 * </ul></p>
 */
async function parse() {
  const source = inputMode.value === 'url' ? urlInput.value.trim() : contentInput.value.trim()
  if (!source) {
    ElMessage.warning(inputMode.value === 'url' ? '请输入 OpenAPI URL' : '请粘贴 OpenAPI 内容')
    return
  }

  parsing.value = true
  try {
    let raw: any
    if (inputMode.value === 'url') {
      const resp = await fetch(urlInput.value, { mode: 'cors' })
      if (!resp.ok) {
        throw new Error(`HTTP ${resp.status} ${resp.statusText}`)
      }
      const text = await resp.text()
      raw = tryParse(text)
    } else {
      raw = tryParse(contentInput.value)
    }

    if (!raw || typeof raw !== 'object') {
      throw new Error('无法解析为 JSON / YAML 对象')
    }

    const ops = extractOperations(raw)
    if (ops.length === 0) {
      ElMessage.warning('未在 OpenAPI 文档中找到任何 paths/operations')
      parsedOperations.value = []
      return
    }
    parsedOperations.value = ops
    ElMessage.success(`成功解析 ${ops.length} 个操作，请勾选要导入的项`)
  } catch (e: any) {
    ElMessage.error('OpenAPI 解析失败：' + (e?.message || String(e)))
    parsedOperations.value = []
  } finally {
    parsing.value = false
  }
}

/** 自动判断 JSON / YAML 并解析 */
function tryParse(text: string): any {
  const trimmed = text.trim()
  // 优先尝试 JSON（首字符是 { 或 [）
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      return JSON.parse(trimmed)
    } catch {
      // 降级到 YAML
    }
  }
  try {
    return yaml.load(trimmed)
  } catch (e) {
    // 再尝试一次 JSON（容错）
    try {
      return JSON.parse(trimmed)
    } catch {
      throw new Error('既不是合法 JSON 也不是合法 YAML：' + (e as Error).message)
    }
  }
}

/** 从 OpenAPI 文档提取所有 operations */
function extractOperations(doc: any): ParsedOperationRow[] {
  const paths = doc.paths
  if (!paths || typeof paths !== 'object') return []
  const rows: ParsedOperationRow[] = []
  const VALID_METHODS = ['get', 'post', 'put', 'delete', 'patch']
  for (const [path, pathItem] of Object.entries(paths)) {
    if (!pathItem || typeof pathItem !== 'object') continue
    for (const method of VALID_METHODS) {
      const opDef = (pathItem as any)[method]
      if (!opDef || typeof opDef !== 'object') continue
      const upperMethod = method.toUpperCase() as HttpMethod
      const name = opDef.operationId || opDef.summary || `${upperMethod.toLowerCase()} ${path}`
      const summary: string = opDef.summary || opDef.description || ''
      const { headers, params } = extractParameters(opDef.parameters, pathItem)
      rows.push({
        selected: true,
        name: String(name).replace(/\s+/g, '_'),
        method: upperMethod,
        path,
        summary,
        headers,
        params
      })
    }
  }
  return rows
}

/** 提取 parameters 为 headers / params */
function extractParameters(
  opParams: any[] | undefined,
  pathItem: any
): { headers: KeyValueItem[]; params: KeyValueItem[] } {
  const headers: KeyValueItem[] = []
  const params: KeyValueItem[] = []
  // path 级 parameters 也合并进来
  const allParams = [
    ...(Array.isArray(pathItem?.parameters) ? pathItem.parameters : []),
    ...(Array.isArray(opParams) ? opParams : [])
  ]
  for (const p of allParams) {
    if (!p || !p.name) continue
    const item: KeyValueItem = { key: String(p.name), value: '' }
    if (p.in === 'header') {
      headers.push(item)
    } else {
      // query / path / cookie 都归入 params
      params.push(item)
    }
  }
  return { headers, params }
}

function confirmImport() {
  const selected = parsedOperations.value.filter((r) => r.selected)
  if (selected.length === 0) {
    ElMessage.warning('请至少勾选一个操作')
    return
  }
  const ops: RestOperation[] = selected.map((r) => ({
    name: r.name,
    method: r.method,
    path: r.path,
    headers: r.headers.map((h) => ({ ...h })),
    params: r.params.map((p) => ({ ...p })),
    body: null
  }))
  emit('import', ops)
  ElMessage.success(`已导入 ${ops.length} 个操作`)
  visible.value = false
}

/** HTTP 方法的 tag 类型（用于表格颜色区分） */
function methodTagType(method: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (method) {
    case 'GET':
      return 'success'
    case 'POST':
      return 'warning'
    case 'PUT':
      return ''
    case 'DELETE':
      return 'danger'
    default:
      return 'info'
  }
}
</script>

<script lang="ts">
export default { name: 'OpenApiImporter' }
</script>

<template>
  <el-dialog
    v-model="visible"
    title="导入 OpenAPI / Swagger"
    width="880px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-radio-group v-model="inputMode" style="margin-bottom: 12px">
      <el-radio-button value="content">粘贴内容</el-radio-button>
      <el-radio-button value="url">URL 拉取</el-radio-button>
    </el-radio-group>

    <div v-if="inputMode === 'url'" style="margin-bottom: 12px">
      <el-input v-model="urlInput" placeholder="https://api.example.com/openapi.yaml">
        <template #prepend>URL</template>
      </el-input>
      <div class="hint">支持 CORS 跨域的可访问 URL，返回 JSON 或 YAML</div>
    </div>
    <div v-else style="margin-bottom: 12px">
      <el-input
        v-model="contentInput"
        type="textarea"
        :rows="8"
        placeholder="在此粘贴 OpenAPI 3.0 / Swagger 2.0 的 JSON 或 YAML 内容"
      />
    </div>

    <div style="margin-bottom: 12px">
      <el-button type="primary" :loading="parsing" @click="parse">解析</el-button>
      <el-button @click="open">重置</el-button>
    </div>

    <el-table v-if="hasParsed" :data="parsedOperations" border max-height="320">
      <el-table-column width="55" align="center">
        <template #header>
          <el-checkbox :model-value="allSelected" @change="handleSelectAllChange" />
        </template>
        <template #default="{ row }">
          <el-checkbox v-model="row.selected" />
        </template>
      </el-table-column>
      <el-table-column label="操作名" prop="name" min-width="160" show-overflow-tooltip />
      <el-table-column label="方法" prop="method" width="80">
        <template #default="{ row }">
          <el-tag :type="methodTagType(row.method)" size="small">{{ row.method }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="路径" prop="path" min-width="180" show-overflow-tooltip />
      <el-table-column label="说明" prop="summary" min-width="160" show-overflow-tooltip />
      <el-table-column label="参数数" width="80" align="center">
        <template #default="{ row }">
          {{ row.params.length + row.headers.length }}
        </template>
      </el-table-column>
    </el-table>

    <div v-else-if="!parsing" class="empty-tip">
      解析后此处展示操作列表，可勾选要导入的项
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="hasParsed" @click="toggleAll(true)">全选</el-button>
      <el-button v-if="hasParsed" @click="toggleAll(false)">全不选</el-button>
      <el-button type="primary" :disabled="!hasParsed || selectedCount === 0" @click="confirmImport">
        导入选中操作 ({{ selectedCount }})
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.empty-tip {
  padding: 32px;
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}
</style>
