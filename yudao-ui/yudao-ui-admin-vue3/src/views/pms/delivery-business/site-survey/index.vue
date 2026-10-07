<template>
  <el-alert v-if="pendingIntent && !formVisible" title="工勘操作结果尚未确定，请恢复原请求确认结果。" type="warning" :closable="false">
    <template #default><el-button :loading="saving" @click="recoverOperation">恢复原请求</el-button></template>
  </el-alert>
  <el-alert v-if="receiptResult && !formVisible" :title="`工勘${receiptTitle}已完成：对象 ${receiptResult.id}，版本 ${receiptResult.version}`"
    type="success" :closable="false">
    <template #default>
      <span>{{ receiptReadError }}</span>
      <el-button v-if="!receiptResult.deleted" :loading="receiptReading" @click="reopenFromReceipt">按回执打开工勘</el-button>
    </template>
  </el-alert>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-180px"
          :disabled="projectLocked"
        />
      </el-form-item>
      <el-form-item label="工勘名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SITE_SURVEY_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button :disabled="!can('QUERY')" @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button v-if="can('CREATE')" :disabled="!!pendingIntent || receiptReopenRequired" type="primary" @click="openForm()" v-hasPermi="['pms:sol-site-survey:create']"
          ><Icon icon="ep:plus" />新增工勘</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="name" label="工勘名称" min-width="180" />
      <el-table-column prop="location" label="工勘地点" min-width="180" show-overflow-tooltip />
      <el-table-column prop="locationResolutionStatus" label="地点状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.locationResolutionStatus === 'RESOLVED' ? 'success' : 'warning'">
            {{ row.locationResolutionStatus || 'UNRESOLVED' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="surveyDate" label="工勘日期" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SITE_SURVEY_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button v-if="can('QUERY')" link type="primary" @click="openForm(row, true)">详情</el-button>
          <el-button
            v-if="row.status === 0 && can('UPDATE')"
            link
            type="primary"
            @click="openForm(row)"
            v-hasPermi="['pms:sol-site-survey:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0 && can('CONFIRM')"
            @click="handleAction(row, 'confirm')"
            v-hasPermi="['pms:sol-site-survey:update']"
            >确认</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 && can('REJECT')"
            @click="handleAction(row, 'reject')"
            v-hasPermi="['pms:sol-site-survey:update']"
            >驳回</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0 && can('DELETE')"
            @click="remove(row)"
            :disabled="!!row.outsourceRequestId"
            :title="row.outsourceRequestId ? '已关联转包申请，请先处理关联申请' : undefined"
            v-hasPermi="['pms:sol-site-survey:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog
    v-model="formVisible"
    :title="readonly ? '工勘详情' : form.id ? '编辑工勘' : '新增工勘'"
    :aria-label="readonly ? '工勘详情' : form.id ? '编辑工勘' : '新增工勘'"
    width="min(960px, 95vw)"
    :before-close="beforeClose"
  >
    <el-alert v-if="pendingIntent" title="工勘操作结果尚未确定，请恢复原请求确认结果。" type="warning" :closable="false">
    <template #default><el-button :loading="saving" @click="recoverOperation">恢复原请求</el-button></template>
    </el-alert>
    <el-alert v-if="receiptResult" :title="`工勘${receiptTitle}已完成：对象 ${receiptResult.id}，版本 ${receiptResult.version}`"
    type="success" :closable="false">
    <template #default>
      <span>{{ receiptReadError }}</span>
      <el-button v-if="!receiptResult.deleted" :loading="receiptReading" @click="reopenFromReceipt">按回执打开工勘</el-button>
    </template>
    </el-alert>
    <el-alert
      v-if="readonly"
      title="当前工勘记录只读，已确认、驳回和归档内容不会被编辑覆盖。"
      type="info"
      :closable="false"
    />
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      :disabled="readonly || saving"
    >
      <el-row :gutter="16">
        <el-col v-if="!projectLocked" :span="12">
          <el-form-item label="项目编号" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id || projectLocked"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="!projectLocked" :span="12">
          <el-form-item label="工勘名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="工勘日期" prop="surveyDate">
            <el-date-picker
              v-model="form.surveyDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="工勘人员" prop="surveyorUserId">
            <el-select
              v-model="form.surveyorUserId"
              filterable
              clearable
              placeholder="请选择工勘人员"
            >
              <el-option
                v-for="user in users"
                :key="user.id"
                :value="user.id"
                :label="user.nickname"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="工勘地点" prop="location">
            <el-input
              v-model="form.location"
              placeholder="填写现场地点说明，或使用下方结构化地点维护"
            />
            <PmsLocationSelector
              v-if="!readonly"
              v-model="form.locationMaintenance"
              :project-id="form.projectId"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24" v-if="!readonly && !integratedOutsource">
          <el-form-item label="分工／转包">
            <el-checkbox v-model="form.outsourceRequired">需要转包</el-checkbox>
            <el-button
              v-if="form.outsourceRequired && !form.outsourceRequestId"
              link
              type="primary"
              :loading="saving"
              @click="startOutsource"
              v-hasPermi="['pms:res-outsource:create']"
            >
              保存工勘并发起转包申请
            </el-button>
          </el-form-item>
        </el-col>
        <el-col
          :span="24"
          v-if="
            !integratedOutsource &&
            (form.outsourceRequestId || (readonly && form.outsourceRequired))
          "
        >
          <el-form-item label="转包关联">
            <el-link
              v-if="form.outsourceRequestId"
              :href="readonly ? undefined : outsourceDetailUrl(form.outsourceRequestId, openedExecution, stageCode)"
              :disabled="readonly"
              target="_blank"
              rel="noopener"
              type="primary"
            >
              查看转包申请 #{{ form.outsourceRequestId }}
            </el-link>
            <span v-else>已勾选需要转包，尚未关联申请</span>
            <p v-if="form.outsourceRequestId">取消勾选不会撤回或删除已有申请。</p>
          </el-form-item>
        </el-col>
        <el-col :span="24" v-if="!readonly">
          <el-form-item label="填写表单">
            <el-button v-if="!form.formRevisionId" @click="useStandardForm"
              >接入动态表单（保留已填写内容）</el-button
            >
            <span v-else>已绑定发布修订 {{ form.formRevisionId }}</span>
            <el-button v-if="form.id" @click="useStandardForm"
              >使用完整工勘模板（保留原内容）</el-button
            >
            <el-button
              v-if="!form.id"
              @click="chooseTemplate"
              v-hasPermi="['pms:dynamic-form-template:query']"
              >选择已发布工勘模板</el-button
            >
          </el-form-item>
        </el-col>
        <el-col :span="24" v-if="form.formRevisionId">
          <SiteSurveyDynamicForm
            v-if="formVisible"
            ref="dynamicFormRef"
            :model-value="form"
            :readonly="readonly"
            @update:model-value="updateDynamicForm"
            @action="performSurveyAction"
            @integrated-outsource="integratedOutsource = $event"
          />
        </el-col>
        <template v-else>
          <el-col :span="12">
            <el-form-item label="供电情况" prop="powerSupply"
              ><el-input v-model="form.businessValues!.powerSupply"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机柜情况" prop="cabinet"
              ><el-input v-model="form.businessValues!.cabinet"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="网口情况" prop="networkPort"
              ><el-input v-model="form.businessValues!.networkPort"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="光纤情况" prop="fiber"
              ><el-input v-model="form.businessValues!.fiber"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模块情况" prop="module"
              ><el-input v-model="form.businessValues!.module"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="线缆情况" prop="cable"
              ><el-input v-model="form.businessValues!.cable"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接地情况" prop="ground"
              ><el-input v-model="form.businessValues!.ground"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="施工资源" prop="constructionResource">
              <el-input v-model="form.businessValues!.constructionResource" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="工勘结论" prop="conclusion">
              <Editor v-model="form.businessValues!.conclusion" height="200px" :readonly="readonly" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.businessValues!.remark" type="textarea" />
            </el-form-item>
          </el-col>
        </template>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="beforeClose(() => (formVisible = false))">{{
        readonly ? '关闭' : '取消'
      }}</el-button>
      <el-button v-if="!readonly" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
  <Dialog
    v-model="templateVisible"
    title="选择工勘动态表单"
    aria-label="选择工勘动态表单"
    width="min(700px, 95vw)"
  >
    <el-table :data="templates">
      <el-table-column prop="templateName" label="表单名称" />
      <el-table-column label="操作"
        ><template #default="{ row }">
          <el-button :disabled="readonly" @click="selectTemplate(row)">使用此表单</el-button>
        </template></el-table-column
      >
    </el-table>
    <p>按平台模板分页，仅显示当前页的现场工勘模板。</p>
    <el-pagination
      :total="templateTotal"
      v-model:current-page="templatePage"
      :page-size="20"
      layout="prev, pager, next"
      @current-change="loadTemplates"
    />
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { useUserStore } from '@/store/modules/user'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as SiteSurveyApi from '@/api/pms/engineering/site-survey/entity'
import type { SiteSurveyVO, SiteSurveyCommandResult } from '@/api/pms/engineering/site-survey/entity'
import { newIdempotencyKey, type BusinessOperationReceipt } from '@/api/pms/platform/businessmodel'
import { OperationRejected } from '@/components/BusinessView/operationClient'
import { pinSurveySave, pinSurveyAction, submitSurveyIntent, intentResult, requireSurveyReceiptRow, type SurveyReceiptIntent, type SurveyAction } from './surveyReceiptIntent'
import type { TaskExecutionContext } from '@/api/pms/project/task-business'
import type { StageExecutionContext } from '@/api/pms/project/stage-business'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import SiteSurveyDynamicForm from './SiteSurveyDynamicForm.vue'
import { hasStructuredSurveyLocation } from './siteSurveyForm'
import { resolveSurveyExecution } from './siteSurveyExecutionShortcut'
import { surveyProcurementRoute, type SurveyMaterialSelection } from './surveyBusinessForm'
import {
  outsourceShortcutRoute,
  outsourceDetailUrl,
  positiveShortcutId
} from './siteSurveyOutsource'

defineOptions({ name: 'PmsSiteSurveyEntity' })
const props = defineProps<{
  projectId?: number | string
  readonly?: boolean
  allowedActions?: string[]
  objectId?: number | string
  taskId?: number | string
  taskExecution?: TaskExecutionContext
  stageExecution?: StageExecutionContext
  stageCode?: string
}>()
const emit = defineEmits<{ saved: []; changed: []; 'dirty-change': [value: boolean] }>()
const projectLocked = computed(() => props.projectId != null)
const executionSelection = (): SiteSurveyApi.SiteSurveyExecutionSelection | undefined => props.taskExecution || props.stageExecution
  ? { ...(props.taskExecution ? { task: { ...props.taskExecution } } : {}),
      ...(props.stageExecution ? { stage: { ...props.stageExecution } } : {}) } : undefined
let openedExecution: SiteSurveyApi.SiteSurveyExecutionSelection | undefined
const sameId = (left: unknown, right: unknown) => String(left ?? '') === String(right ?? '')
const inProject = (row: Pick<SiteSurveyVO, 'projectId'>) =>
  !projectLocked.value || sameId(row.projectId, props.projectId)
// Host actions narrow existing UI permissions; the Owner API remains the authority.
const can = (action: string) =>
  action === 'QUERY'
    ? props.readonly === true || props.allowedActions === undefined || props.allowedActions.includes(action)
    : !props.readonly && (props.allowedActions === undefined || props.allowedActions.includes(action))
let contextSequence = 0
let listSequence = 0
let formSequence = 0
const contextKey = () => JSON.stringify([String(props.projectId ?? ''), String(props.objectId ?? ''), String(props.taskId ?? '')])
const current = (sequence: number) => sequence === contextSequence
// Existing location and project API types are numeric; a type assertion never coerces a Snowflake string on the wire.
const ownerId = (id: number | string) => id as number
const message = useMessage()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const detailReadonly = ref(false)
const pendingIntent = shallowRef<SurveyReceiptIntent>()
const operationReceipt = shallowRef<BusinessOperationReceipt>()
const committedIntent = shallowRef<SurveyReceiptIntent>()
const receiptResult = shallowRef<SiteSurveyCommandResult>()
const receiptTitle = ref('')
const receiptReadError = ref('')
const receiptReading = ref(false)
const receiptReopenRequired = ref(false)
const readonly = computed(() => !!pendingIntent.value || receiptReopenRequired.value || detailReadonly.value || !can(form.id ? 'UPDATE' : 'CREATE'))
const integratedOutsource = ref(false)
const users = ref<UserApi.UserVO[]>([])
const dynamicFormRef = ref<InstanceType<typeof SiteSurveyDynamicForm>>()
const templateVisible = ref(false)
const templates = ref<DynamicFormApi.DynamicFormSelectionVO[]>([])
const templatePage = ref(1)
const templateTotal = ref(0)
const openedValue = ref('')
const rows = ref<SiteSurveyVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: props.projectId as number | undefined,
  name: '',
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SiteSurveyVO>({ projectId: 0, name: '', businessValues: {} })
const rules = {
  projectId: [{ required: true, validator: (_rule: unknown, value: unknown, callback: (error?: Error) => void) => callback(/^[1-9]\d*$/.test(String(value ?? '')) ? undefined : new Error('请选择项目')) }],
  name: [{ required: true, message: '请输入工勘名称' }]
}

const load = async () => {
  if (!can('QUERY')) return
  const context = contextSequence
  const sequence = ++listSequence
  loading.value = true
  try {
    const data = await SiteSurveyApi.getSiteSurveyPage({ ...query, projectId: ownerId(props.projectId ?? query.projectId!) })
    if (!current(context) || sequence !== listSequence || !can('QUERY')) return
    rows.value = data.list.filter(inProject)
    total.value = data.total
  } finally {
    if (current(context) && sequence === listSequence) loading.value = false
  }
}
const openForm = async (row?: SiteSurveyVO, view = false) => {
  let requestedExecution = executionSelection()
  const action = row ? (view ? 'QUERY' : 'UPDATE') : 'CREATE'
  if (!can(action) || saving.value || pendingIntent.value || receiptReopenRequired.value && action !== 'QUERY' || (row && !inProject(row) && row.projectId != null)) return
  // Refreshing the same project/object must not overwrite an unfinished form.
  if (formVisible.value && sameId(row?.id, form.id) && isDirty()) return
  const context = contextSequence
  const sequence = ++formSequence
  if (!(await confirmLeave()) || !current(context) || sequence !== formSequence) return
  const requestedId = row?.id
  if (requestedId != null) row = await SiteSurveyApi.getSiteSurvey(requestedId)
  if (!current(context) || sequence !== formSequence || !can(action)) return
  if (requestedId != null && (!row || !sameId(row.id, requestedId))) {
    message.warning('工勘对象不匹配，请重新加载')
    return
  }
  if (row && !inProject(row)) {
    message.warning('该工勘不属于当前项目')
    return
  }
  if (row && !view && !requestedExecution) {
    try { requestedExecution = await resolveSurveyExecution(row.projectId, route.query) }
    catch { message.warning('工勘执行轮次已不可用，请从原任务或阶段重新进入'); return }
    if (!current(context) || sequence !== formSequence || !can(action)) return
  }
  formVisible.value = false
  openedExecution = requestedExecution
  detailReadonly.value = view || (!!row && row.status !== 0)
  integratedOutsource.value = false
  for (const key of Object.keys(form)) delete (form as unknown as Record<string, unknown>)[key]
  Object.assign(
    form,
    {
      id: undefined,
      projectId: ownerId(props.projectId ?? 0),
      name: '',
      surveyDate: '',
      surveyorUserId: userStore.getUser.id,
      location: '',
      locationMaintenance: undefined,
      businessValues: {},
      fieldBindings: {},
      fieldCatalog: [],
      version: undefined,
      status: 0,
      formRevisionId: undefined,
      formRevisionVersion: undefined,
      extensionValues: undefined,
      outsourceRequired: false,
      outsourceRequestId: undefined,
      projectEndDateVersion: undefined,
      projectEndDateChanged: false
    },
    row || {}
  )
  form.businessValues ??= {}
  form.locationMaintenance = toLocationMaintenance(row)
  if (!row && projectLocked.value) {
    // 工勘名称默认取宿主项目名称；项目与名称字段在项目上下文中不显示。
    const host = await loadHostProject()
    if (current(context) && sequence === formSequence) form.name = host?.projectName ?? ''
  }
  if (!row) {
    try { await useStandardForm() }
    catch { if (current(context)) message.warning('默认动态表单不可用，可继续填写基础工勘内容') }
  }
  if (!current(context) || sequence !== formSequence) return
  openedValue.value = JSON.stringify(form)
  formVisible.value = true
}

const hostProject = ref<ProjectApi.ProjectMasterVO>()
const loadHostProject = () => hostProject.value
  ? Promise.resolve(hostProject.value)
  : ProjectApi.getProject(ownerId(props.projectId!)).then((row) => (hostProject.value = row))

const useStandardForm = async () => {
  if (readonly.value || !inProject(form)) return
  const context = contextSequence
  const sequence = formSequence
  const schema = await SiteSurveyApi.getDefaultFormSchema()
  if (!current(context) || sequence !== formSequence || readonly.value) return
  const schemaFields = new Set(Object.values(schema.fieldBindings))
  const retainedExtras = Object.keys(form.extensionValues || {})
  if (retainedExtras.some((key) => !schemaFields.has(key))) {
    message.warning('新模板不包含当前已保存的扩展字段，未切换以保留原数据。')
    return
  }
  form.formRevisionId = schema.revisionId
  form.extensionDefinitionRevisionId = undefined
  form.formRevisionVersion = schema.revisionVersion
  form.fieldCatalog = schema.fieldCatalog
  form.fieldBindings = schema.fieldBindings
}
const loadTemplates = async () => {
  if (readonly.value) return
  const context = contextSequence
  const result = await DynamicFormApi.getTemplateSelection({
    pageNo: templatePage.value,
    pageSize: 20
  })
  if (!current(context) || readonly.value) return
  templates.value = result.list.filter((item) => item.categoryCode === 'SITE_SURVEY')
  templateTotal.value = result.total
}
const chooseTemplate = async () => {
  if (readonly.value) return
  const context = contextSequence
  await loadTemplates()
  if (current(context) && !readonly.value) templateVisible.value = true
}
const selectTemplate = async (template: DynamicFormApi.DynamicFormSelectionVO) => {
  if (readonly.value) return
  const context = contextSequence
  const sequence = formSequence
  const revision = await DynamicFormApi.getRevision(template.currentPublishedRevisionId)
  const schema = await SiteSurveyApi.getFormSchema(revision.revisionId, revision.revisionVersion)
  if (!current(context) || sequence !== formSequence || readonly.value) return
  form.formRevisionId = revision.revisionId
  form.extensionDefinitionRevisionId = undefined
  form.formRevisionVersion = revision.revisionVersion
  form.fieldCatalog = schema.fieldCatalog
  form.fieldBindings = schema.fieldBindings
  templateVisible.value = false
}
const isDirty = () => !!pendingIntent.value || formVisible.value && openedValue.value !== JSON.stringify(form)
watch(() => isDirty(), (value) => emit('dirty-change', value), { flush: 'sync' })
const confirmLeave = async () => {
  if (saving.value || pendingIntent.value) return false
  if (!isDirty()) return true
  try {
    await message.confirm('尚有未保存的工勘内容，确定放弃并关闭？')
    return !saving.value && !pendingIntent.value
  } catch {
    return false
  }
}
// Host performs this only after confirming that the pending switch is still current.
const discardChanges = () => {
  if (saving.value || pendingIntent.value) return false
  ++formSequence
  if (openedValue.value) {
    const baseline = JSON.parse(openedValue.value)
    for (const key of Object.keys(form)) {
      if (!(key in baseline)) delete (form as unknown as Record<string, unknown>)[key]
    }
    Object.assign(form, baseline)
  }
  formVisible.value = false
  templateVisible.value = false
  return true
}
const beforeClose = async (done: () => void) => {
  if ((await confirmLeave()) && discardChanges()) done()
}
const updateDynamicForm = (value: SiteSurveyVO) => {
  if (!readonly.value && inProject(form)) Object.assign(form, value)
}
defineExpose({ isDirty, discardChanges, confirmLeave, requestLeave: confirmLeave })

const toLocationMaintenance = (row?: SiteSurveyVO): LocationMaintainRequest | undefined => {
  if (!row) return { projectId: form.projectId }
  if (row.locationResolutionStatus !== 'RESOLVED') {
    return { projectId: row.projectId, fallbackLocation: row.location }
  }
  return {
    projectId: row.projectId,
    address: row.addressId ? { id: row.addressId, expectedVersion: row.addressVersion } : undefined,
    site: row.siteId ? { id: row.siteId, expectedVersion: row.siteVersion } : undefined,
    siteLocation: row.siteLocationId
      ? {
          id: row.siteLocationId,
          expectedVersion: row.siteLocationVersion
        }
      : undefined
  }
}

const savePayload = () => {
  const payload = { ...form, execution: openedExecution }
  const maintenance = payload.locationMaintenance
  if (!hasStructuredSurveyLocation(maintenance)) {
    if (!(maintenance?.fallbackLocation || payload.location)?.trim()) {
      message.error('请选择地点或填写兼容地点')
      return
    }
    payload.location = maintenance?.fallbackLocation || payload.location
    payload.locationMaintenance = undefined
    return payload
  }
  if (!maintenance) return
  if (!maintenance.site?.id && !maintenance.address?.id && !maintenance.address?.detailAddress) {
    message.error('新地点必须填写详细地址')
    return
  }
  if (!maintenance?.site?.id && !maintenance?.address?.id && !maintenance.address?.provinceCode) {
    message.error('新地点请选择省市区')
    return
  }
  if (!maintenance.site?.id && !maintenance.site?.name) {
    message.error('新地点必须填写站点名称')
    return
  }
  if (maintenance.siteLocation && !maintenance.siteLocation.id && !maintenance.siteLocation.name) {
    maintenance.siteLocation = undefined
  }
  if (maintenance.address && !maintenance.address.id) {
    maintenance.address.fullAddress = [
      maintenance.address.countryName,
      maintenance.address.provinceName,
      maintenance.address.cityName,
      maintenance.address.districtName,
      maintenance.address.detailAddress
    ]
      .filter(Boolean)
      .join('')
  }
  const siteLabel = maintenance?.site?.name
    ? `${maintenance?.site?.name}（${maintenance?.address?.fullAddress ?? maintenance?.addressText ?? ''}）`
    : (maintenance?.address?.fullAddress ?? maintenance?.addressText)
  const chainNames = [
    maintenance?.siteLocation?.name,
    ...(maintenance?.extraSiteLocations ?? []).map((item) => item?.name)
  ].filter(Boolean)
  payload.location =
    maintenance?.fallbackLocation ||
    [siteLabel, ...chainNames].filter(Boolean).join(' / ') ||
    payload.location
  return payload
}
const reopenFromReceipt = async () => {
  const receipt = operationReceipt.value, intent = committedIntent.value, result = receiptResult.value
  if (!receipt || !intent || !result || result.deleted || pendingIntent.value || receiptReading.value) return false
  const context = contextSequence
  receiptReading.value = true
  try {
    if (intent.context !== context || !can('QUERY') || !inProject({ projectId: ownerId(result.projectId) }))
      throw new Error('当前项目或读取权限已改变，请从原项目查看回执对象')
    const row = requireSurveyReceiptRow(result, await SiteSurveyApi.getSiteSurvey(ownerId(result.id)))
    if (!current(context) || operationReceipt.value !== receipt || !can('QUERY')) return false
    if (!inProject(row)) throw new Error('回执工勘不属于当前项目')
    ++formSequence
    for (const key of Object.keys(form)) delete (form as unknown as Record<string, unknown>)[key]
    Object.assign(form, row, { businessValues: row.businessValues ?? {}, fieldBindings: row.fieldBindings ?? {}, fieldCatalog: row.fieldCatalog ?? [] })
    form.locationMaintenance = toLocationMaintenance(row)
    openedExecution = intent.operation === 'save' ? intent.payload.execution : intent.execution
    detailReadonly.value = row.status !== 0 || !can('UPDATE')
    receiptReopenRequired.value = false
    receiptReadError.value = ''
    openedValue.value = JSON.stringify(form)
    formVisible.value = true
    return true
  } catch (error) {
    if (operationReceipt.value === receipt) {
      receiptReadError.value = `操作已完成，但回执对象暂未打开：${error instanceof Error ? error.message : '读取失败'}`
      receiptReopenRequired.value = true
      message.warning(receiptReadError.value)
    }
    return false
  } finally { receiptReading.value = false }
}
const executeIntent = async (intent: SurveyReceiptIntent, recovery = false) => {
  if (saving.value || !recovery && pendingIntent.value) return false
  saving.value = true
  pendingIntent.value = intent
  try {
    let receipt: BusinessOperationReceipt
    try { receipt = await submitSurveyIntent(intent) }
    catch (error) {
      // A refusal of a recovery cannot disprove that the original request committed.
      if (!recovery && error instanceof OperationRejected) {
        pendingIntent.value = undefined
        message.warning(`工勘${intent.title}未执行：${error.message}`)
      } else message.warning('工勘操作结果尚未确定，已保留原内容、版本及执行上下文；请恢复原请求确认结果。')
      return false
    }
    const result = intentResult(intent, receipt)
    operationReceipt.value = receipt
    committedIntent.value = intent
    receiptResult.value = result
    receiptTitle.value = intent.title
    receiptReadError.value = ''
    pendingIntent.value = undefined
    message.success(`工勘${intent.title}已完成`)
    if (!current(intent.context)) {
      receiptReadError.value = '原项目操作已完成，当前上下文已改变；请从原项目查看回执对象。'
      receiptReopenRequired.value = true
      return false
    }
    if (intent.operation === 'save') {
      form.id = ownerId(result.id)
      form.version = result.version
      openedValue.value = JSON.stringify(form)
    }
    if (intent.operation === 'save') emit('saved')
    emit('changed')
    if (result.deleted) {
      if (sameId(form.id, result.id)) { formVisible.value = false; openedValue.value = JSON.stringify(form) }
      receiptReopenRequired.value = false
    } else receiptReopenRequired.value = true
    const opened = result.deleted || await reopenFromReceipt()
    try { await load() }
    catch { message.warning('操作已完成，列表暂未刷新；可按回执查看实际对象。') }
    return opened && current(intent.context)
  } catch {
    message.warning('工勘回执尚不能确认，已保留原请求；请恢复原请求确认结果。')
    return false
  } finally { saving.value = false }
}
const recoverOperation = () => pendingIntent.value ? executeIntent(pendingIntent.value, true) : Promise.resolve(false)
const save = async () => {
  if (readonly.value || saving.value || !inProject(form)) return false
  const context = contextSequence
  try {
    await formRef.value.validate()
    if (form.formRevisionId) await dynamicFormRef.value?.validate()
  } catch {
    if (current(context)) message.warning('请检查工勘必填项和表单内容')
    return false
  }
  if (!current(context) || readonly.value || saving.value || !inProject(form)) return false
  const payload = savePayload()
  if (!payload) return false
  return executeIntent(pinSurveySave(payload, newIdempotencyKey(), context))
}
const startOutsource = async () => {
  if (readonly.value || saving.value || !inProject(form) || !form.outsourceRequired || form.outsourceRequestId) return
  const execution = openedExecution
  const stageCode = props.stageCode
  if (await save()) await router.push(outsourceShortcutRoute(form.id!, execution, stageCode))
}
const performSurveyAction = async (kind: string, sn?: string) => {
  if (readonly.value || saving.value) return
  if (kind === 'outsource') {
    await startOutsource()
    return
  }
  if (kind !== 'material' && kind !== 'procurement' && kind !== 'exchange') return
  if (kind === 'exchange') {
    const selected = (form.businessValues?.selectedMaterials ||
      []) as SurveyMaterialSelection[]
    if (
      form.businessValues?.materialMatches !== false ||
      !selected.some(
        (row) => row.sn === sn && row.reason?.trim() && row.projectId === form.projectId
      )
    )
      return
  } else if (form.businessValues?.railTrayRequired !== true) return
  if ((await save()) && !readonly.value) await router.push(surveyProcurementRoute(kind, form.id!, sn))
}
const remove = async (row: SiteSurveyVO) => {
  const execution = executionSelection()
  if (!can('DELETE') || !inProject(row) || saving.value || pendingIntent.value || receiptReopenRequired.value || row.status !== 0 && !row.outsourceRequestId) return
  const context = contextSequence
  if (row.outsourceRequestId) {
    message.warning('工勘已关联转包申请，请先处理关联申请，不能删除来源记录')
    return
  }
  let intent: SurveyReceiptIntent
  try { intent = pinSurveyAction('delete', row, execution, newIdempotencyKey(), context) }
  catch (error) { message.warning((error as Error).message); return }
  try { await message.delConfirm() } catch { return }
  if (!current(context) || !can('DELETE') || !inProject(row) || saving.value || pendingIntent.value) return
  await executeIntent(intent)
}
const handleAction = async (row: SiteSurveyVO, action: SurveyAction) => {
  const execution = executionSelection()
  if (!['confirm', 'reject', 'archive'].includes(action) || !can(action.toUpperCase()) || !inProject(row) || saving.value || pendingIntent.value || receiptReopenRequired.value || row.status !== (action === 'archive' ? 1 : 0)) return
  const context = contextSequence
  let intent: SurveyReceiptIntent
  try { intent = pinSurveyAction(action, row, execution, newIdempotencyKey(), context) }
  catch (error) { message.warning((error as Error).message); return }
  try { await message.confirm(`是否${intent.title}工勘【${row.name}】？`) } catch { return }
  if (!current(context) || !can(action.toUpperCase()) || !inProject(row) || saving.value || pendingIntent.value) return
  await executeIntent(intent)
}
onMounted(() => {
  UserApi.getSimpleUserList()
    .then((result) => (users.value = result))
    .catch(() => message.warning('工勘人员列表加载失败'))
})
watch(contextKey, async () => {
  ++contextSequence
  ++listSequence
  ++formSequence
  loading.value = false
  saving.value = false
  formVisible.value = false
  templateVisible.value = false
  rows.value = []
  total.value = 0
  query.pageNo = 1
  query.projectId = props.projectId as number | undefined
  hostProject.value = undefined
  const context = contextSequence
  await load()
  // Task workbenches open the list. Only an explicit list action opens a survey dialog.
  // Standalone object links retain their existing deep-link behavior.
  if (current(context) && props.objectId != null && props.taskId == null) {
    await openForm({ id: ownerId(props.objectId) } as SiteSurveyVO, !can('UPDATE'))
  }
}, { immediate: true, flush: 'sync' })
watch(
  () => route.query.surveyId,
  async (value) => {
    if (!value || props.objectId != null || projectLocked.value) return
    const id = positiveShortcutId(value)
    if (!id) {
      message.warning('工勘编号无效')
      return
    }
    await openForm({ id } as SiteSurveyVO, !can('UPDATE'))
  },
  { immediate: true }
)
onBeforeUnmount(() => { ++contextSequence; ++formSequence; ++listSequence })
</script>

<style scoped>
@media (max-width: 767px) {
  :deep(.el-col-12) {
    max-width: 100%;
    flex-basis: 100%;
  }
  :deep(.el-form--inline .el-form-item) {
    display: block;
    margin-right: 0;
  }
}
</style>
