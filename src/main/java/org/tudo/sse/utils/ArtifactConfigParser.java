package org.tudo.sse.utils;

import org.tudo.sse.CLIException;

import java.nio.file.Path;

public class ArtifactConfigParser extends CommonConfigParser implements CLIParsingUtilities {

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

    public static class ArtifactConfig extends CommonConfig {

        public long since;
        public long until;

        public Path outputDirectory;
        public boolean outputEnabled;

        public ArtifactConfig(){
            super();

            since = -1L;
            until = -1L;
            outputEnabled = false;
            outputDirectory = null;
        }


    }

}
