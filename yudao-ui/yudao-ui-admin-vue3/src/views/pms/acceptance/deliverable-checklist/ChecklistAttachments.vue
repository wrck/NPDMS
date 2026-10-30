<template>
  <div>
    <el-upload v-if="!readonly" multiple :auto-upload="false" :file-list="pendingFiles"
      accept=".doc,.xls,.ppt,.txt,.pdf" :limit="5" :disabled="draftBusy" :on-change="selectPending" :before-remove="beforeRemovePending" :on-remove="removePending" v-hasPermi="['pms:file:upload']">
      <el-button type="primary" :disabled="draftBusy"><Icon icon="ep:upload-filled" />选取文件</el-button>
    </el-upload>
    <el-alert v-if="draftError" :title="draftError" type="error" :closable="false" />
    <div v-for="material in materials" :key="material.id">
      <PmsFileReferenceList v-if="material.fileBusinessKey" v-bind="material.fileBusinessKey" :artifact-id="material.fileArtifactId" :editable="!readonly" @detached="refresh" />
      <el-button v-if="!readonly && material.status === 'ACTIVE'" :disabled="busy" @click="withdraw(material.id)">撤回材料</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <template #default><el-button :disabled="busy" @click="pending ? collect() : refresh()">{{ pending ? '重试归集已上传附件' : '重新读取材料' }}</el-button></template>
    </el-alert>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { PmsFileReferenceList } from '@/components/PmsFileArtifact'
import { listMaterials, withdrawMaterial, type DeliveryMaterialVO } from '@/api/pms/platform/delivery'
import * as ChecklistApi from '@/api/pms/acceptance/deliverable-checklist'
import { useNativeAttachmentDraft } from '@/views/pms/engineering/attachment/useNativeAttachmentDraft'
const props = defineProps<{ entityId?: number; readonly: boolean }>()
const emit = defineEmits<{ changed: [owner?: ChecklistApi.DeliverableChecklistVO] }>()
const materials = ref<DeliveryMaterialVO[]>([])
const error = ref('')
const busy = ref(false)
const pending = ref(false)
let sequence = 0
const refresh = async () => {
  if (!props.entityId) return
  const id = props.entityId, current = ++sequence
  try {
    const rows = await listMaterials('ACC', 'deliverableChecklist', id, 'ACC.CHECKLIST_ATTACHMENT')
    if (id !== props.entityId || current !== sequence) return
    materials.value = rows.filter(row => row.materialKind === 'FILE' && row.fileBusinessKey?.ownerContext === 'ACC'
      && row.fileBusinessKey.objectType === 'DELIVERABLE_CHECKLIST' && String(row.fileBusinessKey.objectId) === String(id)
      && row.fileBusinessKey.purposeCode === 'CHECKLIST_ATTACHMENT')
    error.value = ''; emit('changed')
  } catch (failure: any) { if (id === props.entityId && current === sequence) error.value = failure?.message || '材料读取失败' }
}
const collect = async () => {
  if (props.readonly || !props.entityId || busy.value) return false
  const id = props.entityId
  busy.value = true; error.value = ''
  pending.value = true
  try {
    const owner = await ChecklistApi.getDeliverableChecklist(id)
    if (id !== props.entityId || props.readonly) return false
    if (!owner || owner.id !== id || owner.status !== 0) throw new Error('核对清单已不可编辑，请重新读取')
    await ChecklistApi.updateDeliverableChecklist(owner)
    const saved = await ChecklistApi.getDeliverableChecklist(id)
    if (id !== props.entityId || props.readonly) return false
    pending.value = false; emit('changed', saved)
    await refresh()
    return true
  } catch (failure: any) { if (id === props.entityId) error.value = failure?.message || '附件已上传，归集失败'; return false }
  finally { busy.value = false }
}
const withdraw = async (id: number) => {
  if (props.readonly || busy.value) return
  busy.value = true; error.value = ''
  try { await withdrawMaterial(id); await refresh() }
  catch (failure: any) { error.value = failure?.message || '材料撤回失败' }
  finally { busy.value = false }
}
const {pendingFiles,draftBusy,draftError,selectPending,beforeRemovePending,removePending,resetPending,savePending}=useNativeAttachmentDraft({
 context:()=>({module:'ACC',type:'DELIVERABLE_CHECKLIST',purpose:'CHECKLIST_ATTACHMENT',accept:'.doc,.xls,.ppt,.txt,.pdf'}),readonly:()=>props.readonly,collect
})
defineExpose({savePending})
watch(() => props.entityId, (_next,previous) => {
 if(previous && previous!==props.entityId)resetPending()
 pending.value = false; materials.value = []; void refresh()
}, { immediate: true })
</script>
