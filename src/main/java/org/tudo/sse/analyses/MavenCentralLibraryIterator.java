package org.tudo.sse.analyses;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.tudo.sse.analyses.config.LibraryAnalysisConfig;
import org.tudo.sse.analyses.input.FileBasedLibraryIterator;
import org.tudo.sse.model.Artifact;
import org.tudo.sse.model.ArtifactIdent;
import org.tudo.sse.model.LibraryResolutionContext;
import org.tudo.sse.resolution.ResolverFactory;
import org.tudo.sse.resolution.releases.DefaultMavenReleaseListProvider;
import org.tudo.sse.resolution.releases.IReleaseListProvider;
import org.tudo.sse.utils.LibraryIndexIterator;
import org.tudo.sse.utils.MavenCentralRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Iterator that produces libraries that are enriched with index-, pom- or jar information, as configured. This is the
 * iterator equivalent to the {@link MavenCentralLibraryAnalysis}. Only supports single-threaded resolution.
 *
 * @author Johannes Düsing
 */
public class MavenCentralLibraryIterator implements Iterator<LibraryResolutionContext> {

    private final Logger log = LoggerFactory.getLogger(MavenCentralLibraryIterator.class);
    private final LibraryAnalysisConfig config;

    private Iterator<String> source;

    private boolean badSource = false;
    private boolean sourceClosed = false;

    private long currentPosition = 0L;
    private long librariesTaken = 0L;
    private long lastPositionSaved = 0L;

    private final ResolverFactory resolverFactory;
    private final boolean resolvePom;
    private final boolean resolveJar;

    private final IReleaseListProvider releaseListProvider;
    private Consumer<FailedLibraryEvent> failedLibraryCallback = null;

    /**
     * Creates a new iterator instance with the given configuration values.
     * @param resolvePom Whether information on artifact pom files shall be resolved
     * @param resolveTransitivePoms Whether transitive poms should also be resolved
     * @param resolveJar Whether information on jar files shall be resolved
     * @param config The analysis configuration, see {@link org.tudo.sse.analyses.config.LibraryAnalysisConfigBuilder}
     */
    public MavenCentralLibraryIterator(boolean resolvePom,
                                       boolean resolveTransitivePoms,
                                       boolean resolveJar,
                                       LibraryAnalysisConfig config) {
        this.config = config;
        this.resolvePom = resolvePom;
        this.resolveJar = resolveJar;

        if(this.config.multipleThreads){
            log.warn("Library iterator does no support multiple threads - using a single thread for resolution");
        }

        this.setUnderlyingSource();

        this.resolverFactory = new ResolverFactory(resolveTransitivePoms);
        this.releaseListProvider = DefaultMavenReleaseListProvider.getInstance();

        try {
            if(!this.badSource)
                this.skipInitial();
        } catch(Exception x){
            log.error("Failed to initialize iterator", x);
            this.badSource = true;
        }
    }

    @Override
    public boolean hasNext() {
        // If the underlying source is invalid, we have no next element
        if(this.badSource) return false;

        // If the config specifies a limited amount of libraries to take, and that amount is reached, we have no next element
        if(this.config.hasTake() && this.librariesTaken >= this.config.take){
            closeSource();
            return false;
        }

        // Otherwise, we have a next library if the underlying source has a next element
        try {
            boolean hasNext = this.source.hasNext();

            if(!hasNext)
                closeSource();

            return hasNext;
        } catch(Exception x){
            // If accessing the source fails, we report an error and mark the source as bad - also we assume we have no
            // next element.
            log.error("Failed to access source", x);
            this.badSource = true;
            return false;
        }
    }

