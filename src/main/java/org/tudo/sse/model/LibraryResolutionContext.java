package org.tudo.sse.model;

import java.util.ArrayList;
import java.util.List;

public final class LibraryResolutionContext extends ResolutionContext {

    private final String libraryGA;
    private final List<Artifact> libraryArtifacts;

    private LibraryResolutionContext(String libraryGA) {
        this.libraryGA = libraryGA;
        this.libraryArtifacts = new ArrayList<>();
    }

    public static LibraryResolutionContext newInstance(String libraryGA) {
        return new LibraryResolutionContext(libraryGA);
    }

    public void addLibraryArtifact(Artifact a){
        this.libraryArtifacts.add(a);
    }

    public List<Artifact> getLibraryArtifacts(){
        return this.libraryArtifacts;
    }

    public String getLibraryGA() {
        return libraryGA;
    }

}
