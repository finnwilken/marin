package org.tudo.sse.multithreading;

/**
 * A singleton message that is used in multithreaded mode to let the queue manager keep track of the number of completed
 * work items (libraries or artifacts processed).
 */
public class WorkItemFinishedMessage {

    private static final WorkItemFinishedMessage _instance = new WorkItemFinishedMessage();

    private WorkItemFinishedMessage() {}

    /**
     * Retrieves the singleton instance of this message
     * @return The singleton instance
     */
    public static WorkItemFinishedMessage getInstance() {
        return _instance;
    }
}
