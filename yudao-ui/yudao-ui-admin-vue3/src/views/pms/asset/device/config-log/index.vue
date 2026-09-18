<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="设备编号" prop="deviceId">
        <PmsEntitySelect
          v-model="query.deviceId"
          :api="DeviceArchiveApi.getDeviceArchivePage"
          :label-field="['sn', 'name']"
          value-field="id"
          query-field="sn"
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
      <el-table-column prop="fileUrl" label="配置文件URL" min-width="200" show-overflow-tooltip />
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" min-width="160" :formatter="dateFormatter" />
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
import { onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import * as DeviceConfigLogApi from '@/api/pms/asset/device/archive'
import type { DeviceConfigLogVO } from '@/api/pms/asset/device/archive'
import * as DeviceArchiveApi from '@/api/pms/asset/device/archive'
import EquipmentTag from '@/components/EquipmentTag/index.vue'

defineOptions({ name: 'PmsAssetDeviceConfigLog' })
const loading = ref(false)
const rows = ref<DeviceConfigLogVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  deviceId: undefined as number | undefined,
  configType: '',
  sourceSystem: ''
})

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
onMounted(load)
</script>
