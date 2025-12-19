package org.tudo.sse.analyses.config.parsing;

import org.tudo.sse.CLIException;
import org.tudo.sse.analyses.config.ArtifactAnalysisConfig;
import org.tudo.sse.analyses.config.ArtifactAnalysisConfigBuilder;
import org.tudo.sse.analyses.config.InvalidConfigurationException;

/**
 * Parser for creating {@link ArtifactAnalysisConfig} objects from CLI parameters.
 * Configuration is obtained by parsing command line arguments provided as an array of strings.
 */
public class ArtifactAnalysisConfigParser extends LibraryAnalysisConfigParser implements CLIParsingUtilities {

    /**
     * Creates a new artifact config parser instance.
     */
    public ArtifactAnalysisConfigParser() { super(); }

    /**
     * Parses a given array containing JVM command line arguments into an {@link org.tudo.sse.analyses.config.ArtifactAnalysisConfig} object. Throws an exception
     * if the given arguments are not valid.
     * @param args Array of command line arguments, as passed by the JVM
     * @return Corresponding {@link ArtifactAnalysisConfig} if parsing was successful
     * @throws CLIException If parsing failed. Contains details about the specific argument that was invalid.
     */
    public ArtifactAnalysisConfig parseArtifactConfig(String[] args) throws CLIException {
        final ArtifactAnalysisConfigBuilder configBuilder = new ArtifactAnalysisConfigBuilder();

        for(int i = 0; i < args.length; i += 2) {
            handleArtifactParameter(args, i, configBuilder);
        }

        return configBuilder.build();
    }


    private void handleArtifactParameter(String[] args, int i, ArtifactAnalysisConfigBuilder configBuilder) throws CLIException {
        try {

            switch(args[i]) {
                case "-su":
                case "--since-until":
                    final long[] sinceUntil = nextArgAsLongPair(args, i);
                    configBuilder.withSinceUtil(sinceUntil[0], sinceUntil[1]);
                    break;
                case "-o":
                case "--output":
                    configBuilder.withOutputDirectory(nextArgAsDirectoryReference(args, i));
                    break;
                default:
                    super.handleParameter(args, i , configBuilder);

            }

        } catch (InvalidConfigurationException icx) {
            CLIException wrapped = new CLIException(icx.getMessage(), icx.getAttributeName());
            wrapped.initCause(icx);
            throw wrapped;
        }
    }

}
