<!-- src/components/ConnectorDesigner/TestConsole.vue -->
<script setup lang="ts">
/**
 * 连接器测试控制台（Task 10）。
 *
 * <p>借鉴 Power Apps Custom Connectors 的 Test 标签页：
 * <ol>
 *   <li>选择操作（下拉）</li>
 *   <li>根据操作的 params / body 动态生成输入表单</li>
 *   <li>点击「发送」调用 testOperation API 测试单个操作</li>
 *   <li>展示响应：状态码（带颜色）、Headers 表格、Body（JSON 树形）、耗时</li>
 *   <li>请求历史持久化到 localStorage（key: lowcode:connector-test-history:${connectorCode}），
 *       最多保留 20 条，支持点击「重放」回填表单与响应，支持「清空历史」</li>
 * </ol></p>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import JsonTree from './JsonTree.vue'
import {
  testOperation,
  type ConnectorType,
  type RestOperation,
  type SqlTemplate,
  type TestOperationResult
} from '@/api/lowcode-connector'

const props = defineProps<{
  /** 连接器编码（已保存后用于调用测试接口） */
  connectorCode: string
  type: ConnectorType
  operations: RestOperation[]
  sqlTemplates: SqlTemplate[]
}>()

/**
 * 测试历史记录项。
 *
 * <p>持久化到 localStorage（key: lowcode:connector-test-history:${connectorCode}），
 * 最多保留 20 条。包含请求/响应全部信息，支持点击重放（回填表单）。</p>
 */
interface TestHistoryItem {
  timestamp: number
  operation: string
  method: string
  url: string
  requestHeaders: Record<string, string>
  requestBody: unknown
  status: number
  responseHeaders: Record<string, string>
  responseBody: unknown
  duration: number
  error?: string
}

/** 历史记录保留上限 */
const HISTORY_LIMIT = 20

/** 历史记录折叠面板展开状态（el-collapse v-model 为展开项 name 数组） */
const historyCollapsed = ref<string[]>([])

const selectedOpName = ref<string>('')
const paramValues = ref<Record<string, string>>({})
const bodyText = ref<string>('')
const sending = ref(false)
const currentResult = ref<TestOperationResult | null>(null)
const history = ref<TestHistoryItem[]>([])

/** 拼接 localStorage key */
function historyStorageKey(): string {
  return `lowcode:connector-test-history:${props.connectorCode}`
}

/**
 * 从 localStorage 读取历史记录。
 *
 * <p>容错处理：connectorCode 为空、localStorage 不可用、JSON 解析失败、
 * 数据结构异常时均返回空数组，避免影响主流程。</p>
 */
function loadHistoryFromStorage(): TestHistoryItem[] {
  if (!props.connectorCode) return []
  try {
    const raw = localStorage.getItem(historyStorageKey())
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    // 仅保留符合结构的最小校验项
    return parsed
      .filter((item): item is TestHistoryItem =>
        item !== null &&
        typeof item === 'object' &&
        typeof (item as TestHistoryItem).timestamp === 'number' &&
        typeof (item as TestHistoryItem).operation === 'string'
      )
      .slice(0, HISTORY_LIMIT)
  } catch {
    return []
  }
}

/** 将当前 history 持久化到 localStorage（失败时静默降级） */
function saveHistoryToStorage() {
  if (!props.connectorCode) return
  try {
    localStorage.setItem(historyStorageKey(), JSON.stringify(history.value))
  } catch {
    // 容量超限或隐私模式下静默忽略
  }
}

const availableOps = computed(() => {
  if (props.type === 'REST') {
    return props.operations
  }
  return props.sqlTemplates.map((t) => ({
    name: t.name,
    method: 'GET' as const,
    path: '',
    headers: [],
    params: [],
    body: null
  }))
})

const currentOp = computed<RestOperation | null>(() => {
  if (!selectedOpName.value) return null
  if (props.type === 'REST') {
    return props.operations.find((o) => o.name === selectedOpName.value) || null
  }
  const t = props.sqlTemplates.find((s) => s.name === selectedOpName.value)
  if (!t) return null
  return {
    name: t.name,
    method: 'GET',
    path: '',
    headers: [],
    params: [],
    body: null
  }
})

const showBody = computed(
  () =>
    props.type === 'REST' &&
    currentOp.value !== null &&
    (currentOp.value.method === 'POST' || currentOp.value.method === 'PUT')
)

