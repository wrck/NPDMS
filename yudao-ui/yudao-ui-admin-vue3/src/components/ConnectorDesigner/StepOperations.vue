<!-- src/components/ConnectorDesigner/StepOperations.vue -->
<script setup lang="ts">
/**
 * 连接器分步表单 — Step 3 操作列表（REST）/ SQL 模板（DB）。
 *
 * <p>REST 操作表格：operationName、method、path、headers（KV 表）、body（JSON textarea）、
 * params（KV 表）。支持新增 / 编辑 / 删除。提供 "导入 OpenAPI" 按钮（Task 9）批量导入。</p>
 *
 * <p>DB SQL 模板列表：operationName、sqlType（QUERY/UPDATE）、sqlTemplate。</p>
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import OpenApiImporter from './OpenApiImporter.vue'
import {
  createEmptyRestOperation,
  createEmptySqlTemplate
} from '@/components/ConnectorDesigner/config'
import type {
  ConnectorType,
  HttpMethod,
  KeyValueItem,
  RestOperation,
  SqlTemplate,
  SqlType
} from '@/api/lowcode-connector'

const props = defineProps<{
  type: ConnectorType
  operations: RestOperation[]
  sqlTemplates: SqlTemplate[]
  baseUrl?: string
}>()

const emit = defineEmits<{
  'update:operations': [value: RestOperation[]]
  'update:sqlTemplates': [value: SqlTemplate[]]
}>()

const METHODS: HttpMethod[] = ['GET', 'POST', 'PUT', 'DELETE']
const SQL_TYPES: SqlType[] = ['QUERY', 'UPDATE']

// REST 操作编辑对话框
const restDialogVisible = ref(false)
const editingRestIndex = ref(-1)
const editingRest = ref<RestOperation>(createEmptyRestOperation())

// SQL 模板编辑对话框
const sqlDialogVisible = ref(false)
const editingSqlIndex = ref(-1)
const editingSql = ref<SqlTemplate>(createEmptySqlTemplate())

const openApiImporterRef = ref<InstanceType<typeof OpenApiImporter> | null>(null)

// ---------------- REST 操作 ----------------
function addRestOperation() {
  editingRestIndex.value = -1
  editingRest.value = createEmptyRestOperation()
  restDialogVisible.value = true
}

function editRestOperation(index: number) {
  editingRestIndex.value = index
  const op = props.operations[index]
  editingRest.value = {
    name: op.name,
    method: op.method,
    path: op.path,
    headers: op.headers.map((h) => ({ ...h })),
    params: op.params.map((p) => ({ ...p })),
    body: op.body
  }
  restDialogVisible.value = true
}

function saveRestOperation() {
  if (!editingRest.value.name.trim()) {
    ElMessage.warning('请输入操作名')
    return
  }
  const op: RestOperation = {
    name: editingRest.value.name,
    method: editingRest.value.method,
    path: editingRest.value.path,
    headers: editingRest.value.headers.filter((h) => h.key.trim()),
    params: editingRest.value.params.filter((p) => p.key.trim()),
    body: editingRest.value.body?.trim() ? editingRest.value.body.trim() : null
  }
  if (editingRestIndex.value === -1) {
    emit('update:operations', [...props.operations, op])
  } else {
    const updated = [...props.operations]
    updated[editingRestIndex.value] = op
    emit('update:operations', updated)
  }
  restDialogVisible.value = false
}

function removeRestOperation(index: number) {
  const updated = [...props.operations]
  updated.splice(index, 1)
  emit('update:operations', updated)
}

// headers / params KV 表操作
function addKv(list: KeyValueItem[]) {
  list.push({ key: '', value: '' })
}
function removeKv(list: KeyValueItem[], index: number) {
  list.splice(index, 1)
}

// OpenAPI 导入
function openImporter() {
  openApiImporterRef.value?.open()
}

function handleImport(ops: RestOperation[]) {
  // 合并：去重（按 name），已存在则跳过
  const existingNames = new Set(props.operations.map((o) => o.name))
  const toAdd = ops.filter((o) => !existingNames.has(o.name))
  const dupCount = ops.length - toAdd.length
  if (toAdd.length === 0) {
    ElMessage.warning(`全部 ${ops.length} 个操作已存在（按名称去重），未导入新操作`)
    return
  }
  emit('update:operations', [...props.operations, ...toAdd])
  if (dupCount > 0) {
    ElMessage.info(`已新增 ${toAdd.length} 个操作，跳过 ${dupCount} 个重名操作`)
  }
}

// ---------------- SQL 模板 ----------------
function addSqlTemplate() {
  editingSqlIndex.value = -1
  editingSql.value = createEmptySqlTemplate()
  sqlDialogVisible.value = true
}

function editSqlTemplate(index: number) {
  editingSqlIndex.value = index
  const t = props.sqlTemplates[index]
  editingSql.value = { name: t.name, sqlType: t.sqlType, sqlTemplate: t.sqlTemplate }
  sqlDialogVisible.value = true
}

function saveSqlTemplate() {
  if (!editingSql.value.name.trim()) {
    ElMessage.warning('请输入操作名')
    return
  }
  if (!editingSql.value.sqlTemplate.trim()) {
    ElMessage.warning('请输入 SQL 模板')
    return
  }
  const t: SqlTemplate = {
    name: editingSql.value.name,
    sqlType: editingSql.value.sqlType,
    sqlTemplate: editingSql.value.sqlTemplate
  }
  if (editingSqlIndex.value === -1) {
    emit('update:sqlTemplates', [...props.sqlTemplates, t])
  } else {
    const updated = [...props.sqlTemplates]
    updated[editingSqlIndex.value] = t
    emit('update:sqlTemplates', updated)
  }
  sqlDialogVisible.value = false
}

function removeSqlTemplate(index: number) {
  const updated = [...props.sqlTemplates]
  updated.splice(index, 1)
  emit('update:sqlTemplates', updated)
}

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

<template>
  <div class="step-operations">
    <!-- REST 操作列表 -->
    <template v-if="props.type === 'REST'">
      <div class="step-toolbar">
        <el-button type="primary" size="small" @click="addRestOperation">新增操作</el-button>
        <el-button size="small" @click="openImporter">导入 OpenAPI</el-button>
      </div>
      <el-table :data="props.operations" border size="small">
        <el-table-column label="操作名" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="方法" prop="method" width="80">
          <template #default="{ row }">
            <el-tag :type="methodTagType(row.method)" size="small">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路径" prop="path" min-width="180" show-overflow-tooltip />
        <el-table-column label="Headers" width="90" align="center">
          <template #default="{ row }">{{ row.headers.length }}</template>
        </el-table-column>
        <el-table-column label="Params" width="80" align="center">
          <template #default="{ row }">{{ row.params.length }}</template>
        </el-table-column>
        <el-table-column label="Body" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.body" size="small" type="info">有</el-tag>
            <span v-else class="dash">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ $index }">
            <el-button size="small" link @click="editRestOperation($index)">编辑</el-button>
            <el-button size="small" link type="danger" @click="removeRestOperation($index)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="props.operations.length === 0"
        description="暂无操作，点击「新增操作」或「导入 OpenAPI」"
        :image-size="60"
      />
    </template>

    <!-- DB SQL 模板列表 -->
    <template v-else>
      <div class="step-toolbar">
        <el-button type="primary" size="small" @click="addSqlTemplate">新增 SQL 模板</el-button>
      </div>
      <el-table :data="props.sqlTemplates" border size="small">
        <el-table-column label="操作名" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" prop="sqlType" width="100">
          <template #default="{ row }">
            <el-tag :type="row.sqlType === 'QUERY' ? 'success' : 'warning'" size="small">
              {{ row.sqlType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="SQL 模板" prop="sqlTemplate" min-width="280" show-overflow-tooltip />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ $index }">
            <el-button size="small" link @click="editSqlTemplate($index)">编辑</el-button>
            <el-button size="small" link type="danger" @click="removeSqlTemplate($index)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="props.sqlTemplates.length === 0"
        description="暂无 SQL 模板，点击「新增 SQL 模板」"
        :image-size="60"
      />
    </template>

    <!-- REST 操作编辑对话框 -->
    <el-dialog
      v-model="restDialogVisible"
      :title="editingRestIndex === -1 ? '新增操作' : '编辑操作'"
      width="720px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="editingRest" label-width="90px" size="small">
        <el-form-item label="操作名" required>
          <el-input v-model="editingRest.name" placeholder="如 getUser" />
        </el-form-item>
        <el-form-item label="HTTP 方法" required>
          <el-select v-model="editingRest.method" style="width: 160px">
            <el-option v-for="m in METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径" required>
          <el-input v-model="editingRest.path" placeholder="/users/{id}" />
          <div class="field-hint">支持路径参数，如 /users/{id}</div>
        </el-form-item>
        <el-form-item label="Headers">
          <div class="kv-list">
            <div v-for="(kv, idx) in editingRest.headers" :key="idx" class="kv-row">
              <el-input v-model="kv.key" placeholder="key" style="width: 180px" />
              <el-input v-model="kv.value" placeholder="value" style="flex: 1" />
              <el-button link type="danger" @click="removeKv(editingRest.headers, idx)">
                删除
              </el-button>
            </div>
            <el-button size="small" @click="addKv(editingRest.headers)">+ 添加 Header</el-button>
          </div>
        </el-form-item>
        <el-form-item label="Params">
          <div class="kv-list">
            <div v-for="(kv, idx) in editingRest.params" :key="idx" class="kv-row">
              <el-input v-model="kv.key" placeholder="key" style="width: 180px" />
              <el-input v-model="kv.value" placeholder="value" style="flex: 1" />
              <el-button link type="danger" @click="removeKv(editingRest.params, idx)">
                删除
              </el-button>
            </div>
            <el-button size="small" @click="addKv(editingRest.params)">+ 添加 Param</el-button>
          </div>
        </el-form-item>
        <el-form-item label="Body">
          <el-input
            v-model="editingRest.body"
            type="textarea"
            :rows="5"
            placeholder='JSON 请求体，如 {"name":"test"}（GET/DELETE 可留空）'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="restDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRestOperation">保存</el-button>
      </template>
    </el-dialog>

    <!-- SQL 模板编辑对话框 -->
    <el-dialog
      v-model="sqlDialogVisible"
      :title="editingSqlIndex === -1 ? '新增 SQL 模板' : '编辑 SQL 模板'"
      width="680px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="editingSql" label-width="90px">
        <el-form-item label="操作名" required>
          <el-input v-model="editingSql.name" placeholder="如 findUserById" />
        </el-form-item>
        <el-form-item label="SQL 类型" required>
          <el-select v-model="editingSql.sqlType" style="width: 200px">
            <el-option v-for="t in SQL_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
          <span class="field-hint" style="margin-left: 12px">
            QUERY 返回结果集，UPDATE 返回影响行数
          </span>
        </el-form-item>
        <el-form-item label="SQL 模板" required>
          <el-input
            v-model="editingSql.sqlTemplate"
            type="textarea"
            :rows="8"
            placeholder="SELECT * FROM users WHERE id = :id"
          />
          <div class="field-hint">支持命名参数，如 :id、:name</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sqlDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSqlTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- OpenAPI 导入器 -->
    <OpenApiImporter ref="openApiImporterRef" :base-url="props.baseUrl" @import="handleImport" />
  </div>
</template>

<script lang="ts">
export default { name: 'StepOperations' }
</script>

<style scoped lang="scss">
.step-operations {
  .step-toolbar {
    margin-bottom: 10px;
    display: flex;
    gap: 8px;
  }
  .dash {
    color: var(--el-text-color-placeholder);
  }
  .field-hint {
    margin-top: 4px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .kv-list {
    width: 100%;
  }
  .kv-row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 6px;
  }
}
</style>
