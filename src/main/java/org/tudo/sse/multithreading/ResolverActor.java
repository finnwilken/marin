package org.tudo.sse.multithreading;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import org.tudo.sse.ArtifactFactory;
import org.tudo.sse.model.ArtifactIdent;
import org.tudo.sse.model.resolution.ArtifactResolutionContext;

/**
 * This class is spawned in multiple threads
 * allowing for faster resolution of large quantity of artifacts.
 */
public class ResolverActor extends AbstractActor {

    /**
     * Creates a new ResolverActor
     */
    public ResolverActor() {}

    /**
     * Sets up the inherited properties for the actor.
     * @return properties created for the ResolverActor class
     */
    public static Props props() {
        return Props.create(ResolverActor.class, ResolverActor::new);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ProcessIdentifierMessage.class, message -> {
                    final ArtifactIdent identifier = message.getIdentifier();
                    final ArtifactResolutionContext ctx = message.getArtifactResolutionContext();

                    message.getInstance().callResolver(identifier, ctx);
                    message.getInstance().analyzeArtifact(ctx.getArtifact(identifier));

                    getSender().tell(WorkItemFinishedMessage.getInstance(), getSelf());
                })
                .match(ProcessLibraryMessage.class, message -> {
                    message.getProcessEntryCallback().get();
                    getSender().tell(WorkItemFinishedMessage.getInstance(), getSelf());
                }).build();
    }

}
