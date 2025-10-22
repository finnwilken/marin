package org.tudo.sse.analyses;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tudo.sse.ArtifactFactory;
import org.tudo.sse.CLIException;
import org.tudo.sse.model.Artifact;
import org.tudo.sse.model.ArtifactIdent;
import org.tudo.sse.model.index.IndexInformation;
import org.tudo.sse.multithreading.ProcessIdentifierMessage;
import org.tudo.sse.multithreading.WorkloadIsFinalMessage;
import org.tudo.sse.resolution.ResolverFactory;
import org.tudo.sse.utils.ArtifactConfigParser;
import org.tudo.sse.utils.IndexIterator;
import org.tudo.sse.multithreading.QueueActor;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The MavenCentralAnalysis enables analysis of artifacts on the maven central repository for jobs of any size.
 * Takes in cli to be configured to the specific job desired.
 */
public abstract class MavenCentralArtifactAnalysis {

    private ArtifactConfigParser.ArtifactConfig artifactConfig;
    private ActorRef queueActorRef;
    private ResolverFactory resolverFactory;

    /**
     * Defines whether this analysis requires artifacts to have index information annotated.
     */
    protected final boolean resolveIndex;

    /**
     * Defines whether this analysis requires artifacts to have pom information annotated.
     */
    protected final boolean resolvePom;

    /**
     * Defines whether this analysis requires artifacts to have resolved transitive pom information.
     */
    protected final boolean processTransitives;

    /**
     * Defines whether this analysis requires artifacts to have jar information annotated.
     */
    protected final boolean resolveJar;


    private static final Logger log = LogManager.getLogger(MavenCentralArtifactAnalysis.class);

    /**
     * Creates a new Maven Central Analysis with the given configuration options.
     *
     * @param requiresIndex Whether this analysis requires index information
     * @param requiresPom Whether this analysis requires POM information
     * @param requiresTransitives Whether this analysis requires transitive POM information. If true, "requiresPOM" will
     *                            be set to true no matter its given parameter value.
     * @param requiresJar Whether this analysis requires JAR information
     */
    protected MavenCentralArtifactAnalysis(boolean requiresIndex,
                                           boolean requiresPom,
                                           boolean requiresTransitives,
                                           boolean requiresJar)  {

        if(!requiresIndex && !requiresPom && !requiresTransitives && !requiresJar){
            log.warn("Potential misconfiguration - no data sources (index, POM, JAR) are required by this analysis");
        } else if(requiresTransitives && !requiresPom){
            log.warn("Potential misconfiguration - analysis requires transitive information but no POM information. " +
                    "POM information will also be collected to provide transitive information.");
        }
        resolveIndex = requiresIndex;
        resolvePom = requiresPom || requiresTransitives; // Cannot have transitive information without POM information
        processTransitives = requiresTransitives;
        resolveJar = requiresJar;
        // Initialize with default config, update later
        artifactConfig = new ArtifactConfigParser.ArtifactConfig();
    }


    /**
     * Main analysis implementation. Defines how a single artifact shall be analyzed. Artifacts will have information
     * annotated corresponding to the protected attributes' values.
     * @param current The artifact to analyze
     */
    public abstract void analyzeArtifact(Artifact current);

    /**
     * Returns the CLI configuration for this analysis.
     * @return CLI information for this analysis
     */
    public ArtifactConfigParser.ArtifactConfig getSetupInfo() {
        return this.artifactConfig;
    }

    private void printRunInfo(){
        log.info("Running a Maven Central Analysis Implementation:");
        if(resolveIndex) log.info      ("\t - The analysis requires index information");
        if(resolvePom) log.info        ("\t - The analysis requires pom information");
        if(processTransitives) log.info("\t - The analysis requires transitive pom dependencies");
        if(resolveJar)log.info        ("\t - The analysis requires jar information");

        log.info("The current run has been configured as follows:");

        if(this.artifactConfig.multipleThreads){
            log.info("\t - Using " + this.artifactConfig.threadCount + " threads");
        } else {
            log.info("\t - Using one thread");
        }

        if(this.artifactConfig.inputListFile == null){
            log.info("\t - Reading artifacts from Maven Central index");
            if(this.artifactConfig.progressRestoreFile != null) log.info("\t - Restoring last index position from " + this.artifactConfig.progressRestoreFile);
            if(this.artifactConfig.progressOutputFile != null)       log.info("\t - Writing last index position to " + this.artifactConfig.progressOutputFile);
            if(this.artifactConfig.skip >= 0)          log.info("\t - Skipping " + this.artifactConfig.skip + " artifacts");
            if(this.artifactConfig.take >= 0)          log.info("\t - Taking " + this.artifactConfig.take + " artifacts");
            if(this.artifactConfig.since >= 0)         log.info("\t - Skipping artifacts before " + this.artifactConfig.since);
            if(this.artifactConfig.until >= 0)         log.info("\t - Taking artifacts until " + this.artifactConfig.until);

        } else {
            log.info("\t - Reading artifacts from GAV-list at " + this.artifactConfig.inputListFile);
        }
    }

