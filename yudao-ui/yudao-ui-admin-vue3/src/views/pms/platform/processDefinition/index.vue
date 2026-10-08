<template>
  <ContentWrap>
    <div class="mb-10px flex items-center justify-between">
      <h3 class="m-0">中性过程定义（模板）</h3>
      <el-button type="primary" @click="openCreate">新建定义</el-button>
    </div>
    <el-table v-loading="loading" :data="definitions">
      <el-table-column prop="definitionCode" label="定义编码" min-width="170" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column label="实体" min-width="150">
        <template #default="{ row }">{{ row.ownerModule }}/{{ row.entityType }}</template>
      </el-table-column>
      <el-table-column prop="operationCode" label="操作" min-width="90" />
      <el-table-column prop="ruleCode" label="规则语义" min-width="130" />
      <el-table-column label="版本" width="70">
        <template #default="{ row }">v{{ row.definitionVersion }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'DRAFT'"
            type="primary"
            link
            @click="publish(row)"
          >
            发布
          </el-button>
          <el-button v-if="row.status === 'PUBLISHED'" type="primary" link @click="select(row)">
            实例与结果
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <ContentWrap v-if="selected">
    <h4 class="mt-0">{{ selected.name }}（{{ selected.definitionCode }}）的执行实例与结果</h4>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="同步运行" name="run">
        <el-form inline>
          <el-form-item label="实体ID">
            <el-input v-model="runEntityId" style="width: 140px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="running" @click="run">同步执行</el-button>
          </el-form-item>
        </el-form>
        <el-alert v-if="runOutcome" :type="runOutcome.verdict === 'SATISFIED' ? 'success' : 'warning'" :closable="false">
          后端 {{ runOutcome.backendId }} · 判定 {{ runOutcome.verdict }}
          <template v-if="runOutcome.resultId">· 结果 {{ runOutcome.resultId }}</template>
          <template v-if="runOutcome.replay">· 幂等重放</template>
        </el-alert>
      </el-tab-pane>
      <el-tab-pane :label="`实例（${instances.length}）`" name="instances">
        <el-table :data="instances" v-loading="panelLoading">
          <el-table-column prop="backendId" label="执行后端" min-width="160" />
          <el-table-column label="判定" width="110">
            <template #default="{ row }">
              <el-tag :type="row.verdict === 'SATISFIED' ? 'success' : row.verdict === 'UNKNOWN' ? 'warning' : 'info'">
                {{ row.verdict }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="definitionVersion" label="定义版本" width="90" />
          <el-table-column prop="resultId" label="结果身份" min-width="280" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane :label="`结果（${results.length}）`" name="results">
        <el-table :data="results" v-loading="panelLoading">
          <el-table-column prop="resultId" label="结果身份" min-width="280" />
          <el-table-column prop="formationBasis" label="形成依据" min-width="200" />
          <el-table-column label="形成时间" width="170">
            <template #default="{ row }">{{ formatTime(row.formedAt) }}</template>
          </el-table-column>
          <el-table-column label="有效" width="80">
            <template #default="{ row }">
              <el-tag :type="row.valid ? 'success' : 'danger'">{{ row.valid ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>

  <Dialog v-model="createVisible" title="新建过程定义" width="720px">
    <el-form :model="createForm" label-width="110px">
      <el-form-item label="定义编码" required>
        <el-input v-model="createForm.definitionCode" placeholder="如 TICKET_ESCALATION" />
      </el-form-item>
      <el-form-item label="名称" required>
        <el-input v-model="createForm.name" />
      </el-form-item>
      <el-form-item label="实体" required>
        <el-select v-model="entityKey" style="width: 100%" @change="onEntityChange">
          <el-option
            v-for="model in catalog"
            :key="`${model.ownerModule}/${model.entityType}`"
            :label="`${model.title}（${model.ownerModule}/${model.entityType}）`"
            :value="`${model.ownerModule}/${model.entityType}`"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="触发操作" required>
        <el-select v-model="createForm.operationCode" style="width: 100%">
          <el-option v-for="op in operations" :key="op.code" :label="`${op.name}（${op.code}）`" :value="op.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="规则语义" required>
        <el-select v-model="createForm.ruleCode" style="width: 100%">
          <el-option label="FIELD_CONDITION（字段条件）" value="FIELD_CONDITION" />
        </el-select>
      </el-form-item>
      <el-form-item label="结果类型" required>
        <el-input v-model="createForm.resultType" placeholder="如 demo.ticket.escalated" />
      </el-form-item>
      <el-form-item label="字段条件" required>
        <div style="width: 100%">
          <div v-for="(condition, index) in createForm.conditions" :key="index" class="mb-5px flex gap-5px">
            <el-select v-model="condition.fieldCode" placeholder="字段" style="width: 200px">
              <el-option v-for="field in fields" :key="field.code" :label="field.name" :value="field.code" />
            </el-select>
            <el-select v-model="condition.operator" style="width: 120px">
              <el-option v-for="op in CONDITION_OPERATORS" :key="op" :label="op" :value="op" />
            </el-select>
            <el-input
              v-model="condition.valueText"
              placeholder="值（多个用逗号分隔）"
              style="flex: 1"
            />
            <el-button link type="danger" @click="createForm.conditions.splice(index, 1)">删除</el-button>
          </div>
          <el-button link type="primary" @click="addCondition">+ 添加条件</el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="creating" @click="submitCreate">保存草稿</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getModelCatalog,
  getModelDetail,
  type FieldVO,
  type ModelSummaryVO,
  type OperationVO
} from '@/api/pms/platform/businessmodel'
import {
  createDefinition,
  getDefinitionPage,
  getScenarioInstances,
  getScenarioResults,
  publishDefinition,
  runScenario,
  type ProcessDefinitionVO,
  type ScenarioResultVO,
  type ScenarioRunOutcomeVO
} from '@/api/pms/platform/processdefinition'
import { serverErrorMessage } from '@/components/BusinessEntity/useBusinessEntity'

defineOptions({ name: 'PmsProcessDefinition' })

const CONDITION_OPERATORS = ['EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'LIKE', 'IN', 'IS_NULL', 'NOT_NULL']

interface ConditionDraft {
  fieldCode: string
  operator: string
  valueText: string
}

const definitions = ref<ProcessDefinitionVO[]>([])
const catalog = ref<ModelSummaryVO[]>([])
const loading = ref(false)
const createVisible = ref(false)
const creating = ref(false)
const entityKey = ref('')

const createForm = reactive({
  definitionCode: '',
  name: '',
  ownerModule: '',
  entityType: '',
  operationCode: '',
  ruleCode: 'FIELD_CONDITION',
  resultType: '',
  conditions: [] as ConditionDraft[]
})

const operations = ref<OperationVO[]>([])
const fields = ref<FieldVO[]>([])

const selected = ref<ProcessDefinitionVO | null>(null)
const activeTab = ref('run')
const instances = ref<ScenarioRunOutcomeVO[]>([])
const results = ref<ScenarioResultVO[]>([])
const panelLoading = ref(false)
const runEntityId = ref('')
const running = ref(false)
const runOutcome = ref<ScenarioRunOutcomeVO | null>(null)

const conditionValues = (condition: ConditionDraft): (string | number | null)[] => {
  const text = (condition.valueText ?? '').trim()
  if (!text) return []
  return text.split(',').map((item) => {
    const trimmed = item.trim()
    const numeric = Number(trimmed)
    return trimmed !== '' && !Number.isNaN(numeric) ? numeric : trimmed
  })
}

onMounted(async () => {
  loading.value = true
  try {
    const [page, models] = await Promise.all([getDefinitionPage({ pageSize: 50 }), getModelCatalog()])
    definitions.value = page.list
    catalog.value = models
  } catch (error) {
    ElMessage.error(serverErrorMessage(error, '定义加载失败'))
  } finally {
    loading.value = false
  }
})

const openCreate = () => {
  createForm.definitionCode = ''
  createForm.name = ''
  createForm.operationCode = ''
  createForm.resultType = ''
  createForm.conditions = []
  entityKey.value = ''
  operations.value = []
  fields.value = []
  createVisible.value = true
}

const onEntityChange = async (key: string) => {
  const [ownerModule, entityType] = key.split('/')
  createForm.ownerModule = ownerModule
  createForm.entityType = entityType
  createForm.operationCode = ''
  const detail = await getModelDetail(ownerModule, entityType)
  operations.value = detail.operations.filter((op) => op.executable)
  fields.value = detail.fields.filter((field) => field.readable)
}

const addCondition = () =>
  createForm.conditions.push({ fieldCode: '', operator: 'EQ', valueText: '' })

const submitCreate = async () => {
  if (!createForm.definitionCode || !createForm.name || !createForm.ownerModule || !createForm.resultType) {
    ElMessage.warning('请完整填写定义编码、名称、实体与结果类型')
    return
  }
  if (createForm.conditions.some((condition) => !condition.fieldCode)) {
    ElMessage.warning('条件字段不能为空')
    return
  }
  creating.value = true
  try {
    await createDefinition({
      definitionCode: createForm.definitionCode,
      name: createForm.name,
      ownerModule: createForm.ownerModule,
      entityType: createForm.entityType,
      operationCode: createForm.operationCode,
      ruleCode: createForm.ruleCode,
      resultType: createForm.resultType,
      conditions: createForm.conditions.map((condition) => ({
        fieldCode: condition.fieldCode,
        operator: condition.operator,
        values: conditionValues(condition)
      }))
    })
    ElMessage.success('草稿已保存')
    createVisible.value = false
    definitions.value = (await getDefinitionPage({ pageSize: 50 })).list
  } catch (error) {
    ElMessage.error(serverErrorMessage(error, '定义保存失败'))
  } finally {
    creating.value = false
  }
}

const publish = async (row: ProcessDefinitionVO) => {
  try {
    const published = await publishDefinition(row.id)
    ElMessage.success(`已发布为版本 v${published.definitionVersion}`)
    definitions.value = (await getDefinitionPage({ pageSize: 50 })).list
  } catch (error) {
    ElMessage.error(serverErrorMessage(error, '发布失败'))
  }
}

const select = async (row: ProcessDefinitionVO) => {
  selected.value = row
  activeTab.value = 'run'
  runOutcome.value = null
  panelLoading.value = true
  try {
    const [instanceList, resultList] = await Promise.all([
      getScenarioInstances(row.definitionCode),
      getScenarioResults(row.definitionCode)
    ])
    instances.value = instanceList
    results.value = resultList
  } catch (error) {
    ElMessage.error(serverErrorMessage(error, '实例加载失败'))
  } finally {
    panelLoading.value = false
  }
}

const run = async () => {
  if (!selected.value) return
  const entityId = Number(runEntityId.value)
  if (!entityId) {
    ElMessage.warning('请输入实体ID')
    return
  }
  running.value = true
  try {
    runOutcome.value = await runScenario({
      definitionCode: selected.value.definitionCode,
      entityId,
      idempotencyKey: crypto.randomUUID()
    })
    instances.value = await getScenarioInstances(selected.value.definitionCode)
    results.value = await getScenarioResults(selected.value.definitionCode)
  } catch (error) {
    ElMessage.error(serverErrorMessage(error, '同步执行失败'))
  } finally {
    running.value = false
  }
}

const formatTime = (millis: number) => {
  const date = new Date(millis)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}
</script>
