package org.tudo.sse.analyses.config;

import java.nio.file.Path;

/**
 * Class representing the configuration of a {@link org.tudo.sse.analyses.MavenCentralArtifactAnalysis} instance.
 *
 * @author Johannes Düsing
 */
public class ArtifactAnalysisConfig extends LibraryAnalysisConfig {

    static final long DEFAULT_VALUE_SINCE = -1L;
    static final long DEFAULT_VALUE_UNTIL = -1L;
    static final Path DEFAULT_VALUE_OUTPUT = null;

    /**
     * Timestamp before which artifacts shall be excluded from analysis, or -1 if disabled.
     */
    public long since;

    /**
     * Timestamp after which artifacts shall be excluded from analysis, or -1 if disabled.
     */
    public long until;

    /**
     * Directory to write file outputs to, or null if disabled.
     */
    public Path outputDirectory;

    /**
     * Whether files shall be written to the output directory.
     */
    public boolean outputEnabled;

    /**
     * Creates a new configuration object with default values.
     */
    ArtifactAnalysisConfig(){
        super();

        since = DEFAULT_VALUE_SINCE;
        until = DEFAULT_VALUE_UNTIL;
        outputEnabled = false;
        outputDirectory = DEFAULT_VALUE_OUTPUT;
    }


}
