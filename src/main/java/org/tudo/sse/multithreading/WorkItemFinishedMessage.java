package org.tudo.sse.multithreading;

public class WorkItemFinishedMessage {

    private static final WorkItemFinishedMessage _instance = new WorkItemFinishedMessage();

    private WorkItemFinishedMessage() {}

    public static WorkItemFinishedMessage getInstance() {
        return _instance;
    }
}