    @Override
    public LibraryResolutionContext next() {
        if(!hasNext())
            throw new IllegalStateException("No more libraries available");

        final String nextGA = this.source.next();
        final String[] gaParts = nextGA.split(":"); // Valid tuple, ensured by source
        final LibraryResolutionContext ctx = LibraryResolutionContext.newInstance(nextGA);

        // Get list of all library releases
        final List<ArtifactIdent> allReleases = getReleaseIdentifiers(gaParts[0], gaParts[1]);

        if(allReleases != null){
            // For each release, build artifact and enrich with data
            for(ArtifactIdent release : allReleases){
                final Artifact artifact = ctx.createArtifact(release);
                ctx.addLibraryArtifact(artifact);

                if(this.resolvePom){
                    resolverFactory.runPom(artifact.getIdent(), ctx);
                }

                if(this.resolveJar){
                    resolverFactory.runJar(artifact.getIdent(), ctx);
                }
            }
        } else {
            // If we failed to obtain a release list, the respective callback has been invoked. Here, we just return an
            // empty context.
            log.warn("No releases for library {}, returning empty context", nextGA);
        }

        this.librariesTaken += 1L;
        this.currentPosition += 1L;

        writePositionIfNeeded();

        return ctx;
    }

    /**
     * Sets the callback that is invoked when obtaining a release list for a given library fails.
     * @param failedLibraryCallback Callback on failed libraries
     */
    public void setFailedLibraryCallback(Consumer<FailedLibraryEvent> failedLibraryCallback){
        this.failedLibraryCallback = failedLibraryCallback;
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

            if(this.failedLibraryCallback != null){
                final var event = new FailedLibraryEvent(groupId, artifactId, x);
                try { this.failedLibraryCallback.accept(event); } catch (Exception ex) {
                    log.warn("Unexcepted exception in failed analysis callback for library {}:{}", groupId, artifactId, ex);
                }
            }

            return null;
        }
    }

    private void skipInitial(){
        final long entitiesToSkip = AnalysisUtils.getInitialPosition(this.config);

        if(entitiesToSkip > 0L && this.badSource)
            return;

        for(int i = 0; i < entitiesToSkip && this.source.hasNext(); i++){
            this.source.next();
            this.currentPosition += 1L;
        }

        log.debug("Successfully skipped to position {}", this.currentPosition);

        if(!this.source.hasNext()){
            log.warn("Reached end of input source while skipping to position {}", entitiesToSkip);
        }
    }

    private void writePositionIfNeeded(){
        if(this.currentPosition - this.lastPositionSaved > this.config.progressWriteInterval){
            AnalysisUtils.writePosition(this.currentPosition, this.config);
            this.lastPositionSaved = this.currentPosition;
        }
    }

    private void setUnderlyingSource(){
        if(this.config.hasInputList()){
            try {
                var iterator = new FileBasedLibraryIterator(this.config.inputListFile);
                iterator.validateInput();
                this.source = iterator;
            } catch (IOException | IllegalArgumentException x){
                log.error("The given input list file is invalid", x);
                this.badSource = true;
            }
        } else {
            try {
                this.source = new LibraryIndexIterator(MavenCentralRepository.RepoBaseURI);
            } catch (IOException iox){
                log.error("Failed to access Maven Central Index", iox);
                this.badSource = true;
            }

        }
    }

    private void closeSource(){
        if(!this.sourceClosed && !this.badSource && this.source instanceof AutoCloseable){
            try {((AutoCloseable)this.source).close();}
            catch (Exception ignored){}
            this.sourceClosed = true;
        }
    }

    /**
     * Event that is thrown when obtaining a library's release list fails.
     */
    public static class FailedLibraryEvent {

        private final String groupId;
        private final String artifactId;
        private final Throwable cause;

        /**
         * Creates a new event instance.
         * @param groupId The library group id
         * @param artifactId The library artifact id
         * @param cause The throwable that caused the failure
         */
        FailedLibraryEvent(String groupId, String artifactId, Throwable cause){
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.cause = cause;
        }

        /**
         * Get the failed library's groupID
         * @return Maven groupID
         */
        public String getGroupId() {
            return this.groupId;
        }

        /**
         * Get the failed library's artifactID
         * @return Maven artifactID
         */
        public String getArtifactId() {
            return this.artifactId;
        }

        /**
         * Get the failure cause
         * @return Throwable that caused the failure
         */
        public Throwable getCause() {
            return this.cause;
        }
    }

}
