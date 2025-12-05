package org.tudo.sse.utils;

import org.tudo.sse.CLIException;

import java.nio.file.Path;

/**
 * Parser for ArtifactConfig objects, i.e. configurations of the {@link org.tudo.sse.analyses.MavenCentralArtifactAnalysis}.
 * Configuration is obtained by parsing command line arguments provided as an array of strings.
 */
public class ArtifactConfigParser extends CommonConfigParser implements CLIParsingUtilities {

    /**
     * Creates a new artifact config parser instance.
     */
    public ArtifactConfigParser() { super(); }

    /**
     * Parses a given array containing JVM command line arguments into an {@link ArtifactConfig} object. Throws an exception
     * if the given arguments are not valid.
     * @param args Array of command line arguments, as passed by the JVM
     * @return Corresponding {@link ArtifactConfig} if parsing was successful
     * @throws CLIException If parsing failed. Contains details about the specific argument that was invalid.
     */
    public ArtifactConfig parseArtifactConfig(String[] args) throws CLIException {
        final ArtifactConfig artifactConfig = new ArtifactConfig();

        for(int i = 0; i < args.length; i += 2) {
            handleArtifactParameter(args, i, artifactConfig);
        }

        return artifactConfig;
    }


    private void handleArtifactParameter(String[] args, int i, ArtifactConfig config) throws CLIException {
        switch(args[i]) {
            case "-su":
            case "--since-until":
                if(config.skip != -1L)
                    throw new CLIException("Cannot apply time-based filtering when pagination (-st) is used", args[i]);
                if(config.since != -1L)
                    throw new CLIException("Values for since and until cannot be set twice!", args[i]);
                if(config.inputListFile != null)
                    throw new CLIException("Cannot apply time-based filtering when input list (-i) is used", args[i]);

                final long[] sinceUntil = nextArgAsLongPair(args, i);
                config.since = sinceUntil[0];
                config.until = sinceUntil[1];
                break;

            case "-o":
            case "--output":
                config.outputEnabled = true;
                config.outputDirectory = nextArgAsDirectoryReference(args, i);
                break;

            case "-st":
            case "--skip-take":
                if(config.since != -1L)
                    throw new CLIException("Cannot apply pagination when time-based filtering (-su) is used", args[i]);
                super.handleParameter(args, i, config);
                break;

            case "-i":
            case "--inputs":
                if(config.since != -1L)
                    throw new CLIException("Cannot use input list when time-based filtering (-su) is applied", args[i]);
                super.handleParameter(args, i, config);
                break;

            default:
                super.handleParameter(args, i , config);

        }
    }

    /**
     * Class representing the configuration of a {@link org.tudo.sse.analyses.MavenCentralArtifactAnalysis} instance.
     */
    public static class ArtifactConfig extends CommonConfig {

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
        public ArtifactConfig(){
            super();

            since = -1L;
            until = -1L;
            outputEnabled = false;
            outputDirectory = null;
        }


    }

}
