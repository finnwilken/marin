package org.tudo.sse.model.resolution;

import org.tudo.sse.model.ArtifactIdent;

public class ArtifactResolutionContext extends ResolutionContext {

    private final ArtifactIdent identifier;

    private ArtifactResolutionContext(ArtifactIdent identifier) {
        this.identifier = identifier;
    }

    public static ArtifactResolutionContext newInstance(ArtifactIdent identifier) {
        return new ArtifactResolutionContext(identifier);
    }

    public ArtifactIdent getIdentifier() {
        return identifier;
    }

}
