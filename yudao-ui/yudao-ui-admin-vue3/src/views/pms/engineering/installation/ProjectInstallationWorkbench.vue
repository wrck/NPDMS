<template>
  <ContentWrap>
    <el-alert :title="completionText" :type="completed ? 'success' : 'info'" :closable="false" class="mb-16px" />
    <el-alert v-if="errorText" :title="errorText" type="error" :closable="false" class="mb-16px" />
    <el-form inline @submit.prevent>
      <el-form-item label="序列号"><el-input v-model="sn" clearable placeholder="查询设备序列号" /></el-form-item>
      <el-form-item label="产品编码"><el-input v-model="productCode" clearable placeholder="查询产品编码" /></el-form-item>
      <el-form-item label="产品型号"><el-input v-model="productModel" clearable placeholder="查询产品型号" /></el-form-item>
      <el-form-item><el-button :loading="loading" @click="load">刷新</el-button></el-form-item>
      <el-form-item v-if="!readonly">
        <el-button type="primary" :disabled="loading || !selectedDevices.length" @click="openDevices(selectedDevices)" v-hasPermi="['pms:imp-installation:create']">批量维护安装地址（{{ selectedDevices.length }}）</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" ref="deviceTableRef" :data="pagedDevices" row-key="deviceId" @selection-change="selectedDevices = $event" empty-text="本项目暂无设备序列号">
      <el-table-column v-if="!readonly" type="selection" width="50" :reserve-selection="true" />
      <el-table-column prop="sn" label="设备序列号" min-width="170" />
      <el-table-column prop="productCode" label="产品编码" min-width="140" />
      <el-table-column prop="productModel" label="产品型号" min-width="140" />
      <el-table-column prop="productName" label="产品名称" min-width="160" />
      <el-table-column label="安装地址" min-width="200"><template #default="{ row }">{{ deviceAddress(row) || '未填写' }}</template></el-table-column>
      <el-table-column label="操作" width="230"><template #default="{ row }">
        <el-button v-if="!readonly" link type="primary" @click="openDevices([row])" v-hasPermi="['pms:imp-installation:create']">维护安装地址</el-button>
        <el-button link type="primary" @click="openHistory(row)" v-hasPermi="['pms:imp-installation:query']">安装历史</el-button>
      </template></el-table-column>
    </el-table>
    <Pagination :total="visibleDevices.length" v-model:page="devicePageNo" v-model:limit="devicePageSize" />
  </ContentWrap>
  <el-drawer v-model="historyVisible" :title="`${historyDevice?.sn || ''} · 安装历史`" size="min(1000px, 90vw)">
    <el-table :data="pagedHistory" empty-text="该设备暂无安装历史">
      <el-table-column prop="installLocation" label="安装地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="installTime" label="安装时间" width="170" :formatter="dateFormatter" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><dict-tag :type="DICT_TYPE.PMS_ENG_STATUS" :value="row.status" /></template>
      </el-table-column>
      <el-table-column prop="effectiveFrom" label="生效时间" width="170" :formatter="dateFormatter" />
      <el-table-column prop="effectiveTo" label="失效时间" width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="openForm(row)">查看</el-button></template>
      </el-table-column>
    </el-table>
    <Pagination :total="deviceHistory.length" v-model:page="historyPageNo" v-model:limit="historyPageSize" />
  </el-drawer>

  <Dialog v-model="dialogVisible" :title="form.id ? (readOnly ? '查看安装' : '编辑安装') : '新增安装'" width="min(900px, 95vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="readOnly">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="关联设备" prop="equipmentId">
            <span v-if="targetDevices.length">{{ targetDeviceSummary }}</span>
            <DeviceTag v-else :device-id="form.equipmentId" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="安装人员" prop="installerUserId">
            <span>{{ users.find(user => user.id === form.installerUserId)?.nickname || form.installerUserId || '—' }}</span>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="安装时间" prop="installTime">
            <span v-if="form.installTime">{{ formatDate(new Date(Number(form.installTime))) }}</span>
            <span v-else class="text-13px text-gray-500">保存后自动取提交时间</span>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="安装位置" prop="installLocation">
            <span v-if="readOnly">{{ form.installLocation || '未填写' }}</span>
            <PmsLocationSelector
              v-else-if="formVisible"
              :key="form.id ?? 'new'"
              v-model="form.locationMaintenance"
              :project-id="props.projectId"
              project-customer-sites-only
            />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="现场照片" prop="photoUrl"
            ><UploadImg v-model="form.photoUrl" :disabled="readOnly"
          /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <component v-if="nativeBusiness && formVisible" :is="nativeBusiness.Form" :key="String(form.id || 'new')" :record="form" kind="installation" :readonly="readOnly" />
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { locationSnapshotText } from '@/utils/locationSnapshot'
import PmsLocationSelector from '@/components/PmsLocationSelector/index.vue'
import DeviceTag from '@/components/DeviceTag/index.vue'
import { computed, onMounted, ref, watch, onBeforeUnmount } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE } from '@/utils/dict'
import { formatDate, dateFormatter } from '@/utils/formatTime'
import * as OriginalInstallationApi from '@/api/pms/engineering/installation'
import { useNativeBusiness, useNativeBusinessApi, useNativeBusinessEntry, useNativeBusinessDialog, useNativeBusinessLeave } from '@/components/ProjectBusiness/native/nativeBusinessContext'
import * as DeviceApi from '@/api/pms/asset/device'
import type { InstallationVO } from '@/api/pms/engineering/installation'
import * as UserApi from '@/api/system/user'
import { useUserStore } from '@/store/modules/user'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'ProjectInstallationWorkbench' })
const props = defineProps<{ projectId: number; /** 内嵌于未进入阶段的任务工作区时强制只读：隐藏业务写操作，查看照常 */ readonly?: boolean }>()
const nativeBusiness = useNativeBusiness()
const InstallationApi = useNativeBusinessApi('installation', OriginalInstallationApi)
const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<InstallationVO[]>([])
const devices = ref<DeviceApi.DeviceListVO[]>([])
const selectedDevices = ref<DeviceApi.DeviceListVO[]>([])
const targetDevices = ref<DeviceApi.DeviceListVO[]>([])
const targetDeviceSummary = computed(() => {
  const counts = new Map<string, number>()
  for (const device of targetDevices.value) {
    const code = device.productCode?.trim() || '未维护产品编码'
    counts.set(code, (counts.get(code) ?? 0) + 1)
  }
  return [...counts].map(([code, count]) => `${code} × ${count}`).join('、')
})
const sn = ref('')
const productCode = ref('')
const productModel = ref('')
const deviceTableRef = ref<{ clearSelection: () => void }>()
const devicePageNo = ref(1), devicePageSize = ref(20)
const historyVisible = ref(false)
const historyDevice = ref<DeviceApi.DeviceListVO>()
const historyPageNo = ref(1), historyPageSize = ref(20)
const deviceHistory = computed(() => rows.value.filter(row => String(row.equipmentId) === String(historyDevice.value?.deviceId)))
const pagedHistory = computed(() => deviceHistory.value.slice((historyPageNo.value - 1) * historyPageSize.value, historyPageNo.value * historyPageSize.value))
const openHistory = (device: DeviceApi.DeviceListVO) => {
  if (!checkPermi(['pms:imp-installation:query'])) return
  historyDevice.value = device
  historyPageNo.value = 1
  historyVisible.value = true
}
const addressCompletion = ref<OriginalInstallationApi.InstallationAddressCompletion>()
const errorText = ref('')
const visibleDevices = computed(() => devices.value.filter(d =>
  d.sn.includes(sn.value.trim()) &&
  (!productCode.value.trim() || (d.productCode ?? '').includes(productCode.value.trim())) &&
  (!productModel.value.trim() || (d.productModel ?? '').includes(productModel.value.trim()))
))
const pagedDevices = computed(() => visibleDevices.value.slice((devicePageNo.value - 1) * devicePageSize.value, devicePageNo.value * devicePageSize.value))
watch([sn, productCode, productModel], () => { devicePageNo.value = 1 })
const currentInstallation = (device: DeviceApi.DeviceListVO) => rows.value.find(row =>
  String(row.equipmentId) === String(device.deviceId) && row.status === 2 && !!row.effectiveFrom && !row.effectiveTo
)
const deviceAddress = (device: DeviceApi.DeviceListVO) => {
  const latest = currentInstallation(device)
  return latest?.installLocation?.trim() || locationSnapshotText(device.locationSnapshot).trim()
}
const addressedCount = computed(() => addressCompletion.value?.addressedDevices ?? 0)
const completed = computed(() => !loading.value && !errorText.value && addressCompletion.value?.completed === true)
const completionText = computed(() => loading.value ? '正在核对全部项目设备安装地址…' : errorText.value ? '安装地址核对失败，请刷新重试' : !devices.value.length ? '本项目暂无设备序列号，尚不能判定硬件安装完成' : `安装地址已填写 ${addressedCount.value}/${devices.value.length} 台${completed.value ? '，硬件安装完成条件已满足' : '，所有设备均有安装地址后完成'}`)
let loadSequence = 0
const users = ref<UserApi.UserVO[]>([])
const formVisible = ref(false)
const formRef = ref()
type InstallationForm = Omit<InstallationVO, 'installTime'> & { installTime?: string | number | null }
const form = ref<InstallationForm>({ projectId: 0, status: 0 })
const readOnly = computed(() => !!props.readonly || !!form.value.id || !checkPermi(['pms:imp-installation:create']) || !!nativeBusiness && !nativeBusiness.can('create', 'installation'))
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  installLocation: [{ validator: (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
    const location = form.value.locationMaintenance
    if (location?.site && !location.site.id) {
      if (!location.site.name?.trim()) return callback(new Error('请填写站点名称'))
      if (!location.address?.id && !location.address?.detailAddress?.trim()) return callback(new Error('请填写详细地址'))
      if (!location.address?.id && !location.address?.provinceCode) return callback(new Error('请选择省市区'))
    }
    callback(location?.site?.id || location?.site?.name?.trim() || location?.fallbackLocation?.trim() ? undefined : new Error('请选择站点或填写安装位置'))
  } }],
}

