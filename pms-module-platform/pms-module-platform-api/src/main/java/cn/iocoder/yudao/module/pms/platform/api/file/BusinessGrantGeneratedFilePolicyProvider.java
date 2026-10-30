package cn.iocoder.yudao.module.pms.platform.api.file;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
public interface BusinessGrantGeneratedFilePolicyProvider {
 String ownerContext(); String objectType();
 Policy lockAndRevalidate(BusinessGrantGeneratedFilePolicyQuery query);
 record Policy(Long grantId,Long issuanceVersion,Long executionUserId,FileBusinessObjectPolicyFact filePolicy) {}
}
