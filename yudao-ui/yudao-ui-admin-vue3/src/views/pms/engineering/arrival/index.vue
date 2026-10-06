<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="projectName"
          value-field="id"
          query-field="projectName"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ARRIVAL_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:imp-arrival:create']"
          ><Icon icon="ep:plus" />新增签收</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="equipmentId" label="设备编号" width="100" />
      <el-table-column prop="quantity" label="数量" width="80" />
      <el-table-column prop="arrivalTime" label="到货时间" width="160" :formatter="dateFormatter" />
      <el-table-column prop="inspectionResult" label="验收结果" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ARRIVAL_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:imp-arrival:query']"
            >{{ editableRecord(row) ? '编辑' : '查看' }}</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'sign')"
            v-hasPermi="['pms:imp-arrival:update']"
            >签收</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0"
            @click="handleAction(row, 'markAbnormal')"
            v-hasPermi="['pms:imp-arrival:update']"
            >标记异常</el-button
          >
          <el-button v-if="row.status !== 1" link type="danger" @click="remove(row)" v-hasPermi="['pms:imp-arrival:delete']"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <Dialog v-model="formVisible" :title="form.id ? (readOnly ? '查看签收' : '编辑签收') : '新增签收'" width="min(780px, 95vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="readOnly">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目编号" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              @change="form.equipmentId = undefined"
              :api="ProjectApi.getProjectPage"
              label-field="projectName"
              value-field="id"
              query-field="projectName"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="到货时间" prop="arrivalTime">
            <el-date-picker v-model="form.arrivalTime" type="datetime" value-format="x" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="签收人" prop="receiverUserId"><el-input v-model="form.receiverUserId" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联设备" prop="equipmentId">
            <ProjectDeviceSelect v-model="form.equipmentId" :project-id="form.projectId" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数量" prop="quantity"><el-input-number v-model="form.quantity" :min="0" class="!w-full" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="验收结果" prop="inspectionResult">
            <Editor v-model="form.inspectionResult" height="200px" :readonly="readOnly" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="异常记录" prop="exceptionRecord">
            <Editor v-model="form.exceptionRecord" height="200px" :readonly="readOnly" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <DeliveryPanel v-if="form.id" :key="`${form.id}-${deliveryEpoch}`" owner-module="IMP" entity-type="arrival" :entity-id="form.id" :readonly="true" />
    <section class="sign-document-section" aria-label="签收单附件">
      <h4>签收单附件</h4>
      <template v-if="form.id">
        <el-skeleton v-if="slotLoading" :rows="2" animated />
        <PmsFileReferenceList
          v-else-if="!slotError"
          :key="`sign-slot-${slotEpoch}`"
          owner-context="IMP"
          object-type="ARRIVAL"
          :object-id="String(form.id)"
          purpose-code="ARRIVAL_SIGN_DOCUMENT"
          reference-key="arrival-sign-document"
          :artifact-id="signSlot?.artifactId"
          :editable="!readOnly && !deliverySaving"
          @loaded="signDocumentLoaded"
          @detached="signDocumentDetached"
        />
        <PmsFileUploader
          v-if="!readOnly && !deliverySaving && !slotLoading && !slotError"
          :key="form.id"
          owner-context="IMP"
          object-type="ARRIVAL"
          :object-id="String(form.id)"
          purpose-code="ARRIVAL_SIGN_DOCUMENT"
          reference-key="arrival-sign-document"
          category-code="ARRIVAL_SIGN_DOCUMENT"
          :artifact-id="signSlot?.artifactId"
          :expected-reference-version="signSlot?.referenceVersion"
          accept=".pdf,.png,.jpg,.jpeg,.txt,.doc,.docx,.xls,.xlsx"
          @completed="signDocumentUploaded"
        />
        <el-alert v-if="slotError" :title="slotError" type="error" :closable="false" show-icon>
          <template #default><el-button @click="loadSignDocument(form.id)">重新读取附件</el-button></template>
        </el-alert>
        <el-alert v-if="deliveryError" :title="deliveryError" type="error" :closable="false" show-icon>
          <template #default>
            <el-button :loading="deliverySaving" :disabled="readOnly" @click="collectSignDocument(form.id)">重试归集已上传附件</el-button>
          </template>
        </el-alert>
        <el-button v-if="!readOnly" :loading="deliverySaving"
          :disabled="slotLoading || !!slotError" @click="withdrawObsoleteSignMaterials(form.id)">
          撤回已失效旧版本材料
        </el-button>
        <div v-if="legacyAttachments.length" class="legacy-links">
          <span>历史附件：</span>
          <a v-for="url in legacyAttachments" :key="url" :href="url" target="_blank" rel="noopener"
            >查看附件</a
          >
        </div>
      </template>
      <span v-else class="slot-hint">保存后可上传签收单附件，自动归入交付清单</span>
    </section>
    <template #footer>
      <el-button :disabled="deliverySaving" @click="formVisible = false">取消</el-button>
      <el-button v-if="!readOnly" type="primary" :loading="saving" :disabled="deliverySaving" @click="save">保存</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import DeliveryPanel from '@/components/BusinessEntity/DeliveryPanel.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as ArrivalApi from '@/api/pms/engineering/arrival'
