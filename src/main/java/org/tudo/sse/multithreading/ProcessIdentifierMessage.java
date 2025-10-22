package org.tudo.sse.multithreading;

import org.tudo.sse.analyses.MavenCentralArtifactAnalysis;
import org.tudo.sse.model.ArtifactIdent;

/**
 * A message passed to the processing queue to indicate that a given analysis instance must process the given
 * artifact identifier.
 */
public class ProcessIdentifierMessage implements WorkItem {

    private final ArtifactIdent identifier;
    private final MavenCentralArtifactAnalysis instance;

    /**
     * Creates a new message with the given artifact identifier and analysis instance.
     * @param identifier The artifact identifier that must be processed
     * @param instance The analysis instance that must process the identifier
     */
    public ProcessIdentifierMessage(ArtifactIdent identifier, MavenCentralArtifactAnalysis instance) {
        this.identifier = identifier;
        this.instance = instance;
    }

    /**
     * Retrieves the identifier that must be processed
     * @return The artifact identifier
     */
    public ArtifactIdent getIdentifier() {
        return identifier;
    }

    /**
     * Retrieves the analysis instance that must process the identifier
     * @return The analysis instance
     */
    public MavenCentralArtifactAnalysis getInstance() {
        return instance;
    }
}
