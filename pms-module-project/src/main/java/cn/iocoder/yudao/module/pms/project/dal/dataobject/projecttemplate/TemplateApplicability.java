package cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.Set;

/**
 * PM-03 模板适用条件。四个集合彼此独立，不允许互相推导。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateApplicability implements Serializable {

    private Integer schemaVersion;
    private Set<String> signingMethodCodes;
    private Set<String> projectCategoryCodes;
    private Set<String> implementationModeCodes;
    private Set<String> majorProjectLevelCodes;
}
