<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="项目编号" prop="projectId">
        <PmsEntitySelect
          v-model="query.projectId"
          :api="ProjectApi.getProjectPage"
          label-field="name"
          value-field="id"
          query-field="name"
          placeholder="请选择项目"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item label="联调编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_JOINT_TEST_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:eng-joint-test:create']"
          ><Icon icon="ep:plus" />新增联调</el-button
        >
        <el-button disabled title="外部采集仅保留扩展入口，当前不连接设备">一键收集未接入</el-button>
      </el-form-item>
    </el-form>
    <el-alert title="本页面保存本地联调用例、结果与手工附件；远程配置收集及自动对比未接入，不作为项目联调里程碑完成依据。" type="info" :closable="false" />
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="联调编码" min-width="140" />
      <el-table-column prop="testCase" label="联调用例" min-width="200" show-overflow-tooltip><template #default="{ row }"><div v-dompurify-html="row.testCase" class="max-h-60px overflow-hidden"></div></template></el-table-column>
      <el-table-column prop="equipmentId" label="设备编号" width="100">
        <template #default="{ row }">
          <EquipmentTag :equipment-id="row.equipmentId" />
        </template>
      </el-table-column>
      <el-table-column prop="testTime" label="联调时间" width="160" :formatter="dateFormatter" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_JOINT_TEST_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:eng-joint-test:query']"
            >{{ editableRecord(row) ? '编辑' : '查看' }}</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'start')"
            v-hasPermi="['pms:eng-joint-test:update']"
            >开始联调</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'pass')"
            v-hasPermi="['pms:eng-joint-test:update']"
            >联调通过</el-button
          >
          <el-button
            link
            type="danger"
            v-if="row.status === 1"
            @click="handleFail(row)"
            v-hasPermi="['pms:eng-joint-test:update']"
            >联调失败</el-button
          >
          <el-button v-if="row.status === 0 || row.status === 1" link type="danger" @click="remove(row)" v-hasPermi="['pms:eng-joint-test:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? (readOnly ? '查看联调' : '编辑联调') : '新增联调'" width="min(780px, 95vw)">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="readOnly">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="项目编号" prop="projectId">
            <PmsEntitySelect
              v-model="form.projectId"
              :api="ProjectApi.getProjectPage"
              label-field="name"
              value-field="id"
              query-field="name"
              placeholder="请选择项目"
              :disabled="!!form.id"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联调编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联设备" prop="equipmentId">
            <PmsEntitySelect
              v-model="form.equipmentId"
              :api="EquipmentApi.getEquipmentPage"
              :label-field="['serialNumber', 'name']"
              value-field="id"
              query-field="serialNumber"
              placeholder="请选择设备"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联调人" prop="testerUserId"><el-input v-model="form.testerUserId" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联调时间" prop="testTime">
            <el-date-picker v-model="form.testTime" type="datetime" value-format="x" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="参与方" prop="participants"><el-input v-model="form.participants" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="联调用例" prop="testCase">
            <Editor v-model="form.testCase" height="200px" :readonly="readOnly" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="联调结果" prop="result">
            <Editor v-model="form.result" height="200px" :readonly="readOnly" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="证据附件" prop="evidenceUrl"><UploadFile v-model="form.evidenceUrl!" :disabled="readOnly" :file-type="['log', 'txt', 'cfg', 'conf', 'doc', 'xls', 'ppt', 'pdf']" /></el-form-item>
        </el-col>
        <el-col v-if="form.exceptionRecord" :span="24">
          <el-form-item label="异常记录"><el-input :model-value="form.exceptionRecord" type="textarea" :rows="4" readonly data-testid="joint-test-exception" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input v-model="form.remark" type="textarea" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="formVisible = false">取消</el-button>
      <el-button v-if="!readOnly" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="failVisible" title="联调失败-记录异常" width="min(540px, 95vw)">
    <el-form :model="failForm" label-width="100px">
      <el-form-item label="异常记录" required>
        <el-input v-model="failForm.exceptionRecord" type="textarea" :rows="4" placeholder="失败项不能静默通过，必须记录异常或创建问题单" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="failVisible = false">取消</el-button>
      <el-button type="danger" :loading="saving" @click="confirmFail">确认失败</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as JointTestApi from '@/api/pms/engineering/joint-test'
import type { JointTestVO } from '@/api/pms/engineering/joint-test'
import * as ProjectApi from '@/api/pms/project/project'
import * as EquipmentApi from '@/api/pms/asset/equipment'
import EquipmentTag from '@/components/EquipmentTag/index.vue'
import { checkPermi } from '@/utils/permission'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'PmsEngJointTest' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<JointTestVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: '', code: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
type JointTestForm = Omit<JointTestVO, 'testTime'> & { testTime?: number | string | null }
const form = ref<JointTestForm>({ projectId: 0, code: '', testCase: '', status: 0 })
const editableRecord = (row: Pick<JointTestVO, 'status'>) => (row.status === 0 || row.status === 1) && checkPermi(['pms:eng-joint-test:update'])
const readOnly = computed(() => form.value.id ? !editableRecord(form.value) : !checkPermi(['pms:eng-joint-test:create']))
const rules = {
  projectId: [{ required: true, message: '请选择项目' }, { validator: (_rule: unknown, value: number | string, callback: (error?: Error) => void) => callback(Number(value) > 0 ? undefined : new Error('请选择项目')) }],
  code: [{ required: true, message: '请输入联调编码' }],
  testCase: [{ required: true, message: '请输入联调用例' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await JointTestApi.getJointTestPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: JointTestVO) => {
  form.value = {
      id: undefined,
      projectId: 0,
      code: '',
      testCase: '',
      equipmentId: undefined,
      participants: '',
      testTime: undefined,
      testerUserId: undefined,
      result: '',
      exceptionRecord: '',
      remark: '',
      version: undefined,
      status: 0,
      ...row,
      // Keep UploadFile on the existing string API contract for NULL legacy evidence.
      evidenceUrl: row?.evidenceUrl ?? ''
  }
  formVisible.value = true
}
const save = async () => {
  if (readOnly.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    const data: JointTestVO = { ...form.value, testTime: form.value.testTime == null || form.value.testTime === '' ? undefined : Number(form.value.testTime) }
    data.id ? await JointTestApi.updateJointTest(data) : await JointTestApi.createJointTest(data)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: JointTestVO) => {
  if (row.status === 2 || row.status === 3) return
  await message.delConfirm()
  await JointTestApi.deleteJointTest(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: JointTestVO, action: 'start' | 'pass') => {
  const actionText = { start: '开始联调', pass: '联调通过' }[action]
  await message.confirm(`确认${actionText}记录【${row.code}】？`)
  if (action === 'start') await JointTestApi.startJointTest(row.id!)
  if (action === 'pass') await JointTestApi.passJointTest(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
const failVisible = ref(false)
const failForm = reactive({ id: 0, exceptionRecord: '' })
const handleFail = (row: JointTestVO) => {
  failForm.id = row.id!
  failForm.exceptionRecord = ''
  failVisible.value = true
}
const confirmFail = async () => {
  if (!failForm.exceptionRecord.trim()) {
    message.warning('请输入异常记录')
    return
  }
  saving.value = true
  try {
    await JointTestApi.failJointTest(failForm.id, failForm.exceptionRecord.trim())
    message.success('已记录联调失败')
    failVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
