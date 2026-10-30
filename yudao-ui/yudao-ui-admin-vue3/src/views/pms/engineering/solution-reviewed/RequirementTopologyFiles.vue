<template>
  <div class="requirement-topology-files">
    <p v-if="files.length">引用需求分析 V{{ source?.revision.revisionNo }} 的拓扑附件</p>
    <PmsFileReferenceList
      v-for="file in files" :key="file.referenceKey"
      owner-context="SOL" object-type="REQUIREMENT_ANALYSIS_REVISION"
      :object-id="String(source!.revision.ref.revisionId)" :purpose-code="purpose"
      :reference-key="file.referenceKey" :artifact-id="file.artifactId" :version-no="file.versionNo" />
    <p v-if="!files.length">所引用需求版本暂无有效拓扑附件</p>
  </div>
</template>
<script setup lang="ts">
import { PmsFileReferenceList } from '@/components/PmsFileArtifact'
import type { View } from '@/api/pms/engineering/requirement-analysis/entity'
import { requirementTopologyFiles, requirementTopologyPurpose as purpose } from './requirementTopology'
const props = defineProps<{ source: View | null }>()
const files = computed(() => requirementTopologyFiles(props.source))
</script>
