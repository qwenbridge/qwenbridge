package io.qwenbridge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "io.qwenbridge", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

  @ArchTest
  static final ArchRule controllers_should_not_depend_on_provider_implementations =
      classes()
          .that()
          .resideInAPackage("..api..")
          .should()
          .onlyDependOnClassesThat()
          .resideOutsideOfPackage("..provider.implementation..");

  @ArchTest
  static final ArchRule provider_spi_should_not_depend_on_web_layer =
      classes()
          .that()
          .resideInAPackage("..provider.spi..")
          .should()
          .onlyDependOnClassesThat()
          .resideOutsideOfPackage("..api..");

  @ArchTest
  static final ArchRule layering_should_remain_explicit =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Api")
          .definedBy("io.qwenbridge..api..", "io.qwenbridge.api..")
          .layer("Pipeline")
          .definedBy("io.qwenbridge.pipeline..")
          .layer("DomainServices")
          .definedBy(
              "io.qwenbridge..service..",
              "io.qwenbridge..decision..",
              "io.qwenbridge..intent..",
              "io.qwenbridge..semantic..")
          .layer("Providers")
          .definedBy("io.qwenbridge..provider..")
          .whereLayer("Api")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Providers")
          .mayOnlyBeAccessedByLayers("DomainServices", "Pipeline");
}
