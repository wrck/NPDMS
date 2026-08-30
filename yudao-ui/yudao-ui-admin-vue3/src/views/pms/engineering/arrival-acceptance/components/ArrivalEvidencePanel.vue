<template>
  <section class="evidence-panel" aria-labelledby="arrival-evidence-title">
    <div class="section-heading">
      <div>
        <h3 id="arrival-evidence-title">签收证据</h3>
        <p>保存 PLT 稳定引用与不可变版本，不展示或编辑原始文件地址。</p>
      </div>
      <el-tag :type="presentation.tone">{{ presentation.label }}</el-tag>
    </div>

    <dl v-if="evidence" class="evidence-facts">
      <div
        ><dt>文件</dt><dd :title="artifactName">{{ shownArtifactName }}</dd></div
      >
      <div
        ><dt>引用键</dt><dd>{{ evidence.referenceKey }}</dd></div
      >
      <div
        ><dt>版本</dt><dd>v{{ evidence.fileVersionNo }}</dd></div
      >
      <div
        ><dt>重试</dt><dd>{{ evidence.retryCount }} 次</dd></div
      >
    </dl>
    <el-empty v-else description="尚未绑定签收证据" :image-size="64" />

    <PmsFileUploader
      v-if="editable"
      owner-context="IMP"
      object-type="ARRIVAL_ACCEPTANCE"
      :object-id="String(acceptanceId)"
      purpose-code="RECEIPT"
      :reference-key="evidence?.referenceKey || 'arrival-receipt'"
      category-code="ARRIVAL_ACCEPTANCE_RECEIPT"
      :artifact-id="asArtifactId(evidence?.artifactId)"
      :expected-reference-version="evidence?.fileFactVersion.referenceVersion"
      @completed="completeUpload"
    />
  </section>
</template>

<script setup lang="ts">
import * as FileApi from '@/api/pms/platform/file'
import type {
  ArrivalEvidence,
  FileRevision,
  WireLong
} from '@/api/pms/engineering/arrival-acceptance'
import { PmsFileUploader } from '@/components/PmsFileArtifact'
import type { FileSelection } from '@/components/PmsFileArtifact'
import { evidenceSyncPresentation, truncateEvidenceName } from '../arrivalAcceptanceInteraction'

const props = defineProps<{
  acceptanceId: WireLong
  evidence: ArrivalEvidence | null
  editable: boolean
}>()
const emit = defineEmits<{ revision: [value: FileRevision] }>()
const artifactName = ref('已绑定文件')
const presentation = computed(() => evidenceSyncPresentation(props.evidence?.syncStatus))
const shownArtifactName = computed(() => truncateEvidenceName(artifactName.value, 52))

// PLT 的路径参数可原样携带 Long 字符串；这里只满足共享组件当前的静态类型，禁止 Number 转换。
const asArtifactId = (value?: WireLong | null) =>
  value === null || value === undefined ? undefined : (value as number)

const keyOf = (referenceKey: string) => ({
  ownerContext: 'IMP',
  objectType: 'ARRIVAL_ACCEPTANCE',
  objectId: String(props.acceptanceId),
  purposeCode: 'RECEIPT',
  referenceKey
})

const completeUpload = async (selection: FileSelection) => {
  const artifact = await FileApi.getArtifact(selection.artifactId, keyOf(selection.referenceKey))
  const versions = await FileApi.getVersions(selection.artifactId, {
    ...keyOf(selection.referenceKey),
    pageSize: 20
  })
  const version = versions.items.find((item) => item.versionNo === selection.versionNo)
  if (!version) throw new Error('PLT 未返回刚完成的文件版本')
  artifactName.value = artifact.name
  emit('revision', {
    artifactId: selection.artifactId,
    referenceKey: selection.referenceKey,
    versionNo: selection.versionNo,
    scopeVersion: artifact.reference.scopeVersion,
    fileFactVersion: {
      artifactVersion: artifact.artifactVersion,
      referenceVersion: artifact.reference.referenceVersion,
      availabilityVersion: version.availabilityVersion
    },
    hash: version.sha256
  })
}

watch(
  () => props.evidence,
  async (evidence) => {
    const artifactId = asArtifactId(evidence?.artifactId)
    if (!evidence || artifactId === undefined) return
    try {
      artifactName.value = (
        await FileApi.getArtifact(artifactId, keyOf(evidence.referenceKey))
      ).name
    } catch {
      artifactName.value = '文件信息暂不可用'
    }
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.evidence-panel {
  padding-top: 20px;
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.section-heading h3,
.section-heading p,
.evidence-facts dd {
  margin: 0;
}

.section-heading p,
.evidence-facts dt {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.evidence-facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 0 0 16px;
}

.evidence-facts dd {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (width <= 767px) {
  .section-heading,
  .evidence-facts {
    display: grid;
    grid-template-columns: 1fr;
  }
}
</style>