watch(selectedOpName, () => {
  resetForm()
})

function resetForm() {
  paramValues.value = {}
  if (currentOp.value) {
    currentOp.value.params.forEach((p) => {
      paramValues.value[p.key] = ''
    })
  }
  bodyText.value = ''
  currentResult.value = null
}

function statusClass(code: number | undefined): string {
  if (code === undefined) return 'status-unknown'
  if (code >= 200 && code < 300) return 'status-2xx'
  if (code >= 300 && code < 400) return 'status-3xx'
  if (code >= 400 && code < 500) return 'status-4xx'
  if (code >= 500) return 'status-5xx'
  return 'status-unknown'
}

function statusText(code: number | undefined): string {
  if (code === undefined) return '—'
  return String(code)
}

const headerRows = computed<[string, string][]>(() => {
  if (!currentResult.value?.headers) return []
  return Object.entries(currentResult.value.headers)
})

const hasResult = computed(() => currentResult.value !== null)
const hasHistory = computed(() => history.value.length > 0)

async function send() {
  if (!selectedOpName.value) {
    ElMessage.warning('请先选择操作')
    return
  }
  if (!props.connectorCode) {
    ElMessage.warning('请先保存连接器后再测试')
    return
  }

  // 解析 body
  let body: unknown = undefined
  if (showBody.value && bodyText.value.trim()) {
    try {
      body = JSON.parse(bodyText.value)
    } catch {
      ElMessage.error('Body 不是合法 JSON')
      return
    }
  }

  // 组装 params（过滤空值）
  const params: Record<string, unknown> = {}
  Object.entries(paramValues.value).forEach(([k, v]) => {
    if (v !== '' && v !== null && v !== undefined) {
      params[k] = v
    }
  })

  // 构建请求侧元信息（用于历史记录回放）
  const op = currentOp.value
  const requestHeaders: Record<string, string> = {}
  if (op) {
    op.headers.forEach((h) => {
      if (h.key) requestHeaders[h.key] = h.value
    })
  }

  sending.value = true
  const timestamp = Date.now()
  const historyItem: TestHistoryItem = {
    timestamp,
    operation: selectedOpName.value,
    method: op?.method || '',
    url: op?.path || '',
    requestHeaders,
    requestBody: body,
    status: 0,
    responseHeaders: {},
    responseBody: undefined,
    duration: 0
  }

  try {
    const result = await testOperation(props.connectorCode, {
      operationName: selectedOpName.value,
      params,
      body
    })
    currentResult.value = result
    // 回填响应侧字段
    historyItem.status = result.statusCode || 0
    historyItem.responseHeaders = result.headers || {}
    historyItem.responseBody = result.body
    historyItem.duration = result.durationMillis || 0
    if (result.error) {
      historyItem.error = result.error
      ElMessage.warning('测试返回错误：' + result.error)
    } else if (result.statusCode && result.statusCode >= 400) {
      ElMessage.warning(`响应状态码 ${result.statusCode}`)
    } else {
      ElMessage.success('测试完成')
    }
  } catch (e: any) {
    const errorMsg = e?.message || String(e)
    historyItem.error = errorMsg
    currentResult.value = { error: errorMsg }
    ElMessage.error('测试请求失败：' + errorMsg)
  } finally {
    sending.value = false
    // 入历史，最多保留 HISTORY_LIMIT 条
    history.value.unshift(historyItem)
    if (history.value.length > HISTORY_LIMIT) {
      history.value = history.value.slice(0, HISTORY_LIMIT)
    }
    // 持久化到 localStorage
    saveHistoryToStorage()
  }
}

/**
 * 重放历史记录：填充操作选择 + Body 文本，并展示历史响应。
 *
 * <p>选择操作后会触发 selectedOpName 的 watch，自动 resetForm 重置参数表单，
 * 故通过 setTimeout 在 watch 触发后再回填 Body 与响应。</p>
 */
function loadHistory(item: TestHistoryItem) {
  selectedOpName.value = item.operation
  // 等待 currentOp 计算完成后再回填
  setTimeout(() => {
    // 重置参数表单为操作定义的默认空值
    paramValues.value = {}
    if (currentOp.value) {
      currentOp.value.params.forEach((p) => {
        paramValues.value[p.key] = ''
      })
    }
    bodyText.value =
      item.requestBody !== undefined && item.requestBody !== null
        ? safeStringify(item.requestBody)
        : ''
    // 还原响应展示
    currentResult.value = {
      statusCode: item.status || undefined,
      headers: item.responseHeaders,
      body: item.responseBody,
      durationMillis: item.duration || undefined,
      error: item.error
    }
  }, 0)
}

