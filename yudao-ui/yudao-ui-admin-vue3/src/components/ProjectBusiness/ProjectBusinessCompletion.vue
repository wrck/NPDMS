<template>
  <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="refresh">重新检查</el-button></el-alert>
  <el-alert v-else-if="result" :title="result.verdict.satisfied===true?'业务完成条件已满足':result.verdict.satisfied===false?'业务完成条件尚未满足':'业务事实不完整，暂不能判定完成'" :type="result.verdict.satisfied===true?'success':'info'" :closable="false">
    <span v-for="reason in result.verdict.diagnostics" :key="reason">{{reason}}</span><el-button link :loading="loading" @click="refresh">重新检查</el-button>
  </el-alert>
</template>
<script setup lang="ts">
import {ref,watch,onBeforeUnmount} from 'vue'
import type {ProjectBusinessApi,BusinessId,BusinessConditionResult} from '@/api/pms/platform/business'
const props=defineProps<{api:ProjectBusinessApi;entityId:BusinessId;version?:number}>()
const result=ref<BusinessConditionResult|null>(),error=ref(''),loading=ref(false);let generation=0
const refresh=async()=>{const active=++generation;loading.value=true;error.value=''
  try{const value=await props.api.businessCompletion(props.entityId);if(active===generation)result.value=value}
  catch(failure){if(active===generation)error.value=failure instanceof Error?failure.message:'完成条件检查失败'}
  finally{if(active===generation)loading.value=false}
}
watch(()=>[props.api.base,props.entityId,props.version],refresh,{immediate:true});onBeforeUnmount(()=>generation++)
defineExpose({refresh})
</script>
