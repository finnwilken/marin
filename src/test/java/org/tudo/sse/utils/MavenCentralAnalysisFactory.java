package org.tudo.sse.utils;

import org.tudo.sse.MavenCentralAnalysis;
import org.tudo.sse.analyses.MavenCentralLibraryAnalysis;
import org.tudo.sse.model.Artifact;

import java.util.List;

public class MavenCentralAnalysisFactory {

    public static MavenCentralAnalysis buildEmptyAnalysisWithNoRequirements() {
        return buildAnalysisWithRequirements(false, false, false, false);
    }

    public static MavenCentralAnalysis buildEmptyAnalysisWithPomRequirement() {
        return buildAnalysisWithRequirements(false, true, false, false);
    }

    public static MavenCentralAnalysis buildEmptyAnalysisWithIndexRequirement() {
        return buildAnalysisWithRequirements(true, false, false, false);
    }

    private static MavenCentralAnalysis buildAnalysisWithRequirements(boolean requiresIndex, boolean requiresPom,
                                                                      boolean requiresTransitives, boolean requiresJar) {
        return new MavenCentralAnalysis(requiresIndex, requiresPom, requiresTransitives, requiresJar) {
            @Override
            public void analyzeArtifact(Artifact current) {

            }
        };
    }

    public static MavenCentralLibraryAnalysis buildEmptyLibraryAnalysisWithNoRequirements(){
        return buildLibraryAnalysisWithRequirements(false, false, false);
    }

    private static MavenCentralLibraryAnalysis buildLibraryAnalysisWithRequirements(boolean requiresPom,
                                                                                    boolean requiresTransitives,
                                                                                    boolean requiresJar) {
        return new MavenCentralLibraryAnalysis(requiresPom, requiresTransitives, requiresJar) {
            @Override
            protected void analyzeLibrary(String libraryGA, List<Artifact> releases) {

            }
        };
    }
}
