<template>
  <el-drawer
    v-model="visible"
    :title="title"
    size="100%"
    destroy-on-close
    class="dynamic-form-editor-drawer"
  >
    <div v-loading="loading" class="editor-shell">
      <el-alert
        title="高信任配置"
        description="管理员配置的接口、iframe、事件、函数及 parseFunc 会在当前登录用户浏览器中按 FormCreate 原生能力执行，目标系统仍独立鉴权。"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="editor-toolbar">
        <div class="revision-identity">
          <el-tag :type="revision?.status === 'PUBLISHED' ? 'success' : 'warning'">
            {{ revision?.status || '-' }}
          </el-tag>
          <span>修订 {{ revision?.revisionNo ?? '-' }}</span>
          <span>版本 {{ revision?.revisionVersion ?? '-' }}</span>
          <span
            >{{ revision?.designerVersion || '-' }} / {{ revision?.rendererVersion || '-' }}</span
          >
        </div>
        <div class="editor-actions">
          <el-button
            v-if="revision?.status === 'DRAFT'"
            @click="mode = mode === 'DESIGN' ? 'PREVIEW' : 'DESIGN'"
          >
            {{ mode === 'DESIGN' ? '预览' : '返回设计' }}
          </el-button>
          <el-button @click="load">重新读取</el-button>
          <el-button
            v-if="canSave && mode === 'DESIGN'"
            type="primary"
            :loading="saving"
            @click="save"
          >
            保存草稿
          </el-button>
        </div>
      </div>

      <div v-if="narrow" class="desktop-advice">
        当前宽度可预览；完整拖拽设计建议使用 768px 以上桌面宽度。
      </div>
      <div v-show="mode === 'DESIGN'" class="designer-host">
        <fc-designer ref="designer" class="my-designer" :config="designerConfig" />
      </div>
      <div v-if="mode === 'PREVIEW'" class="preview-host">
        <form-create
          v-model="previewValue"
          :option="preview.option"
          :rule="preview.rule"
          :disabled="true"
        />
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { useFormCreateDesigner } from '@/components/FormCreate'
import * as DynamicFormApi from '@/api/pms/platform/dynamic-form'
import type { DynamicFormRevisionVO } from '@/api/pms/platform/dynamic-form'
import {
  decodeDynamicForm,
  encodeDynamicForm,
  restoreDynamicFormDesigner,
  sameDynamicFormPayload
} from '../components/dynamicFormCodec'
import { usePmsFileArtifactDesignerRule } from '../components/usePmsFileArtifactDesignerRule'
import { registerDynamicFormComponents } from '../components/registerDynamicFormComponents'

defineOptions({ name: 'DynamicFormTemplateEditor' })
const props = defineProps<{ modelValue: boolean; revisionId?: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; changed: [] }>()
const message = useMessage()

const designerConfig = {
  switchType: [],
  autoActive: true,
  useTemplate: false,
  formOptions: { form: { labelWidth: '100px' } },
  fieldReadonly: false,
  hiddenDragMenu: false,
  hiddenDragBtn: false,
  hiddenMenu: [],
  hiddenItem: [],
  hiddenItemConfig: {},
  disabledItemConfig: {},
  showSaveBtn: false,
  showConfig: true,
  showBaseForm: true,
  showControl: true,
  showPropsForm: true,
  showEventForm: true,
  showValidateForm: true,
  showFormConfig: true,
  showInputData: true,
  showDevice: true,
  appendConfigData: []
}

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const designer = ref()
useFormCreateDesigner(designer)
registerDynamicFormComponents()
const revision = ref<DynamicFormRevisionVO>()
const loading = ref(false)
const saving = ref(false)
const mode = ref<'DESIGN' | 'PREVIEW'>('DESIGN')
const preview = reactive<{ option: Record<string, unknown>; rule: Record<string, unknown>[] }>({
  option: {},
  rule: []
})
const previewValue = ref({})
const narrow = useMediaQuery('(width <= 767px)')
let registeredDesigner: unknown
const title = computed(() => `动态表单修订 ${revision.value?.revisionNo ?? ''}`)
const canSave = computed(
  () =>
    revision.value?.status === 'DRAFT' && revision.value.allowedActions.includes('PATCH_REVISION')
)

const addControlledFileRule = async () => {
  await nextTick()
  if (!designer.value || registeredDesigner === designer.value) return
  const rule = usePmsFileArtifactDesignerRule()
  designer.value?.addComponent(rule)
  designer.value?.appendMenuItem('main', {
    icon: rule.icon,
    name: rule.name,
    label: rule.label
  })
  registeredDesigner = designer.value
}

const applyRevision = async (data: DynamicFormRevisionVO) => {
  revision.value = data
  const decoded = decodeDynamicForm(data.formConfJson, data.formRulesJson)
  preview.option = decoded.option
  preview.rule = decoded.rule
  await nextTick()
  if (designer.value) restoreDynamicFormDesigner(designer, data.formConfJson, data.formRulesJson)
  if (data.status === 'PUBLISHED') mode.value = 'PREVIEW'
}

const load = async () => {
  if (!props.revisionId) return
  loading.value = true
  try {
    await applyRevision(await DynamicFormApi.getRevision(props.revisionId))
    await addControlledFileRule()
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!revision.value || !designer.value) return
  const intended = encodeDynamicForm(designer)
  saving.value = true
  try {
    await DynamicFormApi.patchRevision(revision.value.revisionId, revision.value.revisionVersion, {
      ...intended,
      engineCode: 'FORM_CREATE_ELEMENT_PLUS',
      designerVersion: '3.4.0',
      rendererVersion: '3.2.38'
    })
    await load()
    message.success('草稿修订已保存')
    emit('changed')
  } catch (error) {
    const authoritative = await DynamicFormApi.getRevision(revision.value.revisionId)
    const committed = sameDynamicFormPayload(authoritative, intended)
    if (committed) {
      await applyRevision(authoritative)
      message.success('已确认草稿修订保存成功')
      emit('changed')
      return
    }
    revision.value = authoritative
    message.warning('保存结果未知且权威版本未包含本次设计，请保留当前页面并再次保存。')
    throw error
  } finally {
    saving.value = false
  }
}

watch(
  () => [visible.value, props.revisionId],
  ([open]) => {
    if (open) load()
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.editor-shell {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.editor-toolbar,
.revision-identity,
.editor-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.editor-toolbar {
  justify-content: space-between;
}

.designer-host {
  height: calc(100vh - 190px);
  min-height: 520px;
}

.my-designer {
  height: 100%;
}

.preview-host {
  width: 100%;
  max-width: 1080px;
  padding: 16px;
  margin: 0 auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  box-sizing: border-box;
}

.desktop-advice {
  padding: 8px 12px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
}

@media (width <= 767px) {
  .editor-actions,
  .editor-actions :deep(.el-button) {
    width: 100%;
  }

  .editor-actions :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  .designer-host {
    overflow: auto;
  }
}
</style>
