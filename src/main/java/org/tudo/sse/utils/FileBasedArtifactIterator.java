package org.tudo.sse.utils;

import org.eclipse.aether.transfer.ArtifactNotFoundException;
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

    /**
     * Checks whether the underlying file exists and contains only valid GAV triples.
     * @throws IOException If accessing the underlying file fails
     * @throws IllegalArgumentException If the underlying file contents are not valid
     */
    public void validateInput() throws IOException, IllegalArgumentException {
        var lines = Files.readAllLines(inputPath);
        int lineCnt = 0;

        for(String line: lines){
            final String[] parts =  line.split(":");
            if(parts.length != 3){
                throw new IllegalArgumentException("Not a valid GAV-Triple: " + line + " (" + inputPath.getFileName() + ":" + lineCnt + ")");
            }

            for(String part: parts){
                if(part.isBlank()){
                    throw new IllegalArgumentException("Not a valid GAV-Triple: " + line + " (" + inputPath.getFileName() + ":" + lineCnt + ")");
                }
            }

            lineCnt += 1;
        }

        this.lineIterator = lines.iterator();
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
