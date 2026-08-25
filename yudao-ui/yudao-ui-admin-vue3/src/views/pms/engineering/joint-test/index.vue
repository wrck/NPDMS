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
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="联调编码" min-width="140" />
      <el-table-column prop="testCase" label="联调用例" min-width="200" show-overflow-tooltip />
      <el-table-column prop="equipmentId" label="设备编号" width="100">
        <template #default="{ row }">
          <EquipmentTag :equipment-id="row.equipmentId" />
        </template>
      </el-table-column>
      <el-table-column prop="testTime" label="联调时间" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_JOINT_TEST_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="380" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:eng-joint-test:update']"
            >编辑</el-button
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
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:eng-joint-test:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑联调' : '新增联调'" width="780px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
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
            <el-date-picker v-model="form.testTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="参与方" prop="participants"><el-input v-model="form.participants" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="联调用例" prop="testCase">
            <Editor v-model="form.testCase" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="联调结果" prop="result">
            <Editor v-model="form.result" height="200px" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="证据附件" prop="evidenceUrl"><UploadFile v-model="form.evidenceUrl!" /></el-form-item>
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
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </Dialog>

  <Dialog v-model="failVisible" title="联调失败-记录异常" width="540px">
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
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as JointTestApi from '@/api/pms/engineering/joint-test'
import type { JointTestVO } from '@/api/pms/engineering/joint-test'
import * as ProjectApi from '@/api/pms/project/project'
import * as EquipmentApi from '@/api/pms/asset/equipment'
import EquipmentTag from '@/components/EquipmentTag/index.vue'

defineOptions({ name: 'PmsEngJointTest' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<JointTestVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: '', code: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<JointTestVO>({ projectId: 0, code: '', testCase: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
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
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      code: '',
      testCase: '',
      equipmentId: undefined,
      participants: '',
      testTime: '',
      testerUserId: undefined,
      result: '',
      exceptionRecord: '',
      evidenceUrl: '',
      remark: '',
      version: undefined
    },
    row || {}
  )
  formVisible.value = true
}
const save = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    form.id ? await JointTestApi.updateJointTest(form) : await JointTestApi.createJointTest(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: JointTestVO) => {
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
  if (!failForm.exceptionRecord) {
    message.warning('请输入异常记录')
    return
  }
  saving.value = true
  try {
    await JointTestApi.failJointTest(failForm.id, failForm.exceptionRecord)
    message.success('已记录联调失败')
    failVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