import type { ArrivalVO } from '@/api/pms/engineering/arrival'
import * as ProjectApi from '@/api/pms/project/projects'
import ProjectDeviceSelect from '@/components/ProjectDeviceSelect/index.vue'
import { PmsFileReferenceList, PmsFileUploader } from '@/components/PmsFileArtifact'
import type { FileSelection } from '@/components/PmsFileArtifact'
import type { FileArtifactVO } from '@/api/pms/platform/file'
import { getArtifact } from '@/api/pms/platform/file'
import { listMaterials, withdrawMaterial } from '@/api/pms/platform/delivery'
import { checkPermi } from '@/utils/permission'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'PmsEngArrival' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ArrivalVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: '', code: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
type ArrivalForm = Omit<ArrivalVO, 'arrivalTime'> & { arrivalTime?: string | number | null }
const form = ref<ArrivalForm>({ projectId: 0, code: '', status: 0 })
const editableRecord = (row: Pick<ArrivalVO, 'status'>) => (row.status === 0 || row.status === 2) && checkPermi(['pms:imp-arrival:update'])
const readOnly = computed(() => form.value.id ? !editableRecord(form.value) : !checkPermi(['pms:imp-arrival:create']))
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  arrivalTime: [{ required: true, message: '请选择到货时间' }]
}
// 槽位只保留挂接事实：completion 先给最小事实驱动列表重载，@loaded 再精化为当前版本。
const signSlot = ref<{ artifactId?: number; referenceVersion?: number }>()
const slotEpoch = ref(0)
const slotLoading = ref(false)
const slotError = ref('')
let slotSequence = 0
const deliveryEpoch = ref(0)
const deliverySaving = ref(false)
const deliveryError = ref('')
const legacyAttachments = computed(() =>
  (form.value.attachmentUrl ?? '')
    .split(',')
    .map((url) => url.trim())
    .filter((url) => url.length > 0)
)
const onSignDocumentLoaded = (artifact: FileArtifactVO) => {
  signSlot.value = { artifactId: artifact.artifactId, referenceVersion: artifact.reference.referenceVersion }
}
const loadSignDocument = async (id: number) => {
  const sequence = ++slotSequence
  slotLoading.value = true; slotError.value = ''
  try {
    const materials = await listMaterials('IMP', 'arrival', id, 'IMP.ARRIVAL_SIGN_DOCUMENT')
    const candidates = materials.filter(item => item.materialKind === 'FILE' && item.fileBusinessKey?.ownerContext === 'IMP'
      && item.fileBusinessKey.objectType === 'ARRIVAL' && String(item.fileBusinessKey.objectId) === String(id)
      && item.fileBusinessKey.purposeCode === 'ARRIVAL_SIGN_DOCUMENT' && item.fileBusinessKey.referenceKey === 'arrival-sign-document')
    // Materials retain historical artifacts. Only the current native reference
    // resolves with this complete stable key; never infer a new file identity.
    let current: FileArtifactVO | undefined
    for (const candidate of candidates) {
      try { current = await getArtifact(candidate.fileArtifactId!, candidate.fileBusinessKey!); if (current) break }
      catch { if (candidate === candidates[candidates.length - 1]) throw new Error('附件引用已变化，请重新读取') }
    }
    if (sequence !== slotSequence || id !== form.value.id) return
    signSlot.value = current?.reference.status === 'ACTIVE'
      ? { artifactId: current.artifactId, referenceVersion: current.reference.referenceVersion } : undefined
    slotEpoch.value += 1
  } catch (error: any) {
    if (sequence === slotSequence && id === form.value.id) slotError.value = error?.message || '附件读取失败，请重试'
  } finally { if (sequence === slotSequence) slotLoading.value = false }
}
const collectSignDocument = async (id: number) => {
  if (id !== form.value.id || readOnly.value || deliverySaving.value) return
  deliverySaving.value = true
  deliveryError.value = ''
  try {
    // Save the persisted Owner solely to collect its locked file set. Local edits
    // remain in the form until the user explicitly saves them.
    const saved: ArrivalVO = await ArrivalApi.getArrival(id)
    if (!saved || saved.id !== id || !editableRecord(saved)) throw new Error('到货记录已不可编辑，请重新读取')
    await ArrivalApi.updateArrival(saved)
    const current: ArrivalVO = await ArrivalApi.getArrival(id)
    if (id === form.value.id) {
      form.value.version = current.version
      form.value.status = current.status
      deliveryEpoch.value += 1
    }
  } catch (error: any) {
    if (id === form.value.id) deliveryError.value = error?.message || '附件已上传，交付件归集失败，请重试'
  } finally {
    deliverySaving.value = false
  }
}
const onSignDocumentUploaded = async (id: number, selection: FileSelection) => {
  if (id !== form.value.id) return
  signSlot.value = { artifactId: selection.artifactId }
  slotEpoch.value += 1
  await collectSignDocument(id)
}
const signDocumentUploaded = computed(() => onSignDocumentUploaded.bind(null, form.value.id!))
const withdrawObsoleteSignMaterials = async (id: number) => {
  if (id !== form.value.id || readOnly.value || deliverySaving.value || slotLoading.value || slotError.value) return
  deliverySaving.value = true; deliveryError.value = ''
  try {
    const candidates = await listMaterials('IMP', 'arrival', id, 'IMP.ARRIVAL_SIGN_DOCUMENT')
    for (const material of candidates) {
      const key = material.fileBusinessKey
      if (material.status !== 'ACTIVE' || material.materialKind !== 'FILE' || !key || key.ownerContext !== 'IMP'
        || key.objectType !== 'ARRIVAL' || String(key.objectId) !== String(id)
        || key.purposeCode !== 'ARRIVAL_SIGN_DOCUMENT' || key.referenceKey !== 'arrival-sign-document') continue
      const actual = await getArtifact(material.fileArtifactId!, key)
      if (!actual) throw new Error('无法读取实际附件，未撤回材料')
      if (actual.reference.status !== 'ACTIVE' || actual.reference.versionNo !== material.fileVersionNo) {
        await withdrawMaterial(material.id)
      }
    }
    if (id === form.value.id) deliveryEpoch.value += 1
  } catch (error: any) {
    if (id === form.value.id) deliveryError.value = error?.message || '旧版本材料撤回失败，请刷新后重试'
  } finally { deliverySaving.value = false }
}
const onSignDocumentDetached = () => {
  signSlot.value = undefined
  slotEpoch.value += 1
  deliveryEpoch.value += 1
}
const signDocumentLoaded = computed(() => {
  const id = form.value.id
  return (artifact: FileArtifactVO) => { if (id === form.value.id) onSignDocumentLoaded(artifact) }
})
const signDocumentDetached = computed(() => {
  const id = form.value.id
  return () => { if (id === form.value.id) onSignDocumentDetached() }
})

