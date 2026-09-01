<template>
  <ContentWrap>
    <el-form :model="query" inline class="-mb-15px">
      <el-form-item label="配置编码">
        <el-input
          v-model="query.configurationCode"
          clearable
          class="!w-200px"
          @keyup.enter="load"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.statusCode" clearable class="!w-140px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="reload"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openCreate" v-hasPermi="['pms:cutover-config:manage']">
          <Icon icon="ep:plus" />新建草稿
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="rows" empty-text="暂无割接配置修订">
      <el-table-column prop="configurationCode" label="配置编码" min-width="170" />
      <el-table-column prop="configurationName" label="配置名称" min-width="180" />
      <el-table-column prop="revisionNo" label="修订" width="80" align="center" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.statusCode)">{{ statusLabel(row.statusCode) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="changeSummary"
        label="变更说明"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column
        prop="updateTime"
        label="更新时间"
        min-width="165"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" width="340" :fixed="isSmallScreen ? false : 'right'">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">
            {{ row.statusCode === 'DRAFT' ? '编辑' : '查看' }}
          </el-button>
          <el-button
            v-if="row.statusCode === 'DRAFT'"
            link
            type="success"
            @click="prevalidate(row)"
            v-hasPermi="['pms:cutover-config:manage']"
            >预检</el-button
          >
          <el-button
            v-if="row.statusCode === 'DRAFT'"
            link
            type="success"
            @click="publish(row)"
            v-hasPermi="['pms:cutover-config:publish']"
            >发布</el-button
          >
          <el-button
            v-if="row.statusCode !== 'DRAFT'"
            link
            type="warning"
            @click="copy(row)"
            v-hasPermi="['pms:cutover-config:manage']"
            >复制为草稿</el-button
          >
          <el-button
            v-if="row.statusCode === 'PUBLISHED'"
            link
            type="danger"
            @click="disable(row)"
            v-hasPermi="['pms:cutover-config:disable']"
            >停用</el-button
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

  <Dialog
    v-model="editorVisible"
    :title="editorTitle"
    width="min(1320px, 94vw)"
    :fullscreen="isSmallScreen"
  >
    <CutoverConfigurationEditor
      ref="editorRef"
      v-model="form"
      :readonly="readonly"
      :validation-errors="validationErrors"
    />
    <template #footer>
      <el-button @click="editorVisible = false">关闭</el-button>
      <el-button
        v-if="!readonly && form.id"
        type="success"
        plain
        @click="prevalidate(form)"
        v-hasPermi="['pms:cutover-config:manage']"
        >发布预检</el-button
      >
      <el-button
        v-if="!readonly"
        type="primary"
        :loading="saving"
        @click="save"
        v-hasPermi="['pms:cutover-config:manage']"
        >保存草稿</el-button
      >
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import * as CutoverConfigApi from '@/api/pms/cutover/cutover-config'
import type {
  CutoverConfiguration,
  CutoverConfigStatus,
  CutoverValidationError
} from '@/api/pms/cutover/cutover-config'
import CutoverConfigurationEditor from './components/CutoverConfigurationEditor.vue'

defineOptions({ name: 'PmsCutoverConfig' })

const message = useMessage()
const isSmallScreen = useMediaQuery('(max-width: 768px)')
const loading = ref(false)
const saving = ref(false)
const rows = ref<CutoverConfiguration[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  configurationCode: '',
  statusCode: undefined as CutoverConfigStatus | undefined
})
const editorVisible = ref(false)
const editorRef = ref<InstanceType<typeof CutoverConfigurationEditor>>()
const form = ref<CutoverConfiguration>(newDraft())
const validationErrors = ref<CutoverValidationError[]>([])
const readonly = computed(() => !!form.value.id && form.value.statusCode !== 'DRAFT')
const editorTitle = computed(() => {
  if (!form.value.id) return '新建割接配置草稿'
  return `${readonly.value ? '查看' : '编辑'}：${form.value.configurationCode} / 修订${form.value.revisionNo}`
})

