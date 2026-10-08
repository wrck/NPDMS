<template>
  <div class="native-attachments">
    <p>附件统一保存到交付材料，单文件上限 5MB。</p>
    <p v-if="legacyLinks.length">历史附件（只读）：{{ legacyLinks.join('，') }}</p>
    <p v-if="!entityId">请先保存业务记录，再打开记录上传附件。</p>
    <template v-else>
      <DeliveryPanel :key="`${kind}-${entityId}-${deliveryEpoch}`" :owner-module="config.module" :entity-type="config.type" :entity-id="entityId" :readonly="true" />
      <div v-for="material in materials" :key="material.id">
        <PmsFileReferenceList v-if="material.fileBusinessKey" v-bind="material.fileBusinessKey"
          :artifact-id="material.fileArtifactId" :version-no="material.fileVersionNo" :editable="!readonly && material.status === 'ACTIVE'" @detached="refresh" />
        <el-button v-if="!readonly && material.status === 'ACTIVE'" :disabled="busy" @click="withdraw(material.id)">撤回材料</el-button>
        <span v-else-if="material.status === 'WITHDRAWN'">已撤回</span>
      </div>
      <PmsFileUploader v-if="!readonly && !error && !pending" :key="referenceKey"
        :owner-context="config.module" :object-type="config.type" :object-id="String(entityId)"
        :purpose-code="config.purpose" :reference-key="referenceKey" :category-code="config.purpose"
        :accept="config.accept" @completed="completed" />
      <el-alert v-if="error" :title="error" type="error" :closable="false">
        <template #default><el-button :disabled="busy" @click="pending ? collect() : refresh()">{{ pending ? '重试归集已上传附件' : '重新读取材料' }}</el-button></template>
      </el-alert>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import DeliveryPanel from '@/components/BusinessEntity/DeliveryPanel.vue'
