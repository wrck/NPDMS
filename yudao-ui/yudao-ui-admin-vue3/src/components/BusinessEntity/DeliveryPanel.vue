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
    <el-form inline class="mb-8px">
      <el-form-item label="材料类型">
        <el-select v-model="uploadTypeCode" placeholder="选择类型" style="width: 180px" @change="onTypeChange">
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
        <input ref="fileInputRef" type="file" @change="onFileChange" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="uploading" :disabled="!selectedFile || !uploadTypeCode" @click="uploadAndRegister">
          上传并登记
        </el-button>
      </el-form-item>
    </el-form>
    <div v-if="uploadHint" class="mb-8px text-12px color-#909399">{{ uploadHint }}</div>

    <!-- 交付材料 -->
    <h4>交付材料</h4>
    <el-table :data="materials" size="small" border @selection-change="onMaterialSelectionChange">
      <el-table-column type="selection" width="42" :selectable="(row) => row.status === 'ACTIVE'" />
      <el-table-column prop="typeCode" label="类型" width="150" />
      <el-table-column prop="title" label="标题" min-width="120" show-overflow-tooltip />
      <el-table-column prop="fileName" label="文件名" min-width="160" show-overflow-tooltip />
      <el-table-column label="版本" width="70">
        <template #default="{ row }">v{{ row.fileVersionNo }}</template>
      </el-table-column>
      <el-table-column prop="sourceKind" label="来源" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status === 'ACTIVE' ? '有效' : '已撤回' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button v-if="row.status === 'ACTIVE'" link type="danger" size="small" @click="withdraw(row)">
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
        <template #default="{ row }">{{ row.count }} / {{ row.minimumQuantity }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前提交" min-width="120">
        <template #default="{ row }">
          <template v-if="currentSubmissions[row.id]">
            #{{ currentSubmissions[row.id].id }}（{{ currentSubmissions[row.id].materialIds.length }} 项）
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
            :disabled="selectedMaterialsByType(row.typeCode).length === 0"
            @click="submit(row)"
          >
            提交所选（{{ selectedMaterialsByType(row.typeCode).length }}）
          </el-button>
          <el-button
            link
            type="success"
            size="small"
            :disabled="row.status !== 'SATISFIED' && row.status !== 'CONFIRMED'"
            @click="confirm(row)"
          >
            确认
          </el-button>
          <el-button
            v-if="currentSubmissions[row.id]"
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
  getDeliveryTypes,
  listMaterials,
  listRequirements,
  listSubmissions,
  registerMaterial,
  submitDelivery,
  withdrawMaterial,
  withdrawSubmission,
  type DeliveryMaterialVO,
  type DeliveryRequirementVO,
  type DeliverySubmissionVO,
  type DeliveryTypeVO
} from '@/api/pms/platform/delivery'
import { initializeUpload, completeUpload } from '@/api/pms/platform/file'

defineOptions({ name: 'DeliveryPanel' })

const props = defineProps<{ ownerModule: string; entityType: string; entityId: number }>()

const emit = defineEmits<{ (e: 'changed'): void }>()

const loading = ref(false)
const panelError = ref('')
const types = ref<DeliveryTypeVO[]>([])
const requirements = ref<DeliveryRequirementVO[]>([])
const materials = ref<DeliveryMaterialVO[]>([])
const currentSubmissions = ref<Record<number, DeliverySubmissionVO | undefined>>({})
const selectedMaterials = ref<DeliveryMaterialVO[]>([])

const uploadTypeCode = ref('')
const uploadTitle = ref('')
const selectedFile = ref<File>()
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement>()

const enabledTypes = computed(() => types.value.filter((type) => type.enabled))
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

const statusTagType = (status: string) =>
  ({ OPEN: 'warning', SATISFIED: 'success', CONFIRMED: 'success' })[status] ?? 'info'

const errorMessage = (error: any, fallback: string) =>
  error?.response?.data?.msg || error?.message || fallback

const reload = async () => {
  loading.value = true
  panelError.value = ''
  try {
    const [typeList, requirementList, materialList] = await Promise.all([
      getDeliveryTypes(),
      listRequirements(props.ownerModule, props.entityType, props.entityId),
      listMaterials(props.ownerModule, props.entityType, props.entityId)
    ])
    types.value = typeList ?? []
    requirements.value = requirementList ?? []
    materials.value = materialList ?? []
    const submissionEntries = await Promise.all(
      requirements.value.map(async (requirement) => {
        const list = await listSubmissions(requirement.id)
        return [requirement.id, (list ?? []).find((item) => item.status === 'CURRENT')]
      })
    )
    currentSubmissions.value = Object.fromEntries(submissionEntries)
  } catch (error: any) {
    panelError.value = errorMessage(error, '交付件装载失败')
  } finally {
    loading.value = false
  }
}

const onTypeChange = () => {
  if (fileInputRef.value) fileInputRef.value.value = ''
  selectedFile.value = undefined
}

const onFileChange = (event: Event) => {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0]
}

const uploadAndRegister = async () => {
  const type = enabledTypes.value.find((item) => item.typeCode === uploadTypeCode.value)
  const file = selectedFile.value
  if (!type || !file) return
  uploading.value = true
  panelError.value = ''
  try {
    const referenceKey = crypto.randomUUID()
    const initResult = await initializeUpload(
      {
        modeCode: 'CREATE_ARTIFACT',
        ownerContext: 'PLT',
        objectType: 'DELIVERY_MATERIAL',
        objectId: `${props.ownerModule}:${props.entityType}:${props.entityId}`,
        purposeCode: type.typeCode,
        referenceKey,
        fileName: file.name,
        categoryCode: type.category,
        declaredSizeBytes: file.size,
        declaredMediaType: file.type || 'application/octet-stream'
      },
      crypto.randomUUID()
    )
    const completeResult = await completeUpload(
      initResult.artifactId,
      initResult.sessionId,
      file,
      crypto.randomUUID()
    )
    await registerMaterial({
      ownerModule: props.ownerModule,
      entityType: props.entityType,
      entityId: props.entityId,
      typeCode: type.typeCode,
      fileReferenceId: completeResult.referenceId,
      title: uploadTitle.value || undefined
    })
    uploadTitle.value = ''
    if (fileInputRef.value) fileInputRef.value.value = ''
    selectedFile.value = undefined
    await reload()
    emit('changed')
  } catch (error: any) {
    panelError.value = errorMessage(error, '上传或登记失败')
  } finally {
    uploading.value = false
  }
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
