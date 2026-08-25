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
          <el-button
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑工勘' : '新增工勘'" width="900px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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
              :disabled="!!form.id"
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
            <el-input v-model="form.surveyorUserId" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="工勘地点" prop="locationMaintenance">
            <PmsLocationSelector v-model="form.locationMaintenance" :project-id="form.projectId" />
          </el-form-item>
        </el-col>
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
            <Editor v-model="form.conclusion" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as SiteSurveyApi from '@/api/pms/engineering/site-survey'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'
import * as ProjectApi from '@/api/pms/project/projects'

defineOptions({ name: 'PmsEngSiteSurvey' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SiteSurveyVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: '',
  code: '',
  name: '',
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SiteSurveyVO>({ projectId: 0, code: '', name: '' })
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
const openForm = (row?: SiteSurveyVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
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
      version: undefined
    },
    row || {}
  )
  form.locationMaintenance = toLocationMaintenance(row)
  formVisible.value = true
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
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = savePayload()
    if (!payload) return
    form.id
      ? await SiteSurveyApi.updateSiteSurvey(payload)
      : await SiteSurveyApi.createSiteSurvey(payload)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: SiteSurveyVO) => {
  await message.delConfirm()
  await SiteSurveyApi.deleteSiteSurvey(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: SiteSurveyVO, action: 'confirm' | 'reject' | 'archive') => {
  const actionText = { confirm: '确认', reject: '驳回', archive: '归档' }[action]
  await message.confirm(`确认${actionText}工勘【${row.code}】？`)
  if (action === 'confirm') await SiteSurveyApi.confirmSiteSurvey(row.id!)
  if (action === 'reject') await SiteSurveyApi.rejectSiteSurvey(row.id!)
  if (action === 'archive') await SiteSurveyApi.archiveSiteSurvey(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
