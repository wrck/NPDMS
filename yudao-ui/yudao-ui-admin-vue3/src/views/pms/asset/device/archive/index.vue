<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="序列号" prop="sn">
        <el-input v-model="query.sn" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="设备名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_DEVICE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="open()" v-hasPermi="['pms:device:create']"
          ><Icon icon="ep:plus" />新增设备</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无设备数据">
      <el-table-column prop="sn" label="序列号" min-width="160" />
      <el-table-column prop="name" label="设备名称" min-width="160" />
      <el-table-column prop="productModel" label="设备型号" min-width="120" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_DEVICE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column prop="locationSnapshot" label="位置快照" min-width="140" show-overflow-tooltip />
      <el-table-column prop="locationResolutionStatus" label="地点状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.locationResolutionStatus === 'RESOLVED' ? 'success' : 'warning'">
            {{ row.locationResolutionStatus || 'UNRESOLVED' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="warrantyEndDate"
        label="保修截止"
        min-width="120"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">地点详情</el-button>
          <el-button link type="primary" @click="open(row)" v-hasPermi="['pms:device:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="warning"
            @click="openStatusChange(row)"
            v-hasPermi="['pms:device:status-change']"
            >状态变更</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:device:delete']"
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
      <el-form-item label="序列号" prop="sn">
        <el-input v-model="form.sn" :disabled="!!form.id" />
      </el-form-item>
      <el-form-item label="设备名称" prop="name"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="设备型号"><el-input v-model="form.productModel" /></el-form-item>
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
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          :disabled="projectLocked"
        />
      </el-form-item>
      <el-alert type="info" :closable="false" show-icon class="mb-16px">
        设备当前位置由安装完成动作生效，此处仅维护设备档案。
      </el-alert>
      <el-form-item label="保修开始日期">
        <el-date-picker
          v-model="form.warrantyStartDate"
          type="date"
          value-format="YYYY-MM-DD"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="保修结束日期">
        <el-date-picker
          v-model="form.warrantyEndDate"
          type="date"
          value-format="YYYY-MM-DD"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="detailVisible" title="设备当前位置与变更轨迹" width="760px">
    <el-descriptions v-if="detail" :column="2" border>
      <el-descriptions-item label="设备"
        >{{ detail.sn }} {{ detail.name }}</el-descriptions-item
      >
      <el-descriptions-item label="解析状态">
        <el-tag :type="detail.locationResolutionStatus === 'RESOLVED' ? 'success' : 'warning'">
          {{ detail.locationResolutionStatus || 'UNRESOLVED' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="当前地点" :span="2">{{
        detail.locationSnapshot || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="站点">{{
        detail.siteId ? `#${detail.siteId}` : '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="站点位置">{{
        detail.siteLocationId ? `#${detail.siteLocationId}` : '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="生效时间">{{
        detail.locationEffectiveFrom || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="来源安装记录">
        {{ detail.locationRecordId ? `#${detail.locationRecordId}` : '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="发生时快照" :span="2">
        <pre class="snapshot">{{ detail.locationSnapshot || '-' }}</pre>
      </el-descriptions-item>
    </el-descriptions>
    <el-divider content-position="left">位置变更历史</el-divider>
    <el-timeline>
      <el-timeline-item
        v-for="item in locationHistory"
        :key="item.id"
        :timestamp="String(item.createTime || '')"
      >
        <strong>{{ item.changeType }}</strong> · {{ item.changeDescription || '-' }}
      </el-timeline-item>
    </el-timeline>
  </Dialog>

  <Dialog v-model="statusVisible" title="设备状态变更" width="520px">
    <el-form ref="statusFormRef" :model="statusForm" :rules="statusRules" label-width="120px">
      <el-form-item label="设备编号" prop="id">
        <el-input-number
          v-model="statusForm.id"
          :min="1"
          controls-position="right"
          :disabled="true"
        />
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
          <el-option value="IN_STOCK" label="IN_STOCK 在库" />
          <el-option value="IN_USE" label="IN_USE 在用" />
        </el-select>
      </el-form-item>
      <el-form-item label="变更描述"
        ><el-input v-model="statusForm.changeDescription" type="textarea"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="statusVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveStatusChange">提交</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import type { DeviceArchiveVO, DeviceArchiveStatusChangeReqVO } from '@/api/pms/asset/device/archive'
import type { DeviceArchiveVersionVO } from '@/api/pms/asset/device/archive'
import * as ProjectApi from '@/api/pms/project/projects'
import * as CustomerApi from '@/api/pms/project/customer'

defineOptions({ name: 'PmsAssetDeviceArchive' })
const props = defineProps<{ projectId?: number | string }>()
const projectLocked = computed(() => props.projectId != null)
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<DeviceArchiveVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  sn: '',
  name: '',
  projectId: props.projectId as number | undefined,
  status: undefined as string | undefined
})
const visible = ref(false)
const detailVisible = ref(false)
const detail = ref<DeviceArchiveVO>()
const locationHistory = ref<DeviceArchiveVersionVO[]>([])
const formRef = ref()
const form = reactive<DeviceArchiveVO>({
  sn: '',
  name: ''
})
const rules = {
  sn: [{ required: true, message: '请输入序列号' }],
  name: [{ required: true, message: '请输入设备名称' }]
}

const statusVisible = ref(false)
const statusFormRef = ref()
const statusForm = reactive<DeviceArchiveStatusChangeReqVO>({
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
    const data = await DeviceArchiveApi.getDeviceArchivePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const open = (row?: DeviceArchiveVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      sn: '',
      name: '',
      productModel: '',
      customerId: undefined,
      projectId: projectLocked.value ? (props.projectId as number) : undefined,
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
    const payload = {
      id: form.id,
      sn: form.sn,
      name: form.name,
      productModel: form.productModel,
      customerId: form.customerId,
      projectId: form.projectId,
      warrantyStartDate: form.warrantyStartDate,
      warrantyEndDate: form.warrantyEndDate,
      remark: form.remark,
    }
    form.id
      ? await DeviceArchiveApi.updateDeviceArchive(form.id, payload)
      : await DeviceArchiveApi.createDeviceArchive(payload)
    message.success('保存成功')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const openDetail = async (row: DeviceArchiveVO) => {
  const [current, versions] = await Promise.all([
    DeviceArchiveApi.getDeviceArchiveRecord(row.id!),
    DeviceArchiveApi.getDeviceArchiveVersions(row.id!)
  ])
  detail.value = current
  locationHistory.value = (versions || []).filter(
    (item: DeviceArchiveVersionVO) => item.changeType === 'LOCATION_EFFECTIVE'
  )
  detailVisible.value = true
}
const remove = async (row: DeviceArchiveVO) => {
  await message.delConfirm()
  await DeviceArchiveApi.deleteDeviceArchive(row.id!)
  message.success('删除成功')
  await load()
}
const openStatusChange = (row: DeviceArchiveVO) => {
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
    await DeviceArchiveApi.changeDeviceArchiveStatus(statusForm.id!, statusForm)
    message.success('状态变更成功')
    statusVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
watch(
  () => props.projectId,
  async () => {
    query.pageNo = 1
    query.projectId = props.projectId as number | undefined
    visible.value = false
    detailVisible.value = false
    statusVisible.value = false
    await load()
  }
)
</script>

<style scoped>
.snapshot {
  margin: 0;
  word-break: break-all;
  white-space: pre-wrap;
}
</style>
