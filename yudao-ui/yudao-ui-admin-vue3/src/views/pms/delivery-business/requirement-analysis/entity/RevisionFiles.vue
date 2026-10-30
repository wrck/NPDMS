<template>
  <section class="controlled-file-field" aria-label="需求分析附件">
    <div v-for="fact in current" :key="fact.referenceKey" class="file-slot">
      <PmsFileReferenceList
        owner-context="SOL"
        object-type="REQUIREMENT_ANALYSIS_REVISION"
        :object-id="String(revisionId)"
        :purpose-code="purposeCode"
        :reference-key="fact.referenceKey"
        :artifact-id="fact.artifactId"
        :version-no="fact.versionNo"
        :editable="canWrite"
        :owner-execution-context="ownerExecutionContext"
        @loaded="handleLoaded"
        @detached="handleDetached(fact.referenceKey)"
      />
      <PmsFileUploader
        ref="versionUploaders"
        v-if="canWrite"
        auto-submit
        hide-submit
        owner-context="SOL"
        object-type="REQUIREMENT_ANALYSIS_REVISION"
        :object-id="String(revisionId)"
        :purpose-code="purposeCode"
        :reference-key="fact.referenceKey"
        :artifact-id="fact.artifactId"
        :owner-execution-context="ownerExecutionContext"
        :expected-reference-version="
          fact.referenceVersion ?? fact.fileFactVersion?.referenceVersion
        "
        category-code="DYNAMIC_FORM_ATTACHMENT"
        :accept="acceptedFiles"
        @completed="handleCompleted"
      />
    </div>
    <TopologyMultiUpload
      v-if="canWrite && fieldKey === 'NETWORK_TOPOLOGY__ATTACHMENTS'"
      :key="String(revisionId)" :revision-id="String(revisionId)" :disabled="!canWrite" :accept="acceptedFiles"
      :owner-execution-context="ownerExecutionContext" @completed="handleCompleted" @busy="multiBusy = $event" />
    <PmsFileUploader
      ref="newUploader"
      v-if="canWrite && fieldKey !== 'NETWORK_TOPOLOGY__ATTACHMENTS'"
        auto-submit
        hide-submit
      owner-context="SOL"
      object-type="REQUIREMENT_ANALYSIS_REVISION"
      :object-id="String(revisionId)"
      :purpose-code="purposeCode"
      :reference-key="uploadSlotKey"
      :owner-execution-context="ownerExecutionContext"
      category-code="DYNAMIC_FORM_ATTACHMENT"
      :accept="acceptedFiles"
      @completed="handleCompleted"
    />
    <el-empty v-else-if="!current.length" description="暂无文件材料" :image-size="56" />
  </section>
</template>

<script setup lang="ts">
import { generateUUID } from '@/utils'
import {
  PmsFileReferenceList,
  PmsFileUploader,
  type FileSelection
} from '@/components/PmsFileArtifact'
import type { DynamicFormFileFactVO, JsonObject } from '@/api/pms/platform/dynamic-form'
import type { FileArtifactVO } from '@/api/pms/platform/file'
import TopologyMultiUpload from './TopologyMultiUpload.vue'

defineOptions({ name: 'RequirementRevisionFiles' })

const props = withDefaults(
  defineProps<{
    revisionId?: string | number
    fieldKey?: string
    modelValue?: string[]
    currentFacts?: DynamicFormFileFactVO[]
    allowedActions?: string[]
    disabled?: boolean
    ownerExecutionContext?: JsonObject
  }>(),
  { currentFacts: () => [], allowedActions: () => [], disabled: false }
)
const emit = defineEmits<{
  'update:modelValue': [value: string[]]
  'upload-busy-change': [busy: boolean]
}>()
const versionUploaders = ref<InstanceType<typeof PmsFileUploader>[]>([])
const newUploader = ref<InstanceType<typeof PmsFileUploader>>()
const multiBusy = ref(false)
const uploadBusy = computed(() => multiBusy.value || !!newUploader.value?.isBusy()
  || versionUploaders.value.some(uploader => uploader.isBusy()))
watch(uploadBusy, busy => emit('upload-busy-change', busy), { flush: 'sync' })
onBeforeUnmount(() => emit('upload-busy-change', false))

type VisibleFile = Pick<DynamicFormFileFactVO, 'artifactId' | 'versionNo' | 'referenceKey'> &
  Partial<Pick<DynamicFormFileFactVO, 'fileFactVersion'>> & { referenceVersion?: number }
const toVisibleFiles = (facts: DynamicFormFileFactVO[]): VisibleFile[] =>
  facts.map((fact) => ({
    ...fact,
    referenceVersion: fact.fileFactVersion?.referenceVersion
  }))
const current = ref<VisibleFile[]>(toVisibleFiles(props.currentFacts))
const uploadSlotKey = ref(generateUUID())
const purposeCode = computed(() => `FORM_FIELD_ATTACHMENT/${props.fieldKey || ''}`)
const acceptedFiles = computed(() => '.pdf,.png,.jpg,.jpeg,.txt,.doc,.docx,.xls,.xlsx,.ppt,.pptx'
  + (props.fieldKey === 'NETWORK_TOPOLOGY__ATTACHMENTS' ? ',.vsd,.vsdx' : ''))
const canWrite = computed(
  () => !!props.revisionId && !props.disabled && props.allowedActions.includes('PATCH_FORM')
)
const syncRuntimeValue = () =>
  emit(
    'update:modelValue',
    current.value.map((item) => item.referenceKey)
  )

const handleCompleted = (selection: FileSelection) => {
  current.value = [
    ...current.value.filter((item) => item.referenceKey !== selection.referenceKey),
    {
      artifactId: selection.artifactId,
      versionNo: selection.versionNo,
      referenceKey: selection.referenceKey
    }
  ]
  uploadSlotKey.value = generateUUID()
  syncRuntimeValue()
}

const handleDetached = (referenceKey: string) => {
  current.value = current.value.filter((item) => item.referenceKey !== referenceKey)
  syncRuntimeValue()
}

const handleLoaded = (artifact: FileArtifactVO) => {
  current.value = current.value.map((item) =>
    item.referenceKey === artifact.reference.referenceKey
      ? { ...item, referenceVersion: artifact.reference.referenceVersion }
      : item
  )
}

watch(
  () => props.currentFacts,
  (facts) => {
    current.value = toVisibleFiles(facts)
    syncRuntimeValue()
  },
  { deep: true, immediate: true }
)
</script>

<style scoped>
.controlled-file-field,
.file-slot {
  display: grid;
  gap: 12px;
  min-width: 0;
}

@media (width <= 767px) {
  .controlled-file-field {
    width: 100%;
  }
}
</style>
