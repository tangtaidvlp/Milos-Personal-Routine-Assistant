package com.tom.payment.routinemanager.support;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * Picks the test profiles from the {@code test.db} system property, which
 * build.gradle sets from -PtestDb (default h2). An IDE run without the
 * property uses H2.
 */
public class TestDatabaseProfiles implements ActiveProfilesResolver {

    public static final String PROPERTY = "test.db";

    @Override
    public String[] resolve(Class<?> testClass) {
        String db = System.getProperty(PROPERTY, "h2");
        return switch (db) {
            case "h2" -> new String[] {"test", "test-h2"};
            case "postgres" -> new String[] {"test", "test-postgres"};
            default -> throw new IllegalStateException(
                    PROPERTY + " must be h2 or postgres, got '" + db + "'");
        };
    }
}
