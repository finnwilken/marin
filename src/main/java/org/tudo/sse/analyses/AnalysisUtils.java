package org.tudo.sse.analyses;

import org.tudo.sse.analyses.config.LibraryAnalysisConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Utility methods for general analysis implementations.
 *
 * @author Johannes Düsing
 */
interface AnalysisUtils {

    LibraryAnalysisConfig getConfig();

    /**
     * Retrieves the initial position to start analysis from, based on the current configuration.
     * @return Initial number of entities to skip before starting
     */
    default long getInitialPosition() {
        final var config = getConfig();

        if(config.progressRestoreFile != null) return getRestoreProgressValue();
        else if(config.hasSkip()) return config.skip;
        else return 0L;
    }

    /**
     * Retrieves the last progress value to start from based on a previous run.
     * @return Progress value
     */
    default long getRestoreProgressValue() {
        BufferedReader indexReader;
        try {
            indexReader = new BufferedReader(new FileReader(getConfig().progressRestoreFile.toFile()));
            String line = indexReader.readLine();
            return Integer.parseInt(line);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the current position to the specified progress output file.
     * @param currentPosition The current progress to write
     */
    default void writePosition(long currentPosition) {
        final var config = getConfig();

        try(BufferedWriter writer = Files.newBufferedWriter(config.progressOutputFile)) {
            writer.write(Long.toString(currentPosition));
        } catch(IOException ignored) {}
    }
}
