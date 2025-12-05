package org.tudo.sse.utils;

import org.tudo.sse.model.ArtifactIdent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Iterator that reads Maven Central artifact identifiers from a text file. Expects one colon-separated triple of
 * groupID:artifactID:version per line.
 */
public class FileBasedArtifactIterator implements Iterator<ArtifactIdent> {

    private final Path inputPath;

    private Iterator<String> lineIterator;

    /**
     * Creates a new iterator instance for the given file path.
     * @param input Path to a text file containing GAV triples
     */
    public FileBasedArtifactIterator(Path input){
        this.inputPath = input;
    }

    @Override
    public boolean hasNext() {
        if(this.lineIterator == null){
            try {
                var lines = Files.readAllLines(this.inputPath);
                this.lineIterator = lines.iterator();
            } catch (IOException iox){
                throw new IllegalArgumentException("Failed to access input file", iox);
            }
        }

        return this.lineIterator.hasNext();
    }

    @Override
    public ArtifactIdent next() {
        String line = this.lineIterator.next();

        String[] parts = line.split(":");
        if(parts.length == 3) {
            return new ArtifactIdent(parts[0], parts[1], parts[2]);
        } else {
            throw new IllegalStateException("Not a valid GAV-Triple: " + line);
        }
    }
}
