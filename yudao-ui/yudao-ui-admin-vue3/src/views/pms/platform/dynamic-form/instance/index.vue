<template>
  <div class="dynamic-instance-page">
    <ContentWrap>
      <el-form inline class="query-form">
        <el-form-item class="query-actions">
          <el-button @click="load"><Icon icon="ep:refresh" />刷新</el-button>
          <el-button
            v-hasPermi="['pms:dynamic-form-instance:create']"
            type="primary"
            @click="openCreate"
            ><Icon icon="ep:plus" />新建实例</el-button
          >
        </el-form-item>
      </el-form>
    </ContentWrap>
    <ContentWrap>
      <el-alert
        title="模板停用仅阻止新建实例，不会改变既有实例冻结的修订或已填写内容。"
        type="info"
        :closable="false"
        class="mb-12px"
      />
      <el-table
        v-if="!narrow"
        data-testid="instance-desktop-list"
        v-loading="loading"
        :data="rows"
        empty-text="暂无动态表单实例"
      >
        <el-table-column prop="instanceCode" label="实例编码" min-width="150" />
        <el-table-column
          prop="instanceName"
          label="实例名称"
          min-width="190"
          show-overflow-tooltip
        />
        <el-table-column prop="templateName" label="冻结模板" min-width="180" />
        <el-table-column label="冻结修订" min-width="110"
          ><template #default="{ row }"
            >修订 {{ row.templateRevisionNo }}</template
          ></el-table-column
        >
        <el-table-column prop="instanceVersion" label="实例版本" width="100" />
        <el-table-column prop="updateTime" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="130" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openInstance(row.instanceId)">{{
              row.allowedActions.includes('PATCH_INSTANCE') ? '填写' : '查看'
            }}</el-button></template
          ></el-table-column
        >
      </el-table>
      <Pagination
        v-if="!narrow"
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="load"
      />
      <div v-else class="mobile-cards" data-testid="instance-mobile-list">
        <el-card v-for="row in rows" :key="row.instanceId" shadow="never">
          <div class="card-title"
            ><strong>{{ row.instanceName }}</strong
            ><span>{{ row.instanceCode }}</span></div
          >
          <div class="card-meta"
            ><span>{{ row.templateName }}</span
            ><el-tag>修订 {{ row.templateRevisionNo }}</el-tag
            ><span>V{{ row.instanceVersion }}</span></div
          >
          <el-button type="primary" class="card-open" @click="openInstance(row.instanceId)">{{
            row.allowedActions.includes('PATCH_INSTANCE') ? '填写' : '查看'
          }}</el-button>
        </el-card>
      </div>
    </ContentWrap>

    <Dialog v-model="createVisible" title="选择模板并创建实例" width="900px">
      <div class="selection-layout">
        <div class="selection-search"
          ><span>请选择当前已启用的发布模板</span
          ><el-button @click="loadSelection">刷新模板</el-button></div
        >
        <el-empty v-if="!selection.length" description="暂无可选择的已启用发布模板" />
        <div class="selection-cards">
          <el-card
            v-for="item in selection"
            :key="item.templateId"
            :class="{ selected: selected?.templateId === item.templateId }"
            shadow="never"
            @click="selectTemplate(item)"
          >
            <div class="card-title"
              ><strong>{{ item.templateName }}</strong
              ><el-tag>修订 {{ item.currentPublishedRevisionNo }}</el-tag></div
            >
            <div>{{ item.templateCode }} · {{ item.categoryCode }}</div>
            <p>{{ item.description || '无说明' }}</p>
          </el-card>
        </div>
        <div v-if="selectedRevision" class="selection-preview">
          <h4>模板预览</h4>
          <form-create
            v-model="previewValue"
            :option="preview.option"
            :rule="preview.rule"
            :disabled="true"
          />
        </div>
        <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
          <el-form-item label="实例名称" prop="instanceName"
            ><el-input v-model="createForm.instanceName" maxlength="128" show-word-limit
          /></el-form-item>
        </el-form>
      </div>
      <template #footer
        ><el-button @click="createVisible = false">取消</el-button
        ><el-button
          type="primary"
          :disabled="!canCreateSelected"
          :loading="creating"
          @click="createInstance"
          >创建实例</el-button
        ></template
      >
    </Dialog>

    <DynamicFormInstanceForm
      v-model="instanceVisible"
      :instance-id="activeInstanceId"
      @changed="load"
    />
  </div>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import type {
  DynamicFormInstanceSummaryVO,
  DynamicFormRevisionVO,
  DynamicFormSelectionVO
} from '@/api/pms/platform/dynamic-form'
import DynamicFormInstanceForm from './DynamicFormInstanceForm.vue'
import { decodeDynamicForm } from '../components/dynamicFormCodec'
import { registerDynamicFormComponents } from '../components/registerDynamicFormComponents'
import { stableCommandIntent } from '../components/dynamicFormRuntime'

