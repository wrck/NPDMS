import request from '@/config/axios'
import { getDeliveryTypes } from '@/api/pms/platform/delivery'
import { initializeUpload,completeUpload } from '@/api/pms/platform/file'
import { createDeliveryUploadAttempt,type DeliveryUploadAttempt } from '@/components/DeliveryArtifact/uploadDeliveryFile'
export interface CustomerDocumentAttached {version:number;customerPlanUrl:string;materialIds:number[]}
export const saveCustomerSolutionDocuments=async(root:{id:number;version:number},files:File[],previous:DeliveryUploadAttempt[]=[]):Promise<{attached:CustomerDocumentAttached;attempts:DeliveryUploadAttempt[]}>=>{
  if(!root.id||root.version===undefined)throw new Error('请先保存真实方案草稿')
  const type=(await getDeliveryTypes(true)).find(item=>item.typeCode==='IMPLEMENTATION_PLAN')
  if(!type)throw new Error('实施方案交付类型不可用')
  for(let i=0;i<files.length;i++){
    const file=files[i];let attempt=previous[i]
    if(!attempt||attempt.file!==file||attempt.owner.entityId!==root.id){attempt=createDeliveryUploadAttempt({ownerModule:'SOL',entityType:'solution',entityId:root.id},type,file,file.name);previous[i]=attempt}
    if(!attempt.referenceId){
      if(!/\.(docx?|xlsx?|pptx?|txt|pdf)$/i.test(file.name)||file.size<=0||file.size>=5*1024*1024)throw new Error('方案文件格式不支持或大小超过限制')
      const mime:Record<string,string>={doc:'application/msword',docx:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',xls:'application/vnd.ms-excel',xlsx:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',ppt:'application/vnd.ms-powerpoint',pptx:'application/vnd.openxmlformats-officedocument.presentationml.presentation',txt:'text/plain',pdf:'application/pdf'}
      attempt.initialized ||= await initializeUpload({modeCode:'CREATE_ARTIFACT',ownerContext:'PLT',objectType:'DELIVERY_MATERIAL',objectId:`SOL:solution:${root.id}`,purposeCode:'IMPLEMENTATION_PLAN',referenceKey:attempt.referenceKey,fileName:file.name,categoryCode:type.category,declaredSizeBytes:file.size,declaredMediaType:file.type||mime[file.name.split('.').pop()!.toLowerCase()]},attempt.initKey)
      const completed=await completeUpload(attempt.initialized.artifactId,attempt.initialized.sessionId,file,attempt.completeKey);attempt.referenceId=completed.referenceId
    }
  }
  const attached=await request.post<CustomerDocumentAttached>({url:`/api/v1/pms/solutions/${root.id}/customer-files`,data:{expectedVersion:root.version,referenceIds:previous.slice(0,files.length).map(item=>item.referenceId)}})
  return {attached,attempts:previous.slice(0,files.length)}
}
export const resolveCustomerSolutionDocument=async(url:string):Promise<string>=>url.startsWith('/api/v1/pms/solutions/')?await request.get({url}):url
export const downloadCustomerSolutionDocument=async(url:string)=>{const resolved=await resolveCustomerSolutionDocument(url);window.open(resolved,'_blank','noopener')}

// Split the persisted list before decoding a new native locator's display name.
export const customerSolutionDocumentUrls=(value:string):string[]=>value.split(',').filter(Boolean)
export const customerSolutionDocumentName=(url:string):string=>{
  if(!url.startsWith('/api/v1/pms/solutions/'))return ''
  const encoded=url.slice(url.lastIndexOf('/')+1)
  try{return decodeURIComponent(encoded)}catch{return encoded}
}
