<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="序列号" prop="serialNumber">
        <el-input v-model="query.serialNumber" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="设备名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_EQUIPMENT_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:equipment:create']"
          ><Icon icon="ep:plus" />新增设备</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无设备数据">
      <el-table-column prop="serialNumber" label="序列号" min-width="160" />
      <el-table-column prop="name" label="设备名称" min-width="160" />
      <el-table-column prop="model" label="设备型号" min-width="120" />
      <el-table-column prop="customerName" label="客户" min-width="140" />
      <el-table-column prop="projectName" label="项目" min-width="140" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_EQUIPMENT_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="location" label="位置" min-width="140" show-overflow-tooltip />
      <el-table-column prop="warrantyEndDate" label="保修截止" min-width="120" :formatter="dateFormatter" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:equipment:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="warning"
            @click="openStatusChange(row)"
            v-hasPermi="['pms:equipment:status-change']"
            >状态变更</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:equipment:delete']"
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

  <Dialog v-model="visible" :title="form.id ? '编辑设备' : '新增设备'" width="640px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
      <el-form-item label="序列号" prop="serialNumber">
        <el-input v-model="form.serialNumber" :disabled="!!form.id" />
      </el-form-item>
      <el-form-item label="设备名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="设备型号"><el-input v-model="form.model" /></el-form-item>
      <el-form-item label="所属客户">
        <PmsEntitySelect
          v-model="form.customerId"
          :api="CustomerApi.getCustomerPage"
          :label-field="['code', 'name']"
          value-field="id"
          query-field="name"
          placeholder="请选择客户"
        />
      </el-form-item>
      <el-form-item label="所属项目">
        <PmsEntitySelect
          v-model="form.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
        />
      </el-form-item>
      <el-form-item label="设备位置"><el-input v-model="form.location" /></el-form-item>
      <el-form-item label="保修开始日期">
        <el-date-picker v-model="form.warrantyStartDate" type="date" value-format="YYYY-MM-DD" class="!w-220px" />
      </el-form-item>
      <el-form-item label="保修结束日期">
        <el-date-picker v-model="form.warrantyEndDate" type="date" value-format="YYYY-MM-DD" class="!w-220px" />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="statusVisible" title="设备状态变更" width="520px">
    <el-form ref="statusFormRef" :model="statusForm" :rules="statusRules" label-width="120px">
      <el-form-item label="设备编号" prop="id">
        <el-input-number v-model="statusForm.id" :min="1" controls-position="right" :disabled="true" />
      </el-form-item>
      <el-form-item label="动作" prop="action">
        <el-select v-model="statusForm.action" class="!w-220px" @change="onActionChange">
          <el-option value="DEPLOY" label="DEPLOY 部署" />
          <el-option value="REPORT_FAULT" label="REPORT_FAULT 故障上报" />
          <el-option value="START_REPAIR" label="START_REPAIR 开始维修" />
          <el-option value="COMPLETE_REPAIR" label="COMPLETE_REPAIR 完成维修" />
          <el-option value="SCRAP" label="SCRAP 报废" />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="statusForm.action === 'COMPLETE_REPAIR'"
        label="目标状态"
        prop="targetStatus"
      >
        <el-select v-model="statusForm.targetStatus" class="!w-220px">
          <el-option :value="0" label="0 在库" />
          <el-option :value="1" label="1 在用" />
        </el-select>
      </el-form-item>
      <el-form-item label="变更描述"><el-input v-model="statusForm.changeDescription" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="statusVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveStatusChange">提交</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as EquipmentApi from '@/api/pms/asset/equipment'
import type { EquipmentVO, EquipmentStatusChangeReqVO } from '@/api/pms/asset/equipment'
import * as ProjectApi from '@/api/pms/project/project'
import * as CustomerApi from '@/api/pms/project/customer'

defineOptions({ name: 'PmsAssetEquipment' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<EquipmentVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  serialNumber: '',
  name: '',
  status: undefined as number | undefined
})
const visible = ref(false)
const formRef = ref()
const form = reactive<EquipmentVO>({
  serialNumber: '',
  name: ''
})
const rules = {
  serialNumber: [{ required: true, message: '请输入序列号' }],
  name: [{ required: true, message: '请输入设备名称' }]
}

const statusVisible = ref(false)
const statusFormRef = ref()
const statusForm = reactive<EquipmentStatusChangeReqVO>({
  id: 0,
  action: 'DEPLOY',
  targetStatus: undefined,
  changeDescription: ''
})
const statusRules = {
  id: [{ required: true, message: '请输入设备编号' }],
  action: [{ required: true, message: '请选择动作' }],
  targetStatus: [{ required: true, message: '请选择目标状态' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await EquipmentApi.getEquipmentPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const open = (row?: EquipmentVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      serialNumber: '',
      name: '',
      model: '',
      customerId: undefined,
      projectId: undefined,
      location: '',
      warrantyStartDate: undefined,
      warrantyEndDate: undefined,
      remark: ''
    },
    row || {}
  )
  visible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id ? await EquipmentApi.updateEquipment(form) : await EquipmentApi.createEquipment(form)
    message.success('保存成功')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: EquipmentVO) => {
  await message.delConfirm()
  await EquipmentApi.deleteEquipment(row.id!)
  message.success('删除成功')
  await load()
}
const openStatusChange = (row: EquipmentVO) => {
  Object.assign(statusForm, {
    id: row.id,
    action: 'DEPLOY',
    targetStatus: undefined,
    changeDescription: ''
  })
  statusVisible.value = true
}
const onActionChange = () => {
  if (statusForm.action !== 'COMPLETE_REPAIR') {
    statusForm.targetStatus = undefined
  }
}
const saveStatusChange = async () => {
  await statusFormRef.value.validate()
  saving.value = true
  try {
    await EquipmentApi.changeEquipmentStatus(statusForm)
    message.success('状态变更成功')
    statusVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
