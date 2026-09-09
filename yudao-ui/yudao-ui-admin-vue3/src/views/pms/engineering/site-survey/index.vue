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
        <el-button
          data-testid="original-survey-add"
          type="primary"
          @click="openForm()"
          v-hasPermi="['pms:eng-site-survey:create']"
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
            {{ row.locationResolutionStatus === 'RESOLVED' ? '已维护' : '待维护' }}
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
          <el-button
            link
            type="primary"
            @click="openForm(row)"
            v-hasPermi="['pms:eng-site-survey:update']"
            >{{ row.status === 0 || row.status === 2 ? '编辑' : '查看' }}</el-button
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
    v-model="dialogVisible"
    :title="readonly ? '查看工勘' : form.id ? '编辑工勘' : '新增工勘'"
    :width="dialogWidth"
    :close-on-click-modal="false"
    :before-close="beforeDialogClose"
  >
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" class="mb-16px" />
    <el-alert
      v-if="readonly"
      title="已确认或归档的工勘记录只读，正文和地点不再原位修改。"
      type="info"
      :closable="false"
      class="mb-16px"
    />
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      :label-position="narrow ? 'top' : 'right'"
      :disabled="readonly || saving || awaitingReload"
    >
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="项目编号" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="工勘编码" prop="code">
            <el-input
              data-testid="original-survey-code"
              v-model="form.code"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="工勘名称" prop="name"
            ><el-input data-testid="original-survey-name" v-model="form.name"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="工勘日期" prop="surveyDate">
            <el-date-picker
              v-model="form.surveyDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="工勘人员" prop="surveyorUserId">
            <el-input data-testid="original-survey-surveyorUserId" v-model="form.surveyorUserId" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="工勘地点" prop="locationMaintenance">
            <span v-if="readonly">{{ form.location || '未填写' }}</span>
            <PmsLocationSelector
              v-else
              v-model="form.locationMaintenance"
              :project-id="form.projectId"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="供电情况" prop="powerSupply"
            ><el-input data-testid="original-survey-powerSupply" v-model="form.powerSupply"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="机柜情况" prop="cabinet"
            ><el-input data-testid="original-survey-cabinet" v-model="form.cabinet"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="网口情况" prop="networkPort"
            ><el-input data-testid="original-survey-networkPort" v-model="form.networkPort"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="光纤情况" prop="fiber"
            ><el-input data-testid="original-survey-fiber" v-model="form.fiber"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="模块情况" prop="module"
            ><el-input data-testid="original-survey-module" v-model="form.module"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="线缆情况" prop="cable"
            ><el-input data-testid="original-survey-cable" v-model="form.cable"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="接地情况" prop="ground"
            ><el-input data-testid="original-survey-ground" v-model="form.ground"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="施工资源" prop="constructionResource">
            <el-input
              data-testid="original-survey-constructionResource"
              v-model="form.constructionResource"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="工勘结论" prop="conclusion">
            <Editor
              v-model="form.conclusion"
              height="200px"
              :readonly="readonly || saving || awaitingReload"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input data-testid="original-survey-remark" v-model="form.remark" type="textarea" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button data-testid="original-survey-cancel" :disabled="saving" @click="closeForm">{{
        readonly ? '关闭' : '取消'
      }}</el-button>
      <el-button v-if="awaitingReload" type="primary" @click="reloadSaved"
        >重新读取已保存记录</el-button
      >
      <el-button
        v-else-if="!readonly"
        data-testid="original-survey-save"
        type="primary"
        :loading="saving"
        @click="save"
        >保存</el-button
      >
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, toRaw } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as SiteSurveyApi from '@/api/pms/engineering/site-survey'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'
import * as ProjectApi from '@/api/pms/project/projects'

defineOptions({ name: 'PmsEngSiteSurvey' })
const message = useMessage()
const narrow = useMediaQuery('(max-width: 767px)')
const dialogWidth = computed(() => (narrow.value ? 'calc(100vw - 24px)' : '900px'))
const saveError = ref('')
const awaitingReload = ref(false)
const savedId = ref<number>()
const baseline = ref('')
const locationBaseline = ref('')
const readonly = computed(() => form.status === 1 || form.status === 3)
const isDirty = () =>
  formVisible.value && !readonly.value && JSON.stringify(form) !== baseline.value
