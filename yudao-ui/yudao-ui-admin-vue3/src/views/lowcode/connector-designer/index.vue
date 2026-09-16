<script setup lang="ts">
/**
 * 连接器设计器主页面（批次 2 — T8/T9/T10）。
 *
 * <p>借鉴 Power Apps Custom Connectors 的分步表单流程，将原先的「表格 + JSON 文本框」
 * 重构为 el-steps 分步表单：
 * <ol>
 *   <li>Step 1 基本信息（code/name/description/type）</li>
 *   <li>Step 2 认证配置（REST）/ 数据源配置（DB）</li>
 *   <li>Step 3 操作列表（REST）/ SQL 模板（DB），REST 集成 OpenAPI 导入（T9）</li>
 *   <li>Step 4 响应映射</li>
 *   <li>Step 5 分页配置（仅 REST）</li>
 *   <li>Step 6 重试与超时</li>
 *   <li>Step 7 测试控制台（T10）</li>
 * </ol></p>
 *
 * <p>config 在保存时序列化为 JSON 字符串存入 connector.config；
 * 加载时通过 parseConnectorConfig 反向解析，并兼容旧版简单 JSON。</p>
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteConnector,
  getConnectorList,
  saveConnector,
  type ConnectorConfig,
  type ConnectorType,
  type AuthType,
  type LowCodeConnector,
  type PaginationConfig,
  type ResponseMapping,
  type RestOperation,
  type RetryConfig,
  type SqlTemplate
} from '@/api/lowcode-connector'
import {
  createDefaultConfig,
  parseConnectorConfig,
  serializeConnectorConfig
} from '@/components/ConnectorDesigner/config'
import StepBasicInfo from '@/components/ConnectorDesigner/StepBasicInfo.vue'
import StepAuth from '@/components/ConnectorDesigner/StepAuth.vue'
import StepOperations from '@/components/ConnectorDesigner/StepOperations.vue'
import StepResponseMapping from '@/components/ConnectorDesigner/StepResponseMapping.vue'
import StepPagination from '@/components/ConnectorDesigner/StepPagination.vue'
import StepRetry from '@/components/ConnectorDesigner/StepRetry.vue'
import TestConsole from '@/components/ConnectorDesigner/TestConsole.vue'

defineOptions({ name: 'ConnectorDesignerView' })

const list = ref<LowCodeConnector[]>([])
const dialogVisible = ref(false)
const activeStep = ref(0)
const saving = ref(false)

/** 列表加载 */
async function load() {
  list.value = await getConnectorList()
}

// ---------------- 分步表单状态 ----------------
/** 顶层字段（直接对应 LowCodeConnector 的非 config 字段） */
const basic = reactive({
  code: '',
  name: '',
  description: '',
  bizType: '',
  type: 'REST' as ConnectorType
})

/** 结构化 config（分步表单各 Step 共享） */
const config = reactive<ConnectorConfig>(createDefaultConfig('REST'))

/** 当前编辑的连接器 id（保存后用于测试） */
const editingId = ref<number | undefined>(undefined)

const steps = computed(() => {
  if (basic.type === 'REST') {
    return [
      { title: '基本信息', description: '编码 / 名称 / 类型' },
      { title: '认证配置', description: 'Base URL + Auth' },
      { title: '操作列表', description: 'REST 操作 + OpenAPI 导入' },
      { title: '响应映射', description: 'JSONPath → 实体字段' },
      { title: '分页配置', description: 'OFFSET / PAGE / NEXT_LINK' },
      { title: '重试与超时', description: 'maxAttempts / timeout' },
      { title: '测试', description: '测试控制台' }
    ]
  }
  return [
    { title: '基本信息', description: '编码 / 名称 / 类型' },
    { title: '数据源配置', description: 'JDBC + 连接池' },
    { title: 'SQL 模板', description: 'QUERY / UPDATE' },
    { title: '响应映射', description: '列 → 实体字段' },
    { title: '重试与超时', description: 'maxAttempts / timeout' },
    { title: '测试', description: '测试控制台' }
  ]
})

