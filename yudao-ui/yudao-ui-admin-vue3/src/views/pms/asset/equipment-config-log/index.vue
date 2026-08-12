<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="设备编号" prop="equipmentId">
        <PmsEntitySelect
          v-model="query.equipmentId"
          :api="EquipmentApi.getEquipmentPage"
          :label-field="['serialNumber', 'name']"
          value-field="id"
          query-field="serialNumber"
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
        <el-button @click="load" v-hasPermi="['pms:equipment-config:query']"
          ><Icon icon="ep:search" />查询</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无设备配置日志数据">
      <el-table-column prop="equipmentId" label="设备编号" width="110">
        <template #default="{ row }">
          <EquipmentTag :equipment-id="row.equipmentId" />
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
import * as EquipmentConfigLogApi from '@/api/pms/asset/equipment-config-log'
import type { EquipmentConfigLogVO } from '@/api/pms/asset/equipment-config-log'
import * as EquipmentApi from '@/api/pms/asset/equipment'
import EquipmentTag from '@/components/EquipmentTag/index.vue'

defineOptions({ name: 'PmsAssetEquipmentConfigLog' })
const loading = ref(false)
const rows = ref<EquipmentConfigLogVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  equipmentId: undefined as number | undefined,
  configType: '',
  sourceSystem: ''
})

const load = async () => {
  loading.value = true
  try {
    const data = await EquipmentConfigLogApi.getEquipmentConfigLogPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
