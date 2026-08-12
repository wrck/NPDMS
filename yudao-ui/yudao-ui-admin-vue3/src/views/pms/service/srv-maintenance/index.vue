<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="设备编号" prop="equipmentId">
        <PmsEntitySelect
          v-model="query.equipmentId"
          :api="EquipmentApi.getEquipmentPage"
          :label-field="['serialNumber','name']"
          value-field="id"
          query-field="serialNumber"
          placeholder="请选择设备"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="维保编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="维保状态" prop="maintenanceStatus">
        <el-select v-model="query.maintenanceStatus" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SRV_MAINTENANCE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:srv-maintenance:create']"
          ><Icon icon="ep:plus" />新增维保</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="维保编码" min-width="140" />
      <el-table-column prop="equipmentId" label="设备编号" width="120" />
      <el-table-column prop="projectId" label="项目编号" width="120" />
      <el-table-column prop="serviceLevel" label="服务等级" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SERVICE_LEVEL" :value="row.serviceLevel" />
        </template>
      </el-table-column>
      <el-table-column prop="startDate" label="开始日期" width="120" />
      <el-table-column prop="endDate" label="结束日期" width="120" />
      <el-table-column prop="maintenanceStatus" label="状态" width="110">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_MAINTENANCE_STATUS" :value="row.maintenanceStatus" />
        </template>
      </el-table-column>
      <el-table-column prop="autoCalculated" label="自动计算" width="100">
        <template #default="{ row }">
          <el-tag :type="row.autoCalculated ? 'success' : 'info'">{{ row.autoCalculated ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="manualOverride" label="手工覆盖" width="100">
        <template #default="{ row }">
          <el-tag :type="row.manualOverride ? 'warning' : 'info'">{{ row.manualOverride ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:srv-maintenance:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            @click="handleAction(row, 'calculateStatus', '自动计算状态')"
            v-hasPermi="['pms:srv-maintenance:update']"
            >计算状态</el-button
          >
          <el-button
            link
            type="warning"
            @click="openOverride(row)"
            v-hasPermi="['pms:srv-maintenance:update']"
            >手工覆盖</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:srv-maintenance:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑维保记录' : '新增维保记录'" width="780px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="设备编号" prop="equipmentId">
            <PmsEntitySelect
              v-model="form.equipmentId"
              :api="EquipmentApi.getEquipmentPage"
              :label-field="['serialNumber','name']"
              value-field="id"
              query-field="serialNumber"
              placeholder="请选择设备"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="维保编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="项目编号" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="name"
              value-field="id"
              query-field="name"
              placeholder="请选择项目"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="服务等级" prop="serviceLevel">
            <el-select v-model="form.serviceLevel" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SERVICE_LEVEL)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="开始日期" prop="startDate">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="结束日期" prop="endDate">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
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

  <Dialog v-model="overrideVisible" title="手工覆盖维保状态" width="540px">
    <el-form ref="overrideFormRef" :model="overrideForm" :rules="overrideRules" label-width="140px">
      <el-form-item label="维保状态" prop="maintenanceStatus">
        <el-select v-model="overrideForm.maintenanceStatus" class="!w-full">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SRV_MAINTENANCE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="overrideVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitOverride">确认覆盖</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as SrvMaintenanceApi from '@/api/pms/service/srv-maintenance'
import * as EquipmentApi from '@/api/pms/asset/equipment'
import * as ProjectApi from '@/api/pms/project/project'
import type { SrvMaintenanceVO } from '@/api/pms/service/srv-maintenance'
import EquipmentTag from '@/components/EquipmentTag/index.vue'
import ProjectTag from '@/components/ProjectTag/index.vue'

defineOptions({ name: 'PmsSrvMaintenance' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SrvMaintenanceVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  equipmentId: '',
  projectId: '',
  code: '',
  maintenanceStatus: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SrvMaintenanceVO>({ equipmentId: 0, code: '' })
const rules = {
  equipmentId: [{ required: true, message: '请输入设备编号' }],
  code: [{ required: true, message: '请输入维保编码' }],
  serviceLevel: [{ required: true, message: '请选择服务等级' }],
  startDate: [{ required: true, message: '请选择开始日期' }],
  endDate: [{ required: true, message: '请选择结束日期' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await SrvMaintenanceApi.getSrvMaintenancePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: SrvMaintenanceVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      equipmentId: 0,
      projectId: undefined,
      code: '',
      startDate: '',
      endDate: '',
      maintenanceStatus: 0,
      serviceLevel: 'STANDARD',
      autoCalculated: false,
      manualOverride: false,
      remark: '',
      version: undefined
    },
    row || {}
  )
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id ? await SrvMaintenanceApi.updateSrvMaintenance(form) : await SrvMaintenanceApi.createSrvMaintenance(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: SrvMaintenanceVO) => {
  await message.delConfirm()
  await SrvMaintenanceApi.deleteSrvMaintenance(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: SrvMaintenanceVO, action: 'calculateStatus', actionText: string) => {
  await message.confirm(`确认${actionText}【${row.code}】？`)
  await (SrvMaintenanceApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}

// 手工覆盖
const overrideVisible = ref(false)
const overrideFormRef = ref()
const overrideForm = reactive({
  id: 0,
  maintenanceStatus: 1,
  version: undefined as number | undefined
})
const overrideRules = {
  maintenanceStatus: [{ required: true, message: '请选择维保状态' }]
}
const openOverride = (row: SrvMaintenanceVO) => {
  overrideForm.id = row.id!
  overrideForm.maintenanceStatus = row.maintenanceStatus ?? 1
  overrideForm.version = row.version
  overrideVisible.value = true
}
const submitOverride = async () => {
  await overrideFormRef.value.validate()
  saving.value = true
  try {
    await SrvMaintenanceApi.manualOverride({
      id: overrideForm.id,
      maintenanceStatus: overrideForm.maintenanceStatus,
      version: overrideForm.version
    })
    message.success('覆盖成功')
    overrideVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