const isLastStep = computed(() => activeStep.value === steps.value.length - 1)

/** Step 5（分页）仅 REST 类型展示 */
function showStep(stepTitle: string): boolean {
  if (basic.type === 'DB' && stepTitle === '分页配置') return false
  return true
}

// ---------------- 新建 / 编辑 ----------------
function openNew() {
  editingId.value = undefined
  activeStep.value = 0
  Object.assign(basic, {
    code: '',
    name: '',
    description: '',
    bizType: '',
    type: 'REST'
  })
  resetConfig('REST')
  dialogVisible.value = true
}

function openEdit(row: LowCodeConnector) {
  editingId.value = row.id
  activeStep.value = 0
  basic.code = row.code
  basic.name = row.name
  basic.description = row.description || ''
  basic.bizType = row.bizType || ''
  basic.type = row.type
  const parsed = parseConnectorConfig(row.config, row.type)
  Object.assign(config, parsed)
  dialogVisible.value = true
}

function resetConfig(type: ConnectorType) {
  const fresh = createDefaultConfig(type)
  Object.assign(config, fresh)
}

/** 类型切换时重置 config（保留基本信息） */
function handleTypeChange(type: ConnectorType) {
  // 切换类型时配置结构不同，需重新初始化
  Object.assign(config, createDefaultConfig(type))
  activeStep.value = 0
}

// ---------------- 步骤导航 ----------------
function next() {
  if (activeStep.value === 0) {
    if (!basic.code.trim() || !basic.name.trim()) {
      ElMessage.warning('请填写编码和名称')
      return
    }
  }
  if (activeStep.value < steps.value.length - 1) {
    activeStep.value++
  }
}

function prev() {
  if (activeStep.value > 0) {
    activeStep.value--
  }
}

function goToStep(index: number) {
  activeStep.value = index
}

// ---------------- 保存 ----------------
function buildConfigObject(): ConnectorConfig {
  return {
    type: basic.type,
    baseUrl: config.baseUrl,
    authType: config.authType,
    username: config.username,
    password: config.password,
    token: config.token,
    headerName: config.headerName,
    apiKey: config.apiKey,
    operations: config.operations,
    driverClassName: config.driverClassName,
    dbUrl: config.dbUrl,
    dbUsername: config.dbUsername,
    dbPassword: config.dbPassword,
    maxPoolSize: config.maxPoolSize,
    sqlTemplates: config.sqlTemplates,
    responseMapping: config.responseMapping,
    pagination: config.pagination,
    retry: config.retry
  } as ConnectorConfig
}

function buildConnector(): LowCodeConnector {
  return {
    id: editingId.value,
    code: basic.code,
    name: basic.name,
    description: basic.description,
    bizType: basic.bizType,
    type: basic.type,
    config: serializeConnectorConfig(buildConfigObject()),
    status: 'ACTIVE'
  }
}

async function save() {
  if (!basic.code.trim() || !basic.name.trim()) {
    ElMessage.warning('请填写编码和名称')
    activeStep.value = 0
    return
  }
  saving.value = true
  try {
    const saved = await saveConnector(buildConnector())
    editingId.value = saved.id
    ElMessage.success('保存成功')
    await load()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || String(e)))
  } finally {
    saving.value = false
  }
}

async function saveAndClose() {
  if (!basic.code.trim() || !basic.name.trim()) {
    ElMessage.warning('请填写编码和名称')
    activeStep.value = 0
    return
  }
  saving.value = true
  try {
    await saveConnector(buildConnector())
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.message || String(e)))
  } finally {
    saving.value = false
  }
}

