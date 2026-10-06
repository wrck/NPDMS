<template>
  <el-upload v-model:file-list="uploadFiles" :auto-upload="false" :limit="5" :disabled="readonly" :on-change="changed" :on-remove="removed" :on-exceed="exceeded">
    <el-button :disabled="readonly">选择方案文件</el-button>
    <template #tip><div class="el-upload__tip">保存草稿时上传并登记交付件；支持 doc / xls / ppt / txt / pdf，每个文件小于 5 MB。</div></template>
  </el-upload>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import type { UploadFile, UploadFiles, UploadUserFile, UploadRawFile } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
const props=defineProps<{readonly?:boolean}>()
const files=defineModel<File[]>({required:true})
const message=useMessage()
const uploadFiles=ref<UploadUserFile[]>([])
watch(files, value=>{uploadFiles.value=value.map(file=>({name:file.name,raw:file as UploadRawFile,uid:(file as UploadRawFile).uid,status:'ready'}))},{immediate:true})
const valid=(file:File)=>/\.(docx?|xlsx?|pptx?|txt|pdf)$/i.test(file.name)&&file.size>0&&file.size<5*1024*1024
const changed=(file:UploadFile,list:UploadFiles)=>{if(props.readonly)return;if(file.raw&&!valid(file.raw)){list.splice(list.indexOf(file),1);message.error('方案文件格式不支持或大小超过限制');return}files.value=list.flatMap(item=>item.raw?[item.raw]:[])}
const removed=(_file:UploadFile,list:UploadFiles)=>{if(!props.readonly)files.value=list.flatMap(item=>item.raw?[item.raw]:[])}
const exceeded=()=>message.error('最多选择5个方案文件')
</script>
