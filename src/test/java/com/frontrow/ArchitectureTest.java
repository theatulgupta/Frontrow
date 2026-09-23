package com.frontrow;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.frontrow", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule catalogIsIsolated = noClasses()
            .that().resideInAPackage("com.frontrow.catalog..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.frontrow.booking..",
                    "com.frontrow.payment..",
                    "com.frontrow.risk..",
                    "com.frontrow.outbox..",
                    "com.frontrow.expiry..");

    @ArchTest
    static final ArchRule inventoryIsAWriteGate = noClasses()
            .that().resideInAPackage("com.frontrow.inventory..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.frontrow.payment..",
                    "com.frontrow.risk..",
                    "com.frontrow.outbox..",
                    "com.frontrow.booking..");

    @ArchTest
    static final ArchRule riskStaysOffTheSalePath = noClasses()
            .that().resideInAPackage("com.frontrow.risk..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.frontrow.booking..",
                    "com.frontrow.payment..",
                    "com.frontrow.outbox..");

    @ArchTest
    static final ArchRule paymentDoesNotCallTheApiOrRisk = noClasses()
            .that().resideInAPackage("com.frontrow.payment..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.frontrow.booking.api..",
                    "com.frontrow.risk..");

    @ArchTest
    static final ArchRule identityHasNoDomainDependencies = noClasses()
            .that().resideInAPackage("com.frontrow.identity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.frontrow.booking..",
                    "com.frontrow.payment..",
                    "com.frontrow.catalog..");
}
