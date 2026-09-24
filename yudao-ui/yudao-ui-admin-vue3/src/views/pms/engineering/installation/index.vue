<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item v-if="!props.projectId" label="项目编号" prop="projectId">
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
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:imp-installation:create']"
          ><Icon icon="ep:plus" />新增安装</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="equipmentId" label="设备编号" width="100">
        <template #default="{ row }">
          <DeviceTag :device-id="row.equipmentId" />
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
      <el-table-column prop="installTime" label="安装时间" width="160" :formatter="dateFormatter" />
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
            v-hasPermi="['pms:imp-installation:query']"
            >{{ editableRecord(row) ? '编辑' : '查看' }}</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'start')"
            v-hasPermi="['pms:imp-installation:update']"
            >开始安装</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'complete')"
            v-hasPermi="['pms:imp-installation:update']"
            >完成安装</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0"
            @click="handleAction(row, 'markAbnormal')"
            v-hasPermi="['pms:imp-installation:update']"
            >标记异常</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status !== 2"
            @click="remove(row)"
            v-hasPermi="['pms:imp-installation:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? (readOnly ? '查看安装' : '编辑安装') : '新增安装'" width="min(900px, 95vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="readOnly">
      <el-row :gutter="16">
        <el-col v-if="!props.projectId" :span="12">
          <el-form-item label="项目编号" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              @change="form.equipmentId = undefined"
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
          <el-form-item label="关联设备" prop="equipmentId">
            <ProjectDeviceSelect v-model="form.equipmentId" :project-id="form.projectId" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="安装人员" prop="installerUserId">
            <el-select
              v-model="form.installerUserId"
              filterable
              clearable
              class="!w-full"
              placeholder="默认当前用户，可下拉选择"
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
        <el-col :span="12">
          <el-form-item label="安装时间" prop="installTime">
            <span v-if="form.installTime">{{ formatDate(new Date(Number(form.installTime))) }}</span>
            <span v-else class="text-13px text-gray-500">保存后自动取提交时间</span>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="安装位置" prop="installLocation">
            <el-input
              v-model="form.installLocation"
              placeholder="站点未维护时手动填写站点"
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
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { formatDate, dateFormatter } from '@/utils/formatTime'
import * as InstallationApi from '@/api/pms/engineering/installation'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import type { InstallationVO } from '@/api/pms/engineering/installation'
import * as ProjectApi from '@/api/pms/project/projects'
import * as UserApi from '@/api/system/user'
import { useUserStore } from '@/store/modules/user'
import ProjectDeviceSelect from '@/components/ProjectDeviceSelect/index.vue'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'PmsEngInstallation' })
const props = defineProps<{ projectId?: number }>()
const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<InstallationVO[]>([])
const total = ref(0)
const users = ref<UserApi.UserVO[]>([])
const query = reactive({ pageNo: 1, pageSize: 10, projectId: props.projectId ?? '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
type InstallationForm = Omit<InstallationVO, 'installTime'> & { installTime?: string | number | null }
const form = ref<InstallationForm>({ projectId: 0, status: 0 })
const editableRecord = (row: Pick<InstallationVO, 'status'>) => [0, 1, 3].includes(row.status ?? -1) && checkPermi(['pms:imp-installation:update'])
const readOnly = computed(() => form.value.id ? !editableRecord(form.value) : !checkPermi(['pms:imp-installation:create']))
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  installLocation: [{ required: true, message: '请填写安装位置（站点）' }],
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
  formVisible.value = true
}

const savePayload = () => {
  const payload: InstallationVO = { ...form.value, installTime: form.value.installTime == null || form.value.installTime === '' ? undefined : Number(form.value.installTime) }
  payload.installLocation = form.value.installLocation?.trim() || undefined
  payload.locationMaintenance = undefined
  return payload
}
const save = async () => {
  if (readOnly.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = savePayload()
    form.value.id
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
  if (row.status === 2) return
  await message.delConfirm()
  await InstallationApi.deleteInstallation(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: InstallationVO, action: 'start' | 'complete' | 'markAbnormal') => {
  const actionText = { start: '开始安装', complete: '完成安装', markAbnormal: '标记异常' }[action]
  await message.confirm(`确认${actionText}该安装记录？`)
  if (action === 'start') await InstallationApi.startInstallation(row.id!)
  if (action === 'complete') {
    await InstallationApi.completeInstallation(row.id!)
    if (row.equipmentId) {
      await Promise.all([
        DeviceArchiveApi.getDeviceArchiveRecord(row.equipmentId),
        DeviceArchiveApi.getDeviceArchiveVersions(row.equipmentId)
      ])
    }
  }
  if (action === 'markAbnormal') await InstallationApi.markAbnormalInstallation(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(() => {
  load()
  UserApi.getSimpleUserList()
    .then((result) => (users.value = result))
    .catch(() => (users.value = []))
})
</script>
