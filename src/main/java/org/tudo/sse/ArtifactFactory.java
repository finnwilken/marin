package org.tudo.sse;

import java.util.HashMap;
import java.util.Map;

import org.opalj.log.GlobalLogContext$;
import org.opalj.log.OPALLogger;
import org.tudo.sse.model.*;
import org.tudo.sse.model.index.IndexInformation;
import org.tudo.sse.model.jar.JarInformation;
import org.tudo.sse.model.pom.PomInformation;
import org.tudo.sse.utils.MarinOpalLogger;

/**
 * The ArtifactFactory handles the creation and storage of artifacts resolved.
 * Using a map double resolutions are avoided and faster retrievals are possible.
 */
public final class ArtifactFactory {

    static {
        // Use global OPAL Logger - will only forward OPAL messages with level error or fatal
        OPALLogger.updateLogger(GlobalLogContext$.MODULE$, MarinOpalLogger.getGlobalLogger());
    }

    private ArtifactFactory() {}

    /**
     * A map that stores all artifacts collected during index, pom, and jar resolution.
     */
    public static final Map<ArtifactIdent, Artifact> artifacts = new HashMap<>();

    /**
     * Creates a new artifact or retrieves an existing one. If a new artifact is created, no information is attached.
     * @param ident The artifact identifier
     * @return The artifact with the given identifier
     */
    public static Artifact createArtifact(ArtifactIdent ident) {
        assert(ident != null);
        if(artifacts.containsKey(ident)) {
            return artifacts.get(ident);
        } else {
            Artifact newArtifact = new Artifact(ident);
            artifacts.put(ident, newArtifact);
            return newArtifact;
        }
    }

    /**
     * This method creates a new Artifact or retrieves it from the map.
     * @param artifactInformation an identifier used to keep track of artifacts
     * @return a newly created or retrieved artifact
     */
    public static Artifact createArtifact(ArtifactInformation artifactInformation) {

        //first check if it exists in artifacts
        if(artifacts.containsKey(artifactInformation.getIdent())) {
            Artifact current = artifacts.get(artifactInformation.getIdent());

            //see if it matches an empty field
            if(current.getIndexInformation() == null && (artifactInformation instanceof IndexInformation)) {
                current.setIndexInformation((IndexInformation) artifactInformation);
            } else if(current.getPomInformation() == null && (artifactInformation instanceof PomInformation)) {
                current.setPomInformation((PomInformation) artifactInformation);
            } else if (current.getJarInformation() == null && (artifactInformation instanceof JarInformation)) {
                current.setJarInformation((JarInformation) artifactInformation);
            }

            //return it
            return current;
        }

        Artifact newArtifact;
        if(artifactInformation instanceof IndexInformation) {
            newArtifact = new Artifact((IndexInformation) artifactInformation);
        } else if (artifactInformation instanceof PomInformation) {
            newArtifact = new Artifact((PomInformation) artifactInformation);
        } else {
            newArtifact = new Artifact((JarInformation) artifactInformation);
        }

        artifacts.put(artifactInformation.getIdent(), newArtifact);
        return newArtifact;
    }

    /**
     * Searches for an artifact with the matching identifier, returning it if found.
     * @param ident The artifact identifier for which to look up an artifact definition
     *
     * @return The artifact object belonging to this identifier, or null if no such object exists
     */
    public static Artifact getArtifact(ArtifactIdent ident) {
        if(artifacts.containsKey(ident)) {
            return artifacts.get(ident);
        }
        return null;
    }
}
