package org.tudo.sse.analyses;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tudo.sse.ArtifactFactory;
import org.tudo.sse.model.Artifact;
import org.tudo.sse.utils.MavenCentralAnalysisFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class MavenCentralLibraryAnalysisTest {

    final MavenCentralLibraryAnalysis emptyAnalysis =
            MavenCentralAnalysisFactory.buildEmptyLibraryAnalysisWithNoRequirements();

    @AfterEach
    public void cleanup(){
        ArtifactFactory.artifacts.clear();
    }

    @Test
    @DisplayName("The CLI parser must parse common argument values correctly")
    void parseCLIRegular(){
        final String validArgs = "-st 20:10000 --progress-restore-file pom.xml --progress-output-file marin-progress --multi 12 --save-progress-interval 20";
        final String[] argsArray = validArgs.split(" ");

        emptyAnalysis.config.parseArguments(argsArray);

        assert(emptyAnalysis.config.multipleThreads);
        assertEquals(12, emptyAnalysis.config.threadCount);
        assertEquals(Paths.get("pom.xml"), emptyAnalysis.config.progressRestoreFile);
        assertEquals(Paths.get("marin-progress"), emptyAnalysis.config.progressOutputFile);
        assertEquals(20, emptyAnalysis.config.skip);
        assertEquals(10000, emptyAnalysis.config.take);
        assertEquals(20, emptyAnalysis.config.progressWriteInterval);
        assertNull(emptyAnalysis.config.gaInputListFile);
    }

    @Test
    @DisplayName("The CLI parser must set the correct defaults for empty arguments")
    void parseCLIDefaults(){
        emptyAnalysis.config.parseArguments(new String[]{});

        assertFalse(emptyAnalysis.config.multipleThreads);
        assertEquals(1, emptyAnalysis.config.threadCount);
        assertNull(emptyAnalysis.config.progressRestoreFile);
        assertEquals(Paths.get("marin-progress"), emptyAnalysis.config.progressOutputFile);
        assertEquals(-1, emptyAnalysis.config.skip);
        assertEquals(-1, emptyAnalysis.config.take);
        assertEquals(100, emptyAnalysis.config.progressWriteInterval);
        assertNull(emptyAnalysis.config.gaInputListFile);
    }

    @Test
    @DisplayName("The CLI parser must fail if input files do not exist")
    void parseNonExistingFile(){
        final String validArgs = "--progress-restore-file foo.input";
        final String[] argsArray = validArgs.split(" ");

        assertThrows(RuntimeException.class, () -> emptyAnalysis.config.parseArguments(argsArray));
    }

    @Test
    @DisplayName("The CLI parser must accept pagination on custom GA input lists")
    void parseCustomGA(){
        final String validArgs = "-st 20:10000 --coordinates pom.xml";
        final String[] argsArray = validArgs.split(" ");

        emptyAnalysis.config.parseArguments(argsArray);

        assertEquals(Paths.get("pom.xml"), emptyAnalysis.config.gaInputListFile);
        assertEquals(20, emptyAnalysis.config.skip);
    }

    @Test
    @DisplayName("The CLI parser must not accept setting an index position on a custom GA input list")
    void parseNonExistingIndex(){
        final String validArgs = "--progress-restore-file pom.xml --coordinates pom.xml";
        final String[] argsArray = validArgs.split(" ");

        assertThrows(RuntimeException.class, () -> emptyAnalysis.config.parseArguments(argsArray));
    }

    @Test
    @DisplayName("An analysis with no requirements must not produce information instances")
    void simpleIndexAnalysis(){

        final List<String> librariesHit = new java.util.ArrayList<>();

        final MavenCentralLibraryAnalysis analysis = new MavenCentralLibraryAnalysis(false, false, false) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {
                assertFalse(releases.isEmpty());
                assertNull(releases.get(0).getIndexInformation());
                assertNull(releases.get(0).getPomInformation());
                assertNull(releases.get(0).getJarInformation());
                librariesHit.add(libraryGA);
            }
        };

        final String args = "-st 0:3";
        final String[] argsArray = args.split(" ");

        analysis.runAnalysis(argsArray);

        assertEquals(3, librariesHit.size());
        assert(librariesHit.contains("yom:yom"));
    }

    @Test
    @DisplayName("An analysis with JAR requirements must produce JAR information instances")
    void simpleIndexWithJarAnalysis(){

        final List<String> librariesHit = new java.util.ArrayList<>();

        final MavenCentralLibraryAnalysis analysis = new MavenCentralLibraryAnalysis(false, false, true) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {
                assertFalse(releases.isEmpty());
                assertNull(releases.get(0).getIndexInformation());
                assertNull(releases.get(0).getPomInformation());
                assertNotNull(releases.get(0).getJarInformation());
                librariesHit.add(libraryGA);
            }
        };

        final String args = "-st 3:3";
        final String[] argsArray = args.split(" ");

        analysis.runAnalysis(argsArray);

        assertEquals(3, librariesHit.size());
        assertFalse(librariesHit.contains("yom:yom"));
        assert(librariesHit.contains("yan:yan"));
    }

    @Test
    @DisplayName("An analysis with POM requirements must produce POM information instances")
    void simpleIndexWithPomAnalysis() throws IOException{

        final List<String> librariesHit = new java.util.ArrayList<>();

        final MavenCentralLibraryAnalysis analysis = new MavenCentralLibraryAnalysis(true, true, false) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {
                assertFalse(releases.isEmpty());
                assertNull(releases.get(0).getIndexInformation());
                assertNotNull(releases.get(0).getPomInformation());
                assertNull(releases.get(0).getJarInformation());
                librariesHit.add(libraryGA);
            }
        };

        final String args = "-st 5000:10 --save-progress-interval 5";
        final String[] argsArray = args.split(" ");

        analysis.runAnalysis(argsArray);

        assertEquals(10, librariesHit.size());
        assertFalse(librariesHit.contains("yom:yom"));
        assertFalse(librariesHit.contains("yan:yan"));

        long progress = Long.parseLong(Files.readString(analysis.config.progressOutputFile));

        assert((progress - 5000) < analysis.config.progressWriteInterval);
    }

    @Test
    @DisplayName("An analysis with multithreading must not miss any libraries")
    void parallelIndexAnalysis() throws IOException{

        final AtomicInteger count = new AtomicInteger(0);

        final MavenCentralLibraryAnalysis analysis = new MavenCentralLibraryAnalysis(false, false, false) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {
                assertFalse(releases.isEmpty());
                assertNull(releases.get(0).getIndexInformation());
                assertNull(releases.get(0).getPomInformation());
                assertNull(releases.get(0).getJarInformation());
                count.incrementAndGet();
            }
        };

        final String args = "-st 5000:500 --multi 8";
        final String[] argsArray = args.split(" ");

        analysis.runAnalysis(argsArray);

        assertEquals(500, count.get());

        long progress = Long.parseLong(Files.readString(analysis.config.progressOutputFile));

        // Skip values must be part of the progress! Last progress write must have been somewhere after 5400 (interval 100)
        assert(progress >= 5400);
    }

    @Test
    @DisplayName("An analysis with input file must not miss any libraries")
    void analysisFromFile() throws IOException {

        final Path validLibraryInput = testResource("library-names-valid.txt");

        assertNotNull(validLibraryInput);
        assert(Files.exists(validLibraryInput));

        final List<String> expectedLibraries = Files.readAllLines(validLibraryInput);

        final List<String> librariesHit = new java.util.ArrayList<>();

        final MavenCentralLibraryAnalysis analysis = new MavenCentralLibraryAnalysis(false, false, false) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {
                assertFalse(releases.isEmpty());
                assertNull(releases.get(0).getIndexInformation());
                assertNull(releases.get(0).getPomInformation());
                assertNull(releases.get(0).getJarInformation());
                librariesHit.add(libraryGA);
            }
        };

        final String args = "--coordinates " + validLibraryInput.toAbsolutePath();
        final String[] argsArray = args.split(" ");

        analysis.runAnalysis(argsArray);

        assertEquals(expectedLibraries, librariesHit);
    }

    @Test
    @DisplayName("An analysis with input file must allow pagination")
    void analysisFromFileWithPagination() throws IOException {

        final Path validLibraryInput = testResource("library-names-valid.txt");

        assertNotNull(validLibraryInput);
        assert(Files.exists(validLibraryInput));

        final List<String> expectedLibraries = Files.readAllLines(validLibraryInput);

        final List<String> librariesHit = new java.util.ArrayList<>();

        final MavenCentralLibraryAnalysis analysis = new MavenCentralLibraryAnalysis(false, false, false) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {
                assertFalse(releases.isEmpty());
                assertNull(releases.get(0).getIndexInformation());
                assertNull(releases.get(0).getPomInformation());
                assertNull(releases.get(0).getJarInformation());
                librariesHit.add(libraryGA);
            }
        };

        final String args = "-st 1:2 --coordinates " + validLibraryInput.toAbsolutePath();
        final String[] argsArray = args.split(" ");

        analysis.runAnalysis(argsArray);

        assertEquals(2, librariesHit.size());
        assert(librariesHit.contains(expectedLibraries.get(1)));
        assert(librariesHit.contains(expectedLibraries.get(2)));
    }

    @Test
    @DisplayName("An analysis with input file must not fail on invalid inputs")
    void analysisFromInvalidFile() throws IOException {

        final Path validLibraryInput = testResource("library-names-invalid.txt");

        assertNotNull(validLibraryInput);
        assert (Files.exists(validLibraryInput));

        final MavenCentralLibraryAnalysis analysis = new MavenCentralLibraryAnalysis(false, false, false) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {
                fail("Analysis should not be executed on invalid input: " + libraryGA);
            }
        };

        final String args = "--coordinates " + validLibraryInput.toAbsolutePath();
        final String[] argsArray = args.split(" ");

        analysis.runAnalysis(argsArray);
    }

    @Test
    @DisplayName("An analysis must apply pagination on top of progress restore values")
    void analysisWithRestoreAndPagination() throws IOException {

        final Path restoreFile = testResource("testingIndexPosition.txt");

        assertNotNull(restoreFile);
        assert (Files.exists(restoreFile));

        final List<String> librariesHit = new java.util.ArrayList<>();

        final MavenCentralLibraryAnalysis analysis = new MavenCentralLibraryAnalysis(false, false, false) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {
                librariesHit.add(libraryGA);
            }
        };

        final String args = "-st 0:5 --progress-restore-file " + restoreFile.toAbsolutePath();
        final String[] argsArray = args.split(" ");

        analysis.runAnalysis(argsArray);

        // When restoring progress - even though we do not skip anything - we do not want to see the first index entry!
        assertEquals(5, librariesHit.size());
        assertFalse(librariesHit.contains("yom:yom"));
    }

    private Path testResource(String pathToResource){
        try {
            return Path.of(getClass().getClassLoader().getResource(pathToResource).toURI());
        } catch (Exception x){
            fail("Test setup: Failed to load resource file " + pathToResource, x);
        }
        return null;
    }

}
