<template>
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
          :disabled="!!props.projectId"
        />
      </el-form-item>
      <el-form-item label="工勘编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
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
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:eng-site-survey:create']"
          ><Icon icon="ep:plus" />新增工勘</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="工勘编码" min-width="140" />
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
          <el-button link type="primary" @click="openForm(row, true)">详情</el-button>
          <el-button
            v-if="row.status === 0"
            link
            type="primary"
            @click="openForm(row)"
            v-hasPermi="['pms:eng-site-survey:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'confirm')"
            v-hasPermi="['pms:eng-site-survey:update']"
            >确认</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0"
            @click="handleAction(row, 'reject')"
            v-hasPermi="['pms:eng-site-survey:update']"
            >驳回</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.status === 1"
            @click="handleAction(row, 'archive')"
            v-hasPermi="['pms:eng-site-survey:update']"
            >归档</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 0"
            @click="remove(row)"
            :disabled="!!row.outsourceRequestId"
            :title="row.outsourceRequestId ? '已关联转包申请，请先处理关联申请' : undefined"
            v-hasPermi="['pms:eng-site-survey:delete']"
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
        <el-col :span="12">
          <el-form-item label="项目编号" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id || !!props.projectId"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="工勘编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
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
              v-hasPermi="['pms:eng-outsource:create']"
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
              :href="outsourceDetailUrl(form.outsourceRequestId)"
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
            ref="dynamicFormRef"
            :model-value="form"
            :readonly="readonly"
            @update:model-value="Object.assign(form, $event)"
            @action="performSurveyAction"
            @integrated-outsource="integratedOutsource = $event"
          />
        </el-col>
        <template v-else>
          <el-col :span="12">
            <el-form-item label="供电情况" prop="powerSupply"
              ><el-input v-model="form.powerSupply"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="机柜情况" prop="cabinet"
              ><el-input v-model="form.cabinet"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="网口情况" prop="networkPort"
              ><el-input v-model="form.networkPort"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="光纤情况" prop="fiber"
              ><el-input v-model="form.fiber"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模块情况" prop="module"
              ><el-input v-model="form.module"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="线缆情况" prop="cable"
              ><el-input v-model="form.cable"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接地情况" prop="ground"
              ><el-input v-model="form.ground"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="施工资源" prop="constructionResource">
              <el-input v-model="form.constructionResource" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="工勘结论" prop="conclusion">
              <Editor v-model="form.conclusion" height="200px" :readonly="readonly" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" />
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
          <el-button @click="selectTemplate(row)">使用此表单</el-button>
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
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as SiteSurveyApi from '@/api/pms/engineering/site-survey'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import SiteSurveyDynamicForm from './SiteSurveyDynamicForm.vue'
import { hasStructuredSurveyLocation } from './siteSurveyForm'
import { surveyProcurementRoute, type SurveyMaterialSelection } from './surveyBusinessForm'
import {
  outsourceShortcutRoute,
  outsourceDetailUrl,
  positiveShortcutId
} from './siteSurveyOutsource'

