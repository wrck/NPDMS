<template>
  <section aria-label="业务附件">
    <p v-if="legacy">历史附件（只读）：{{ legacy }}</p>
    <el-skeleton v-if="loading" :rows="2" animated />
    <template v-else>
      <div v-for="material in materials" :key="material.id" class="material">
        <PmsFileReferenceList v-if="material.fileBusinessKey" v-bind="material.fileBusinessKey"
          :artifact-id="material.fileArtifactId" :version-no="material.fileVersionNo"
          :editable="!effectiveReadonly && !busy && material.status === 'ACTIVE'" @detached="refresh" />
        <el-button v-if="!effectiveReadonly && material.status === 'ACTIVE'" :disabled="busy" @click="withdraw(material.id)">撤回材料</el-button>
        <span v-else-if="material.status === 'WITHDRAWN'">已撤回</span>
      </div>
      <PmsFileUploader v-if="!effectiveReadonly && !error && !pending" ref="uploader" :key="referenceKey"
        :owner-context="policy.ownerModule" :object-type="policy.objectType" :object-id="String(entityId)"
        :purpose-code="policy.purpose" :reference-key="referenceKey" :category-code="policy.purpose"
        :artifact-id="slot?.artifactId" :expected-reference-version="slot?.referenceVersion"
        :accept="policy.accept" :disabled="busy" @completed="completed" />
    </template>
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <template #default><el-button :disabled="busy" @click="pending ? collect() : refresh()">{{ pending ? '重试归集已上传附件' : '重新读取材料' }}</el-button></template>
    </el-alert>
  </section>
</template>
<script setup lang="ts">
import { computed, inject, onBeforeUnmount, provide, ref, watch } from 'vue'
import { formContextKey } from 'element-plus'
import { PmsFileUploader, PmsFileReferenceList } from '@/components/PmsFileArtifact'
import { getReference, type FileReferenceVO } from '@/api/pms/platform/file'
import { listMaterials, withdrawMaterial, type DeliveryMaterialVO } from '@/api/pms/platform/delivery'
import type { BusinessId, ProjectBusinessApi } from '@/api/pms/platform/business'
import { generateUUID } from '@/utils'
import { useMessage } from '@/hooks/web/useMessage'

export interface NativeAttachmentPolicy { ownerModule: string; entityType: string; objectType: string; purpose: string; accept: string; referenceKey?: string }
const props = defineProps<{ api: ProjectBusinessApi; entityId: BusinessId; policy: NativeAttachmentPolicy; readonly?: boolean; legacy?: string }>()
const parentForm = inject(formContextKey, undefined)
const effectiveReadonly = computed(() => !!props.readonly || !!parentForm?.disabled)
// A frozen business form still permits file history and downloads. Keep its write protection explicit.
if (parentForm) provide(formContextKey, { ...parentForm, disabled: false })
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const materials = ref<DeliveryMaterialVO[]>([]), slot = ref<FileReferenceVO>()
const loading = ref(false), busy = ref(false), error = ref(''), pending = ref(false)
const referenceKey = ref(''), uploader = ref<InstanceType<typeof PmsFileUploader>>()
let generation = 0
let attempt: { key: string; version: number } | undefined
const refresh = async () => {
  const active = generation, policy = props.policy, id = props.entityId
  loading.value = true
  try {
    const [rows, currentSlot] = await Promise.all([
      listMaterials(policy.ownerModule, policy.entityType, id as number, `${policy.ownerModule}.${policy.purpose}`),
      !effectiveReadonly.value && policy.referenceKey ? getReference({ownerContext:policy.ownerModule,objectType:policy.objectType,objectId:String(id),purposeCode:policy.purpose,referenceKey:policy.referenceKey}) : undefined
    ])
    if (active !== generation) return
    materials.value = rows.filter(row => row.materialKind === 'FILE' && row.fileBusinessKey?.ownerContext === policy.ownerModule
      && row.fileBusinessKey.objectType === policy.objectType && String(row.fileBusinessKey.objectId) === String(id)
      && row.fileBusinessKey.purposeCode === policy.purpose)
    slot.value = currentSlot?.status === 'ACTIVE' ? currentSlot : undefined
    error.value = ''
  } catch (failure) { if (active === generation) error.value = failure instanceof Error ? failure.message : '材料读取失败' }
  finally { if (active === generation) loading.value = false }
}
const collect = async () => {
  if (effectiveReadonly.value || busy.value) return
  const active = generation, id = props.entityId, api = props.api
  busy.value = true; error.value = ''
  try {
    if (!attempt) {
      const row = await api.get(id)
      if (active !== generation) return
      if (!row.available || row.concurrencyBasis == null) throw new Error('业务记录已不可编辑，请重新读取')
      attempt = {key:generateUUID(),version:row.concurrencyBasis}
    }
    // The inherited save collects the persisted native file set without resubmitting unsaved form fields.
    await api.update(id, {}, attempt.version, attempt.key)
    if (active !== generation) return
    pending.value = false; attempt = undefined
    referenceKey.value = props.policy.referenceKey || generateUUID()
    await refresh(); emit('changed')
  } catch (failure) { if (active === generation) error.value = failure instanceof Error ? failure.message : '附件已上传，归集失败' }
  finally { if (active === generation) busy.value = false }
}
const completed = async () => { pending.value = true; await collect() }
const withdraw = async (id: number) => {
  if (effectiveReadonly.value || busy.value) return
  const active = generation
  busy.value = true
  try { await withdrawMaterial(id); if (active === generation) { await refresh(); emit('changed') } }
  catch (failure) { if (active === generation) error.value = failure instanceof Error ? failure.message : '材料撤回失败' }
  finally { if (active === generation) busy.value = false }
}
const requestLeave = async () => {
  if (busy.value || loading.value || uploader.value?.isBusy()) return false
  if (!pending.value && !uploader.value?.hasPendingFile()) return true
  try { await message.confirm('附件尚未完成上传或归集，确定离开？'); return true } catch { return false }
}
watch(() => [props.api.base,props.entityId,props.policy,effectiveReadonly.value], () => {
  generation++; busy.value = false; pending.value = false; attempt = undefined; materials.value = []; slot.value = undefined
  referenceKey.value = props.policy.referenceKey || generateUUID(); void refresh()
}, {immediate:true})
onBeforeUnmount(() => { generation++ })
defineExpose({requestLeave})
</script>
<style scoped>.material{margin-bottom:12px}</style>
