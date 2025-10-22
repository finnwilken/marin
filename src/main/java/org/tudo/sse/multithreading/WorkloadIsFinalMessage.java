package org.tudo.sse.multithreading;

/**
 * This class is a marker message sent to the queue actor. It signals that no more work items will be scheduled in the
 * current analysis run.
 */
public class WorkloadIsFinalMessage {

    private static WorkloadIsFinalMessage _instance;

    private WorkloadIsFinalMessage() {}


    public static WorkloadIsFinalMessage getInstance(){
        if(_instance == null) _instance = new WorkloadIsFinalMessage();
        return _instance;
    }
}
