<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目" prop="projectId">
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
      <el-form-item label="设备" prop="equipmentId">
        <PmsEntitySelect
          v-model="query.equipmentId"
          :api="EquipmentApi.getEquipmentPage"
          :label-field="['serialNumber', 'name']"
          value-field="id"
          query-field="serialNumber"
          placeholder="请选择设备"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="转维保编号" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_MAINT_TRANSITION_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:acc-maintenance-transition:create']"
          ><Icon icon="ep:plus" />新增转维保</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="转维保编号" min-width="140" />
      <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="equipmentId" label="设备编号" width="100">
        <template #default="{ row }">
          <EquipmentTag :equipment-id="row.equipmentId" />
        </template>
      </el-table-column>
      <el-table-column prop="acceptanceDate" label="验收日期" width="120" />
      <el-table-column prop="warrantyStartDate" label="维保开始" width="120" />
      <el-table-column prop="warrantyEndDate" label="维保结束" width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_MAINT_TRANSITION_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="520" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:acc-maintenance-transition:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'submitMaintenanceTransition', '提交')"
            v-hasPermi="['pms:acc-maintenance-transition:update']"
            >提交</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'activateMaintenanceTransition', '生效')"
            v-hasPermi="['pms:acc-maintenance-transition:update']"
            >生效</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 2"
            @click="handleAction(row, 'expireMaintenanceTransition', '过期')"
            v-hasPermi="['pms:acc-maintenance-transition:update']"
            >过期</el-button
          >
          <el-button
            link
            type="primary"
            v-if="row.status === 3"
            @click="handleAction(row, 'renewMaintenanceTransition', '续保')"
            v-hasPermi="['pms:acc-maintenance-transition:update']"
            >续保</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:acc-maintenance-transition:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑转维保' : '新增转维保'" width="780px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="name"
              value-field="id"
              query-field="name"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备" prop="equipmentId">
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
          <el-form-item label="转维保编号" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="验收日期" prop="acceptanceDate">
            <el-date-picker v-model="form.acceptanceDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="维保开始" prop="warrantyStartDate">
            <el-date-picker v-model="form.warrantyStartDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="维保结束" prop="warrantyEndDate">
            <el-date-picker v-model="form.warrantyEndDate" type="date" value-format="YYYY-MM-DD" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="维保方案" prop="servicePlanUrl">
            <UploadFile v-model="form.servicePlanUrl" />
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
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as MaintenanceTransitionApi from '@/api/pms/project/maintenance-transition'
import * as ProjectApi from '@/api/pms/project/project'
import * as EquipmentApi from '@/api/pms/asset/equipment'
import EquipmentTag from '@/components/EquipmentTag/index.vue'
import type { MaintenanceTransitionVO } from '@/api/pms/project/maintenance-transition'

defineOptions({ name: 'PmsMaintenanceTransition' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<MaintenanceTransitionVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  equipmentId: undefined as number | undefined,
  code: '',
  name: '',
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<MaintenanceTransitionVO>({ projectId: undefined, code: '', name: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入转维保编号' }],
  name: [{ required: true, message: '请输入名称' }]
}


const load = async () => {
  loading.value = true
  try {
    const data = await MaintenanceTransitionApi.getMaintenanceTransitionPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: MaintenanceTransitionVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: undefined,
      equipmentId: undefined,
      code: '',
      name: '',
      acceptanceDate: '',
      warrantyStartDate: '',
      warrantyEndDate: '',
      servicePlanUrl: '',
      status: 0,
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
    form.id
      ? await MaintenanceTransitionApi.updateMaintenanceTransition(form)
      : await MaintenanceTransitionApi.createMaintenanceTransition(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: MaintenanceTransitionVO) => {
  await message.delConfirm()
  await MaintenanceTransitionApi.deleteMaintenanceTransition(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (
  row: MaintenanceTransitionVO,
  action:
    | 'submitMaintenanceTransition'
    | 'activateMaintenanceTransition'
    | 'expireMaintenanceTransition'
    | 'renewMaintenanceTransition',
  actionText: string
) => {
  await message.confirm(`确认${actionText}转维保【${row.code}】？`)
  await (MaintenanceTransitionApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}

onMounted(load)
</script>
