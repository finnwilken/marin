package org.tudo.sse.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class representing the context of resolving software artifacts. It is used to cache artifacts, so they are unique
 * per resolution context and previously resolved information (index, pom, jar) can be reused.
 *
 * @author Johannes Düsing
 */
public abstract class ResolutionContext {

    /**
     * Create an anonymous context that is neither an ArtifactResolutionContext nor a LibraryResolutionContext.
     *
     * @return new anonymous context instance
     */
    public static ResolutionContext createAnonymousContext(){
        return new ResolutionContext() {};
    }

    /**
     * Map of artifacts resolved in this context.
     */
    protected final Map<ArtifactIdent, Artifact> artifactsResolved;

    /**
     * Builds a new ResolutionContext instance.
     */
    protected ResolutionContext(){
        this.artifactsResolved = new HashMap<>();
    }

    /**
     * Creates a new artifact for the given identifier within the current resolution contexts - or returns the existing
     * artifact if present.
     *
     * @param ident Identifier for which to retrieve an Artifact
     * @return Artifact object that will be unique to the given identifier within this context
     */
    public Artifact createArtifact(ArtifactIdent ident) {
        if(!artifactsResolved.containsKey(ident)){
            final Artifact artifact = new Artifact(ident);
            artifactsResolved.put(ident, artifact);
        }

        return artifactsResolved.get(ident);
    }

    /**
     * Retrieves an Artifact instance for the given identifier only if it already exists within this context.
     * @param ident The identifier for which to retrieve an Artifact
     * @return The Artifact for the given identifier, or null if no such artifact exists in this context.
     */
    public Artifact getArtifact(ArtifactIdent ident) {
        return artifactsResolved.getOrDefault(ident, null);
    }

    /**
     * Returns a set of all artifacts resolved within this context.
     * @return Set of resolved artifacts
     */
    public Set<Artifact> getAllArtifactsResolved(){
        return new HashSet<>(artifactsResolved.values());
    }

}
