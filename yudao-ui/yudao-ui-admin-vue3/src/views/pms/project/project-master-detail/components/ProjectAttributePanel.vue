<template>
  <ContentWrap>
    <div class="panel-heading">
      <div>
        <h3>项目属性判定</h3>
        <span>当前值参与模板匹配；调整只追加影响记录，不会重新实例化。</span>
      </div>
      <el-button type="primary" @click="openAdjust" v-hasPermi="['pms:project:classify']">
        <Icon icon="ep:edit" />调整属性
      </el-button>
    </div>

    <el-descriptions :column="descriptionColumns" border size="small">
      <el-descriptions-item label="签约方式">
        <dict-tag
          v-if="project.signingMethod"
          :type="DICT_TYPE.PMS_SIGNING_METHOD"
          :value="project.signingMethod"
        />
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="项目类别">
        <dict-tag
          v-if="project.projectCategory"
          :type="DICT_TYPE.PMS_PROJECT_CATEGORY"
          :value="project.projectCategory"
        />
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="实施方式">
        <dict-tag
          v-if="project.implementationMode"
          :type="DICT_TYPE.PMS_IMPLEMENTATION_METHOD"
          :value="project.implementationMode"
        />
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item label="CRM重大项目级别">
        <dict-tag
          v-if="project.majorProjectLevel"
          :type="DICT_TYPE.PMS_MAJOR_PROJECT_LEVEL"
          :value="project.majorProjectLevel"
        />
        <span v-else>{{ project.sourceType === 'MANUAL' ? '不适用' : '不限' }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="冻结模板">
        #{{ project.lifecycleTemplateId || '-' }} · 修订
        {{ project.lifecycleTemplateRevisionNo || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="项目版本">{{ project.version ?? '-' }}</el-descriptions-item>
    </el-descriptions>
  </ContentWrap>

  <el-drawer v-model="adjustVisible" title="调整项目业务属性" :size="drawerSize" destroy-on-close>
    <el-alert
      title="保存后仅重新判定模板影响，冻结模板和已实例化交付事实保持不变。"
      type="warning"
      :closable="false"
      show-icon
      class="drawer-alert"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="签约方式" prop="signingMethod">
        <el-select v-model="form.signingMethod" :disabled="project.sourceType !== 'MANUAL'">
          <el-option
            v-for="item in signingMethodOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="项目类别" prop="projectCategory">
        <el-select v-model="form.projectCategory">
          <el-option
            v-for="item in projectCategoryOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="实施方式" prop="implementationMode">
        <el-select v-model="form.implementationMode" :disabled="project.sourceType !== 'MANUAL'">
          <el-option
            v-for="item in implementationModeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="调整原因" prop="adjustmentReason">
        <el-input
          v-model="form.adjustmentReason"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="说明本次调整依据"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="adjustVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存并判定影响</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import type { FormInstance, FormRules } from 'element-plus'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { useMessage } from '@/hooks/web/useMessage'
import * as ProjectsApi from '@/api/pms/project/projects'
import type { ProjectMasterVO } from '@/api/pms/project/projects'
import { createSubmissionIdempotencyState } from '../../projects/submissionIdempotency'

defineOptions({ name: 'ProjectAttributePanel' })

const props = defineProps<{ project: ProjectMasterVO }>()
const emit = defineEmits<{ updated: [] }>()
const message = useMessage()
const mobile = useMediaQuery('(max-width: 767px)')
const descriptionColumns = computed(() => (mobile.value ? 1 : 2))
const drawerSize = computed(() => (mobile.value ? '100%' : '520px'))
const signingMethodOptions = computed(() => getStrDictOptions(DICT_TYPE.PMS_SIGNING_METHOD))
const projectCategoryOptions = computed(() => getStrDictOptions(DICT_TYPE.PMS_PROJECT_CATEGORY))
const implementationModeOptions = computed(() =>
  getStrDictOptions(DICT_TYPE.PMS_IMPLEMENTATION_METHOD)
)

const adjustVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const idempotency = createSubmissionIdempotencyState()
const form = reactive<ProjectsApi.ProjectAttributeClassifyReqVO>({
  signingMethod: '',
  projectCategory: '',
  implementationMode: '',
  adjustmentReason: ''
})
const rules: FormRules = {
  signingMethod: [{ required: true, message: '请选择签约方式', trigger: 'change' }],
  projectCategory: [{ required: true, message: '请选择项目类别', trigger: 'change' }],
  implementationMode: [{ required: true, message: '请选择实施方式', trigger: 'change' }],
  adjustmentReason: [
    { required: true, whitespace: true, message: '请输入调整原因', trigger: 'blur' }
  ]
}

const openAdjust = () => {
  Object.assign(form, {
    signingMethod: props.project.signingMethod || '',
    projectCategory: props.project.projectCategory || '',
    implementationMode: props.project.implementationMode || '',
    adjustmentReason: ''
  })
  idempotency.reset()
  adjustVisible.value = true
  formRef.value?.clearValidate()
}

const submit = async () => {
  await formRef.value?.validate()
  if (!props.project.id || props.project.version === undefined) return
  const payload = { ...form, adjustmentReason: form.adjustmentReason.trim() }
  saving.value = true
  try {
    const result = await ProjectsApi.classifyProject(
      props.project.id,
      payload,
      props.project.version,
      idempotency.keyFor(payload)
    )
    message.success(`属性已更新，影响结论：${result.impactResult}`)
    adjustVisible.value = false
    emit('updated')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.panel-heading h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.panel-heading span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.drawer-alert {
  margin-bottom: 16px;
}

:deep(.el-select) {
  width: 100%;
}

@media (width <= 767px) {
  .panel-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .panel-heading .el-button {
    width: 100%;
  }
}
</style>
