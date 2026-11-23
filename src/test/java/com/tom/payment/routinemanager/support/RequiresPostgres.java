package com.tom.payment.routinemanager.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * For integration tests of behaviour only PostgreSQL has. They run with
 * -PtestDb=postgres (always in CI) and are reported as skipped on H2.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@EnabledIfSystemProperty(named = TestDatabaseProfiles.PROPERTY, matches = "postgres",
        disabledReason = "needs -PtestDb=postgres")
public @interface RequiresPostgres {
}
