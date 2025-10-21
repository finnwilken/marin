package org.tudo.sse.multithreading;

import akka.actor.*;
import akka.japi.pf.ReceiveBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class represents the processing queue that resolves jobs and sends them to different threads.
 * The size of the jobs and threads are determined by the configuration set in CliInformation.
 * @see org.tudo.sse.CliInformation
 */
public class QueueActor extends AbstractActor {

    private final int numResolverActors;
    private final AtomicInteger curNumResolvers;
    private final Queue<WorkItem> jobQueue;
    private boolean indexFinished = false;
    private final ActorSystem system;

    private final AtomicLong workItemsCompleted = new AtomicLong(0L);
    private final long progressSaveInterval;
    private long lastProgressSaved = 0L;
    private final Path progressOutputFilePath;

    private static final Logger log = LogManager.getLogger(QueueActor.class);

    /**
     * Creates a new processing queue with the given number of actors and the given actor system.
     * @param numResolverActors Number of ResolverActor instances that will process jobs
     * @param system The underlying ActorSystem
     * @param initialWorkItemPosition The number of work items that have been skipped due to progress restore or skip
     * @param progressSaveInterval The number of completed work items after which to save progress
     * @param progressOutputFilePath The file path to which to write progress to
     */
    public QueueActor(int numResolverActors, ActorSystem system, long initialWorkItemPosition, long progressSaveInterval, Path progressOutputFilePath) {
        this.numResolverActors = numResolverActors;
        this.system = system;
        this.curNumResolvers = new AtomicInteger(0);
        this.jobQueue = new LinkedList<>();

        this.progressSaveInterval = progressSaveInterval;
        this.progressOutputFilePath = progressOutputFilePath;
        this.workItemsCompleted.set(initialWorkItemPosition);
    }

    /**
     * Creates the properties needed to initialize an actor instance of this queue
     * @param numResolverActors The number of ResolverActor instances that shall be used to process jobs
     * @param system The underlying ActorSystem
     * @param initialWorkItemPosition The number of work items that have been skipped due to progress restore or skip
     * @param progressSaveInterval The number of completed work items after which to save progress
     * @param progressOutputFilePath The file path to which to write progress to
     * @return The AKKA actor properties
     */
    public static Props props(int numResolverActors, ActorSystem system, long initialWorkItemPosition, long progressSaveInterval, Path progressOutputFilePath) {
        return Props.create(QueueActor.class, () -> new QueueActor(numResolverActors, system, initialWorkItemPosition, progressSaveInterval, progressOutputFilePath));
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ProcessIdentifierMessage.class, this::forwardToResolvers)
                .match(ProcessLibraryMessage.class, this::forwardToResolvers)
                .match(String.class, message -> {
                    synchronized (jobQueue){
                        if(!jobQueue.isEmpty()) {
                            getSender().tell(jobQueue.peek(), getSelf());
                            jobQueue.remove();
                            if(jobQueue.size() % 10 == 0) log.trace("Distributed a job, queue size " + jobQueue.size());
                        } else {
                            synchronized(curNumResolvers) {
                                if(indexFinished && curNumResolvers.get() == 1) {
                                    log.trace("Shutting down system");
                                    system.terminate();
                                } else {
                                    log.trace("Killing a worker thread");
                                    getSender().tell(PoisonPill.getInstance(), getSelf());
                                    curNumResolvers.decrementAndGet();
                                }
                            }
                        }
                    }

                })
                .match(WorkItemFinishedMessage.class, workItemFinishedMessage -> {
                    // Track completion of work items
                    workItemsCompleted.incrementAndGet();
                    // Write progress file if needed
                    writeProgressIfNeeded();

                    // Distribute next job to worker that is now free, or kill worker if no jobs are left
                    synchronized (jobQueue){
                        if(!jobQueue.isEmpty()) {
                            getSender().tell(jobQueue.remove(), getSelf());
                            if(jobQueue.size() % 10 == 0) log.trace("Distributed a job, queue size " + jobQueue.size());
                        } else {
                            synchronized(curNumResolvers) {
                                if(indexFinished && curNumResolvers.get() == 1) {
                                    log.trace("Shutting down system");
                                    system.terminate();
                                } else {
                                    log.trace("Killing a worker thread");
                                    getSender().tell(PoisonPill.getInstance(), getSelf());
                                    curNumResolvers.decrementAndGet();
                                }
                            }
                        }
                    }
                })
                .match(WorkloadIsFinalMessage.class, workloadIsFinalMessage -> {
                    indexFinished = true;
                    if(curNumResolvers.get() == 0) {
                        system.terminate();
                    }
                })
                .build();
    }

    private void forwardToResolvers(WorkItem message){
        synchronized (curNumResolvers){
            if(curNumResolvers.get() < numResolverActors) {
                ActorRef processor = getContext().actorOf(ResolverActor.props());
                processor.tell(message, getSelf());
                log.info("New resolver created");
                curNumResolvers.incrementAndGet();
            } else {
                jobQueue.add(message);
            }
        }
    }

    private void writeProgressIfNeeded(){
        final boolean needsWrite = (this.workItemsCompleted.get() - this.lastProgressSaved) > this.progressSaveInterval;

        if(needsWrite){
            synchronized (this.progressOutputFilePath){
                // Double-Check for efficient synchronization
                final boolean doesNeedWrite = (this.workItemsCompleted.get() - this.lastProgressSaved) > this.progressSaveInterval;
                if(doesNeedWrite){
                    try(BufferedWriter writer = new BufferedWriter(new FileWriter(this.progressOutputFilePath.toFile()))){
                        writer.write(String.valueOf(this.workItemsCompleted));
                    } catch(IOException ignored){}
                    this.lastProgressSaved = this.workItemsCompleted.get();
                }
            }
        }
    }

}