const closeForm = async () => {
  if (saving.value) return
  if (awaitingReload.value) {
    message.info('记录已保存，请先重新读取确认结果')
    return
  }
  if (isDirty()) {
    try {
      await message.confirm('尚有未保存的工勘内容，是否放弃本次编辑？')
    } catch {
      return
    }
  }
  formVisible.value = false
}
const beforeDialogClose = async (done: () => void) => {
  await closeForm()
  if (!formVisible.value) done()
}
const dialogVisible = computed({
  get: () => formVisible.value,
  set: (value: boolean) => {
    if (value) formVisible.value = true
    else void closeForm()
  }
})
onBeforeRouteLeave(async () => {
  if (!formVisible.value) return true
  await closeForm()
  return !formVisible.value
})
const loading = ref(false)
const saving = ref(false)
const rows = ref<SiteSurveyVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  code: '',
  name: '',
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SiteSurveyVO>({
  projectId: undefined as unknown as number,
  code: '',
  name: ''
})
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入工勘编码' }],
  name: [{ required: true, message: '请输入工勘名称' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await SiteSurveyApi.getSiteSurveyPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = async (row?: SiteSurveyVO) => {
  if (formVisible.value && isDirty()) {
    await closeForm()
    if (formVisible.value) return
  }
  saveError.value = ''
  awaitingReload.value = false
  savedId.value = undefined
  if (row?.id) row = await SiteSurveyApi.getSiteSurvey(row.id)
  Object.assign(
    form,
    {
      id: undefined,
      projectId: query.projectId,
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
      status: 0
    },
    row || {}
  )
  form.locationMaintenance = toLocationMaintenance(row)
  baseline.value = JSON.stringify(form)
  locationBaseline.value = JSON.stringify(form.locationMaintenance)
  formVisible.value = true
}

const toLocationMaintenance = (row?: SiteSurveyVO): LocationMaintainRequest | undefined => {
  if (!row) return undefined
  if (row.locationResolutionStatus !== 'RESOLVED') {
    return { projectId: row.projectId, fallbackLocation: row.location }
  }
  return {
    projectId: row.projectId,
    address: row.addressId ? { id: row.addressId, expectedVersion: row.addressVersion } : undefined,
    site: row.siteId ? { id: row.siteId, expectedVersion: row.siteVersion } : undefined,
    fallbackLocation: row.location,
    siteLocation: row.siteLocationId
      ? {
          id: row.siteLocationId,
          expectedVersion: row.siteLocationVersion
        }
      : undefined
  }
}

const savePayload = () => {
  const payload = {
    ...form,
    locationMaintenance: form.locationMaintenance
      ? structuredClone(toRaw(form.locationMaintenance))
      : undefined
  }
  delete payload.status
  delete payload.addressSnapshot
  delete payload.locationSnapshot
  if (form.id && JSON.stringify(form.locationMaintenance) === locationBaseline.value) {
    delete payload.locationMaintenance
    return payload
  }
  const maintenance = payload.locationMaintenance
  if (maintenance && !maintenance.address && !maintenance.site && !maintenance.siteLocation) {
    if (!maintenance.fallbackLocation?.trim()) {
      message.error('请选择地点或填写兼容地点')
      return
    }
    payload.location = maintenance.fallbackLocation
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
const reloadSaved = async () => {
  if (!savedId.value) return
  saving.value = true
  try {
    const current = await SiteSurveyApi.getSiteSurvey(savedId.value)
    Object.assign(form, current)
    baseline.value = JSON.stringify(form)
    awaitingReload.value = false
    saveError.value = ''
    message.success('工勘已保存并重新读取')
    formVisible.value = false
    await load()
  } catch {
    saveError.value = '记录已保存，但重新读取失败。请重试读取，不要重复提交。'
  } finally {
    saving.value = false
  }
}
const save = async () => {
  if (readonly.value || saving.value || awaitingReload.value) return
  await formRef.value.validate()
  const payload = savePayload()
  if (!payload) return
  saving.value = true
  saveError.value = ''
  try {
    savedId.value = form.id
      ? (await SiteSurveyApi.updateSiteSurvey(payload), form.id)
      : await SiteSurveyApi.createSiteSurvey(payload)
    awaitingReload.value = true
  } catch {
    saveError.value = '保存失败，填写内容已保留。请核对权限、版本或错误提示后重试。'
    return
  } finally {
    saving.value = false
  }
  await reloadSaved()
}
const remove = async (row: SiteSurveyVO) => {
  await message.delConfirm()
  await SiteSurveyApi.deleteSiteSurvey(row.id!, row.version!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: SiteSurveyVO, action: 'confirm' | 'reject' | 'archive') => {
  const actionText = { confirm: '确认', reject: '驳回', archive: '归档' }[action]
  await message.confirm(`确认${actionText}工勘【${row.code}】？`)
  if (action === 'confirm') await SiteSurveyApi.confirmSiteSurvey(row.id!, row.version!)
  if (action === 'reject') await SiteSurveyApi.rejectSiteSurvey(row.id!, row.version!)
  if (action === 'archive') await SiteSurveyApi.archiveSiteSurvey(row.id!, row.version!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>

<style scoped>
@media (max-width: 767px) {
  :deep(.el-form--inline .el-form-item),
  :deep(.el-form--inline .el-select),
  :deep(.el-form--inline .el-input) {
    width: 100% !important;
    margin-right: 0;
  }
}
</style>
