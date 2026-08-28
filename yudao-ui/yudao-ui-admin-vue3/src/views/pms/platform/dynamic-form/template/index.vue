<template>
  <div class="dynamic-template-page">
    <ContentWrap>
      <el-form inline class="query-form">
        <el-form-item class="query-actions">
          <el-button @click="load"><Icon icon="ep:refresh" />刷新</el-button>
          <el-button
            v-hasPermi="['pms:dynamic-form-template:manage']"
            type="primary"
            @click="openCreate"
          >
            <Icon icon="ep:plus" />新建模板
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table
        v-if="!narrow"
        data-testid="template-desktop-list"
        v-loading="loading"
        :data="rows"
        empty-text="暂无共享动态表单模板"
      >
        <el-table-column prop="templateCode" label="模板编码" min-width="150" />
        <el-table-column
          prop="templateName"
          label="模板名称"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column prop="categoryCode" label="分类" min-width="120" />
        <el-table-column label="可用性" width="100">
          <template #default="{ row }">
            <el-tag :type="row.availability === 'ENABLED' ? 'success' : 'info'">
              {{ row.availability === 'ENABLED' ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前草稿" min-width="130">
          <template #default="{ row }">
            <el-button
              v-if="row.currentDraft"
              link
              type="warning"
              @click="openRevision(row.currentDraft.revisionId)"
            >
              修订 {{ row.currentDraft.revisionNo }} / V{{ row.currentDraft.revisionVersion }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="当前发布" min-width="130">
          <template #default="{ row }">
            <el-button
              v-if="row.currentPublished"
              link
              type="success"
              @click="openRevision(row.currentPublished.revisionId)"
            >
              修订 {{ row.currentPublished.revisionNo }}
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="模板版本" prop="templateVersion" width="100" />
        <el-table-column label="操作" fixed="right" min-width="310">
          <template #default="{ row }">
            <el-button v-if="has(row, 'PATCH_TEMPLATE')" link type="primary" @click="openEdit(row)"
              >元数据</el-button
            >
            <el-button
              v-if="has(row, 'CREATE_REVISION')"
              link
              type="warning"
              @click="createNextRevision(row)"
              >创建下一草稿</el-button
            >
            <el-button
              v-if="row.currentDraft?.status === 'DRAFT' && has(row, 'PUBLISH_REVISION')"
              link
              type="success"
              @click="publish(row)"
              >发布草稿</el-button
            >
            <el-button
              v-if="has(row, 'ENABLE')"
              link
              type="success"
              @click="changeAvailability(row, 'ENABLE')"
              >启用</el-button
            >
            <el-button
              v-if="has(row, 'DISABLE')"
              link
              type="info"
              @click="changeAvailability(row, 'DISABLE')"
              >停用</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-if="!narrow"
        v-model:page="query.pageNo"
        v-model:limit="query.pageSize"
        :total="total"
        @pagination="load"
      />
      <div v-else class="mobile-cards" data-testid="template-mobile-list">
        <el-card v-for="row in rows" :key="row.templateId" shadow="never">
          <div class="card-title"
            ><strong>{{ row.templateName }}</strong
            ><span>{{ row.templateCode }}</span></div
          >
          <div class="card-tags">
            <el-tag>{{ row.categoryCode }}</el-tag>
            <el-tag :type="row.availability === 'ENABLED' ? 'success' : 'info'">{{
              row.availability === 'ENABLED' ? '已启用' : '已停用'
            }}</el-tag>
            <el-tag v-if="row.currentDraft" type="warning"
              >草稿 R{{ row.currentDraft.revisionNo }}</el-tag
            >
            <el-tag v-if="row.currentPublished" type="success"
              >发布 R{{ row.currentPublished.revisionNo }}</el-tag
            >
          </div>
          <div class="card-actions">
            <el-button v-if="row.currentDraft" @click="openRevision(row.currentDraft.revisionId)"
              >打开草稿</el-button
            >
            <el-button
              v-if="row.currentPublished"
              @click="openRevision(row.currentPublished.revisionId)"
              >预览发布版</el-button
            >
            <el-button v-if="has(row, 'PATCH_TEMPLATE')" @click="openEdit(row)">元数据</el-button>
            <el-button v-if="has(row, 'CREATE_REVISION')" @click="createNextRevision(row)"
              >创建下一草稿</el-button
            >
            <el-button
              v-if="has(row, 'PUBLISH_REVISION') && row.currentDraft"
              type="success"
              @click="publish(row)"
              >发布草稿</el-button
            >
            <el-button
              v-if="has(row, 'ENABLE')"
              type="success"
              @click="changeAvailability(row, 'ENABLE')"
              >启用</el-button
            >
            <el-button v-if="has(row, 'DISABLE')" @click="changeAvailability(row, 'DISABLE')"
              >停用</el-button
            >
          </div>
        </el-card>
      </div>
    </ContentWrap>

    <Dialog
      v-model="formVisible"
      :title="editing ? '修改模板元数据' : '新建动态表单模板'"
      width="620px"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="form.templateCode" :disabled="!!editing" maxlength="64" />
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName"
          ><el-input v-model="form.templateName" maxlength="128"
        /></el-form-item>
        <el-form-item label="分类" prop="categoryCode"
          ><el-input v-model="form.categoryCode" maxlength="64"
        /></el-form-item>
        <el-form-item label="说明" prop="description"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="512"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTemplate">保存</el-button>
      </template>
    </Dialog>

    <DynamicFormTemplateEditor
      v-if="editorVisible"
      v-model="editorVisible"
      :revision-id="activeRevisionId"
      @changed="load"
    />
  </div>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import type { DynamicFormAction, DynamicFormTemplateVO } from '@/api/pms/platform/dynamic-form'
import DynamicFormTemplateEditor from './DynamicFormTemplateEditor.vue'
import { stableCommandIntent } from '../components/dynamicFormRuntime'

defineOptions({ name: 'PmsDynamicFormTemplate' })
const message = useMessage()
const narrow = useMediaQuery('(width <= 767px)')
const loading = ref(false)
const saving = ref(false)
const rows = ref<DynamicFormTemplateVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10 })

const load = async () => {
  loading.value = true
  try {
    const page = await DynamicFormApi.getTemplatePage(query)
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}
const has = (row: DynamicFormTemplateVO, action: DynamicFormAction) =>
  row.allowedActions.includes(action)

const formVisible = ref(false)
const formRef = ref()
const editing = ref<DynamicFormTemplateVO>()
const form = reactive({
  templateCode: '',
  templateName: '',
  categoryCode: '',
  description: '' as string | null
})
const formRules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请输入分类', trigger: 'blur' }]
}
const openCreate = () => {
  editing.value = undefined
  Object.assign(form, { templateCode: '', templateName: '', categoryCode: '', description: '' })
  formVisible.value = true
}
const openEdit = (row: DynamicFormTemplateVO) => {
  editing.value = row
  Object.assign(form, {
    templateCode: row.templateCode,
    templateName: row.templateName,
    categoryCode: row.categoryCode,
    description: row.description ?? null
  })
  formVisible.value = true
}
const saveTemplate = async () => {
  await formRef.value?.validate()
  saving.value = true
  let createdDraftRevisionId: number | undefined
  try {
    if (editing.value) {
      const patch: DynamicFormApi.PatchTemplateReqVO = {}
      if (form.templateName !== editing.value.templateName) patch.templateName = form.templateName
      if (form.categoryCode !== editing.value.categoryCode) patch.categoryCode = form.categoryCode
      const description = form.description === '' ? null : form.description
      if (description !== (editing.value.description ?? null)) patch.description = description
      if (!Object.keys(patch).length) return message.info('元数据没有变化')
      await DynamicFormApi.patchTemplate(
        editing.value.templateId,
        editing.value.templateVersion,
        patch
      )
    } else {
      const data = {
        templateCode: form.templateCode,
        templateName: form.templateName,
        categoryCode: form.categoryCode,
        description: form.description || undefined
      }
      const command = stableCommandIntent('template-create', data)
      const created = await DynamicFormApi.createTemplate(data, command.key)
      createdDraftRevisionId = created.draftRevisionId
      command.clear()
    }
    formVisible.value = false
    message.success('模板已保存')
    await load()
    if (createdDraftRevisionId) openRevision(createdDraftRevisionId)
  } finally {
    saving.value = false
  }
}

const activeRevisionId = ref<number>()
const editorVisible = ref(false)
const openRevision = (revisionId: number) => {
  activeRevisionId.value = revisionId
  editorVisible.value = true
}
const createNextRevision = async (row: DynamicFormTemplateVO) => {
  const command = stableCommandIntent(`revision-create:${row.templateId}`, {
    version: row.templateVersion
  })
  const created = await DynamicFormApi.createRevision(
    row.templateId,
    row.templateVersion,
    command.key
  )
  command.clear()
  await load()
  openRevision(created.revisionId)
}
const publish = async (row: DynamicFormTemplateVO) => {
  if (!row.currentDraft) return
  await message.confirm(`确认发布修订 ${row.currentDraft.revisionNo}？发布后该修订不可再修改。`)
  const command = stableCommandIntent(`revision-publish:${row.currentDraft.revisionId}`, {
    version: row.currentDraft.revisionVersion
  })
  await DynamicFormApi.publishRevision(
    row.currentDraft.revisionId,
    row.currentDraft.revisionVersion,
    command.key
  )
  command.clear()
  message.success('修订已发布')
  await load()
}
const changeAvailability = async (row: DynamicFormTemplateVO, action: 'ENABLE' | 'DISABLE') => {
  await message.confirm(
    action === 'ENABLE' ? '确认启用该模板供新实例选择？' : '确认停用？既有实例不会改变。'
  )
  const command = stableCommandIntent(`availability:${row.templateId}`, {
    action,
    version: row.templateVersion
  })
  const call = action === 'ENABLE' ? DynamicFormApi.enableTemplate : DynamicFormApi.disableTemplate
  await call(row.templateId, row.templateVersion, command.key)
  command.clear()
  await load()
}

onMounted(load)
</script>

<style scoped lang="scss">
.query-form :deep(.el-input),
.query-form :deep(.el-select) {
  width: 180px;
}

.mobile-cards {
  display: grid;
  gap: 12px;
}

.card-title,
.card-tags,
.card-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.card-title {
  justify-content: space-between;
}

.card-tags {
  margin: 12px 0;
}

@media (width <= 767px) {
  .query-form,
  .query-form :deep(.el-form-item),
  .query-form :deep(.el-input),
  .query-form :deep(.el-select),
  .query-actions :deep(.el-button) {
    width: 100%;
    margin-right: 0;
  }

  .query-actions :deep(.el-form-item__content) {
    display: grid;
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .query-actions :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  .card-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .card-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }
}
</style>