const load = async () => {
  const sequence = ++loadSequence
  const projectId = props.projectId
  loading.value = true
  errorText.value = ''
  selectedDevices.value = []
  deviceTableRef.value?.clearSelection()
  try {
    const allPages = async <T,>(getPage: (pageNo: number) => Promise<{ list: T[]; total: number }>) => {
      const result: T[] = []
      for (let pageNo = 1; ; pageNo++) {
        const page = await getPage(pageNo)
        result.push(...page.list)
        if (result.length >= Number(page.total)) return result
        if (!page.list.length) throw new Error('项目设备或安装记录分页不完整')
      }
    }
    const [deviceList, records, completion] = await Promise.all([
      allPages<DeviceApi.DeviceListVO>(pageNo => DeviceApi.getDevicePage({ projectId, pageNo, pageSize: 100 })),
      allPages<InstallationVO>(pageNo => InstallationApi.getInstallationPage({ projectId, pageNo, pageSize: 100 })),
      InstallationApi.getProjectInstallationAddressCompletion(projectId)
    ])
    if (sequence !== loadSequence) return
    if (completion.totalDevices !== deviceList.length) throw new Error('设备范围在查询中变化，请刷新')
    addressCompletion.value = completion
    devices.value = deviceList
    rows.value = records
  } catch {
    if (sequence === loadSequence) { addressCompletion.value = undefined; devices.value = []; rows.value = []; errorText.value = '加载设备或安装记录失败，请刷新重试。' }
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
const openDevices = (list: DeviceApi.DeviceListVO[]) => {
  if (props.readonly || saving.value || !list.length || !checkPermi(['pms:imp-installation:create'])) return
  openForm()
  targetDevices.value = [...list]
  form.value.equipmentId = list[0].deviceId
  const current = list.map(currentInstallation)
  const latest = current[0]
  // 批量只有地址一致才回填，避免把任意一台设备的地址套给其他设备。
  if (latest && current.every(row => row && row.siteId === latest.siteId &&
    row.siteLocationId === latest.siteLocationId && row.installLocation === latest.installLocation)) {
    form.value.installLocation = latest.installLocation
    form.value.locationMaintenance = locationMaintenance(latest)
  }
}
const locationMaintenance = (row?: InstallationVO): NonNullable<InstallationVO['locationMaintenance']> => row?.siteId ? {
  projectId: props.projectId,
  site: { id: row.siteId, expectedVersion: row.siteVersion },
  siteLocation: row.siteLocationId ? { id: row.siteLocationId, expectedVersion: row.siteLocationVersion } : undefined,
  fallbackLocation: row.installLocation
} : { projectId: props.projectId, fallbackLocation: row?.installLocation }
const openForm = (row?: InstallationVO) => {
  if (saving.value) return
  targetDevices.value = []
  form.value = {
      id: undefined,
      projectId: props.projectId ?? 0,
      equipmentId: undefined,
      installLocation: '',
      installTime: undefined,
      installerUserId: userStore.getUser.id,
      photoUrl: '',
      remark: '',
      version: undefined,
      status: 0,
      ...row
  }
  form.value.locationMaintenance = locationMaintenance(row)
  formVisible.value = true
}

const savePayload = () => {
  const payload: InstallationVO = { ...form.value, installTime: form.value.installTime == null || form.value.installTime === '' ? undefined : Number(form.value.installTime) }
  const maintenance = payload.locationMaintenance ? JSON.parse(JSON.stringify(payload.locationMaintenance)) as NonNullable<InstallationVO['locationMaintenance']> : undefined
  if (maintenance?.address && !maintenance.address.id) {
    maintenance.address.fullAddress = [maintenance.address.countryName, maintenance.address.provinceName,
      maintenance.address.cityName, maintenance.address.districtName, maintenance.address.detailAddress]
      .filter(Boolean).join('')
  }
  if (maintenance?.siteLocation && !maintenance.siteLocation.id && !maintenance.siteLocation.name?.trim()) {
    maintenance.siteLocation = undefined
  }
  payload.installLocation = maintenance?.site && !maintenance.site.id
    ? [maintenance.address?.fullAddress || maintenance.addressText, maintenance.site.name, maintenance.siteLocation?.name,
      ...(maintenance.extraSiteLocations || []).map(location => location.name)].filter(Boolean).join(' / ')
    : maintenance?.fallbackLocation?.trim() || undefined
  payload.locationMaintenance = maintenance?.site || maintenance?.address ? maintenance : undefined
  return payload
}
const save = async () => {
  if (readOnly.value || saving.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = { ...savePayload(), ...await nativeBusiness?.formInput('installation') }
    const projectId = props.projectId
    if (form.value.id) await InstallationApi.updateInstallation(payload)
    else {
      const { equipmentId: _equipmentId, id: _id, status: _status, version: _version,
        installerUserId: _installerUserId, installTime: _installTime, ...shared } = payload
      await InstallationApi.createInstallationBatch({ ...shared, installerUserId: userStore.getUser.id,
        equipmentIds: targetDevices.value.map(device => device.deviceId) })
    }
    if (props.projectId !== projectId) return
    message.success('保存成功，安装已完成')
    formVisible.value = false
    await load(); nativeBusiness?.changed()
  } catch {
    message.error(form.value.id ? '保存失败，请刷新核对记录后重试。' : '批量保存失败，请刷新核对记录后重试。')
    await load(); nativeBusiness?.changed()
  } finally {
    saving.value = false
  }
}
watch(() => props.projectId, () => { formVisible.value = false; historyVisible.value = false; historyDevice.value = undefined; sn.value = ''; productCode.value = ''; productModel.value = ''; void load() }, { immediate: true })
onBeforeUnmount(() => { ++loadSequence })
const dialogVisible = useNativeBusinessDialog('installation', formVisible, () => saving.value)
useNativeBusinessLeave(async () => !saving.value, 'installation')
useNativeBusinessEntry('installation', async id => openForm(await InstallationApi.getInstallation(id as number)))
onMounted(() => {
  UserApi.getSimpleUserList()
    .then((result) => (users.value = result))
    .catch(() => (users.value = []))
})
</script>
