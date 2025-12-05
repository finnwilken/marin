package org.tudo.sse.utils;

import org.tudo.sse.CLIException;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Parser for {@link CommonConfig} objects, i.e. configurations of the {@link org.tudo.sse.analyses.MavenCentralLibraryAnalysis}.
 * Configuration is obtained by parsing command line arguments provided as an array of strings.
 */
public class CommonConfigParser implements CLIParsingUtilities {

    /**
     * Creates a new artifact config parsers instance.
     */
    public CommonConfigParser() { super(); }

    /**
     * Parses a given array containing JVM command line arguments into an {@link CommonConfig} objects. Throws an exception
     * if the given arguments are not valid.
     * @param args Array of command line arguments, as passed by the JVM
     * @return Corresponding {@link CommonConfig} if parsing was successful
     * @throws CLIException If parsing failed. Contains details about the specific argument that was invalid.
     */
    public CommonConfig parseCommonConfig(String[] args) throws CLIException {
        final CommonConfig config = new CommonConfig();


        for(int i = 0; i < args.length; i += 2){
            handleParameter(args, i, config);
        }

        return config;
    }

    /**
     * Parses a given parameter from the array of CLI arguments and applies it to the config object.
     * @param args CLI arguments array
     * @param i Current parsing position
     * @param config Current configuration object
     * @throws CLIException If parsing the current argument fails.
     */
    protected void handleParameter(String[] args, int i, CommonConfig config) throws CLIException {
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

    /**
     * Class representing the configuration of a {@link org.tudo.sse.analyses.MavenCentralLibraryAnalysis} instance.
     */
    public static class CommonConfig {

        /**
         * Number of artifacts to skip from the underlying source, or -1 if disabled.
         */
        public long skip;

        /**
         * Number of artifacts to take from the underlying source, or -1 if no limit shall be applied.
         */
        public long take;

        /**
         * Path to read inputs from, instead of accessing the Maven Central index. Null if the index shall be used.
         */
        public Path inputListFile;

        /**
         * File path to output analysis progress to.
         */
        public Path progressOutputFile;

        /**
         * Path to read previous progress from, in order to restore it. Null if disabled.
         */
        public Path progressRestoreFile;

        /**
         * True if multiple threads shall be used, false for single-threaded analysis.
         */
        public boolean multipleThreads;

        /**
         * Number of threads to use.
         */
        public int threadCount;

        /**
         * Interval of entities after which progress shall be saved. Defaults to 100.
         */
        public int progressWriteInterval;

        /**
         * Creates a new configuration with default values.
         */
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
