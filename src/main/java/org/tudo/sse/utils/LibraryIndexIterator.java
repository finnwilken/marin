package org.tudo.sse.utils;

import org.apache.maven.index.reader.ChunkReader;
import org.apache.maven.index.reader.IndexReader;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class LibraryIndexIterator implements Iterator<String>, AutoCloseable {

    private final Set<Integer> libraryHashesSeen;
    private final Iterator<Map<String, String>> entryIterator;

    private final IndexReader mavenIndexReader;
    private final ChunkReader mavenChunkReader;

    private final Path progressOutputFilePath;

    final long progressSaveInteral;
    private long lastPositionSaved;
    private long currentPosition;

    private String currentLibraryGA;
    private String nextLibraryGA;

    public LibraryIndexIterator(URI baseUri, Path progressOutputFile, long progressSaveInterval) throws IOException {
        this.libraryHashesSeen = new HashSet<>();

        this.progressOutputFilePath = progressOutputFile;
        this.progressSaveInteral = progressSaveInterval;

        // Build intermediate readers that must be kept open until iterator is done and resources can be closed
        this.mavenIndexReader = new IndexReader(null, new HttpResourceHandler(baseUri.resolve(".index/")));
        this.mavenChunkReader = this.mavenIndexReader.iterator().next();

        // Build the actual iterator for entries in the Maven Central index
        this.entryIterator = this.mavenChunkReader.iterator();

        this.lastPositionSaved = -1;
        this.currentPosition = -1;
        this.currentLibraryGA = null;
    }

    public void setIndexPosition(long startIdx){
        Map<String,String> entry;

        do {
            entry = nextEntry();
        } while(this.currentPosition < startIdx);

        this.currentLibraryGA = getGAFromEntry(entry);
    }

    public boolean hasNext() {
        // If currentLibraryGA is null, we have the first hasNext() call after a call to next(). We must find our new
        // currentLibraryGA first.
        if(currentLibraryGA == null){

            // If nextLibraryGA is null, we have no information stored from a previous iteration about the next artifact.
            // We thus must determine the current artifact ourselves and, while we are there, detect the new next artifact
            // as well.
            if(this.nextLibraryGA == null){
                // Walk to the first actual entry
                while(currentLibraryGA == null && entryIterator.hasNext()){
                    currentLibraryGA = getGAFromEntry(nextEntry());
                }
            } else {
                // If we have information about the next artifact, we just copy it
                this.currentLibraryGA = this.nextLibraryGA;
                this.nextLibraryGA = null;
            }

            // Walk up to the next entry that has a different GA (is a different library)
            String nextNewGA = null;

            while(entryIterator.hasNext()){
                final Map<String, String> nextEntry = nextEntry();
                final String nextGA =  getGAFromEntry(nextEntry);

                // Silently ignore malformed entries
                if(nextGA == null) continue;

                if(!currentLibraryGA.equals(nextGA) && isNewLibrary(nextGA)){
                    nextNewGA = nextGA;
                    break;
                }
            }

            this.nextLibraryGA = nextNewGA;
        }

        return this.nextLibraryGA != null;
    }

    public String next() {
        if(hasNext()){
            final String valueToReturn = this.currentLibraryGA;
            this.libraryHashesSeen.add(this.currentLibraryGA.hashCode());
            this.currentLibraryGA = null;
            return valueToReturn;
        } else throw new IllegalStateException("No libraries left on iterator (position " + currentPosition + ")");
    }



    private boolean isNewLibrary(String libraryGA){
        final int hash = libraryGA.hashCode();
        return !libraryHashesSeen.contains(hash);
    }

    private String getGAFromEntry(Map<String, String> entry){
        final String uVal = entry.get("u");
        if(uVal != null){
            final StringBuilder gaBuilder = new StringBuilder();
            boolean patternSeen = false;
            for(char current: uVal.toCharArray()){
                if(current == '|'){
                    if(patternSeen) break;

                    patternSeen = true;
                    gaBuilder.append(':');
                } else {
                    gaBuilder.append(current);
                }
            }

            return gaBuilder.toString();
        } else return null;
    }

    private Map<String, String> nextEntry(){
        writeProgressIfNeeded();
        currentPosition += 1;
        return entryIterator.next();
    }

    private void writeProgressIfNeeded(){
        if(this.currentPosition - this.lastPositionSaved >= this.progressSaveInteral){
            try(BufferedWriter writer = new BufferedWriter(new FileWriter(this.progressOutputFilePath.toFile()))){
                writer.write(String.valueOf(currentPosition));
            } catch(IOException ignored){}
            this.lastPositionSaved = currentPosition;
        }
    }

    @Override
    public void close() throws Exception {
        if(this.mavenChunkReader != null){
            this.mavenChunkReader.close();
        }
        if(this.mavenIndexReader != null) {
            this.mavenIndexReader.close();
        }
    }
}