function newDraft(): CutoverConfiguration {
  return {
    configurationCode: '',
    configurationName: '',
    changeSummary: '',
    dictionarySnapshot: {
      cutoverType: 'pms_cutover_type',
      networkMode: 'pms_network_mode',
      deviceType: 'pms_device_type',
      cutoverLevel: 'pms_risk_level'
    },
    dimensions: [
      {
        code: 'CUTOVER_TYPE',
        name: '割接类型',
        dataType: 'STRING',
        valueSource: 'DICT:pms_cutover_type',
        owner: 'CUT',
        contextPath: 'task.cutoverType',
        enabled: true
      },
      {
        code: 'NETWORK_MODE',
        name: '组网模式',
        dataType: 'STRING',
        valueSource: 'DICT:pms_network_mode',
        owner: 'CUT',
        contextPath: 'task.networkMode',
        enabled: true
      },
      {
        code: 'DEVICE_TYPE',
        name: '设备类型',
        dataType: 'STRING',
        valueSource: 'DICT:pms_device_type',
        owner: 'SYSTEM',
        contextPath: 'task.deviceType',
        enabled: true
      },
      {
        code: 'CUTOVER_LEVEL',
        name: '割接等级',
        dataType: 'STRING',
        valueSource: 'DICT:pms_risk_level',
        owner: 'CUT',
        contextPath: 'assessment.level',
        enabled: true
      }
    ],
    planTemplateSections: [],
    items: [],
    bindingRules: []
  }
}

const statusLabel = (status?: CutoverConfigStatus) =>
  ({
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    DISABLED: '已停用'
  })[status || 'DRAFT']
const statusTagType = (status?: CutoverConfigStatus) =>
  ({
    DRAFT: 'info',
    PUBLISHED: 'success',
    DISABLED: 'warning'
  })[status || 'DRAFT'] as 'info' | 'success' | 'warning'

const load = async () => {
  loading.value = true
  try {
    const data = await CutoverConfigApi.getPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const reload = () => {
  query.pageNo = 1
  load()
}
const openCreate = () => {
  form.value = newDraft()
  validationErrors.value = []
  editorVisible.value = true
}
const openDetail = async (row: CutoverConfiguration) => {
  form.value = await CutoverConfigApi.getDetail(row.id!)
  validationErrors.value = form.value.validationErrors || []
  editorVisible.value = true
}
const save = async () => {
  await editorRef.value?.validate()
  saving.value = true
  try {
    if (form.value.id) {
      await CutoverConfigApi.updateDraft(form.value.id, form.value.version!, form.value)
      message.success('草稿已保存')
    } else {
      const id = await CutoverConfigApi.createDraft(form.value)
      form.value = await CutoverConfigApi.getDetail(id)
      message.success('草稿已创建')
    }
    validationErrors.value = []
    await load()
  } finally {
    saving.value = false
  }
}
const prevalidate = async (row: CutoverConfiguration) => {
  const result = await CutoverConfigApi.validateRevision(row.id!)
  if (result.valid) {
    message.success('发布预检通过')
  } else {
    message.error(`发布预检发现 ${result.errors.length} 项错误`)
  }
  if (editorVisible.value && form.value.id === row.id) {
    validationErrors.value = result.errors
    editorRef.value?.showValidation(result.errors)
  }
}
const publish = async (row: CutoverConfiguration) => {
  await message.confirm(`确认发布 ${row.configurationCode} 修订${row.revisionNo}？`)
  await CutoverConfigApi.publishRevision(row.id!, row.version!)
  message.success('配置已发布')
  await load()
}
const copy = async (row: CutoverConfiguration) => {
  const id = await CutoverConfigApi.copyRevision(row.id!, row.version!)
  message.success('已复制为新草稿')
  await load()
  await openDetail({ id } as CutoverConfiguration)
}
const disable = async (row: CutoverConfiguration) => {
  await message.confirm(
    `确认停用 ${row.configurationCode} 修订${row.revisionNo}？停用后仅保留历史读取。`
  )
  await CutoverConfigApi.disableRevision(row.id!, row.version!)
  message.success('配置已停用')
  await load()
}

onMounted(load)
</script>
