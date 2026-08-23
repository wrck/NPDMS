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
      <el-form-item label="配置编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_ENG_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:eng-configuration:create']"
          ><Icon icon="ep:plus" />新增配置</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="配置编码" min-width="140" />
      <el-table-column prop="equipmentId" label="设备编号" width="100" />
      <el-table-column prop="debugTime" label="调试时间" width="160" />
      <el-table-column prop="debugResult" label="调试结果" min-width="180" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_ENG_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:eng-configuration:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'start')"
            v-hasPermi="['pms:eng-configuration:update']"
            >开始调试</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 1"
            @click="handleAction(row, 'complete')"
            v-hasPermi="['pms:eng-configuration:update']"
            >完成调试</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 0 || row.status === 1"
            @click="handleAction(row, 'markAbnormal')"
            v-hasPermi="['pms:eng-configuration:update']"
            >标记异常</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:eng-configuration:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑配置' : '新增配置'" width="780px">
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
          <el-form-item label="配置编码" prop="code">
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
          <el-form-item label="调试人员" prop="debuggerUserId"><el-input v-model="form.debuggerUserId" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="调试时间" prop="debugTime">
            <el-date-picker v-model="form.debugTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="配置日志" prop="configLogUrl"><UploadFile v-model="form.configLogUrl" /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="调试结果" prop="debugResult">
            <Editor v-model="form.debugResult" height="200px" />
          </el-form-item>
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
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import * as ConfigurationApi from '@/api/pms/engineering/configuration'
import type { ConfigurationVO } from '@/api/pms/engineering/configuration'
import * as ProjectApi from '@/api/pms/project/project'
import * as EquipmentApi from '@/api/pms/asset/equipment'

defineOptions({ name: 'PmsEngConfiguration' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ConfigurationVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: '', code: '', status: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<ConfigurationVO>({ projectId: 0, code: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入配置编码' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await ConfigurationApi.getConfigurationPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: ConfigurationVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      code: '',
      equipmentId: undefined,
      configLogUrl: '',
      debugResult: '',
      debuggerUserId: undefined,
      debugTime: '',
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
    form.id ? await ConfigurationApi.updateConfiguration(form) : await ConfigurationApi.createConfiguration(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ConfigurationVO) => {
  await message.delConfirm()
  await ConfigurationApi.deleteConfiguration(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: ConfigurationVO, action: 'start' | 'complete' | 'markAbnormal') => {
  const actionText = { start: '开始调试', complete: '完成调试', markAbnormal: '标记异常' }[action]
  await message.confirm(`确认${actionText}配置记录【${row.code}】？`)
  if (action === 'start') await ConfigurationApi.startConfiguration(row.id!)
  if (action === 'complete') await ConfigurationApi.completeConfiguration(row.id!)
  if (action === 'markAbnormal') await ConfigurationApi.markAbnormalConfiguration(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