/** 安全序列化（处理循环引用等异常） */
function safeStringify(v: unknown): string {
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v)
  }
}

/** 清空历史记录（含 localStorage） */
async function clearHistory() {
  try {
    await ElMessageBox.confirm('确认清空所有历史记录？此操作不可恢复。', '清空确认', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消'
    })
  } catch {
    return // 用户取消
  }
  history.value = []
  currentResult.value = null
  if (props.connectorCode) {
    try {
      localStorage.removeItem(historyStorageKey())
    } catch {
      /* ignore */
    }
  }
}

function formatTime(ts: number): string {
  const d = new Date(ts)
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${hh}:${mm}:${ss}`
}

/** 完整日期时间格式（用于历史记录展开行展示） */
function formatDateTime(ts: number): string {
  const d = new Date(ts)
  const yyyy = d.getFullYear()
  const MM = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${MM}-${dd} ${formatTime(ts)}`
}

// 组件挂载时加载历史记录
onMounted(() => {
  history.value = loadHistoryFromStorage()
})

// connectorCode 变化时重新加载对应连接器的历史
watch(
  () => props.connectorCode,
  () => {
    history.value = loadHistoryFromStorage()
  }
)
</script>

<template>
  <div class="test-console">
    <el-alert
      v-if="!props.connectorCode"
      title="请先保存连接器后再进行测试"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />

    <el-row :gutter="16">
      <!-- 左侧：请求构建 -->
      <el-col :span="12">
        <el-card shadow="never" class="panel">
          <template #header>
            <span class="panel-title">请求构建</span>
          </template>
          <el-form label-width="100px" size="small">
            <el-form-item label="选择操作">
              <el-select
                v-model="selectedOpName"
                placeholder="请选择操作"
                style="width: 100%"
                filterable
              >
                <el-option
                  v-for="op in availableOps"
                  :key="op.name"
                  :label="op.name"
                  :value="op.name"
                />
              </el-select>
            </el-form-item>

            <template v-if="currentOp">
              <el-form-item v-if="props.type === 'REST'" label="HTTP">
                <el-tag size="small">{{ currentOp.method }}</el-tag>
                <span class="op-path">{{ currentOp.path || '—' }}</span>
              </el-form-item>

              <el-divider content-position="left">参数</el-divider>
              <div v-if="currentOp.params.length === 0" class="muted-tip">该操作无参数</div>
              <el-form-item
                v-for="p in currentOp.params"
                :key="p.key"
                :label="p.key"
              >
                <el-input v-model="paramValues[p.key]" :placeholder="p.value || '请输入值'" />
              </el-form-item>

              <template v-if="showBody">
                <el-divider content-position="left">请求 Body</el-divider>
                <el-form-item label="Body">
                  <el-input
                    v-model="bodyText"
                    type="textarea"
                    :rows="6"
                    placeholder='{"key":"value"}'
                  />
                </el-form-item>
              </template>
            </template>

            <el-form-item>
              <el-button
                type="primary"
                :loading="sending"
                :disabled="!selectedOpName || !props.connectorCode"
                @click="send"
              >
                发送请求
              </el-button>
              <el-button :disabled="!selectedOpName" @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：响应展示 -->
      <el-col :span="12">
        <el-card shadow="never" class="panel">
          <template #header>
            <span class="panel-title">响应结果</span>
          </template>
          <div v-if="!hasResult" class="muted-tip">点击「发送请求」后在此查看响应</div>
          <div v-else class="response-area">
            <!-- 状态码 + 耗时 -->
            <div class="response-meta">
              <div class="meta-item">
                <span class="meta-label">状态码</span>
                <span class="status-badge" :class="statusClass(currentResult?.statusCode)">
                  {{ statusText(currentResult?.statusCode) }}
                </span>
              </div>
              <div class="meta-item">
                <span class="meta-label">耗时</span>
                <span class="meta-value">
                  {{ currentResult?.durationMillis != null ? currentResult.durationMillis + ' ms' : '—' }}
                </span>
              </div>
              <div v-if="currentResult?.error" class="meta-item">
                <span class="meta-label">错误</span>
                <span class="meta-value error-text">{{ currentResult.error }}</span>
              </div>
            </div>

            <!-- 响应 Headers -->
            <div v-if="headerRows.length > 0" class="response-section">
              <div class="section-title">响应 Headers</div>
              <el-table :data="headerRows" size="small" border max-height="160">
                <el-table-column label="名称" width="180" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="header-name">{{ row[0] }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="值" show-overflow-tooltip>
                  <template #default="{ row }">{{ row[1] }}</template>
                </el-table-column>
              </el-table>
            </div>

            <!-- 响应 Body -->
            <div class="response-section">
              <div class="section-title">响应 Body</div>
              <div v-if="currentResult?.body === undefined || currentResult?.body === null" class="muted-tip">
                无响应体
              </div>
              <div v-else class="body-tree">
                <JsonTree :data="currentResult.body" :default-expanded="true" />
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 请求历史（持久化到 localStorage，最多 20 条） -->
    <el-collapse v-model="historyCollapsed" class="history-collapse">
      <el-collapse-item name="history">
        <template #title>
          <div class="history-header">
            <span class="panel-title">
              历史记录（{{ history.length }}/{{ HISTORY_LIMIT }}，持久化）
            </span>
            <el-button
              v-if="hasHistory"
              size="small"
              link
              type="danger"
              @click.stop="clearHistory"
            >清空历史</el-button>
          </div>
        </template>
        <div v-if="!hasHistory" class="muted-tip" style="padding: 12px">
          暂无历史记录。执行测试后将自动保存到浏览器本地，下次打开仍可查看。
        </div>
        <el-table v-else :data="history" size="small" border>
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ formatDateTime(row.timestamp) }}</template>
          </el-table-column>
          <el-table-column label="操作" prop="operation" min-width="140" show-overflow-tooltip />
          <el-table-column label="方法" prop="method" width="80" align="center" />
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <span
                v-if="row.status"
                class="status-badge"
                :class="statusClass(row.status)"
              >
                {{ row.status }}
              </span>
              <span v-else-if="row.error" class="status-badge status-5xx">ERR</span>
              <span v-else class="muted-tip">—</span>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90" align="center">
            <template #default="{ row }">
              {{ row.duration ? row.duration + ' ms' : '—' }}
            </template>
          </el-table-column>
          <el-table-column label="请求 Body" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.requestBody === undefined || row.requestBody === null ? '—' : safeStringify(row.requestBody) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="loadHistory(row)">重放</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script lang="ts">
