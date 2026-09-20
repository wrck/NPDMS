<template>
  <Dialog v-model="visible" title="培训打印模板" width="min(1000px, 96vw)">
    <div v-loading="busy" class="print-templates">
      <el-alert
        :closable="false"
        type="info"
        title="打印模板独立于客户确认表。发布并启用后可供培训记录选择，已保存记录保留原版式快照。"
      />
      <div class="template-toolbar">
        <el-select
          v-model="selectedId"
          placeholder="选择已有打印模板"
          class="!w-320px"
          @change="selectTemplate"
        >
          <el-option
            v-for="item in templates"
            :key="item.templateId"
            :value="item.templateId"
            :label="`${item.templateName} · ${item.availability === 'ENABLED' ? '启用' : '停用'}`"
          />
        </el-select>
        <el-button v-hasPermi="['pms:dynamic-form-template:manage']" @click="newTemplate"
          >新建模板</el-button
        >
        <el-tag v-if="revision"
          >修订 {{ revision.revisionNo }} ·
          {{ revision.status === 'DRAFT' ? '草稿' : '已发布' }}</el-tag
        >
      </div>
      <el-form :model="metadata" label-position="top" :disabled="busy">
        <el-row v-if="!selectedId" :gutter="16">
          <el-col :span="12"
            ><el-form-item label="模板名称" required
              ><el-input v-model="metadata.templateName" maxlength="100" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="模板编码" required
              ><el-input
                v-model="metadata.templateCode"
                maxlength="64"
                placeholder="例如 TRAINING_PRINT_STANDARD" /></el-form-item
          ></el-col>
        </el-row>
      </el-form>
      <el-alert
        :closable="false"
        title="使用动态表单设计器调整标题、字段名称、排列与布局。保留字段标识以加载培训内容及签字图片。"
      />
      <p class="binding-guide"
        >字段标识：projectName 工程名称；name 培训名称；contactName 联系人；contactPhone
        电话；trainingTypes 培训类型；trainingTime 培训时间；trainerName 工程师；traineeCount
        人数；content 内容；skillRating、effectRating、satisfactionRating 评价；signOpinion
        意见；signConfirmerName 签字人；signTime 确认时间；signatureImageDataUrl 签字图片；remark
        备注。自定义评价字段沿用客户确认表的字段标识。</p
      >
      <el-button v-if="revision" type="primary" @click="designerVisible = true">{{
        editable ? '设计表单' : '查看表单'
      }}</el-button>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button
        v-if="
          revision?.status === 'PUBLISHED' && template?.allowedActions.includes('CREATE_REVISION')
        "
        :loading="busy"
        @click="editRevision"
        >新建修订</el-button
      >
      <el-button
        v-if="!selectedId || (editable && revision?.formRulesJson.length === 0)"
        v-hasPermi="['pms:dynamic-form-template:manage']"
        :loading="busy"
        type="primary"
        @click="save"
        >创建并设计</el-button
      >
      <el-button
        v-if="revision?.allowedActions.includes('PUBLISH_REVISION')"
        :loading="busy"
        type="success"
        @click="publish"
        >发布已保存草稿</el-button
      >
      <el-button
        v-if="template?.allowedActions.includes('ENABLE')"
        :loading="busy"
        @click="availability(true)"
        >启用</el-button
      >
      <el-button
        v-if="template?.allowedActions.includes('DISABLE')"
        :loading="busy"
        @click="availability(false)"
        >停用</el-button
      >
    </template>
  </Dialog>
  <DynamicFormTemplateEditor
    v-model="designerVisible"
    :revision-id="revision?.revisionId"
    @changed="selectTemplate"
  />
</template>

<script setup lang="ts">
import { generateUUID } from '@/utils'
import * as Api from '@/api/pms/platform/dynamic-form'
import DynamicFormTemplateEditor from '@/views/pms/platform/dynamic-form/template/DynamicFormTemplateEditor.vue'
import { PRINT_CATEGORY, createTrainingPrintForm } from './trainingPrintForm'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
const designerVisible = ref(false)
const emit = defineEmits<{ changed: [] }>()
const visible = ref(false),
  busy = ref(false)
