<template>
  <el-card shadow="never" class="mt-8px">
    <template #header>
      <div class="flex items-center justify-between">
        <span>交付件（统一交付能力）</span>
        <el-button link type="primary" :loading="loading" @click="reload">刷新</el-button>
      </div>
    </template>

    <el-alert v-if="panelError" :title="panelError" type="error" :closable="false" show-icon class="mb-8px" />

    <!-- 登记材料：两段式上传（init + complete）后登记为交付材料 -->
    <el-form v-if="can('REGISTER_MATERIAL')" inline class="mb-8px">
      <el-form-item label="材料类型">
        <el-select v-model="uploadTypeCode" :disabled="uploadLocked" placeholder="选择类型" style="width: 180px" @change="onTypeChange">
          <el-option
            v-for="type in enabledTypes"
            :key="type.typeCode"
            :label="`${type.name}（${type.typeCode}）`"
            :value="type.typeCode"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="材料标题">
        <el-input v-model="uploadTitle" placeholder="可选" style="width: 180px" />
      </el-form-item>
      <el-form-item>
        <DeliveryUploader ref="uploader" v-if="selectedType" :key="uploadTypeCode"
          :owner-module="ownerModule" :entity-type="entityType" :entity-id="entityId"
          :type="selectedType" :title="uploadTitle || undefined" @completed="onUploaded" />
      </el-form-item>
    </el-form>
    <div v-if="uploadHint" class="mb-8px text-12px color-#909399">{{ uploadHint }}</div>

    <!-- 交付材料 -->
    <h4>交付材料</h4>
    <el-table :data="materials" size="small" border @selection-change="onMaterialSelectionChange">
      <el-table-column type="selection" width="42" :selectable="(row) => row.status === 'ACTIVE'" />
      <el-table-column prop="typeCode" label="类型" width="150" />
      <el-table-column prop="title" label="标题" min-width="120" show-overflow-tooltip />
      <el-table-column label="文件或业务成果" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.materialKind === 'BUSINESS_RESULT'
          ? `${row.businessObjectType} · ${row.businessObjectId}` : row.fileName }}</template>
      </el-table-column>
      <el-table-column label="版本" width="70">
        <template #default="{ row }">{{ row.materialKind === 'BUSINESS_RESULT'
          ? (row.businessRevisionNo ? `业务修订 ${row.businessRevisionNo}` : '—') : `文件 v${row.fileVersionNo}` }}</template>
      </el-table-column>
      <el-table-column prop="sourceKind" label="来源" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status === 'ACTIVE' ? '有效' : '已撤回' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="文件访问" min-width="180">
        <template #default="{ row }">
          <PmsFileReferenceList v-if="row.materialKind === 'FILE' && row.fileBusinessKey"
            v-bind="row.fileBusinessKey" :artifact-id="row.fileArtifactId" :version-no="row.fileVersionNo" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button v-if="can('WITHDRAW_MATERIAL') && row.status === 'ACTIVE'" link type="danger" size="small" @click="withdraw(row)">
            撤回
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 交付要求 -->
    <h4 class="mt-12px">交付要求</h4>
    <el-table :data="requirements" size="small" border>
      <el-table-column prop="typeCode" label="类型" width="150" />
      <el-table-column label="性质" width="80">
        <template #default="{ row }">{{ row.required ? '必交' : '选交' }}</template>
      </el-table-column>
      <el-table-column label="计数口径" width="160">
        <template #default="{ row }">{{ countingUnitLabel(row.countingUnit) }}</template>
      </el-table-column>
      <el-table-column label="数量" width="90">
        <template #default="{ row }">{{ completionErrors[row.id] ? "—" : row.count }} / {{ row.minimumQuantity }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <span v-if="completionErrors[row.id]" class="color-red">判定失败：{{ completionErrors[row.id] }}</span>
          <el-tag v-else :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前提交" min-width="120">
        <template #default="{ row }">
          <template v-if="currentSubmissions[row.id]">
            #{{ currentSubmissions[row.id]?.id }}（{{ currentSubmissions[row.id]?.materialIds.length }} 项）
          </template>
          <template v-else>-</template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            size="small"
            v-if="can('SUBMIT')"
            :disabled="selectedMaterialsByType(row.typeCode).length === 0"
            @click="submit(row)"
          >
            提交所选（{{ selectedMaterialsByType(row.typeCode).length }}）
          </el-button>
          <el-button
            link
            type="success"
            size="small"
            v-if="can('CONFIRM')"
            :disabled="!!completionErrors[row.id] || (row.status !== 'SATISFIED' && row.status !== 'CONFIRMED')"
            @click="confirm(row)"
          >
            确认
          </el-button>
          <el-button
            v-if="can('WITHDRAW_SUBMISSION') && currentSubmissions[row.id]"
            link
            type="danger"
            size="small"
            @click="withdrawCurrentSubmission(row)"
          >
            撤回提交
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  confirmRequirement,
  getDeliveryAllowedActions,
  getDeliveryTypes,
  getCompletion,
  listMaterials,
  listRequirements,
  listSubmissions,
  submitDelivery,
  withdrawMaterial,
  withdrawSubmission,
  type DeliveryMaterialVO,
  type DeliveryRequirementVO,
  type DeliverySubmissionVO,
  type DeliveryTypeVO
} from '@/api/pms/platform/delivery'
import DeliveryUploader from '@/components/DeliveryArtifact/DeliveryUploader.vue'
import PmsFileReferenceList from '@/components/PmsFileArtifact/PmsFileReferenceList.vue'

