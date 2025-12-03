package org.tudo.sse.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class ResolutionContext {

    public static ResolutionContext createAnonymousContext(){
        return new ResolutionContext() {};
    }

    protected Map<ArtifactIdent, Artifact> artifactsResolved = new HashMap<>();

    public Artifact createArtifact(ArtifactIdent ident) {
        if(!artifactsResolved.containsKey(ident)){
            final Artifact artifact = new Artifact(ident);
            artifactsResolved.put(ident, artifact);
        }

        return artifactsResolved.get(ident);
    }

    public Artifact getArtifact(ArtifactIdent ident) {
        return artifactsResolved.getOrDefault(ident, null);
    }

    public Set<Artifact> getAllArtifactsResolved(){
        return new HashSet<>(artifactsResolved.values());
    }

}
