package cn.iocoder.yudao.module.pms.platform.api.file;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantGeneratedFileCommand;
/** Trusted business-grant producer; actual customer action remains distinct from its issuer execution attribution. */
public interface BusinessGrantGeneratedFileApi {
 RegisteredFile create(BusinessGrantGeneratedFileCommand command);
 record RegisteredFile(Long materialId,Long referenceId,Long artifactId,Integer versionNo,String sha256,String fileName,
                       Long grantId,Long issuanceVersion,Long executionUserId) {}
}
