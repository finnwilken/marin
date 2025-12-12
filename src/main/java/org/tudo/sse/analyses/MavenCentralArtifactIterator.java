package org.tudo.sse.analyses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tudo.sse.analyses.config.ArtifactAnalysisConfig;
import org.tudo.sse.model.Artifact;
import org.tudo.sse.model.ArtifactResolutionContext;
import org.tudo.sse.model.index.IndexInformation;
import org.tudo.sse.resolution.ResolverFactory;
import org.tudo.sse.analyses.input.FileBasedArtifactIterator;
import org.tudo.sse.utils.IndexIterator;
import org.tudo.sse.utils.MavenCentralRepository;

import java.io.IOException;
import java.util.Iterator;

/**
 * Iterator that produces artifacts that are enriched with index-, pom- or jar information, as configured. This is the
 * iterator equivalent to the {@link MavenCentralArtifactAnalysis}. Only supports single-threaded resolution.
 *
 * @author Johannes Düsing
 */
public class MavenCentralArtifactIterator implements Iterator<Artifact> {

    private final Logger log = LoggerFactory.getLogger(MavenCentralArtifactIterator.class);
    private final ArtifactAnalysisConfig config;

    private Iterator<ArtifactResolutionContext> source;

    private boolean badSource = false;

    private long currentPosition = 0L;
    private long artifactsTaken = 0L;
    private long lastPositionSaved = 0L;

    private final ResolverFactory resolverFactory;
    private final boolean resolvePom;
    private final boolean resolveJar;

    /**
     * Creates a new iterator instance with the given configuration values.
     *
     * @param resolvePom Whether information on artifact pom files shall be resolved
     * @param resolveTransitivePoms Whether transitive poms should also be resolved
     * @param resolveJar Whether information on jar files shall be resolved
     * @param config The analysis configuration, see {@link org.tudo.sse.analyses.config.ArtifactAnalysisConfigBuilder}
     */
    public MavenCentralArtifactIterator(boolean resolvePom, boolean resolveTransitivePoms, boolean resolveJar, ArtifactAnalysisConfig config) {
        this.config = config;
        this.resolvePom = resolvePom;
        this.resolveJar = resolveJar;

        if(this.config.multipleThreads){
            log.warn("Artifact iterator does no support multiple threads - using a single thread for resolution");
        }

        this.setUnderlyingSource();

        if(this.config.outputEnabled){
            this.resolverFactory = new ResolverFactory(true, this.config.outputDirectory, resolveTransitivePoms);
        } else {
            this.resolverFactory = new ResolverFactory(resolveTransitivePoms);
        }

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
        if(badSource) return false;

        // If the config specifies a limited amount of artifacts to take, and that amount is reached, we have no next element
        if(this.config.hasTake() && this.artifactsTaken >= this.config.take)
            return false;

        // Otherwise, we have a next artifact if the underlying source has a next element
        try {
            return this.source.hasNext();
        } catch(Exception x){
            // If accessing the source fails, we report an error and mark the source as bad - also we assume we have no
            // next element.
            log.error("Failed to access source", x);
            this.badSource = true;
            return false;
        }

    }

    @Override
    public Artifact next(){
        if(!hasNext())
            throw new IllegalStateException("No more artifacts available");

        final ArtifactResolutionContext ctx = this.source.next();
        final Artifact artifact = ctx.getRootArtifact();

        if(this.resolvePom){
            resolverFactory.runPom(artifact.getIdent(), ctx);
        }

        if(this.resolveJar){
            resolverFactory.runJar(artifact.getIdent(), ctx);
        }

        this.artifactsTaken += 1L;
        this.currentPosition += 1L;

        writePositionIfNeeded();

        return artifact;
    }

    private void writePositionIfNeeded(){
        if(this.currentPosition - this.lastPositionSaved > this.config.progressWriteInterval){
            AnalysisUtils.writePosition(this.currentPosition, this.config);
            this.lastPositionSaved = this.currentPosition;
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


    private void setUnderlyingSource(){
        if(this.config.hasInputList()){
            try{
                this.source = new MavenCentralCustomListArtifactSource();
            } catch(IOException | IllegalArgumentException x){
                log.error("The given input list file is invalid", x);
                this.badSource = true;
            }
        } else {
            try {
                this.source = new MavenCentralIndexArtifactSource();
            } catch(IOException uix){
                log.error("Failed to access Maven Central Index", uix);
                this.badSource = true;
            }

        }
    }

    private class MavenCentralIndexArtifactSource implements Iterator<ArtifactResolutionContext> {

        private final IndexIterator index;

        private boolean _needsUpdate = true;
        private boolean _hasNext = false;
        private IndexInformation _nextInfo = null;
        private boolean _indexClosed = false;

        MavenCentralIndexArtifactSource() throws IOException {
            this.index = new IndexIterator(MavenCentralRepository.RepoBaseURI);
        }

        private void findNext(){
            _hasNext = false;
            while(!_hasNext && index.hasNext()){
                var currentInfo = index.next();
                if(currentInfo != null && isValidInfo(currentInfo)){
                    _hasNext = true;
                    _nextInfo = currentInfo;
                }
            }
        }

        private boolean isValidInfo(IndexInformation indexInfo){
            if(!config.hasTimeBasedFiltering()) return true;
            else {
                long timeStamp = indexInfo.getLastModified();
                return config.since <= timeStamp && timeStamp <= config.until;
            }
        }

        @Override
        public boolean hasNext() {
            if(_needsUpdate){
                findNext();
                _needsUpdate = false;
            }

            // If we have no more entries on the underlying index, we should close it to release resources
            if(!_hasNext && !_indexClosed){
                try { this.index.closeReader(); }
                catch (IOException ignored) {}
                this._indexClosed = true;
            }

            return _hasNext;
        }

        @Override
        public ArtifactResolutionContext next() {
            if(hasNext()){
                _needsUpdate = true;

                final ArtifactResolutionContext ctx = ArtifactResolutionContext.newInstance(_nextInfo.getIdent());
                ctx.getRootArtifact().setIndexInformation(_nextInfo);

                return ctx;
            } else throw new IllegalStateException("Call to next on empty artifact source");
        }
    }

    private class MavenCentralCustomListArtifactSource implements Iterator<ArtifactResolutionContext> {

        private final FileBasedArtifactIterator list;

        MavenCentralCustomListArtifactSource() throws IOException {
            list = new FileBasedArtifactIterator(config.inputListFile);
            list.validateInput();
        }

        @Override
        public boolean hasNext() {
            return list.hasNext();
        }

        @Override
        public ArtifactResolutionContext next() {
            if(hasNext()){
                return ArtifactResolutionContext.newInstance(list.next());
            } else throw new IllegalStateException("Call to next on empty artifact source");
        }

    }

}
