<template>
  <div>
    <p>核对清单附件支持 doc、xls、ppt、txt、pdf，单文件上限 5MB。</p>
    <div v-for="material in materials" :key="material.id">
      <PmsFileReferenceList v-if="material.fileBusinessKey" v-bind="material.fileBusinessKey" :artifact-id="material.fileArtifactId" :editable="!readonly" @detached="refresh" />
      <el-button v-if="!readonly && material.status === 'ACTIVE'" :disabled="busy" @click="withdraw(material.id)">撤回材料</el-button>
    </div>
    <PmsFileUploader v-if="!readonly && !error && !pending" :key="referenceKey" owner-context="ACC" object-type="DELIVERABLE_CHECKLIST"
      :object-id="String(entityId)" purpose-code="CHECKLIST_ATTACHMENT" :reference-key="referenceKey" category-code="CHECKLIST_ATTACHMENT"
      accept=".doc,.xls,.ppt,.txt,.pdf" @completed="completed" />
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <template #default><el-button :disabled="busy" @click="pending ? collect() : refresh()">{{ pending ? '重试归集已上传附件' : '重新读取材料' }}</el-button></template>
    </el-alert>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { PmsFileUploader, PmsFileReferenceList } from '@/components/PmsFileArtifact'
import { generateUUID } from '@/utils'
import { listMaterials, withdrawMaterial, type DeliveryMaterialVO } from '@/api/pms/platform/delivery'
import * as ChecklistApi from '@/api/pms/acceptance/deliverable-checklist'
const props = defineProps<{ entityId: number; readonly: boolean }>()
const emit = defineEmits<{ changed: [owner?: ChecklistApi.DeliverableChecklistVO] }>()
const materials = ref<DeliveryMaterialVO[]>([])
const referenceKey = ref(generateUUID())
const error = ref('')
const busy = ref(false)
const pending = ref(false)
let sequence = 0
const refresh = async () => {
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
  if (props.readonly || busy.value) return
  const id = props.entityId
  busy.value = true; error.value = ''
  try {
    const owner = await ChecklistApi.getDeliverableChecklist(id)
    if (!owner || owner.id !== id || owner.status !== 0) throw new Error('核对清单已不可编辑，请重新读取')
    await ChecklistApi.updateDeliverableChecklist(owner)
    const saved = await ChecklistApi.getDeliverableChecklist(id)
    if (id !== props.entityId) return
    pending.value = false; referenceKey.value = generateUUID(); emit('changed', saved)
    await refresh()
  } catch (failure: any) { if (id === props.entityId) error.value = failure?.message || '附件已上传，归集失败' }
  finally { busy.value = false }
}
const completed = async () => { pending.value = true; await collect() }
const withdraw = async (id: number) => {
  if (props.readonly || busy.value) return
  busy.value = true; error.value = ''
  try { await withdrawMaterial(id); await refresh() }
  catch (failure: any) { error.value = failure?.message || '材料撤回失败' }
  finally { busy.value = false }
}
watch(() => props.entityId, () => { pending.value = false; materials.value = []; referenceKey.value = generateUUID(); void refresh() }, { immediate: true })
</script>
