package cn.iocoder.yudao.module.pms.platform.api.file;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFilePolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFileRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadCompletePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitializePolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadPolicyFact;

public interface FileBusinessObjectPolicyProvider {

    String ownerContext();

    String objectType();

    FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query);

    FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query);

    default FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        throw new UnsupportedOperationException("reference set inspection is not implemented");
    }

    default FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(
            FileBusinessObjectReferenceSetRevalidationQuery query) {
        throw new UnsupportedOperationException("reference set locking is not implemented");
    }

    default FileBusinessObjectPolicyFact lockAndRevalidateGeneratedBusinessFile(
            GeneratedBusinessFilePolicyRevalidationQuery query) {
        throw new UnsupportedOperationException("generated business file policy is not implemented");
    }

    default BusinessGrantUploadPolicyFact initializeBusinessGrantUploadPolicy(
            BusinessGrantUploadInitializePolicyQuery query) {
        throw new UnsupportedOperationException("business grant upload initialization is not implemented");
    }

    default BusinessGrantUploadPolicyFact lockAndRevalidateBusinessGrantUpload(
            BusinessGrantUploadCompletePolicyQuery query) {
        throw new UnsupportedOperationException("business grant upload completion is not implemented");
    }

    default BusinessGrantUploadPolicyFact lockAndRevalidateBusinessGrantFiles(
            BusinessGrantFileRevalidationQuery query) {
        throw new UnsupportedOperationException("business grant file revalidation is not implemented");
    }
}
