package com.dp.deviceops.core;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.dp.deviceops.core")
class ArchitectureBoundaryTest {

    @ArchTest
    static final ArchRule coreDoesNotDependOnFrameworks =
            noClasses().should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..", "cn.iocoder..",
                            "com.dp.plat.module.pdp..", "com.baomidou..",
                            "jakarta.persistence..");
}
