package org.tudo.sse.analyses;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tudo.sse.model.Artifact;
import org.tudo.sse.utils.MavenCentralAnalysisFactory;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MavenCentralLibraryAnalysisTest {

    final MavenCentralLibraryAnalysis emptyAnalysis =
            MavenCentralAnalysisFactory.buildEmptyLibraryAnalysisWithNoRequirements();

    @Test
    @DisplayName("The CLI parser must parse common argument values correctly")
    void parseCLIRegular(){
        final String validArgs = "-st 20:10000 -ip pom.xml --name marin-progress --multi 12";
        final String[] argsArray = validArgs.split(" ");

        emptyAnalysis.config.parseArguments(argsArray);

        assert(emptyAnalysis.config.multipleThreads);
        assertEquals(12, emptyAnalysis.config.threadCount);
        assertEquals(Paths.get("pom.xml"), emptyAnalysis.config.progressRestoreFile);
        assertEquals(Paths.get("marin-progress"), emptyAnalysis.config.progressOutputFile);
        assertEquals(20, emptyAnalysis.config.skip);
        assertEquals(10000, emptyAnalysis.config.take);
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
        assertNull(emptyAnalysis.config.gaInputListFile);
    }

    @Test
    @DisplayName("The CLI parser must fail if input files do not exist")
    void parseNonExistingFile(){
        final String validArgs = "-ip foo.input";
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
        final String validArgs = "-ip pom.xml --coordinates pom.xml";
        final String[] argsArray = validArgs.split(" ");

        assertThrows(RuntimeException.class, () -> emptyAnalysis.config.parseArguments(argsArray));
    }

    @Test
    void simpleIndexAnalysis(){

        final List<String> librariesHit = new java.util.ArrayList<>(List.of());

        final MavenCentralLibraryAnalysis analysis = new MavenCentralLibraryAnalysis(false, false, false) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {
                assertFalse(releases.isEmpty());
                assertNull(releases.get(0).getIndexInformation());
                assertNull(releases.get(0).getPomInformation());
                assertNull(releases.get(0).getJarInformation());
                System.out.println(libraryGA);
                librariesHit.add(libraryGA);
            }
        };

        final String args = "-st 0:3";
        final String[] argsArray = args.split(" ");

        analysis.runAnalysis(argsArray);

        assertEquals(3, librariesHit.size());
        assert(librariesHit.contains("yom:yom"));

    }

}