export default { name: 'TestConsole' }
</script>

<style scoped lang="scss">
.test-console {
  .panel {
    margin-bottom: 12px;
  }
  .panel-title {
    font-weight: 600;
  }
  .op-path {
    margin-left: 8px;
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .muted-tip {
    color: var(--el-text-color-placeholder);
    font-size: 13px;
    padding: 8px 0;
  }
  .response-meta {
    display: flex;
    gap: 24px;
    align-items: center;
    padding: 10px 12px;
    background: var(--el-fill-color-light);
    border-radius: 4px;
    margin-bottom: 12px;
    flex-wrap: wrap;
  }
  .meta-item {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .meta-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .meta-value {
    font-size: 13px;
    font-weight: 600;
    &.error-text {
      color: var(--el-color-danger);
    }
  }
  .status-badge {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 10px;
    font-size: 12px;
    font-weight: 600;
    color: #fff;
    &.status-2xx {
      background: #67c23a;
    }
    &.status-3xx {
      background: #909399;
    }
    &.status-4xx {
      background: #e6a23c;
    }
    &.status-5xx {
      background: #f56c6c;
    }
    &.status-unknown {
      background: #909399;
    }
  }
  .response-section {
    margin-top: 12px;
  }
  .section-title {
    font-size: 13px;
    font-weight: 600;
    margin-bottom: 6px;
    color: var(--el-text-color-primary);
  }
  .body-tree {
    padding: 10px;
    background: var(--el-fill-color-light);
    border-radius: 4px;
    max-height: 360px;
    overflow: auto;
    border: 1px solid var(--el-border-color-lighter);
  }
  .header-name {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 12px;
    color: #871094;
  }
  .history-collapse {
    margin-top: 12px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 4px;
  }
  .history-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
    padding-right: 12px;
  }
}
</style>