// ---------------- 列表操作 ----------------
async function remove(row: LowCodeConnector) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确认删除连接器「${row.name}」？`, '确认', { type: 'warning' })
    await deleteConnector(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch {
    /* cancelled or error */
  }
}

async function quickTest(row: LowCodeConnector) {
  // 兼容旧版快速测试入口：直接调用 testConnector
  const { testConnector } = await import('@/api/lowcode-connector')
  try {
    const result = await testConnector(row.code)
    ElMessage.success('测试结果: ' + JSON.stringify(result))
  } catch (e: any) {
    ElMessage.error('测试失败：' + (e?.message || String(e)))
  }
}

// ---------------- 类型变更监听（来自 StepBasicInfo） ----------------
function handleBasicTypeChange(val: ConnectorType) {
  if (val !== basic.type) {
    basic.type = val
    handleTypeChange(val)
  }
}

// 计算属性代理（用于 v-model:sync 到子组件的 update 事件）
const configProxy = {
  // Step 2 (Auth/DataSource)
  authType: computed<AuthType>({
    get: () => config.authType ?? 'NONE',
    set: (v) => (config.authType = v)
  }),
  username: computed<string>({
    get: () => config.username || '',
    set: (v) => (config.username = v)
  }),
  password: computed<string>({
    get: () => config.password || '',
    set: (v) => (config.password = v)
  }),
  token: computed<string>({
    get: () => config.token || '',
    set: (v) => (config.token = v)
  }),
  headerName: computed<string>({
    get: () => config.headerName || '',
    set: (v) => (config.headerName = v)
  }),
  apiKey: computed<string>({
    get: () => config.apiKey || '',
    set: (v) => (config.apiKey = v)
  }),
  baseUrl: computed<string>({
    get: () => config.baseUrl || '',
    set: (v) => (config.baseUrl = v)
  }),
  driverClassName: computed<string>({
    get: () => config.driverClassName || 'com.mysql.cj.jdbc.Driver',
    set: (v) => (config.driverClassName = v)
  }),
  dbUrl: computed<string>({
    get: () => config.dbUrl || '',
    set: (v) => (config.dbUrl = v)
  }),
  dbUsername: computed<string>({
    get: () => config.dbUsername || '',
    set: (v) => (config.dbUsername = v)
  }),
  dbPassword: computed<string>({
    get: () => config.dbPassword || '',
    set: (v) => (config.dbPassword = v)
  }),
  maxPoolSize: computed<number>({
    get: () => config.maxPoolSize ?? 10,
    set: (v) => (config.maxPoolSize = v)
  }),
  // Step 3
  operations: computed<RestOperation[]>({
    get: () => config.operations || [],
    set: (v) => (config.operations = v)
  }),
  sqlTemplates: computed<SqlTemplate[]>({
    get: () => config.sqlTemplates || [],
    set: (v) => (config.sqlTemplates = v)
  }),
  // Step 4
  responseMapping: computed<ResponseMapping[]>({
    get: () => config.responseMapping || [],
    set: (v) => (config.responseMapping = v)
  }),
  // Step 5
  pagination: computed<PaginationConfig>({
    get: () => config.pagination || { type: 'NONE' },
    set: (v) => (config.pagination = v)
  }),
  // Step 6
  retry: computed<RetryConfig>({
    get: () =>
      config.retry || { maxAttempts: 3, waitMillis: 1000, timeoutMillis: 30000, retryOnStatusCodes: [500, 502, 503] },
    set: (v) => (config.retry = v)
  })
}

// 用于测试控制台的连接器编码
const connectorCodeForTest = computed(() => basic.code)

onMounted(load)

import { onMounted } from 'vue'
</script>

<template>
  <div style="padding: 16px">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>连接器配置</span>
          <el-button type="primary" @click="openNew">新建连接器</el-button>
        </div>
      </template>
      <el-table :data="list">
        <el-table-column label="编码" prop="code" min-width="140" show-overflow-tooltip />
        <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" prop="type" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === 'REST' ? '' : 'success'">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="业务" prop="bizType" width="120" show-overflow-tooltip />
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="success" @click="quickTest(row)">快速测试</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 分步表单对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑连接器' : '新建连接器'"
      width="960px"
      :close-on-click-modal="false"
      destroy-on-close
      class="connector-designer-dialog"
    >
      <el-steps :active="activeStep" finish-status="success" align-center class="step-header">
        <el-step
          v-for="(s, idx) in steps"
          :key="s.title"
          :title="s.title"
          :description="s.description"
          @click="goToStep(idx)"
        />
      </el-steps>

      <div class="step-body">
        <!-- Step 1 基本信息 -->
        <div v-show="activeStep === 0">
          <StepBasicInfo
            v-model:code="basic.code"
            v-model:name="basic.name"
            v-model:description="basic.description"
            v-model:bizType="basic.bizType"
            v-model:type="basic.type"
            @update:type="handleBasicTypeChange"
          />
        </div>

        <!-- Step 2 认证 / 数据源 -->
        <div v-show="activeStep === 1">
          <StepAuth
            :type="basic.type"
            v-model:auth-type="configProxy.authType.value"
            v-model:username="configProxy.username.value"
            v-model:password="configProxy.password.value"
            v-model:token="configProxy.token.value"
            v-model:header-name="configProxy.headerName.value"
            v-model:api-key="configProxy.apiKey.value"
            v-model:base-url="configProxy.baseUrl.value"
            v-model:driver-class-name="configProxy.driverClassName.value"
            v-model:db-url="configProxy.dbUrl.value"
            v-model:db-username="configProxy.dbUsername.value"
            v-model:db-password="configProxy.dbPassword.value"
            v-model:max-pool-size="configProxy.maxPoolSize.value"
          />
        </div>

        <!-- Step 3 操作列表 / SQL 模板 -->
        <div v-show="activeStep === 2">
          <StepOperations
            :type="basic.type"
            :operations="configProxy.operations.value"
            :sql-templates="configProxy.sqlTemplates.value"
            :base-url="configProxy.baseUrl.value"
            @update:operations="configProxy.operations.value = $event"
            @update:sql-templates="configProxy.sqlTemplates.value = $event"
          />
        </div>

        <!-- Step 4 响应映射 -->
        <div v-show="activeStep === 3">
          <StepResponseMapping
            :response-mapping="configProxy.responseMapping.value"
            @update:response-mapping="configProxy.responseMapping.value = $event"
          />
        </div>

        <!-- Step 5 分页（仅 REST） -->
        <div v-show="activeStep === 4 && basic.type === 'REST'">
          <StepPagination
            :pagination="configProxy.pagination.value"
            @update:pagination="configProxy.pagination.value = $event"
          />
        </div>

        <!-- Step 6 重试与超时 -->
        <div v-show="(basic.type === 'REST' && activeStep === 5) || (basic.type === 'DB' && activeStep === 4)">
          <StepRetry
            :retry="configProxy.retry.value"
            @update:retry="configProxy.retry.value = $event"
          />
        </div>

        <!-- Step 7 测试 -->
        <div v-show="(basic.type === 'REST' && activeStep === 6) || (basic.type === 'DB' && activeStep === 5)">
          <TestConsole
            :connector-code="connectorCodeForTest"
            :type="basic.type"
            :operations="configProxy.operations.value"
            :sql-templates="configProxy.sqlTemplates.value"
          />
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">关闭</el-button>
          <el-button :disabled="activeStep === 0" @click="prev">上一步</el-button>
          <el-button v-if="!isLastStep" type="primary" @click="next">下一步</el-button>
          <el-button type="success" :loading="saving" @click="save">保存</el-button>
          <el-button type="primary" :loading="saving" @click="saveAndClose">保存并关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.step-header {
  margin-bottom: 20px;
  cursor: pointer;
}
.step-body {
  min-height: 360px;
  padding: 8px 4px;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
:deep(.connector-designer-dialog) {
  .el-dialog__body {
    max-height: 70vh;
    overflow-y: auto;
  }
}
</style>
