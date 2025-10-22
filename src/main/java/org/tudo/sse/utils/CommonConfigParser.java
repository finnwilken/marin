package org.tudo.sse.utils;

import org.tudo.sse.CLIException;

import java.nio.file.Path;
import java.nio.file.Paths;

public class CommonConfigParser implements CLIParsingUtilities {

    public CommonConfig parseCommonConfig(String[] args) throws CLIException {
        final CommonConfig config = new CommonConfig();


        for(int i = 0; i < args.length; i += 2){
            handleParameter(args, i, config);
        }

        return config;
    }

    protected void handleParameter(String[] args, int i, CommonConfig config) throws CLIException{
        switch (args[i]){
            case "-st":
            case "--skip-take":
                if(config.skip != -1L)
                    throw new CLIException("Values for skip and take cannot be set twice!", args[i]);

                final long[] skipTake = nextArgAsLongPair(args, i);
                config.skip = skipTake[0];
                config.take = skipTake[1];
                break;
            case "-prf":
            case "--progress-restore-file":
                if(config.progressRestoreFile != null)
                    throw new CLIException("Progress restore file cannot be set twice!", args[i]);

                config.progressRestoreFile = nextArgAsRegularFileReference(args, i);
                break;
            case "-spi":
            case "--save-progress-interval":
                config.progressWriteInterval = nextArgAsInt(args, i);
                break;
            case "-pof":
            case "--progress-output-file":
                config.progressOutputFile = nextArgAsPath(args, i);
                break;
            case "-i":
            case "--inputs":
                if(config.inputListFile != null)
                    throw new CLIException("Input file cannot be set twice!", args[i]);
                config.inputListFile = nextArgAsRegularFileReference(args, i);
                break;
            case "-t":
            case "--threads":
                final int threads = nextArgAsInt(args, i);
                config.multipleThreads = threads > 1;
                config.threadCount = threads;
                break;
            default:
                throw new CLIException(args[i]);
        }
    }

    public static class CommonConfig {
        public long skip;
        public long take;

        public Path inputListFile;
        public Path progressOutputFile;
        public Path progressRestoreFile;

        public boolean multipleThreads;
        public int threadCount;

        public int progressWriteInterval;

        public CommonConfig() {
            skip = -1L;
            take = -1L;
            inputListFile = null;
            progressOutputFile = Paths.get("marin-progress");
            progressRestoreFile = null;
            multipleThreads = false;
            threadCount = 1;
            progressWriteInterval = 100;
        }
    }
}
