<template>
  <main>
    <h1>文件槽位刷新恢复验证</h1>
    <p data-testid="status">{{ status }}</p>
    <PmsFileReferenceList v-bind="key" :artifact-id="slot.state.artifactId" :editable="true" @loaded="slot.loaded" />
    <PmsFileUploader v-bind="key" :artifact-id="slot.state.artifactId" :expected-reference-version="slot.state.referenceVersion"
      category-code="EVIDENCE" @completed="completed" />
    <output data-testid="identity">{{ slot.state.artifactId }}</output>
  </main>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import PmsFileReferenceList from '@/components/PmsFileArtifact/PmsFileReferenceList.vue'
import PmsFileUploader from '@/components/PmsFileArtifact/PmsFileUploader.vue'
import { useFileSlotState } from '@/components/PmsFileArtifact/useFileSlotState'
import type { FileSelection } from '@/components/PmsFileArtifact/types'
const key = { ownerContext: 'SOL', objectType: 'NATIVE_OWNER', objectId: '9007199254740997',
  purposeCode: 'EVIDENCE', referenceKey: 'fixed-slot' }
const slot = useFileSlotState()
const status = ref('ready')
const completed = (selection: FileSelection) => {
  slot.uploaded(selection)
  // The native save/register consumer is outside this fixture. Simulate its rejection after file completion.
  status.value = 'file saved; material registration rejected'
}
</script>
<style>body{font:16px sans-serif;margin:40px;background:#f7f9fb}main{max-width:720px;margin:auto}output{display:block;margin-top:16px}</style>
