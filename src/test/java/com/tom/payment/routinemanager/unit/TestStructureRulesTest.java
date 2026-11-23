package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.tom.payment.routinemanager.support.BaseComponentTest;

/**
 * Keeps the suite in its agreed shape (F008, contracts/test-harness.md): test kinds by
 * package and name, one shared Spring context, and no Spring in unit tests.
 */
class TestStructureRulesTest {

    private static final String ROOT = "com.tom.payment.routinemanager";
    private static final Set<String> KIND_PACKAGES = Set.of("component", "unit", "integration", "support");
    private static final List<Class<? extends Annotation>> CONTEXT_ANNOTATIONS = List.of(
            SpringBootTest.class, ActiveProfiles.class, TestPropertySource.class, DirtiesContext.class,
            Import.class, AutoConfigureMockMvc.class);

    @Test
    void everyTestClassFollowsTheRulesOfItsKind() throws Exception {
        List<String> problems = new ArrayList<>();
        int integrationClasses = 0;

        for (Class<?> type : testClasses()) {
            String kind = kindOf(type);
            if (kind == null) {
                problems.add(type.getName() + ": test classes belong in component, unit, integration or support");
                continue;
            }
            // Nested, anonymous and local classes are helpers of their enclosing test.
            if (kind.equals("support") || type.getEnclosingClass() != null || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            String name = type.getSimpleName();
            switch (kind) {
                case "component" -> {
                    requireSuffix(problems, type, "ComponentTest");
                    requireBase(problems, type);
                }
                case "integration" -> {
                    integrationClasses++;
                    requireSuffix(problems, type, "IntegrationTest");
                    requireBase(problems, type);
                }
                case "unit" -> {
                    requireSuffix(problems, type, "Test");
                    if (BaseComponentTest.class.isAssignableFrom(type)) {
                        problems.add(name + ": unit tests must not start the backend");
                    }
                }
                default -> { }
            }
            for (Class<? extends Annotation> annotation : CONTEXT_ANNOTATIONS) {
                // Declared on the class itself; the ones inherited from BaseComponentTest are the point.
                if (type.getDeclaredAnnotation(annotation) != null) {
                    problems.add(name + ": @" + annotation.getSimpleName()
                            + " is not allowed; all Spring test config lives in BaseComponentTest");
                }
            }
            for (Field field : type.getDeclaredFields()) {
                if (field.isAnnotationPresent(MockitoBean.class) || field.isAnnotationPresent(MockitoSpyBean.class)) {
                    problems.add(name + "." + field.getName()
                            + ": mock/spy beans are declared only in BaseComponentTest");
                }
            }
        }
        if (integrationClasses > 10) {
            problems.add("at most 10 integration test classes are allowed, found " + integrationClasses);
        }

        assertTrue(problems.isEmpty(), () -> "Test structure rules broken:\n- " + String.join("\n- ", problems));
    }

    /**
     * A refusal must be checked with BaseComponentTest.error(..), which also checks the
     * error body shape. A bare status().isNotFound() would let a wrong body slip through.
     */
    @Test
    void refusalsAreCheckedWithTheErrorBodyShape() throws IOException {
        Path sources = Path.of("src/test/java", ROOT.replace('.', '/'));
        Pattern bareRefusal = Pattern.compile(
                "status\\(\\)\\s*\\.\\s*is(4xx|5xx|BadRequest|Unauthorized|Forbidden|NotFound|Conflict"
                        + "|MethodNotAllowed|UnprocessableEntity|TooManyRequests|InternalServerError"
                        + "|ServiceUnavailable)\\w*\\(\\)"
                        + "|status\\(\\)\\s*\\.\\s*is\\(\\s*[45]\\d\\d\\s*\\)");
        List<String> problems = new ArrayList<>();
        for (String kind : List.of("component", "integration")) {
            try (Stream<Path> files = Files.walk(sources.resolve(kind))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        if (bareRefusal.matcher(lines.get(i)).find()) {
                            problems.add(file.getFileName() + ":" + (i + 1) + ": use error(HttpStatus.…), "
                                    + "which also checks the error body shape");
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), () -> "Bare refusal status checks:\n- " + String.join("\n- ", problems));
    }

    private static String kindOf(Class<?> type) {
        String pkg = type.getPackageName();
        if (!pkg.startsWith(ROOT + ".")) {
            return null;
        }
        String first = pkg.substring(ROOT.length() + 1).split("\\.")[0];
        return KIND_PACKAGES.contains(first) ? first : null;
    }

    private static void requireSuffix(List<String> problems, Class<?> type, String suffix) {
        if (!type.getSimpleName().endsWith(suffix)) {
            problems.add(type.getSimpleName() + ": name must end with " + suffix);
        }
    }

    private static void requireBase(List<String> problems, Class<?> type) {
        if (!BaseComponentTest.class.isAssignableFrom(type)) {
            problems.add(type.getSimpleName() + ": must extend BaseComponentTest");
        }
    }

    /** Every class compiled from src/test/java under the application package. */
    private static List<Class<?>> testClasses() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return true;
            }
        };
        scanner.addIncludeFilter((reader, factory) -> true);
        String testOutput = TestStructureRulesTest.class.getProtectionDomain().getCodeSource().getLocation().toString();
        List<Class<?>> classes = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents(ROOT)) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            var source = type.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation().toString().equals(testOutput)) {
                classes.add(type);
            }
        }
        return classes;
    }
}
