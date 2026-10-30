package cn.iocoder.yudao.module.pms.platform.api.file.dto;
public record BusinessGrantGeneratedFileCommand(Long tenantId,String operationId,String ownerContext,String objectType,
 Long objectId,Long expectedOwnerVersion,Long grantId,Long issuanceVersion,String purposeCode,String categoryCode,
 String fileName,String mediaType,byte[] content) {
 public BusinessGrantGeneratedFileCommand {
  if(tenantId==null||tenantId<0||operationId==null||operationId.isBlank()||operationId.length()>128
   ||ownerContext==null||ownerContext.isBlank()||objectType==null||objectType.isBlank()||objectId==null||objectId<=0
   ||expectedOwnerVersion==null||expectedOwnerVersion<0||grantId==null||grantId<=0||issuanceVersion==null||issuanceVersion<=0
   ||purposeCode==null||purposeCode.isBlank()||categoryCode==null||categoryCode.isBlank()||fileName==null||fileName.isBlank()
   ||mediaType==null||mediaType.isBlank()||content==null||content.length==0)throw new IllegalArgumentException("Invalid business-grant document command");
  content=content.clone();
 }
 @Override public byte[] content(){return content.clone();}
}
