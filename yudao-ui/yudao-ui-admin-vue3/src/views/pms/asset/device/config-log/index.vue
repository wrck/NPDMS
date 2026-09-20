<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item v-if="!props.projectId" label="所属项目" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-220px"
          @change="load"
        />
      </el-form-item>
      <el-form-item label="设备编号" prop="deviceId">
        <PmsEntitySelect
          v-model="query.deviceId"
          :api="DeviceArchiveApi.getDeviceArchivePage"
          :label-field="['sn', 'name']"
          value-field="id"
          query-field="sn"
          :extra-params="props.projectId != null ? { projectId: props.projectId } : {}"
          placeholder="请选择设备"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="配置类型" prop="configType">
        <el-input v-model="query.configType" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="来源系统" prop="sourceSystem">
        <el-input v-model="query.sourceSystem" clearable class="!w-220px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item>
        <el-button @click="load" v-hasPermi="['pms:device:query']"
          ><Icon icon="ep:search" />查询</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无设备配置日志数据">
      <el-table-column prop="deviceId" label="设备编号" width="110">
        <template #default="{ row }">
          <EquipmentTag :equipment-id="row.deviceId" />
        </template>
      </el-table-column>
      <el-table-column prop="configType" label="配置类型" min-width="140" />
      <el-table-column prop="sourceSystem" label="来源系统" min-width="140" />
      <el-table-column prop="collectedAt" label="采集时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column prop="fileHash" label="配置文件哈希" min-width="180" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="!row.fileUrl"
            @click="download(row)"
            v-hasPermi="['pms:device-configuration-log:download']"
            >下载</el-button
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
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import downloadFile from '@/utils/download'
import * as DeviceConfigLogApi from '@/api/pms/asset/device/archive'
import type { DeviceConfigLogVO } from '@/api/pms/asset/device/archive'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import * as DeviceApi from '@/api/pms/asset/device'
import * as ProjectApi from '@/api/pms/project/projects'
import EquipmentTag from '@/components/EquipmentTag/index.vue'

defineOptions({ name: 'PmsAssetDeviceConfigLog' })
const props = defineProps<{ projectId?: number; /** 外部跳入时预置的设备过滤（如 1.1.1 序列号详情行 → 配置Log） */ initialDeviceId?: number }>()
const loading = ref(false)
const rows = ref<DeviceConfigLogVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  projectId: props.projectId as number | undefined,
  deviceId: undefined as number | undefined,
  configType: '',
  sourceSystem: ''
})

// 外部携带设备跳入：首次挂载预置过滤，挂载后再次跳入按新设备重查
onMounted(() => {
  if (props.initialDeviceId != null) query.deviceId = props.initialDeviceId
  load()
})
watch(
  () => props.initialDeviceId,
  (id) => {
    if (id == null) return
    query.deviceId = id
    query.pageNo = 1
    load()
  }
)

const load = async () => {
  loading.value = true
  try {
    const data = await DeviceConfigLogApi.getDeviceConfigLogPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

// 与设备工作台配置Log面板同链路：申请授权票据后下载，逐次重新鉴权
const download = async (row: DeviceConfigLogVO) => {
  const grant = await DeviceApi.createConfigurationLogDownloadUrl(row.deviceId!, row.id!)
  const data = await DeviceApi.downloadConfigurationLog(grant.downloadPath)
  downloadFile.markdown(data, `configuration-log-${row.id}.txt`)
}
</script>
