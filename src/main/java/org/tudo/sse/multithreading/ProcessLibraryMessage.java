package org.tudo.sse.multithreading;

import java.util.function.Supplier;

public class ProcessLibraryMessage implements WorkItem {

    private final Supplier<Void> processEntryCallback;

    public ProcessLibraryMessage(Supplier<Void> callback) {
        this.processEntryCallback = callback;
    }

    public Supplier<Void> getProcessEntryCallback() { return processEntryCallback; }

}