import { PmsFileUploader, PmsFileReferenceList } from '@/components/PmsFileArtifact'
import { generateUUID } from '@/utils'
import { listMaterials, withdrawMaterial, type DeliveryMaterialVO } from '@/api/pms/platform/delivery'
import * as Configuration from '@/api/pms/engineering/configuration'
import * as JointTest from '@/api/pms/engineering/joint-test'
import * as ExternalProcurement from '@/api/pms/engineering/ext-proc'
import * as Outsource from '@/api/pms/engineering/outsource'
import * as MaterialRequisition from '@/api/pms/engineering/material-req'
import * as MaterialExchange from '@/api/pms/engineering/material-exch'
type Kind = 'configuration' | 'jointTest' | 'externalProcurement' | 'outsourceRequest' | 'materialRequisition' | 'materialExchange'
const props = defineProps<{ kind: Kind; entityId?: number | string; readonly: boolean; legacy?: string }>()
const emit = defineEmits<{ changed: [owner: { version?: number; status?: number; configLogUrl?: string }] }>()
const office = '.doc,.xls,.ppt,.txt,.pdf'
const adapters = {
  configuration: { module: 'IMP', type: 'configuration', purpose: 'CONFIGURATION_LOG', states: [0, 1, 3], accept: '.log,.cfg,.conf,' + office, get: Configuration.getConfiguration, update: Configuration.updateConfiguration },
  jointTest: { module: 'IMP', type: 'jointTest', purpose: 'JOINT_TEST_EVIDENCE', states: [0, 1], accept: '.log,.cfg,.conf,' + office, get: JointTest.getJointTest, update: JointTest.updateJointTest },
  externalProcurement: { module: 'IMP', type: 'externalProcurement', purpose: 'EXTERNAL_PROCUREMENT_ATTACHMENT', states: [0, 4], accept: office, get: ExternalProcurement.getExternalProcurement, update: ExternalProcurement.updateExternalProcurement },
  outsourceRequest: { module: 'RES', type: 'outsourceRequest', purpose: 'OUTSOURCE_ATTACHMENT', states: [0, 4], accept: office, get: Outsource.getOutsourceRequest, update: Outsource.updateOutsourceRequest },
  materialRequisition: { module: 'IMP', type: 'materialRequisition', purpose: 'MATERIAL_REQUISITION_ATTACHMENT', states: [0, 4], accept: office, get: MaterialRequisition.getMaterialRequisition, update: MaterialRequisition.updateMaterialRequisition },
  materialExchange: { module: 'IMP', type: 'materialExchange', purpose: 'MATERIAL_EXCHANGE_REASON', states: [0, 4], accept: office, get: MaterialExchange.getMaterialExchange, update: MaterialExchange.updateMaterialExchange }
}
const config = computed(() => adapters[props.kind])
const legacyLinks = computed(() => (props.legacy || '').split(',').filter(value => /^https?:\/\//.test(value)))
const deliveryEpoch = ref(0)
const materials = ref<DeliveryMaterialVO[]>([])
const referenceKey = ref(generateUUID())
const error = ref('')
const busy = ref(false)
const pending = ref(false)
let sequence = 0
const context = () => ({ id: props.entityId, kind: props.kind, sequence })
const current = (seen: ReturnType<typeof context>) => seen.id === props.entityId && seen.kind === props.kind && seen.sequence === sequence
const refresh = async () => {
  if (!props.entityId) return
  const seen = context(), owner = config.value
  try {
    const rows = await listMaterials(owner.module, owner.type, seen.id as number, `${owner.module}.${owner.purpose}`)
    if (!current(seen)) return
    materials.value = rows.filter(row => row.materialKind === 'FILE' && row.fileBusinessKey?.ownerContext === owner.module
      && row.fileBusinessKey.objectType === owner.type && String(row.fileBusinessKey.objectId) === String(seen.id)
      && row.fileBusinessKey.purposeCode === owner.purpose)
    error.value = ''; deliveryEpoch.value++
  } catch (failure: any) { if (current(seen)) error.value = failure?.message || '材料读取失败' }
}
const collect = async () => {
  if (props.readonly || !props.entityId || busy.value) return
  const seen = context(), adapter = config.value
  busy.value = true; error.value = ''
  try {
    const owner = await adapter.get(seen.id as number)
    if (!current(seen)) return
    if (!owner || String(owner.id) !== String(seen.id) || !adapter.states.includes(owner.status)) throw new Error('记录已不可编辑，请重新读取')
    // Persist the saved Owner, keeping unsubmitted form edits and exchange serial snapshots untouched.
    const request = { ...owner }
    if (seen.kind === 'materialExchange') delete request.serials
    await adapter.update(request)
    const saved = await adapter.get(seen.id as number)
    if (!current(seen)) return
    pending.value = false; referenceKey.value = generateUUID(); emit('changed', saved)
    await refresh()
  } catch (failure: any) { if (current(seen)) error.value = failure?.message || '附件已上传，归集失败' }
  finally { if (current(seen)) busy.value = false }
}
const completed = computed(() => {
  const seen = context(), slot = referenceKey.value
  return async () => {
    if (!current(seen) || slot !== referenceKey.value) return
    pending.value = true
    await collect()
  }
})
const withdraw = async (id: number) => {
  if (props.readonly || busy.value) return
  const seen = context()
  busy.value = true; error.value = ''
  try { await withdrawMaterial(id); if (current(seen)) await refresh() }
  catch (failure: any) { if (current(seen)) error.value = failure?.message || '材料撤回失败' }
  finally { if (current(seen)) busy.value = false }
}
watch(() => [props.kind, props.entityId], () => { sequence++; pending.value = false; busy.value = false; error.value = ''; materials.value = []; referenceKey.value = generateUUID(); void refresh() }, { immediate: true })
</script>
<style scoped>
.native-attachments { width: 100%; min-width: 0; overflow-x: auto; }
</style>
