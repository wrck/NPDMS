<template>
  <div class="controlled-file-field">
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
        v-if="canWrite"
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
        accept=".pdf,.png,.jpg,.jpeg,.txt,.doc,.docx,.xls,.xlsx,.ppt,.pptx"
        @completed="handleCompleted"
      />
    </div>
    <PmsFileUploader
      v-if="canWrite"
      owner-context="SOL"
      object-type="REQUIREMENT_ANALYSIS_REVISION"
      :object-id="String(revisionId)"
      :purpose-code="purposeCode"
      :reference-key="uploadSlotKey"
      :owner-execution-context="ownerExecutionContext"
      category-code="DYNAMIC_FORM_ATTACHMENT"
      accept=".pdf,.png,.jpg,.jpeg,.txt,.doc,.docx,.xls,.xlsx,.ppt,.pptx"
      @completed="handleCompleted"
    />
    <el-empty v-else-if="!current.length" description="暂无文件材料" :image-size="56" />
  </div>
</template>

<script setup lang="ts">
import {
  PmsFileReferenceList,
  PmsFileUploader,
  type FileSelection
} from '@/components/PmsFileArtifact'
import type { DynamicFormFileFactVO, JsonObject } from '@/api/pms/platform/dynamic-form'
import type { FileArtifactVO } from '@/api/pms/platform/file'

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
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()

type VisibleFile = Pick<DynamicFormFileFactVO, 'artifactId' | 'versionNo' | 'referenceKey'> &
  Partial<Pick<DynamicFormFileFactVO, 'fileFactVersion'>> & { referenceVersion?: number }
const toVisibleFiles = (facts: DynamicFormFileFactVO[]): VisibleFile[] =>
  facts.map((fact) => ({
    ...fact,
    referenceVersion: fact.fileFactVersion?.referenceVersion
  }))
const current = ref<VisibleFile[]>(toVisibleFiles(props.currentFacts))
const uploadSlotKey = ref(crypto.randomUUID())
const purposeCode = computed(() => `FORM_FIELD_ATTACHMENT/${props.fieldKey || ''}`)
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
  uploadSlotKey.value = crypto.randomUUID()
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