const load = async () => {
  loading.value = true
  try {
    const data = await ArrivalApi.getArrivalPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: ArrivalVO) => {
  form.value = {
      id: undefined,
      projectId: 0,
      code: '',
      arrivalTime: undefined,
      receiverUserId: undefined,
      equipmentId: undefined,
      quantity: undefined,
      inspectionResult: '',
      exceptionRecord: '',
      remark: '',
      version: undefined,
      status: 0,
      ...row,
      // UploadFile derives string/array output from its initial value. Legacy
      // records with NULL evidence must still use this API's string contract.
      attachmentUrl: row?.attachmentUrl ?? ''
  }
  signSlot.value = undefined
  ++slotSequence; slotLoading.value = false; slotError.value = ''
  deliveryError.value = ''
  slotEpoch.value += 1
  formVisible.value = true
  if (form.value.id) void loadSignDocument(form.value.id)
}
const save = async () => {
  if (readOnly.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    // Match the existing TimestampLocalDateTimeDeserializer contract, not a
    // formatted date string that would be interpreted as an invalid timestamp.
    const data: ArrivalVO = { ...form.value, arrivalTime: form.value.arrivalTime == null || form.value.arrivalTime === '' ? undefined : Number(form.value.arrivalTime) }
    if (data.id) {
      await ArrivalApi.updateArrival(data)
      message.success('保存成功')
      formVisible.value = false
    } else {
      // 新建后留在表单内切换为可编辑态：签收单附件挂接依赖已保存的记录 ID。
      const createdId = await ArrivalApi.createArrival(data)
      form.value.id = createdId
      form.value.version = 0
      message.success(
        checkPermi(['pms:imp-arrival:update']) ? '保存成功，可上传签收单附件' : '保存成功'
      )
    }
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ArrivalVO) => {
  if (row.status === 1) return
  await message.delConfirm()
  await ArrivalApi.deleteArrival(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: ArrivalVO, action: 'sign' | 'markAbnormal') => {
  const actionText = { sign: '签收', markAbnormal: '标记异常' }[action]
  await message.confirm(`确认${actionText}该签收记录？`)
  if (action === 'sign') await ArrivalApi.signArrival(row.id!)
  if (action === 'markAbnormal') await ArrivalApi.markAbnormalArrival(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
<style scoped lang="scss">
.sign-document-section {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);

  h4 {
    margin: 0 0 10px;
    font-size: 14px;
    font-weight: 600;
  }

  .legacy-links {
    margin-top: 8px;
    display: flex;
    gap: 12px;
    align-items: center;
    color: var(--el-text-color-secondary);
  }

  .slot-hint {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}
</style>
