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
      <el-form-item label="资源编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="资源名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="状态" prop="readyStatus">
        <el-select v-model="query.readyStatus" clearable class="!w-160px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_RESOURCE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:eng-resource:create']"
          ><Icon icon="ep:plus" />新增资源</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="资源编码" min-width="140" />
      <el-table-column prop="name" label="资源名称" min-width="180" />
      <el-table-column prop="resourceType" label="资源类型" width="120">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_RESOURCE_TYPE" :value="row.resourceType" />
        </template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="80" />
      <el-table-column prop="readyStatus" label="就绪状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_RESOURCE_STATUS" :value="row.readyStatus" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:eng-resource:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.readyStatus === 0"
            @click="handleAction(row, 'markReady')"
            v-hasPermi="['pms:eng-resource:update']"
            >标记就绪</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.readyStatus === 0 || row.readyStatus === 1"
            @click="handleAction(row, 'markAbnormal')"
            v-hasPermi="['pms:eng-resource:update']"
            >标记异常</el-button
          >
          <el-button
            link
            type="info"
            v-if="row.readyStatus === 1 || row.readyStatus === 2"
            @click="handleAction(row, 'resetToNotReady')"
            v-hasPermi="['pms:eng-resource:update']"
            >重置未就绪</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:eng-resource:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑资源' : '新增资源'" width="680px">
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
          <el-form-item label="资源编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="资源名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="资源类型" prop="resourceType">
            <el-select v-model="form.resourceType" clearable class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_RESOURCE_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
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
          <el-form-item label="数量" prop="quantity"><el-input-number v-model="form.quantity" :min="0" class="!w-full" /></el-form-item>
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
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as ResourceApi from '@/api/pms/engineering/resource'
import type { ResourceReadyVO } from '@/api/pms/engineering/resource'
import * as ProjectApi from '@/api/pms/project/project'
import * as EquipmentApi from '@/api/pms/asset/equipment'

defineOptions({ name: 'PmsEngResource' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<ResourceReadyVO[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, projectId: '', code: '', name: '', readyStatus: undefined })
const formVisible = ref(false)
const formRef = ref()
const form = reactive<ResourceReadyVO>({ projectId: 0, code: '', name: '' })
const rules = {
  projectId: [{ required: true, message: '请选择项目' }],
  code: [{ required: true, message: '请输入资源编码' }],
  name: [{ required: true, message: '请输入资源名称' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await ResourceApi.getResourceReadyPage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: ResourceReadyVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      projectId: 0,
      code: '',
      name: '',
      resourceType: '',
      equipmentId: undefined,
      quantity: undefined,
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
    form.id ? await ResourceApi.updateResourceReady(form) : await ResourceApi.createResourceReady(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ResourceReadyVO) => {
  await message.delConfirm()
  await ResourceApi.deleteResourceReady(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: ResourceReadyVO, action: 'markReady' | 'markAbnormal' | 'resetToNotReady') => {
  const actionText = { markReady: '标记就绪', markAbnormal: '标记异常', resetToNotReady: '重置未就绪' }[action]
  await message.confirm(`确认${actionText}资源【${row.code}】？`)
  if (action === 'markReady') await ResourceApi.markReady(row.id!)
  if (action === 'markAbnormal') await ResourceApi.markAbnormal(row.id!)
  if (action === 'resetToNotReady') await ResourceApi.resetToNotReady(row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
