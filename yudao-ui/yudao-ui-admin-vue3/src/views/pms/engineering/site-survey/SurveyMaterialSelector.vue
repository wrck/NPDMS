<template>
  <section
    v-if="survey?.formExtraValues?.extra_materialMatches === false"
    aria-label="不符合现场环境的物料"
  >
    <h4>选择需要更换的物料</h4>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <template v-if="!readonly">
      <div class="filters"
        ><el-input v-model="sn" placeholder="按序列号查找项目设备" @keyup.enter="load" /><el-button
          :loading="loading"
          @click="load"
          >查询物料</el-button
        ></div
      >
      <el-table :data="rows" v-loading="loading" empty-text="当前项目没有可选设备，不生成示例物料">
        <el-table-column label="选择" width="70"
          ><template #default="{ row }"
            ><el-checkbox
              :model-value="selected.some((item) => item.sn === row.sn)"
              :aria-label="`选择物料 ${row.sn}`"
              @update:model-value="(value) => select(row, !!value)" /></template
        ></el-table-column>
        <el-table-column prop="sn" label="序列号" min-width="170" />
        <el-table-column prop="productCode" label="产品编码" min-width="150" />
        <el-table-column prop="productName" label="产品名称" min-width="150" />
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="10"
        :total="total"
        layout="prev, pager, next"
        @current-change="fetchPage"
      />
    </template>
    <el-table :data="selected" empty-text="尚未选择需要更换的物料">
      <el-table-column prop="sn" label="已选序列号" min-width="160" />
      <el-table-column prop="productCode" label="产品编码" min-width="140" />
      <el-table-column prop="productName" label="产品名称" min-width="140" />
      <el-table-column label="不符合项说明" min-width="230"
        ><template #default="{ row }"
          ><el-input
            :model-value="row.reason"
            :disabled="readonly"
            :aria-label="`不符合项说明 ${row.sn}`"
            @update:model-value="(value) => reason(row.sn, value)"
            placeholder="现场环境与物料不适配说明" /></template
      ></el-table-column>
      <el-table-column label="换货申请" width="160"
        ><template #default="{ row }"
          ><el-button
            :disabled="readonly || !row.reason?.trim()"
            @click="launch?.('exchange', row.sn)"
            v-hasPermi="['pms:eng-material-exch:create']"
            >保存并打开换货申请</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <p>申请沿用原换货页面；当前CRM推送尚未接入，不把内部草稿保存标记为推送成功。</p>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as DeviceApi from '@/api/pms/asset/device'
import type { SiteSurveyVO } from '@/api/pms/engineering/site-survey'
import type { SurveyMaterialSelection } from './surveyBusinessForm'
const props = defineProps<{
  modelValue?: SurveyMaterialSelection[]
  getSurvey?: () => SiteSurveyVO
  readonly?: boolean
  disabled?: boolean
  launch?: (kind: string, sn?: string) => void
}>()
const emit = defineEmits<{ 'update:modelValue': [value: SurveyMaterialSelection[]] }>()
const survey = computed(() => props.getSurvey?.())
const selected = computed(() => (Array.isArray(props.modelValue) ? props.modelValue : []))
const rows = ref<DeviceApi.DeviceListVO[]>([])
const page = ref(1),
  total = ref(0),
  sn = ref(''),
  loading = ref(false),
  error = ref('')
let sequence = 0
const fetchPage = async () => {
  const projectId = survey.value?.projectId
  if (
    !projectId ||
    props.readonly ||
    survey.value?.formExtraValues?.extra_materialMatches !== false
  )
    return
  const current = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await DeviceApi.getDevicePage({
      projectId,
      pageNo: page.value,
      pageSize: 10,
      sn: sn.value || undefined
    })
    if (current !== sequence) return
    rows.value = result.list
    total.value = result.total
  } catch {
    if (current === sequence) {
      rows.value = []
      error.value = '项目设备读取失败，请检查设备查询权限或重试。'
    }
  } finally {
    if (current === sequence) loading.value = false
  }
}
const load = () => {
  page.value = 1
  return fetchPage()
}
const select = (row: DeviceApi.DeviceListVO, checked: boolean) => {
  if (props.readonly || props.disabled) return
  const rest = selected.value.filter((item) => item.sn !== row.sn)
  emit(
    'update:modelValue',
    checked
      ? [
          ...rest,
          {
            deviceId: row.deviceId,
            sn: row.sn,
            productCode: row.productCode,
            productName: row.productName,
            productModel: row.productModel,
            projectId: survey.value!.projectId,
            reason: ''
          }
        ]
      : rest
  )
}
const reason = (serial: string, value: string) => {
  if (!props.readonly && !props.disabled)
    emit(
      'update:modelValue',
      selected.value.map((item) => (item.sn === serial ? { ...item, reason: value } : item))
    )
}
watch(
  () => [
    survey.value?.projectId,
    survey.value?.formExtraValues?.extra_materialMatches,
    props.readonly
  ],
  load,
  { immediate: true }
)
</script>
<style scoped>
section {
  min-width: 0;
  width: 100%;
}
.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
p {
  color: var(--el-text-color-secondary);
}
</style>
