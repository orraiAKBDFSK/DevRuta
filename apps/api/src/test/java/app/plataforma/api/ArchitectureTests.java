package app.plataforma.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Hace cumplir los límites del monolito modular: cada módulo expone solo su paquete raíz
 * (por ejemplo {@code identity}); su subpaquete {@code internal} es privado.
 */
@AnalyzeClasses(packages = "app.plataforma.api", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

	@ArchTest
	static final ArchRule identityInternalsArePrivate = noClasses().that().resideOutsideOfPackage("..identity..")
			.should().dependOnClassesThat().resideInAPackage("..identity.internal..");

	@ArchTest
	static final ArchRule profileInternalsArePrivate = noClasses().that().resideOutsideOfPackage("..profile..")
			.should().dependOnClassesThat().resideInAPackage("..profile.internal..");

	@ArchTest
	static final ArchRule notificationsInternalsArePrivate = noClasses().that().resideOutsideOfPackage("..notifications..")
			.should().dependOnClassesThat().resideInAPackage("..notifications.internal..");

	@ArchTest
	static final ArchRule catalogInternalsArePrivate = noClasses().that().resideOutsideOfPackage("..catalog..")
			.should().dependOnClassesThat().resideInAPackage("..catalog.internal..");

	@ArchTest
	static final ArchRule learningInternalsArePrivate = noClasses().that().resideOutsideOfPackage("..learning..")
			.should().dependOnClassesThat().resideInAPackage("..learning.internal..");

	@ArchTest
	static final ArchRule executionInternalsArePrivate = noClasses().that().resideOutsideOfPackage("..execution..")
			.should().dependOnClassesThat().resideInAPackage("..execution.internal..");

	@ArchTest
	static final ArchRule executionIsIndependentOfOtherModules = noClasses().that().resideInAPackage("..execution..")
			.should().dependOnClassesThat().resideInAnyPackage("..identity..", "..profile..", "..notifications..",
					"..catalog..", "..learning..");

	@ArchTest
	static final ArchRule catalogIsIndependentOfOtherModules = noClasses().that().resideInAPackage("..catalog..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("..identity..", "..profile..", "..notifications..", "..learning..", "..execution..");

	@ArchTest
	static final ArchRule learningUsesOtherModulesOnlyThroughTheirPublicApi = noClasses().that()
			.resideInAPackage("..learning..").should().dependOnClassesThat()
			.resideInAnyPackage("..catalog.internal..", "..identity.internal..", "..profile.internal..", "..notifications..",
					"..execution.internal..");

	@ArchTest
	static final ArchRule identityDoesNotDependOnOtherModules = noClasses().that().resideInAPackage("..identity..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("..profile..", "..notifications..", "..catalog..", "..learning..", "..execution..");

	@ArchTest
	static final ArchRule profileDoesNotDependOnOtherModules = noClasses().that().resideInAPackage("..profile..")
			.should().dependOnClassesThat().resideInAnyPackage("..identity.internal..", "..notifications..");

	@ArchTest
	static final ArchRule platformDoesNotDependOnModules = noClasses().that().resideInAPackage("..platform..")
			.should().dependOnClassesThat()
			.resideInAnyPackage("..identity..", "..profile..", "..notifications..", "..catalog..", "..learning..",
					"..execution..");

}
