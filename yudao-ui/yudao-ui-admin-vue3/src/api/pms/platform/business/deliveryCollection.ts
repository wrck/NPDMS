import request from '@/config/axios'
import { createAccessTicket, type FileBusinessKey } from '../file'
import type { DeliveryRecord } from '../businessmodel/delivery'
const base = '/api/v1/pms/business-deliverables'
export const listDeliveries = (params: { projectId:string|number; deliverableType?:string; businessType?:string; businessEntityKey?:string|number; pageNo?:number; pageSize?:number }) =>
  request.get<{list:DeliveryRecord[];total:number}>({url:base,params,silentError:true})
export const editDelivery = (row:DeliveryRecord,title:string) => request.put<DeliveryRecord>({url:`${base}/${row.id}`,data:{version:row.version,title},silentError:true})
export const deleteDelivery = (row:DeliveryRecord) => request.delete<boolean>({url:`${base}/${row.id}`,params:{version:row.version},silentError:true})
export const downloadDelivery = async (row:DeliveryRecord) => {
  const key=await request.get<FileBusinessKey>({url:`${base}/${row.id}/file`,silentError:true})
  return createAccessTicket(row.fileArtifactId,row.fileVersionNo,'DOWNLOAD',{ownerContext:key.ownerContext,objectType:key.objectType,objectId:key.objectId,purposeCode:key.purposeCode,referenceKey:key.referenceKey})
}
