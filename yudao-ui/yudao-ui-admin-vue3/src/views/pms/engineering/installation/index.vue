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
      <el-form-item label="安装编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ENG_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:eng-installation:create']"
          ><Icon icon="ep:plus" />新增安装</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="安装编码" min-width="140" />
      <el-table-column prop="equipmentId" label="设备编号" width="100">
        <template #default="{ row }">
          <EquipmentTag :equipment-id="row.equipmentId" />
        </template>
      </el-table-column>
      <el-table-column
        prop="installLocation"
        label="安装位置"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column prop="locationResolutionStatus" label="地点状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.locationResolutionStatus === 'RESOLVED' ? 'success' : 'warning'">
            {{ row.locationResolutionStatus || 'UNRESOLVED' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="installTime" label="安装时间" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ENG_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openForm(row)"
            v-hasPermi="['pms:eng-installation:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'start')"
            v-hasPermi="['pms:eng-installation:update']"
            >开始安装</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'complete')"
            v-hasPermi="['pms:eng-installation:update']"
            >完成安装</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 1"
            @click="handleAction(row, 'markAbnormal')"
            v-hasPermi="['pms:eng-installation:update']"
            >标记异常</el-button
          >
          <el-button
            link
            type="danger"
            @click="remove(row)"
            v-hasPermi="['pms:eng-installation:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑安装' : '新增安装'" width="900px">
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
          <el-form-item label="安装编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联设备" prop="equipmentId">
            <PmsEntitySelect
              v-model="form.equipmentId"
              :api="EquipmentApi.getEquipmentPage"
              :label-field="['serialNumber', 'name']"
              value-field="id"
              query-field="serialNumber"
              placeholder="请选择设备"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="安装人员" prop="installerUserId"
            ><el-input v-model="form.installerUserId"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="安装时间" prop="installTime">
            <el-date-picker
              v-model="form.installTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="安装位置" prop="locationMaintenance">
            <PmsLocationSelector v-model="form.locationMaintenance" :project-id="form.projectId" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="环境检查" prop="environmentCheck">
            <Editor v-model="form.environmentCheck" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="规格检查" prop="specCheck">
            <Editor v-model="form.specCheck" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="现场照片" prop="photoUrl"
            ><UploadImg v-model="form.photoUrl"
          /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="安装结果" prop="result">
            <Editor v-model="form.result" height="200px" />
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
import * as InstallationApi from '@/api/pms/engineering/installation'
import type { InstallationVO } from '@/api/pms/engineering/installation'
import type { LocationMaintainRequest } from '@/api/pms/asset/location'
import * as ProjectApi from '@/api/pms/project/projects'
import * as EquipmentApi from '@/api/pms/asset/equipment'

defineOptions({ name: 'PmsEngInstallation' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<InstallationVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: '', code: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<InstallationVO>({ projectId: 0, code: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入安装编码' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await InstallationApi.getInstallationPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: InstallationVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      code: '',
      equipmentId: undefined,
      installLocation: '',
      locationMaintenance: undefined,
      installTime: '',
      installerUserId: undefined,
      environmentCheck: '',
      specCheck: '',
      photoUrl: '',
      result: '',
      remark: '',
      version: undefined
    },
    row || {}
  )
  form.locationMaintenance = toLocationMaintenance(row)
  formVisible.value = true
}

const toLocationMaintenance = (row?: InstallationVO): LocationMaintainRequest | undefined => {
  if (!row) return { projectId: form.projectId }
  if (row.locationResolutionStatus !== 'RESOLVED') {
    return { projectId: row.projectId, fallbackLocation: row.installLocation }
  }
  return {
    projectId: row.projectId,
    address: row.addressId ? { id: row.addressId, expectedVersion: row.addressVersion } : undefined,
    site: row.siteId ? { id: row.siteId, expectedVersion: row.siteVersion } : undefined,
    siteLocation: row.siteLocationId
      ? {
          id: row.siteLocationId,
          expectedVersion: row.siteLocationVersion,
          code: '',
          name: '',
          locationType: '',
          treeSort: 0
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
    payload.installLocation = maintenance.fallbackLocation
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
  payload.installLocation =
    maintenance.fallbackLocation || maintenance.address?.fullAddress || payload.installLocation
  return payload
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = savePayload()
    if (!payload) return
    form.id
      ? await InstallationApi.updateInstallation(payload)
      : await InstallationApi.createInstallation(payload)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: InstallationVO) => {
  await message.delConfirm()
  await InstallationApi.deleteInstallation(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: InstallationVO, action: 'start' | 'complete' | 'markAbnormal') => {
  const actionText = { start: '开始安装', complete: '完成安装', markAbnormal: '标记异常' }[action]
  await message.confirm(`确认${actionText}安装记录【${row.code}】？`)
  if (action === 'start') await InstallationApi.startInstallation(row.id!)
  if (action === 'complete') {
    await InstallationApi.completeInstallation(row.id!)
    if (row.equipmentId) {
      await Promise.all([
        EquipmentApi.getEquipment(row.equipmentId),
        EquipmentApi.getEquipmentVersionList(row.equipmentId)
      ])
    }
  }
  if (action === 'markAbnormal') await InstallationApi.markAbnormalInstallation(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