defineOptions({ name: 'PmsEngSiteSurvey' })
const props = defineProps<{ projectId?: number }>()
const emit = defineEmits<{ saved: [] }>()
const message = useMessage()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const readonly = ref(false)
const integratedOutsource = ref(false)
const users = ref<UserApi.UserVO[]>([])
const dynamicFormRef = ref<InstanceType<typeof SiteSurveyDynamicForm>>()
const templateVisible = ref(false)
const templates = ref<DynamicFormApi.DynamicFormSelectionVO[]>([])
const templatePage = ref(1)
const templateTotal = ref(0)
let openedValue = ''
const rows = ref<SiteSurveyVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: props.projectId as number | undefined,
  code: '',
  name: '',
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SiteSurveyVO>({ projectId: 0, code: '', name: '' })
const rules = {
  projectId: [{ required: true, type: 'number', min: 1, message: '请选择项目' }],
  code: [{ required: true, message: '请输入工勘编码' }],
  name: [{ required: true, message: '请输入工勘名称' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await SiteSurveyApi.getSiteSurveyPage({ ...query, projectId: props.projectId ?? query.projectId })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = async (row?: SiteSurveyVO, view = false) => {
  if (row?.id) row = await SiteSurveyApi.getSiteSurvey(row.id)
  if (props.projectId && row && row.projectId !== props.projectId) {
    message.warning('该工勘不属于当前项目')
    return
  }
  readonly.value = view || (!!row && row.status !== 0)
  integratedOutsource.value = false
  Object.assign(
    form,
    {
      id: undefined,
      projectId: props.projectId || 0,
      code: '',
      name: '',
      surveyDate: '',
      surveyorUserId: undefined,
      location: '',
      locationMaintenance: undefined,
      powerSupply: '',
      cabinet: '',
      networkPort: '',
      fiber: '',
      module: '',
      cable: '',
      ground: '',
      constructionResource: '',
      conclusion: '',
      remark: '',
      version: undefined,
      status: 0,
      formRevisionId: undefined,
      formRevisionVersion: undefined,
      formExtraValues: undefined,
      outsourceRequired: false,
      outsourceRequestId: undefined,
      projectEndDateVersion: undefined,
      projectEndDateChanged: false
    },
    row || {}
  )
  form.locationMaintenance = toLocationMaintenance(row)
  if (!row) await useStandardForm()
  openedValue = JSON.stringify(form)
  formVisible.value = true
}

const useStandardForm = async () => {
  const schema = await SiteSurveyApi.getDefaultFormSchema()
  const schemaFields = new Set(
    schema.formRulesJson.filter((rule: any) => rule.field).map((rule: any) => rule.field)
  )
  const retainedExtras = Object.keys(form.formExtraValues || {})
  if (retainedExtras.some((key) => !schemaFields.has(key))) {
    message.warning('新模板不包含当前已保存的扩展字段，未切换以保留原数据。')
    return
  }
  form.formRevisionId = schema.revisionId
  form.formRevisionVersion = schema.revisionVersion
}
const loadTemplates = async () => {
  const result = await DynamicFormApi.getTemplateSelection({
    pageNo: templatePage.value,
    pageSize: 20
  })
  templates.value = result.list.filter((item) => item.categoryCode === 'SITE_SURVEY')
  templateTotal.value = result.total
}
const chooseTemplate = async () => {
  await loadTemplates()
  templateVisible.value = true
}
const selectTemplate = async (template: DynamicFormApi.DynamicFormSelectionVO) => {
  const revision = await DynamicFormApi.getRevision(template.currentPublishedRevisionId)
  await SiteSurveyApi.getFormSchema(revision.revisionId, revision.revisionVersion)
  form.formRevisionId = revision.revisionId
  form.formRevisionVersion = revision.revisionVersion
  templateVisible.value = false
}
const beforeClose = async (done: () => void) => {
  if (saving.value) return
  if (!readonly.value && openedValue !== JSON.stringify(form)) {
    try {
      await message.confirm('尚有未保存的工勘内容，确定放弃并关闭？')
    } catch {
      return
    }
  }
  done()
}

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
  const payload = { ...form }
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
  if (
    !maintenance?.site?.id &&
    (!maintenance?.address?.countryName || !maintenance.address.detailAddress)
  ) {
    message.error('新地点必须填写国家和详细地址')
    return
  }
  if (!maintenance.site?.id && (!maintenance.site?.code || !maintenance.site.name)) {
    message.error('新地点必须填写站点编码和名称')
    return
  }
  if (maintenance.siteLocation && !maintenance.siteLocation.id && !maintenance.siteLocation.code) {
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
  payload.location =
    maintenance.fallbackLocation || maintenance.address?.fullAddress || payload.location
  return payload
}
const save = async () => {
  if (readonly.value || saving.value) return false
  try {
    await formRef.value.validate()
    if (form.formRevisionId) await dynamicFormRef.value?.validate()
  } catch {
    message.warning('请检查工勘必填项和表单内容')
    return false
  }
  saving.value = true
  try {
    const payload = savePayload()
    if (!payload) return false
    if (form.id) await SiteSurveyApi.updateSiteSurvey(payload)
    else form.id = await SiteSurveyApi.createSiteSurvey(payload)
    message.success('保存成功')
    formVisible.value = false
    await load()
    emit('saved')
    return true
  } catch {
    message.warning('保存未完成，已保留填写内容；请检查提示或重新加载最新版本后重试。')
    return false
  } finally {
    saving.value = false
  }
}
const startOutsource = async () => {
  if (!form.outsourceRequired || form.outsourceRequestId) return
  if (await save()) await router.push(outsourceShortcutRoute(form.id!))
}
const performSurveyAction = async (kind: string, sn?: string) => {
  if (readonly.value || saving.value) return
  if (kind === 'outsource') {
    await startOutsource()
    return
  }
  if (kind !== 'material' && kind !== 'procurement' && kind !== 'exchange') return
  if (kind === 'exchange') {
    const selected = (form.formExtraValues?.extra_selectedMaterials ||
      []) as SurveyMaterialSelection[]
    if (
      form.formExtraValues?.extra_materialMatches !== false ||
      !selected.some(
        (row) => row.sn === sn && row.reason?.trim() && row.projectId === form.projectId
      )
    )
      return
  } else if (form.formExtraValues?.extra_railTrayRequired !== true) return
  if (await save()) await router.push(surveyProcurementRoute(kind, form.id!, sn))
}
const remove = async (row: SiteSurveyVO) => {
  if (row.outsourceRequestId) {
    message.warning('工勘已关联转包申请，请先处理关联申请，不能删除来源记录')
    return
  }
  await message.delConfirm()
  await SiteSurveyApi.deleteSiteSurvey(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: SiteSurveyVO, action: 'confirm' | 'reject' | 'archive') => {
  const actionText = { confirm: '确认', reject: '驳回', archive: '归档' }[action]
  await message.confirm(`是否${actionText}工勘【${row.code}】？`)
  if (action === 'confirm') await SiteSurveyApi.confirmSiteSurvey(row.id!)
  if (action === 'reject') await SiteSurveyApi.rejectSiteSurvey(row.id!)
  if (action === 'archive') await SiteSurveyApi.archiveSiteSurvey(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(() => {
  load()
  UserApi.getSimpleUserList()
    .then((result) => (users.value = result))
    .catch(() => message.warning('工勘人员列表加载失败'))
})
watch(
  () => route.query.surveyId,
  async (value) => {
    if (!value) return
    const id = positiveShortcutId(value)
    if (!id) {
      message.warning('工勘编号无效')
      return
    }
    await load()
    await openForm(await SiteSurveyApi.getSiteSurvey(id))
  },
  { immediate: true }
)
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
