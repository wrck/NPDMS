<template>
  <article class="section-card" :aria-labelledby="`section-${section.sectionId}`">
    <header class="section-heading">
      <div>
        <h4 :id="`section-${section.sectionId}`">
          {{ section.sectionName }}
          <span v-if="section.required" class="required-mark" aria-label="必填">*</span>
        </h4>
        <p>
          {{ section.sectionKind === 'CORE' ? '核心章节' : '模板扩展项' }} ·
          {{ fieldTypeLabel(section.fieldType) }} · 章节版本 {{ section.version }}
        </p>
      </div>
      <el-tag v-if="editable" type="warning" size="small">草稿可编辑</el-tag>
      <el-tag v-else type="info" size="small">只读</el-tag>
    </header>

    <div class="section-value">
      <Editor
        v-if="section.fieldType === 'RICH_TEXT' && editable"
        v-model="draftValue"
        :editor-id="`requirement-analysis-${section.sectionId}`"
        :editor-config="richTextConfig"
        :height="220"
        :readonly="false"
      />
      <div
        v-else-if="section.fieldType === 'RICH_TEXT'"
        class="rich-text-readonly"
        v-html="String(draftValue || '')"
      ></div>
      <el-input
        v-else-if="section.fieldType === 'TEXT'"
        v-model="draftValue"
        :readonly="!editable"
        :rows="4"
        type="textarea"
        :aria-label="section.sectionName"
      />
      <el-input-number
        v-else-if="section.fieldType === 'NUMBER'"
        v-model="draftValue"
        :disabled="!editable"
        controls-position="right"
        :aria-label="section.sectionName"
      />
      <el-switch
        v-else-if="section.fieldType === 'BOOLEAN'"
        v-model="draftValue"
        :disabled="!editable"
        active-text="是"
        inactive-text="否"
      />
      <el-select
        v-else-if="section.fieldType === 'SINGLE_SELECT'"
        v-model="draftValue"
        :disabled="!editable"
        clearable
        filterable
        :aria-label="section.sectionName"
      >
        <el-option
          v-for="option in options"
          :key="option.code"
          :label="option.label"
          :value="option.code"
        />
      </el-select>
      <el-select
        v-else
        v-model="draftValue"
        :disabled="!editable"
        clearable
        filterable
        multiple
        :aria-label="section.sectionName"
      >
        <el-option
          v-for="option in options"
          :key="option.code"
          :label="option.label"
          :value="option.code"
        />
      </el-select>
      <div v-if="section.dictionaryType" class="dictionary-note">
        选项来自项目冻结字典 {{ section.dictionaryType }}，后续字典变化不会覆盖本版本。
      </div>
    </div>

    <section class="attachments" aria-label="章节附件">
      <div class="attachments-heading">
        <strong>附件</strong>
        <el-button v-if="editable" link type="primary" @click="addAttachmentSlot">
          新增附件
        </el-button>
      </div>
      <el-empty
        v-if="!draftAttachments.length && !pendingSlots.length"
        description="暂无附件"
        :image-size="48"
      />
      <div
        v-for="attachment in draftAttachments"
        :key="attachment.referenceKey"
        class="attachment-row"
      >
        <PmsFileReferenceList
          owner-context="SOL"
          object-type="REQUIREMENT_ANALYSIS_SECTION"
          :object-id="String(section.sectionId)"
          purpose-code="SECTION_ATTACHMENT"
          :reference-key="attachment.referenceKey"
          :artifact-id="attachment.artifactId"
          :version-no="attachment.versionNo"
          :editable="editable"
          @detached="removeAttachment(attachment.referenceKey)"
        />
        <el-collapse v-if="editable">
          <el-collapse-item title="替换此槽位文件" :name="attachment.referenceKey">
            <PmsFileUploader
              owner-context="SOL"
              object-type="REQUIREMENT_ANALYSIS_SECTION"
              :object-id="String(section.sectionId)"
              purpose-code="SECTION_ATTACHMENT"
              :reference-key="attachment.referenceKey"
              :artifact-id="attachment.artifactId"
              :expected-reference-version="attachment.fileFactVersion.referenceVersion"
              category-code="REQUIREMENT_ANALYSIS_ATTACHMENT"
              @completed="captureAttachment($event, attachment.referenceKey)"
            />
          </el-collapse-item>
        </el-collapse>
      </div>
      <div v-for="slot in pendingSlots" :key="slot" class="attachment-row attachment-row--pending">
        <div class="pending-heading">
          <span>新附件槽位</span>
          <el-button link type="danger" @click="discardSlot(slot)">取消</el-button>
        </div>
        <PmsFileUploader
          owner-context="SOL"
          object-type="REQUIREMENT_ANALYSIS_SECTION"
          :object-id="String(section.sectionId)"
          purpose-code="SECTION_ATTACHMENT"
          :reference-key="slot"
          category-code="REQUIREMENT_ANALYSIS_ATTACHMENT"
          @completed="captureAttachment($event, slot)"
        />
      </div>
    </section>

    <footer v-if="editable" class="section-actions">
      <span v-if="attachmentSyncPending" class="sync-warning" role="alert">
        附件引用已变化，需保存本章节以冻结当前权威文件事实
      </span>
      <span v-if="changed" class="changed-note" role="status">有未保存修改</span>
      <el-button :disabled="!changed" :loading="saving" type="primary" @click="save">
        保存本章节
      </el-button>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { Editor } from '@/components/Editor'