defineOptions({ name: 'DeliveryPanel' })

const props = defineProps<{ ownerModule: string; entityType: string; entityId: string | number; typeCodes?: string[]; readonly?: boolean }>()

const emit = defineEmits<{ (e: 'changed'): void }>()

const allowedActions=ref<string[]>([])
const can=(action:string)=>!props.readonly&&allowedActions.value.includes(action)
const loading = ref(false)
const panelError = ref('')
const completionErrors = ref<Record<number, string>>({})
const types = ref<DeliveryTypeVO[]>([])
const requirements = ref<DeliveryRequirementVO[]>([])
const materials = ref<DeliveryMaterialVO[]>([])
const currentSubmissions = ref<Record<number, DeliverySubmissionVO | undefined>>({})
const selectedMaterials = ref<DeliveryMaterialVO[]>([])

const uploadTypeCode = ref('')
const uploadTitle = ref('')
const uploader = ref<InstanceType<typeof DeliveryUploader>>()
const uploadLocked = computed(() => !!uploader.value?.isBusy() || !!uploader.value?.hasPendingFile())

const enabledTypes = computed(() => types.value.filter((type) => type.enabled && (!props.typeCodes || props.typeCodes.includes(type.typeCode))))
const selectedType = computed(() => enabledTypes.value.find((type) => type.typeCode === uploadTypeCode.value))
const uploadHint = computed(() => {
  const type = enabledTypes.value.find((item) => item.typeCode === uploadTypeCode.value)
  if (!type) return ''
  let media: string[] = []
  try {
    media = JSON.parse(type.allowedMediaJson)
  } catch {
    media = []
  }
  return `允许媒体类型：${media.join('、')}；单文件上限 ${Math.round(type.maxSizeBytes / 1024 / 1024)} MiB`
})

const selectedMaterialsByType = (typeCode: string) =>
  selectedMaterials.value.filter((material) => material.typeCode === typeCode && material.status === 'ACTIVE')

const countingUnitLabel = (unit: string) =>
  ({ MATERIAL: '按材料数', FILE_VERSION: '按文件版本去重', SUBMISSION: '按提交次数' })[unit] ?? unit

const statusLabel = (status: string) =>
  ({ OPEN: '未满足', SATISFIED: '已满足', CONFIRMED: '已确认' })[status] ?? status

const statusTagType = (status: string): 'warning' | 'success' | 'info' =>
  status === 'OPEN' ? 'warning' : status === 'SATISFIED' || status === 'CONFIRMED' ? 'success' : 'info'

const errorMessage = (error: any, fallback: string) =>
  error?.response?.data?.msg || error?.message || fallback

