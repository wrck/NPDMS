<template>
  <ContentWrap>
    <h3>项目交付件归集</h3>
    <el-form inline>
      <el-form-item label="项目"><PmsEntitySelect v-model="projectId" :api="ProjectApi.getProjectPage" label-field="projectName" value-field="id" query-field="projectName" /></el-form-item>
      <el-form-item label="交付件类型"><el-input v-model="deliverableType" clearable aria-label="归集交付件类型" /></el-form-item>
    </el-form>
    <DefaultDeliveryRecords v-if="projectId" :project-id="projectId" :deliverable-type="deliverableType || undefined" readonly />
    <el-empty v-else description="选择项目查看归集记录" />
  </ContentWrap>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import PmsEntitySelect from '@/components/PmsEntitySelect/index.vue'
import * as ProjectApi from '@/api/pms/project/projects'
import DefaultDeliveryRecords from '@/components/BusinessEntity/DefaultDeliveryRecords.vue'
defineOptions({ name: 'DefaultBusinessDeliverables' })
const projectId = ref<string | number>(), deliverableType = ref('')
</script>