    /**
     *  This method is the driver code for the analysis being done on Maven Central.
     *  It can be configured using different command line arguments being passed to it.
     *  There's a single-threaded implementation contained in this class, as well as a multithreaded one called here but defined in different actor classes.
     *
     * @param args cli passed to the program to configure the run
     * @return A map of all artifacts collected during the run
     * @throws URISyntaxException when there is an issue with the url built
     * @throws IOException when there is an issue opening a file
     */
    public final Map<ArtifactIdent, Artifact> runAnalysis(String[] args) throws URISyntaxException, IOException {
        // Obtain CLI arguments
        try {
            this.artifactConfig = new ArtifactConfigParser().parseArtifactConfig(args);
            printRunInfo();
        } catch(CLIException clix){
            throw new RuntimeException(clix);
        }
        
        if(this.artifactConfig.outputEnabled) {
            resolverFactory = new ResolverFactory(this.artifactConfig.outputEnabled, this.artifactConfig.outputDirectory, processTransitives);
        } else {
            resolverFactory = new ResolverFactory(processTransitives);
        }

        if(this.artifactConfig.multipleThreads) {
            ActorSystem system = ActorSystem.create("my-system");
            queueActorRef = system.actorOf(QueueActor.props(this.artifactConfig.threadCount, system, 0,0, null), "queueActor");

            if(this.artifactConfig.inputListFile == null) {
                indexProcessor();
            } else {
                readIdentsIn();
            }

            try {
                system.getWhenTerminated().toCompletableFuture().get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            if(this.artifactConfig.inputListFile == null) {
                indexProcessor();
            } else {
                readIdentsIn();
            }
        }

        return ArtifactFactory.artifacts;
    }

    /**
     * Handles walking the maven central index, choosing how to do so based on the configuration.
     *
     * @throws URISyntaxException when there is an issue with the url built
     * @throws IOException        when there is an issue opening a file
     * @see ArtifactIdent
     */
    public void indexProcessor() throws URISyntaxException, IOException {
        String base = "https://repo1.maven.org/maven2/";
        IndexIterator indexIterator;

        //set up indexIterator here (skip to a position or start from the start)
        if (this.artifactConfig.progressRestoreFile != null) {
            indexIterator = new IndexIterator(new URI(base), getStartingPos());
        } else if(this.artifactConfig.skip != -1) {
            indexIterator = new IndexIterator(new URI(base), this.artifactConfig.skip);
        } else {
            indexIterator = new IndexIterator(new URI(base));
        }

        if (resolveIndex) {
            if (this.artifactConfig.skip != -1 && this.artifactConfig.take != -1) {
                walkPaginated(this.artifactConfig.take, indexIterator);
            } else if (this.artifactConfig.since != -1 && this.artifactConfig.until != -1) {
                walkDates(this.artifactConfig.since, this.artifactConfig.until, indexIterator);
            } else {
                walkAllIndexes(indexIterator);
            }
        } else if (this.artifactConfig.skip != -1 && this.artifactConfig.take != -1) {
            lazyWalkPaginated(this.artifactConfig.take, indexIterator);
        } else if (this.artifactConfig.since != -1 && this.artifactConfig.until != -1) {
            lazyWalkDates(this.artifactConfig.since, this.artifactConfig.until, indexIterator);
        } else {
            lazyWalkAllIndexes(indexIterator);
        }

        writeLastProcessed(indexIterator.getIndex(), this.artifactConfig.progressOutputFile);
    }

    private void processIndex(Artifact current) {
        if(this.artifactConfig.multipleThreads) {
            queueActorRef.tell(new ProcessIdentifierMessage(current.getIdent(), this), ActorRef.noSender());
        } else {
            callResolver(current.getIdent());
            analyzeArtifact(current);
        }
    }

    /**
     * Iterates over all indexes in the maven central index and creates an artifact with the metadata collected.
     *
     * @param indexIterator an iterator for traversing the maven central index
     * @return a list of artifacts containing the maven central index metadata
     * @see Artifact
     * @see IndexInformation
     * @throws IOException when there is an issue opening a file
     */
    public List<Artifact> walkAllIndexes(IndexIterator indexIterator) throws IOException {
        List<Artifact> artifacts = new ArrayList<>();

        while(indexIterator.hasNext()) {
            Artifact current = ArtifactFactory.createArtifact(indexIterator.next());
            artifacts.add(current);
            if(this.artifactConfig.outputEnabled && !resolvePom && !resolveJar) {
                Path filePath = this.artifactConfig.outputDirectory.resolve(current.getIdent().getGroupID() + "-" + current.getIdent().getArtifactID() + "-" + current.getIdent().getVersion() + ".txt");
                if(!Files.exists(filePath)) {
                    Files.createFile(filePath);
                }
            }
            processIndex(current);
            if(indexIterator.getIndex() % this.artifactConfig.progressWriteInterval == 0)
                writeLastProcessed(indexIterator.getIndex(), this.artifactConfig.progressOutputFile);
        }

        if(this.artifactConfig.multipleThreads) {
            queueActorRef.tell(WorkloadIsFinalMessage.getInstance(), ActorRef.noSender());
        }

        indexIterator.closeReader();
        return artifacts;
    }

    /**
     * Iterates over a given number of indexes from the maven central index, and creates an artifact with the metadata collected.
     *
     * @param take number of artifacts from the starting point to capture
     * @param indexIterator an iterator for traversing the maven central index
     * @return a list of artifacts containing the maven central index metadata
     * @see Artifact
     * @see IndexInformation
     * @throws IOException when there is an issue opening a file
     */
    public List<Artifact> walkPaginated(long take, IndexIterator indexIterator) throws IOException {
        List<Artifact> artifacts = new ArrayList<>();

        take += indexIterator.getIndex();
        while(indexIterator.hasNext() && indexIterator.getIndex() < take) {
            Artifact current = ArtifactFactory.createArtifact(indexIterator.next());
            if(this.artifactConfig.outputEnabled && !resolvePom && !resolveJar) {
                Path filePath = this.artifactConfig.outputDirectory.resolve(current.getIdent().getGroupID() + "-" + current.getIdent().getArtifactID() + "-" + current.getIdent().getVersion() + ".txt");
                if(!Files.exists(filePath)) {
                    Files.createFile(filePath);
                }
            }
            artifacts.add(current);
            processIndex(current);
            if(indexIterator.getIndex() % this.artifactConfig.progressWriteInterval == 0)
                writeLastProcessed(indexIterator.getIndex(), this.artifactConfig.progressOutputFile);
        }

        if(this.artifactConfig.multipleThreads) {
            queueActorRef.tell(WorkloadIsFinalMessage.getInstance(), ActorRef.noSender());
        }

        indexIterator.closeReader();
        return artifacts;
    }

    /**
     * Iterates over all indexes in the maven central index.
     * It collects a list of artifacts that are within the range of since and until.
     *
     * @param since lower bound of dates of artifacts to collect
     * @param until upper bound of dates of artifacts to collect
     * @param indexIterator an iterator for traversing the maven central index
     * @return a list of artifacts containing the maven central index metadata
     * @see Artifact
     * @see IndexInformation
     * @throws IOException when there is an issue opening a file
     */
    public List<Artifact> walkDates(long since, long until, IndexIterator indexIterator) throws IOException {
        List<Artifact> artifacts = new ArrayList<>();

        long currentToSince;
        while(indexIterator.hasNext()) {
            IndexInformation temp = indexIterator.next();
            currentToSince = temp.getLastModified();
            if(currentToSince >= since && currentToSince < until) {
                Artifact current = ArtifactFactory.createArtifact(indexIterator.next());
                if(this.artifactConfig.outputEnabled && !resolvePom && !resolveJar) {
                    Path filePath = this.artifactConfig.outputDirectory.resolve(current.getIdent().getGroupID() + "-" + current.getIdent().getArtifactID() + "-" + current.getIdent().getVersion() + ".txt");
                    if(!Files.exists(filePath)) {
                        Files.createFile(filePath);
                    }
                }
                artifacts.add(current);
                processIndex(current);
            }
            if(indexIterator.getIndex() % this.artifactConfig.progressWriteInterval == 0)
                writeLastProcessed(indexIterator.getIndex(), this.artifactConfig.progressOutputFile);
        }

        if(this.artifactConfig.multipleThreads) {
            queueActorRef.tell(WorkloadIsFinalMessage.getInstance(), ActorRef.noSender());
        }

        indexIterator.closeReader();
        return artifacts;
    }

    private void processIndexIdentifier(ArtifactIdent ident) {
        if(this.artifactConfig.multipleThreads){
            queueActorRef.tell(new ProcessIdentifierMessage(ident, this), ActorRef.noSender());
        } else {
            callResolver(ident);
            if(ArtifactFactory.getArtifact(ident) != null) {
                analyzeArtifact(ArtifactFactory.getArtifact(ident));
            }
        }
    }

    /**
     * Iterates over all the indexes in the maven central index and just collects the identifiers
     * @param indexIterator an iterator for traversing the maven central index
     * @return a list of artifact identifiers
     * @see ArtifactIdent
     * @throws IOException when there is an issue opening a file
     */
    public List<ArtifactIdent> lazyWalkAllIndexes(IndexIterator indexIterator) throws IOException {
        List<ArtifactIdent> idents = new ArrayList<>();
        while(indexIterator.hasNext()) {
            ArtifactIdent ident = indexIterator.next().getIdent();
            idents.add(ident);
            if(this.artifactConfig.outputEnabled && !resolvePom && !resolveJar) {
                Path filePath = this.artifactConfig.outputDirectory.resolve(ident.getGroupID() + "-" + ident.getArtifactID() + "-" + ident.getVersion() + ".txt");
                if(!Files.exists(filePath)) {
                    Files.createFile(filePath);
                }
            }
            processIndexIdentifier(ident);
            if(indexIterator.getIndex() % this.artifactConfig.progressWriteInterval == 0)
                writeLastProcessed(indexIterator.getIndex(), this.artifactConfig.progressOutputFile);
        }

        if(this.artifactConfig.multipleThreads) {
            queueActorRef.tell(WorkloadIsFinalMessage.getInstance(), ActorRef.noSender());
        }

        indexIterator.closeReader();
        return idents;
    }

    /**
     * Iterates over a given number of indexes from the maven central index, and collects just the identifiers.
     * @param take how many indexes from the starting position to traverse
     * @param indexIterator an iterator for traversing the maven central index
     * @return a list of artifact identifiers
     * @see ArtifactIdent
     * @throws IOException when there is an issue opening a file
     */
    public List<ArtifactIdent> lazyWalkPaginated(long take, IndexIterator indexIterator) throws IOException{
        List<ArtifactIdent> idents = new ArrayList<>();

        take += indexIterator.getIndex();
        while(indexIterator.hasNext() && indexIterator.getIndex() < take) {
            ArtifactIdent ident = indexIterator.next().getIdent();
            idents.add(ident);
            if(this.artifactConfig.outputEnabled && !resolvePom && !resolveJar) {
                Path filePath = this.artifactConfig.outputDirectory.resolve(ident.getGroupID() + "-" + ident.getArtifactID() + "-" + ident.getVersion() + ".txt");
                if(!Files.exists(filePath)) {
                    Files.createFile(filePath);
                }
            }
            processIndexIdentifier(ident);
            if(indexIterator.getIndex() % this.artifactConfig.progressWriteInterval == 0)
                writeLastProcessed(indexIterator.getIndex(), this.artifactConfig.progressOutputFile);
        }

        if(this.artifactConfig.multipleThreads) {
            queueActorRef.tell(WorkloadIsFinalMessage.getInstance(), ActorRef.noSender());
        }
        indexIterator.closeReader();
        return idents;
    }

    /**
     * Iterates over all index in the maven central index.
     * It collects a list of artifact identifiers that are within the range of since and until.
     *
     * @param since lower bound of dates of artifacts to collect
     * @param until upper bound of dates of artifacts to collect
     * @param indexIterator an iterator for traversing the maven central index
     * @return a list of artifact identifiers within since and until
     * @see ArtifactIdent
     * @throws IOException when there is an issue opening a file
     */
    public List<ArtifactIdent> lazyWalkDates(long since, long until, IndexIterator indexIterator) throws IOException{
        List<ArtifactIdent> idents = new ArrayList<>();

        long currentToSince;
        while(indexIterator.hasNext()) {
            IndexInformation temp = indexIterator.next();
            currentToSince = temp.getLastModified();
            if(currentToSince >= since && currentToSince < until) {
                ArtifactIdent ident = temp.getIdent();
                idents.add(ident);
                if(this.artifactConfig.outputEnabled && !resolvePom && !resolveJar) {
                    Path filePath = this.artifactConfig.outputDirectory.resolve(ident.getGroupID() + "-" + ident.getArtifactID() + "-" + ident.getVersion() + ".txt");
                    if(!Files.exists(filePath)) {
                        Files.createFile(filePath);
                    }
                }
                processIndexIdentifier(ident);
            }
            if(indexIterator.getIndex() % this.artifactConfig.progressWriteInterval == 0)
                writeLastProcessed(indexIterator.getIndex(), this.artifactConfig.progressOutputFile);
        }

        if(this.artifactConfig.multipleThreads) {
            queueActorRef.tell(WorkloadIsFinalMessage.getInstance(), ActorRef.noSender());
        }

        indexIterator.closeReader();

        return idents;
    }

    private void writeLastProcessed(long lastIndexProcessed, Path name) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(name.toFile()));
        writer.write(String.valueOf(lastIndexProcessed));
        writer.close();
    }

    /**
     * Reads in identifiers from a file, using the configuration passed into it.
     *
     * @return a list of identifiers collected from the file
     */
    public List<ArtifactIdent> readIdentsIn() {
        Path toCoordinates = this.artifactConfig.inputListFile;

        BufferedReader coordinatesReader;
        List<ArtifactIdent> identifiers = new ArrayList<>();
        try{
            coordinatesReader = new BufferedReader(new FileReader(toCoordinates.toFile()));
            String line = coordinatesReader.readLine();

            int i = -1;
            if(this.artifactConfig.skip != -1 && this.artifactConfig.take != -1) {
                int toSkip = 0;
                int curTake = 0;
                while(line != null && curTake < this.artifactConfig.take) {
                    if(toSkip >= this.artifactConfig.skip) {
                        String[] parts = line.split(":");
                        if(parts.length == 3) {
                            ArtifactIdent current = new ArtifactIdent(parts[0], parts[1], parts[2]);
                            identifiers.add(current);
                            processIndexIdentifier(current);
                            curTake++;
                        } else {
                            log.error("unable to process Artifact Identifier {} at position {}", line, i);
                        }
                    }
                    line = coordinatesReader.readLine();
                    toSkip++;
                    i++;
                }
            } else if(this.artifactConfig.progressRestoreFile != null) {
                long start = getStartingPos();
                int curPos = 0;
                while(line != null) {
                    if(curPos >= start) {
                        String[] parts = line.split(":");
                        if(parts.length == 3) {
                            ArtifactIdent current = new ArtifactIdent(parts[0], parts[1], parts[2]);
                            identifiers.add(current);
                            processIndexIdentifier(current);
                        } else {
                            log.error("unable to process Artifact Identifier {} at position {}", line, i);
                        }
                    }
                    line = coordinatesReader.readLine();
                    curPos++;
                    i++;
                }
            } else {
                while(line != null) {
                    String[] parts = line.split(":");
                    if(parts.length == 3) {
                        ArtifactIdent current = new ArtifactIdent(parts[0], parts[1], parts[2]);
                        identifiers.add(current);
                        processIndexIdentifier(current);
                    } else {
                        log.error("unable to process Artifact Identifier {} at position {}", line, i);
                    }
                    line = coordinatesReader.readLine();
                    i++;
                }
            }

            if(this.artifactConfig.multipleThreads) {
                queueActorRef.tell(WorkloadIsFinalMessage.getInstance(), ActorRef.noSender());
            }

            writeLastProcessed(i, this.artifactConfig.progressOutputFile);
            coordinatesReader.close();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
        return identifiers;
    }

    private long getStartingPos() {
        BufferedReader indexReader;
        try {
            indexReader = new BufferedReader(new FileReader(this.artifactConfig.progressRestoreFile.toFile()));
            String line = indexReader.readLine();
            return Integer.parseInt(line);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Invokes all resolvers as defined by the analysis configuration to enrich the given artifact identifier.
     * @param identifier Artifact identifier to enrich
     */
    public void callResolver(ArtifactIdent identifier) {
        if(resolvePom && resolveJar) {
            resolverFactory.runBoth(identifier);
        } else if(resolvePom) {
            resolverFactory.runPom(identifier);
        } else if(resolveJar) {
            resolverFactory.runJar(identifier);
        }
    }
}
