package org.tudo.sse.analyses;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import org.tudo.sse.ArtifactFactory;
import org.tudo.sse.CLIException;
import org.tudo.sse.model.Artifact;
import org.tudo.sse.model.ArtifactIdent;
import org.tudo.sse.multithreading.WorkloadIsFinalMessage;
import org.tudo.sse.multithreading.ProcessLibraryMessage;
import org.tudo.sse.multithreading.QueueActor;
import org.tudo.sse.resolution.ResolverFactory;
import org.tudo.sse.resolution.releases.DefaultMavenReleaseListProvider;
import org.tudo.sse.resolution.releases.IReleaseListProvider;
import org.tudo.sse.utils.CLIParser;
import org.tudo.sse.utils.LibraryIndexIterator;
import org.tudo.sse.utils.MavenCentralRepository;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class MavenCentralLibraryAnalysis extends ArtifactAnalysis {

    private final IReleaseListProvider releaseListProvider;
    private final ResolverFactory resolverFactory;

    protected final LibraryAnalysisConfiguration config;

    private ActorRef queueActorRef;

    /**
     * Creates a new Maven Central Analysis with the given configuration options.
     *
     * @param requiresPom         Whether this analysis requires POM information
     * @param requiresTransitives Whether this analysis requires transitive POM information. If true, "requiresPOM" will
     *                            be set to true no matter its given parameter value.
     * @param requiresJar         Whether this analysis requires JAR information
     */
    protected MavenCentralLibraryAnalysis(boolean requiresPom, boolean requiresTransitives, boolean requiresJar) {
        super(false, requiresPom, requiresTransitives, requiresJar);

        this.releaseListProvider = DefaultMavenReleaseListProvider.getInstance();
        this.resolverFactory = new ResolverFactory(processTransitives);
        this.config = new LibraryAnalysisConfiguration();
    }

    @Override
    public final void runAnalysis(String[] args){
        // Obtain CLI arguments
        this.config.parseArguments(args);
        printRunInfo();

        ActorSystem system = null;

        // Get input iterator - this will either be from a configured input file or from the Maven Central Index. The
        // iterator will produce strings of form <GroupID>:<ArtifactID>. If a progress restore file is specified, the
        // progress will be applied to the index iterator automatically.
        Iterator<String> gaIterator = getGaIterator();

        // If there are values to skip, we skip them manually
        if(config.skip > 0){
            log.info("Skipping {} library names", config.skip);
            for(int i = 0; i < config.skip; i++){
                if(!gaIterator.hasNext()){
                    log.warn("No more GA inputs available while skipping to desired position");
                    break;
                } else gaIterator.next();
            }
        }

        // If we want to use multiple threads, we initialize the queue actor that managers workers
        if(this.config.multipleThreads){
            // Get the initial progress so the progress output file is up-to-date
            long initialProgress = gaIterator instanceof LibraryIndexIterator ?
                    ((LibraryIndexIterator)gaIterator).getPosition() : config.skip;
            system = ActorSystem.create("marin-actors");
            this.queueActorRef = system.actorOf(QueueActor.props(config.threadCount, system, initialProgress,
                    config.progressWriteInterval, config.progressOutputFile), "marin-queue-actor");
        }

        long entriesTaken = 0;

        if(config.take >= 0)
            log.info("Taking {} library names", config.take);

        // If specified, we only take the configured amount of entries. If not, we process as long as the iterator
        // provides new entries
        while((config.take < 0 || entriesTaken < config.take) && gaIterator.hasNext()){
            processEntry(gaIterator.next());

            entriesTaken += 1;
        }

        // At this point all libraries are queued (multithreaded) or processed (single-threaded)
        if(config.multipleThreads)
            log.info("Queued a total of {} library names for processing", entriesTaken);
        else
            log.info("Processed a total of {} library names", entriesTaken);

        // Close index iterator if it was used
        if(gaIterator instanceof LibraryIndexIterator){
            LibraryIndexIterator libraryIndexIterator = (LibraryIndexIterator)gaIterator;
            try {
                libraryIndexIterator.close();
            } catch (Exception x) { log.warn("Error closing index iterator: {}", x.getMessage());}
        }

        // Close actor system if it was used
        if(system != null){
            try {
                // Tell the queue that no more work items will follow
                this.queueActorRef.tell(WorkloadIsFinalMessage.getInstance(), ActorRef.noSender());
                system.getWhenTerminated().toCompletableFuture().get();
            } catch (Exception x) { log.warn("Error closing actor system: {}", x.getMessage());}
        }
    }

    /**
     * Main analysis implementation. Defines how a single library shall be analyzed. All artifacts for a given
     * library will have information annotated corresponding to the protected attributes' values.
     * @param libraryGA The library GA tuple (i.e., the library name)
     * @param releases A list of artifacts as ordered by Maven Central's metadata.xml file
     */
    protected abstract void analyzeLibrary(String libraryGA, List<Artifact> releases);

    private void processEntry(String ga){
        final String[] parts = ga.split(":");

        if(parts.length != 2){
            log.warn("Not a valid GA tuple (need <groupID>:<artifactID>): {}", ga);
            return;
        }

        final String groupId = parts[0];
        final String artifactId = parts[1];

        if(!config.multipleThreads){
            process(ga, groupId, artifactId);
        } else {
            final ProcessLibraryMessage msg = new ProcessLibraryMessage(() -> process(ga, groupId, artifactId));
            queueActorRef.tell(msg, ActorRef.noSender());
        }
    }

    private Void process(String ga, String groupId, String artifactId){
        List<ArtifactIdent> identifiers = getReleaseIdentifiers(groupId, artifactId);

        // Abort if we find no releases, error has already been logged at this point
        if(identifiers == null) return null;

        // Resolve all information for all library releases as defined by this analysis
        List<Artifact> libraryArtifacts = new  ArrayList<>();
        for(ArtifactIdent ident : identifiers){
            Artifact a = ArtifactFactory.createArtifact(ident);
            resolveDataAsNeeded(ident);
            libraryArtifacts.add(a);
        }

        try {
            // Call the custom analysis implementation
            analyzeLibrary(ga, libraryArtifacts);
        } catch(Exception x){
            // Whatever the user-defined code does, we do not want to abort!
            log.error("Unknown error in analysis implementation", x);
        }

        return null;
    }

    private List<ArtifactIdent> getReleaseIdentifiers(String groupId, String artifactId){
        try {
            final List<String> libraryVersions = releaseListProvider.getReleases(groupId, artifactId);
            List<ArtifactIdent> identifiers = new ArrayList<>();
            for(String libraryVersion : libraryVersions){
                identifiers.add(new ArtifactIdent(groupId, artifactId, libraryVersion));
            }
            return identifiers;
        } catch (Exception x) {
            log.warn("Failed to obtain version list for library {}:{}", groupId, artifactId, x);
            return null;
        }
    }

    private Iterator<String> getGaIterator(){
        if(config.gaInputListFile != null){
            try {
                final List<String> inputs = Files.readAllLines(config.gaInputListFile);
                log.info("Read {} GAs from input file {}", inputs.size(), config.gaInputListFile);
                return inputs.iterator();
            } catch(IOException iox){
                log.error("Failed to read GA tuples from input file at {}", config.gaInputListFile, iox);
                throw new RuntimeException(iox);
            }
        } else {
            try {
                final LibraryIndexIterator indexIterator = new LibraryIndexIterator(
                        new URI(MavenCentralRepository.RepoBasePath), config.progressOutputFile, config.progressWriteInterval);

                // If we use multiple threads, the queue actor will store progress. If we use a single thread, the
                // iterator must store the progress. This is because we want to store progress after the analysis is
                // actually done, which the iterator cannot know precisely in the multithreaded setting.
                indexIterator.setSaveProgress(!config.multipleThreads);

                if(config.progressRestoreFile != null){
                    final long startingPosition = config.getProgressFromRestoreFile();
                    log.info("Restoring previous progress (index position {})", startingPosition);
                    indexIterator.setPosition(startingPosition);
                }

                return indexIterator;
            } catch (URISyntaxException | IOException iox){
                log.error("Failed to create Maven Central library iterator", iox);
                throw new RuntimeException(iox);
            }

        }
    }

    private void resolveDataAsNeeded(ArtifactIdent identifier){
        if(resolvePom && resolveJar) {
            resolverFactory.runBoth(identifier);
        } else if(resolvePom) {
            resolverFactory.runPom(identifier);
        } else if(resolveJar) {
            resolverFactory.runJar(identifier);
        }
    }

    private void printRunInfo(){
        log.info("Running a Maven Central Library Analysis Implementation:");
        if(resolveIndex) log.info      ("\t - The analysis requires index information");
        if(resolvePom) log.info        ("\t - The analysis requires pom information");
        if(processTransitives) log.info("\t - The analysis requires transitive pom dependencies");
        if(resolveJar)log.info        ("\t - The analysis requires jar information");

        log.info("The current run has been configured as follows:");

        if(config.multipleThreads){
            log.info("\t - Using " + config.threadCount + " threads");
        } else {
            log.info("\t - Using one thread");
        }

        if(config.gaInputListFile == null){
            log.info("\t - Reading libraries from Maven Central index");
            if(config.progressRestoreFile != null) log.info("\t - Restoring last index position from " + config.progressRestoreFile);
            if(config.progressOutputFile != null)  log.info("\t - Writing last index position to " + config.progressOutputFile);
            if(config.skip >= 0)                   log.info("\t - Skipping " + config.skip + " artifacts");
            if(config.take >= 0)                   log.info("\t - Taking " + config.take + " artifacts");
        } else {
            log.info("\t - Reading libraries from GA-list at " + config.gaInputListFile);
        }
    }

    protected static class LibraryAnalysisConfiguration extends CLIParser {

        public long skip;
        public long take;

        public Path gaInputListFile;
        public Path progressOutputFile;
        public Path progressRestoreFile;

        public boolean multipleThreads;
        public int threadCount;

        public int progressWriteInterval;

        public LibraryAnalysisConfiguration() {
            skip = -1L;
            take = -1L;
            gaInputListFile = null;
            progressOutputFile = Paths.get("marin-progress");
            progressRestoreFile = null;
            multipleThreads = false;
            threadCount = 1;
            progressWriteInterval = 100;
        }

        @Override
        public void parseArguments(String[] args) {
            try {
                for(int i = 0; i < args.length; i += 2){
                    switch (args[i]){
                        case "-st":
                            if(skip != -1L)
                                throw new CLIException("Values for skip and take cannot be set twice!");

                            final long[] skipTake = nextArgAsLongPair(args, i);
                            skip = skipTake[0];
                            take = skipTake[1];
                            break;
                        case "--progress-restore-file":
                            if(gaInputListFile != null)
                                throw new CLIException("Cannot restore index position when a custom input list is used!");
                            if(progressRestoreFile != null)
                                throw new CLIException("Progress restore file cannot be set twice!");

                            progressRestoreFile = nextArgAsRegularFileReference(args, i);
                            break;
                        case "--save-progress-interval":
                            progressWriteInterval = nextArgAsInt(args, i);
                            break;
                        case "--progress-output-file":
                            progressOutputFile = nextArgAsPath(args, i);
                            break;
                        case "--coordinates":
                            if(gaInputListFile != null)
                                throw new CLIException("Input file cannot be set twice!");
                            if(progressRestoreFile != null)
                                throw new CLIException("Cannot use custom input list when progress restore file is set!");
                            gaInputListFile = nextArgAsRegularFileReference(args, i);
                            break;
                        case "--multi":
                            final int threads = nextArgAsInt(args, i);
                            multipleThreads = threads > 1;
                            threadCount = threads;
                            break;
                        default:
                            throw new CLIException(args[i]);
                    }
                }
            } catch(CLIException clix){
                throw new RuntimeException(clix);
            }
        }

        long getProgressFromRestoreFile() {
            BufferedReader indexReader;
            try {
                indexReader = new BufferedReader(new FileReader(progressRestoreFile.toFile()));
                String line = indexReader.readLine();
                return Integer.parseInt(line);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