import { PmsFileReferenceList, PmsFileUploader } from '@/components/PmsFileArtifact'
import type { FileSelection } from '@/components/PmsFileArtifact'
import * as FileApi from '@/api/pms/platform/file'
import * as RequirementAnalysisApi from '@/api/pms/engineering/requirement-analysis'
import type {
  RequirementAnalysisAttachmentVO,
  RequirementAnalysisFieldType,
  RequirementAnalysisSectionVO
} from '@/api/pms/engineering/requirement-analysis'
import { useMessage } from '@/hooks/web/useMessage'
import {
  buildRequirementAttachment,
  buildSectionPatch,
  containsEmbeddedMedia,
  patchRequirementSectionAndReload,
  parseSectionOptions,
  parseSectionValue,
  resolvePendingAttachmentSync,
  sameRequirementValue
} from './requirementAnalysisInteraction'

const props = defineProps<{
  preparationId: number
  preparationVersion: number
  contentVersion: number
  projectVersion: number
  section: RequirementAnalysisSectionVO
  reload: () => Promise<void>
}>()
const message = useMessage()
const saving = ref(false)
const draftValue = ref<any>()
const draftAttachments = ref<RequirementAnalysisAttachmentVO[]>([])
const baselineValue = ref<RequirementAnalysisSectionVO['valueSnapshot']>()
const baselineAttachments = ref<RequirementAnalysisAttachmentVO[]>([])
const pendingSlots = ref<string[]>([])
const attachmentSyncPending = ref(false)
const options = computed(() => parseSectionOptions(props.section))
const editable = computed(() =>
  (props.section.allowedActions || []).some((action) =>
    ['PATCH_SECTION', 'PATCH_ITEM', 'EDIT'].includes(action)
  )
)
const changed = computed(
  () =>
    !sameRequirementValue(draftValue.value, baselineValue.value) ||
    !sameRequirementValue(draftAttachments.value, baselineAttachments.value)
)
const richTextConfig: any = {
  placeholder: '请输入章节内容；文件请通过下方附件槽位上传，不要嵌入长期文件地址。',
  customPaste: (_editor: unknown, event: ClipboardEvent) => {
    const html = event.clipboardData?.getData('text/html') || ''
    if (event.clipboardData?.files.length || containsEmbeddedMedia(html)) {
      event.preventDefault()
      message.warning('正文不允许粘贴文件或媒体，请使用章节附件槽位')
      return false
    }
    return true
  },
  MENU_CONF: {
    uploadImage: { customUpload: () => message.warning('图片请作为章节附件上传') },
    uploadVideo: { customUpload: () => message.warning('视频请作为章节附件上传') },
    insertImage: { checkImage: () => '正文不允许嵌入文件地址，请使用章节附件' },
    insertVideo: { checkVideo: () => '正文不允许嵌入文件地址，请使用章节附件' }
  }
}

const cloneAttachments = (value: RequirementAnalysisAttachmentVO[]) =>
  value.map((attachment) => ({
    ...attachment,
    fileFactVersion: { ...attachment.fileFactVersion }
  }))

const attachmentSyncKey = () =>
  `pms:requirement-analysis:attachment-sync:${props.preparationId}:${props.section.sectionId}`
const rememberAttachmentSync = (attachments: RequirementAnalysisAttachmentVO[]) => {
  sessionStorage.setItem(attachmentSyncKey(), JSON.stringify(cloneAttachments(attachments)))
  attachmentSyncPending.value = true
}
const clearAttachmentSync = () => {
  sessionStorage.removeItem(attachmentSyncKey())
  attachmentSyncPending.value = false
}
const pendingAttachmentSync = () => {
  const serialized = sessionStorage.getItem(attachmentSyncKey())
  if (!serialized) return undefined
  try {
    return JSON.parse(serialized) as RequirementAnalysisAttachmentVO[]
  } catch {
    clearAttachmentSync()
    return undefined
  }
}