defineOptions({ name: 'PmsDynamicFormInstance' })
const message = useMessage()
const narrow = useMediaQuery('(width <= 767px)')
registerDynamicFormComponents()
const loading = ref(false)
const rows = ref<DynamicFormInstanceSummaryVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })
const load = async () => {
  loading.value = true
  try {
    const page = await DynamicFormApi.getInstancePage(query)
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}

const instanceVisible = ref(false)
const activeInstanceId = ref<number>()
const openInstance = (id: number) => {
  activeInstanceId.value = id
  instanceVisible.value = true
}

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref()
const createForm = reactive({ instanceName: '' })
const createRules = {
  instanceName: [{ required: true, message: '请输入实例名称', trigger: 'blur' }]
}
const selection = ref<DynamicFormSelectionVO[]>([])
const selected = ref<DynamicFormSelectionVO>()
const selectedRevision = ref<DynamicFormRevisionVO>()
const preview = reactive({
  option: {} as Record<string, unknown>,
  rule: [] as Record<string, unknown>[]
})
const previewValue = ref({})
const canCreateSelected = computed(
  () => selected.value?.allowedActions.includes('CREATE_INSTANCE') ?? false
)
const loadSelection = async () => {
  const page = await DynamicFormApi.getTemplateSelection({ pageNo: 1, pageSize: 50 })
  selection.value = page.list
}
const selectTemplate = async (item: DynamicFormSelectionVO) => {
  selected.value = item
  selectedRevision.value = await DynamicFormApi.getRevision(item.currentPublishedRevisionId)
  const decoded = decodeDynamicForm(
    selectedRevision.value.formConfJson,
    selectedRevision.value.formRulesJson
  )
  preview.option = { ...decoded.option, submitBtn: false, resetBtn: false }
  preview.rule = decoded.rule
}
const openCreate = () => {
  createForm.instanceName = ''
  selected.value = undefined
  selectedRevision.value = undefined
  createVisible.value = true
  loadSelection()
}
const createInstance = async () => {
  await createFormRef.value?.validate()
  if (!selected.value || !canCreateSelected.value) return
  const data = {
    templateRevisionId: selected.value.currentPublishedRevisionId,
    expectedTemplateVersion: selected.value.templateVersion,
    instanceName: createForm.instanceName
  }
  const command = stableCommandIntent('instance-create', data)
  creating.value = true
  try {
    const created = await DynamicFormApi.createInstance(data, command.key)
    command.clear()
    createVisible.value = false
    await load()
    openInstance(created.instanceId)
    message.success('实例已创建并冻结所选修订')
  } finally {
    creating.value = false
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.query-form :deep(.el-input) {
  width: 200px;
}

.mobile-cards {
  display: grid;
  gap: 12px;
}

.card-title,
.card-meta,
.selection-search {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.selection-layout,
.selection-cards {
  display: grid;
  gap: 12px;
}

.selection-cards {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-height: 300px;
  overflow: auto;
}

.selection-cards :deep(.el-card) {
  cursor: pointer;
}

.selection-cards .selected {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.selection-preview {
  max-height: 360px;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--el-border-color-light);
}

@media (width <= 767px) {
  .query-form,
  .query-form :deep(.el-form-item),
  .query-form :deep(.el-input),
  .query-actions :deep(.el-button) {
    width: 100%;
    margin-right: 0;
  }

  .query-actions :deep(.el-form-item__content),
  .mobile-cards,
  .selection-cards {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .query-actions :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  .card-meta {
    justify-content: flex-start;
    margin: 10px 0;
  }

  .card-open {
    width: 100%;
  }

  .selection-search {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
