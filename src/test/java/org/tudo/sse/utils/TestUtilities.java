package org.tudo.sse.utils;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.fail;

public class TestUtilities {

    public static Path testResource(String pathToResource){
        try {
            return Path.of(Objects.requireNonNull(TestUtilities.class.getClassLoader().getResource(pathToResource)).toURI());
        } catch (Exception x){
            fail("Test setup: Failed to load resource file " + pathToResource, x);
        }
        return null;
    }

}