const reload = async () => {
  loading.value = true
  allowedActions.value=[]
  panelError.value = ''
  completionErrors.value = {}
  selectedMaterials.value = []
  try {
    const results = await Promise.allSettled([
      getDeliveryTypes(),
      listRequirements(props.ownerModule, props.entityType, props.entityId),
      listMaterials(props.ownerModule, props.entityType, props.entityId),
      getDeliveryAllowedActions(props.ownerModule,props.entityType,props.entityId)
    ])
    const failures: string[] = []
    const [typeResult, requirementResult, materialResult,actionResult] = results
    allowedActions.value=actionResult.status==='fulfilled'?actionResult.value??[]:[]
    if(actionResult.status==='rejected')failures.push(errorMessage(actionResult.reason,'操作权限装载失败'))
    types.value = typeResult.status === 'fulfilled' ? typeResult.value ?? [] : []
    if (typeResult.status === 'rejected') failures.push(errorMessage(typeResult.reason, '类型装载失败'))
    materials.value = materialResult.status === 'fulfilled'
      ? (materialResult.value ?? []).filter(material => !props.typeCodes || props.typeCodes.includes(material.typeCode)) : []
    if (materialResult.status === 'rejected') failures.push(errorMessage(materialResult.reason, '材料装载失败'))
    const rows = requirementResult.status === 'fulfilled'
      ? (requirementResult.value ?? []).filter(requirement => !props.typeCodes || props.typeCodes.includes(requirement.typeCode)) : []
    if (requirementResult.status === 'rejected') failures.push(errorMessage(requirementResult.reason, '要求装载失败'))
    requirements.value = await Promise.all(rows.map(async requirement => {
      try {
        const fact = await getCompletion(requirement.id)
        return { ...requirement, count: fact.count,
          status: fact.confirmed ? 'CONFIRMED' as const : fact.satisfied ? 'SATISFIED' as const : 'OPEN' as const }
      } catch (error: any) {
        completionErrors.value[requirement.id] = errorMessage(error, '完成事实判定失败')
        return requirement
      }
    }))
    const submissionEntries = await Promise.all(requirements.value.map(async requirement => {
      try {
        const list = await listSubmissions(requirement.id)
        return [requirement.id, (list ?? []).find(item => item.status === 'CURRENT')]
      } catch (error: any) {
        failures.push(errorMessage(error, '提交记录装载失败'))
        return [requirement.id, undefined]
      }
    }))
    currentSubmissions.value = Object.fromEntries(submissionEntries)
    panelError.value = failures.join('；')
  } finally {
    loading.value = false
  }
}

const onTypeChange = () => { uploadTitle.value = '' }
const onUploaded = async () => {
  uploadTitle.value = ''
  await reload()
  emit('changed')
}

const onMaterialSelectionChange = (rows: DeliveryMaterialVO[]) => {
  selectedMaterials.value = rows
}

const withdraw = async (material: DeliveryMaterialVO) => {
  panelError.value = ''
  try {
    await withdrawMaterial(material.id)
    await reload()
  } catch (error: any) {
    panelError.value = errorMessage(error, '材料撤回失败')
  }
}

const submit = async (requirement: DeliveryRequirementVO) => {
  const materialIds = selectedMaterialsByType(requirement.typeCode).map((material) => material.id)
  panelError.value = ''
  try {
    const outcome = await submitDelivery({
      requirementId: requirement.id,
      materialIds,
      requestKey: crypto.randomUUID()
    })
    ElMessage.success(
      `提交${outcome.replay ? '重放' : '成功'}：提交 #${outcome.submissionId}，数量 ${outcome.count}/${requirement.minimumQuantity}`
    )
    await reload()
  } catch (error: any) {
    panelError.value = errorMessage(error, '提交失败')
  }
}

const confirm = async (requirement: DeliveryRequirementVO) => {
  panelError.value = ''
  try {
    await confirmRequirement(requirement.id)
    ElMessage.success('已确认')
    await reload()
    emit('changed')
  } catch (error: any) {
    panelError.value = errorMessage(error, '确认失败')
  }
}

const withdrawCurrentSubmission = async (requirement: DeliveryRequirementVO) => {
  const submission = currentSubmissions.value[requirement.id]
  if (!submission) return
  panelError.value = ''
  try {
    await withdrawSubmission(submission.id)
    ElMessage.success('当前提交已撤回')
    await reload()
    emit('changed')
  } catch (error: any) {
    panelError.value = errorMessage(error, '提交撤回失败')
  }
}

onMounted(reload)
</script>
