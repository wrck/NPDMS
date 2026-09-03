package cn.iocoder.yudao.module.pms.platform.api.file;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArchiveReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFileCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFileFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantFilesRevalidationCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadCompleteCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.BusinessGrantUploadInitialized;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedFileFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedFilesRevalidationCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedUploadCompleteCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AuthenticatedAssistedUploadInitialized;

import java.util.List;

public interface FileArtifactApi {

    FileArtifactVersionFact inspect(FileArtifactVersionQuery query);

    FileArtifactVersionFact lockAndRevalidate(FileArtifactVersionRevalidationQuery query);

    List<FileReferenceSetFact> inspectReferenceSets(FileReferenceSetCollectionQuery query);

    List<FileReferenceSetFact> lockAndRevalidateReferenceSets(
            FileReferenceSetCollectionRevalidationQuery query);

    List<FileArtifactVersionFact> attachExistingVersions(AttachExistingFileVersionsCommand command);

    FileArchiveReferenceSetFact archiveReferenceSets(ArchiveFileReferenceSetsCommand command);

    FileArtifactVersionFact createGeneratedBusinessFile(GeneratedBusinessFileCommand command);

    BusinessGrantUploadInitialized initializeBusinessGrantUpload(BusinessGrantUploadInitializeCommand command);

    BusinessGrantFileFact completeBusinessGrantUpload(BusinessGrantUploadCompleteCommand command);

    List<BusinessGrantFileFact> lockAndRevalidateBusinessGrantFiles(
            BusinessGrantFilesRevalidationCommand command);

    AuthenticatedAssistedUploadInitialized initializeAuthenticatedAssistedUpload(
            AuthenticatedAssistedUploadInitializeCommand command);

    AuthenticatedAssistedFileFact completeAuthenticatedAssistedUpload(
            AuthenticatedAssistedUploadCompleteCommand command);

    List<AuthenticatedAssistedFileFact> lockAndRevalidateAuthenticatedAssistedFiles(
            AuthenticatedAssistedFilesRevalidationCommand command);
}
