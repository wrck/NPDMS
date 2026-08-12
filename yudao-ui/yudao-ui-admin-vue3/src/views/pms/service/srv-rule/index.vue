<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline class="-mb-15px">
      <el-form-item label="规则编码" prop="code">
        <el-input v-model="query.code" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="规则名称" prop="name">
        <el-input v-model="query.name" clearable class="!w-200px" @keyup.enter="load" />
      </el-form-item>
      <el-form-item label="规则类型" prop="ruleType">
        <el-select v-model="query.ruleType" clearable class="!w-140px">
          <el-option
            v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SRV_RULE_TYPE)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-140px">
          <el-option
            v-for="dict in getIntDictOptions(DICT_TYPE.PMS_SRV_RULE_STATUS)"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:search" />查询</el-button>
        <el-button type="primary" @click="openForm()" v-hasPermi="['pms:srv-rule:create']"
          ><Icon icon="ep:plus" />新增规则</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="code" label="规则编码" min-width="140" />
      <el-table-column prop="name" label="规则名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="ruleType" label="类型" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_RULE_TYPE" :value="row.ruleType" />
        </template>
      </el-table-column>
      <el-table-column prop="ruleVersion" label="版本" width="100" />
      <el-table-column prop="effectiveTime" label="生效时间" width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <dict-tag :type="DICT_TYPE.PMS_SRV_RULE_STATUS" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openForm(row)" v-hasPermi="['pms:srv-rule:update']"
            >编辑</el-button
          >
          <el-button
            link
            type="success"
            v-if="row.status === 0"
            @click="handleAction(row, 'publishSrvRule', '发布')"
            v-hasPermi="['pms:srv-rule:update']"
            >发布</el-button
          >
          <el-button
            link
            type="warning"
            v-if="row.status === 1"
            @click="handleAction(row, 'disableSrvRule', '停用')"
            v-hasPermi="['pms:srv-rule:update']"
            >停用</el-button
          >
          <el-button link type="danger" @click="remove(row)" v-hasPermi="['pms:srv-rule:delete']"
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

  <Dialog v-model="formVisible" :title="form.id ? '编辑巡检规则' : '新增巡检规则'" width="780px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="规则编码" prop="code">
            <el-input v-model="form.code" :disabled="!!form.id" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="规则名称" prop="name"><el-input v-model="form.name" /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="规则类型" prop="ruleType">
            <el-select v-model="form.ruleType" class="!w-full">
              <el-option
                v-for="dict in getStrDictOptions(DICT_TYPE.PMS_SRV_RULE_TYPE)"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="规则版本" prop="ruleVersion">
            <el-input v-model="form.ruleVersion" placeholder="如 1.0.0" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="生效时间" prop="effectiveTime">
            <el-date-picker v-model="form.effectiveTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="规则内容" prop="content">
            <Editor v-model="form.content" :height="300" />
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
import { DICT_TYPE, getIntDictOptions, getStrDictOptions } from '@/utils/dict'
import * as SrvRuleApi from '@/api/pms/service/srv-rule'
import type { SrvRuleVO } from '@/api/pms/service/srv-rule'

defineOptions({ name: 'PmsSrvRule' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SrvRuleVO[]>([])
const total = ref(0)
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  code: '',
  name: '',
  ruleType: undefined,
  status: undefined
})
const formVisible = ref(false)
const formRef = ref()
const form = reactive<SrvRuleVO>({ code: '', name: '' })
const rules = {
  code: [{ required: true, message: '请输入规则编码' }],
  name: [{ required: true, message: '请输入规则名称' }],
  ruleType: [{ required: true, message: '请选择规则类型' }]
}

const load = async () => {
  loading.value = true
  try {
    const data = await SrvRuleApi.getSrvRulePage(query)
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const openForm = (row?: SrvRuleVO) => {
  Object.assign(
    form,
    {
      id: undefined,
      code: '',
      name: '',
      ruleType: 'ONLINE',
      ruleVersion: '1.0.0',
      content: '',
      effectiveTime: '',
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
    form.id ? await SrvRuleApi.updateSrvRule(form) : await SrvRuleApi.createSrvRule(form)
    message.success('保存成功')
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: SrvRuleVO) => {
  await message.delConfirm()
  await SrvRuleApi.deleteSrvRule(row.id!)
  message.success('删除成功')
  await load()
}
const handleAction = async (row: SrvRuleVO, action: 'publishSrvRule' | 'disableSrvRule', actionText: string) => {
  await message.confirm(`确认${actionText}规则【${row.code}】？`)
  await (SrvRuleApi as any)[action](row.id!)
  message.success(`${actionText}成功`)
  await load()
}
onMounted(load)
</script>