const reset = () => {
  const value = parseSectionValue(props.section)
  draftValue.value = Array.isArray(value) ? [...value] : value
  baselineValue.value = Array.isArray(value) ? [...value] : value
  baselineAttachments.value = cloneAttachments(props.section.attachments || [])
  const resolved = resolvePendingAttachmentSync(baselineAttachments.value, pendingAttachmentSync())
  if (resolved.syncPending) {
    draftAttachments.value = cloneAttachments(resolved.attachments)
    attachmentSyncPending.value = resolved.syncPending
  } else {
    draftAttachments.value = cloneAttachments(baselineAttachments.value)
    clearAttachmentSync()
  }
  pendingSlots.value = []
}

const addAttachmentSlot = () => pendingSlots.value.push(crypto.randomUUID())
const discardSlot = (slot: string) => {
  pendingSlots.value = pendingSlots.value.filter((value) => value !== slot)
}
const removeAttachment = (referenceKey: string) => {
  draftAttachments.value = draftAttachments.value.filter(
    (attachment) => attachment.referenceKey !== referenceKey
  )
  rememberAttachmentSync(draftAttachments.value)
}

const captureAttachment = async (selection: FileSelection, frozenReferenceKey: string) => {
  const businessKey = {
    ownerContext: 'SOL',
    objectType: 'REQUIREMENT_ANALYSIS_SECTION',
    objectId: String(props.section.sectionId),
    purposeCode: 'SECTION_ATTACHMENT',
    referenceKey: frozenReferenceKey
  }
  const artifact = await FileApi.getArtifact(selection.artifactId, businessKey)
  let cursor: string | undefined
  let version: FileApi.FileVersionVO | undefined
  do {
    const page = await FileApi.getVersions(selection.artifactId, {
      ...businessKey,
      cursor,
      pageSize: 100
    })
    version = page.items.find((row) => row.versionNo === selection.versionNo)
    cursor = version || !page.hasMore ? undefined : page.nextCursor
  } while (cursor)
  if (!version) throw new Error('FILE_VERSION_NOT_FOUND')
  const frozen = buildRequirementAttachment(selection, artifact, version, frozenReferenceKey)
  const index = draftAttachments.value.findIndex(
    (attachment) => attachment.referenceKey === frozenReferenceKey
  )
  if (index >= 0) draftAttachments.value.splice(index, 1, frozen)
  else draftAttachments.value.push(frozen)
  discardSlot(frozenReferenceKey)
  rememberAttachmentSync(draftAttachments.value)
}

const save = async () => {
  const patch = buildSectionPatch(
    props.preparationVersion,
    props.contentVersion,
    props.projectVersion,
    draftValue.value,
    baselineValue.value,
    draftAttachments.value,
    baselineAttachments.value
  )
  if (!patch.submittedFields.length) return message.info('没有需要保存的变化')
  saving.value = true
  try {
    await patchRequirementSectionAndReload(
      () =>
        RequirementAnalysisApi.patchSection(props.preparationId, props.section.sectionId, patch),
      props.reload
    )
    clearAttachmentSync()
    message.success(`${props.section.sectionName}已保存`)
  } catch (error) {
    await props.reload()
    await nextTick()
    if (patch.submittedFields.includes('attachments')) {
      if (!sessionStorage.getItem(attachmentSyncKey())) {
        message.success(`${props.section.sectionName}已由权威事实确认保存`)
        return
      }
      message.warning('已刷新权威版本；附件同步意图已保留，请再次保存本章节')
    }
    throw error
  } finally {
    saving.value = false
  }
}

const fieldTypeLabel = (type: RequirementAnalysisFieldType) =>
  ({
    RICH_TEXT: '富文本',
    TEXT: '文本',
    NUMBER: '数字',
    BOOLEAN: '是/否',
    SINGLE_SELECT: '单选',
    MULTI_SELECT: '多选'
  })[type]

watch(() => props.section, reset, { immediate: true, deep: true })
</script>

<style scoped lang="scss">
.section-card {
  padding: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.section-heading,
.attachments-heading,
.pending-heading,
.section-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-heading h4,
.section-heading p {
  margin: 0;
}

.section-heading p,
.dictionary-note,
.changed-note,
.sync-warning,
.pending-heading {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sync-warning {
  color: var(--el-color-warning-dark-2);
}

.section-heading p {
  margin-top: 4px;
}

.required-mark {
  color: var(--el-color-danger);
}

.section-value,
.attachments,
.section-actions {
  margin-top: 16px;
}

.section-value :deep(.el-select),
.section-value :deep(.el-input-number) {
  width: 100%;
}

.dictionary-note {
  margin-top: 6px;
}

.rich-text-readonly {
  min-height: 64px;
  overflow-wrap: anywhere;
  line-height: 1.7;
}

.attachments {
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.attachment-row {
  padding: 10px;
  margin-top: 10px;
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: var(--el-border-radius-base);
}

.attachment-row--pending {
  background: var(--el-fill-color-lighter);
}

.section-actions {
  justify-content: flex-end;
}

@media (width <= 767px) {
  .section-card {
    padding: 12px;
  }

  .section-heading {
    align-items: flex-start;
  }

  .section-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
