package org.peoplemesh.docs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationDocumentationCoverageTest {

    private static final Path APPLICATION_PROPERTIES = Path.of("src/main/resources/application.properties");
    private static final Path CONFIGURATION_DOC = Path.of("docs/reference/configuration.md");
    private static final Pattern PROPERTY_KEY_PATTERN =
            Pattern.compile("^\\s*(peoplemesh\\.[a-z0-9.-]+)\\s*=.*$");
    private static final Pattern DOCUMENTED_KEY_PATTERN =
            Pattern.compile("`(peoplemesh\\.[^`\\s]+)`");

    @Test
    void allPeoplemeshPropertyKeysAreDocumented() throws IOException {
        Set<String> propertyKeys = extractPropertyKeys(APPLICATION_PROPERTIES);
        Set<String> documentedKeys = extractDocumentedKeys(CONFIGURATION_DOC);

        Set<String> undocumented = new TreeSet<>(propertyKeys);
        undocumented.removeAll(documentedKeys);

        assertTrue(
                undocumented.isEmpty(),
                () -> "Undocumented peoplemesh.* keys in docs/reference/configuration.md: " + undocumented
        );
    }

    private static Set<String> extractPropertyKeys(Path propertiesPath) throws IOException {
        Set<String> keys = new TreeSet<>();
        for (String line : Files.readAllLines(propertiesPath)) {
            Matcher matcher = PROPERTY_KEY_PATTERN.matcher(line);
            if (matcher.matches()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    private static Set<String> extractDocumentedKeys(Path docPath) throws IOException {
        Set<String> keys = new TreeSet<>();
        String content = Files.readString(docPath);
        Matcher matcher = DOCUMENTED_KEY_PATTERN.matcher(content);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }
}
