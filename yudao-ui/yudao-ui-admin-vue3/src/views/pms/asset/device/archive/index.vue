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
      <el-table-column prop="projectId" label="所属项目" width="100" />
      <el-table-column prop="customerId" label="所属客户" width="100" />
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

  <DeviceArchiveFormDialog ref="formDialog" :locked-project-id="projectId" @success="load" />
  <DeviceArchiveStatusChangeDialog ref="statusChangeDialog" @success="load" />
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import type { DeviceArchiveVO, DeviceArchiveVersionVO } from '@/api/pms/asset/device/archive'
import DeviceArchiveFormDialog from '../components/DeviceArchiveFormDialog.vue'
import DeviceArchiveStatusChangeDialog from '../components/DeviceArchiveStatusChangeDialog.vue'

defineOptions({ name: 'PmsAssetDeviceArchive' })
const props = defineProps<{ projectId?: number | string }>()
const message = useMessage()
const loading = ref(false)
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
const detailVisible = ref(false)
const detail = ref<DeviceArchiveVO>()
const locationHistory = ref<DeviceArchiveVersionVO[]>([])
const formDialog = ref<InstanceType<typeof DeviceArchiveFormDialog>>()
const statusChangeDialog = ref<InstanceType<typeof DeviceArchiveStatusChangeDialog>>()

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
  formDialog.value?.open(row)
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
  statusChangeDialog.value?.open(row)
}
onMounted(load)
watch(
  () => props.projectId,
  async () => {
    query.pageNo = 1
    query.projectId = props.projectId as number | undefined
    detailVisible.value = false
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