const selectedId = ref<number>()
const templates = ref<Api.DynamicFormTemplateVO[]>([])
const template = ref<Api.DynamicFormTemplateVO>()
const revision = ref<Api.DynamicFormRevisionVO>()
const metadata = reactive({ templateName: '', templateCode: '' })
const editable = computed(
  () => !selectedId.value || !!revision.value?.allowedActions.includes('PATCH_REVISION')
)
const message = useMessage()
let createKey = generateUUID()
const actionKeys = new Map<string, string>()
const key = (action: string) => {
  if (!actionKeys.has(action)) actionKeys.set(action, generateUUID())
  return actionKeys.get(action)!
}
const refreshList = async () => {
  const all: Api.DynamicFormTemplateVO[] = []
  for (let pageNo = 1; ; pageNo++) {
    const page = await Api.getTemplatePage({ pageNo, pageSize: 100 })
    all.push(...page.list)
    if (all.length >= page.total || !page.list.length) break
  }
  templates.value = all.filter((item) => item.categoryCode === PRINT_CATEGORY)
}
const selectTemplate = async () => {
  if (!selectedId.value) return
  busy.value = true
  try {
    template.value = await Api.getTemplate(selectedId.value)
    const revisionId =
      template.value.currentDraft?.revisionId ?? template.value.currentPublishedRevisionId
    revision.value = revisionId ? await Api.getRevision(revisionId) : undefined
  } finally {
    busy.value = false
  }
}
const newTemplate = () => {
  selectedId.value = undefined
  template.value = undefined
  revision.value = undefined
  metadata.templateCode = ''
  metadata.templateName = ''
  createKey = generateUUID()
}
const open = async () => {
  visible.value = true
  busy.value = true
  try {
    await refreshList()
    newTemplate()
  } finally {
    busy.value = false
  }
}
const save = async () => {
  if (!selectedId.value && (!metadata.templateName.trim() || !metadata.templateCode.trim())) {
    message.warning('请填写模板名称和编码')
    return
  }
  busy.value = true
  try {
    if (!selectedId.value) {
      const created = await Api.createTemplate(
        { ...metadata, categoryCode: PRINT_CATEGORY },
        createKey
      )
      selectedId.value = created.templateId
      revision.value = await Api.getRevision(created.draftRevisionId)
    }
    if (!revision.value) return
    await Api.patchRevision(revision.value.revisionId, revision.value.revisionVersion, {
      ...createTrainingPrintForm(
        getStrDictOptions(DICT_TYPE.PMS_TRAINING_TYPE).map((item) => ({
          label: item.label,
          value: item.value
        }))
      ),
      engineCode: 'FORM_CREATE_ELEMENT_PLUS',
      designerVersion: '3.4.0',
      rendererVersion: '3.2.38'
    })
    await selectTemplate()
    await refreshList()
    designerVisible.value = true
    message.success('已创建表单草稿，请在设计器中配置')
  } finally {
    busy.value = false
  }
}
const editRevision = async () => {
  if (!template.value) return
  busy.value = true
  try {
    await Api.createRevision(
      template.value.templateId,
      template.value.templateVersion,
      key(`revision:${template.value.templateId}:${template.value.templateVersion}`)
    )
    await selectTemplate()
  } finally {
    busy.value = false
  }
}
const publish = async () => {
  if (!revision.value) return
  busy.value = true
  try {
    await Api.publishRevision(
      revision.value.revisionId,
      revision.value.revisionVersion,
      key(`publish:${revision.value.revisionId}:${revision.value.revisionVersion}`)
    )
    await selectTemplate()
    await refreshList()
    emit('changed')
    message.success('已发布；启用后可供培训记录选择')
  } finally {
    busy.value = false
  }
}
const availability = async (enabled: boolean) => {
  if (!template.value) return
  busy.value = true
  try {
    await (enabled ? Api.enableTemplate : Api.disableTemplate)(
      template.value.templateId,
      template.value.templateVersion,
      key(`availability:${enabled}:${template.value.templateId}:${template.value.templateVersion}`)
    )
    await selectTemplate()
    await refreshList()
    emit('changed')
  } finally {
    busy.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
.print-templates {
  display: grid;
  gap: 16px;
}
.template-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.binding-guide {
  line-height: 1.8;
  color: var(--el-text-color-secondary);
}
</style>
