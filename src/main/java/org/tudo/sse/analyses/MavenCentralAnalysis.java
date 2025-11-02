package org.tudo.sse.analyses;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opalj.log.GlobalLogContext$;
import org.opalj.log.OPALLogger;
import org.tudo.sse.utils.MarinOpalLogger;

abstract class MavenCentralAnalysis {

    /**
     * Defines whether this analysis requires artifacts to have index information annotated.
     */
    protected final boolean resolveIndex;

    /**
     * Defines whether this analysis requires artifacts to have pom information annotated.
     */
    protected final boolean resolvePom;

    /**
     * Defines whether this analysis requires artifacts to have resolved transitive pom information.
     */
    protected final boolean processTransitives;

    /**
     * Defines whether this analysis requires artifacts to have jar information annotated.
     */
    protected final boolean resolveJar;

    /**
     * Logger instance for subclasses
     */
    protected final Logger log = LogManager.getLogger(getClass());


    /**
     * Creates a new Maven Central Analysis with the given configuration options.
     *
     * @param requiresIndex Whether this analysis requires index information
     * @param requiresPom Whether this analysis requires POM information
     * @param requiresTransitives Whether this analysis requires transitive POM information. If true, "requiresPOM" will
     *                            be set to true no matter its given parameter value.
     * @param requiresJar Whether this analysis requires JAR information
     */
    protected MavenCentralAnalysis(boolean requiresIndex,
                                   boolean requiresPom,
                                   boolean requiresTransitives,
                                   boolean requiresJar){
        if(!requiresIndex && !requiresPom && !requiresTransitives && !requiresJar){
            log.warn("Potential misconfiguration - no data sources (index, POM, JAR) are required by this analysis");
        } else if(requiresTransitives && !requiresPom){
            log.warn("Potential misconfiguration - analysis requires transitive information but no POM information. " +
                    "POM information will also be collected to provide transitive information.");
        }

        // Use global OPAL Logger - will only forward OPAL messages with level error or fatal
        OPALLogger.updateLogger(GlobalLogContext$.MODULE$, MarinOpalLogger.getGlobalLogger());

        resolveIndex = requiresIndex;
        resolvePom = requiresPom || requiresTransitives; // Cannot have transitive information without POM information
        processTransitives = requiresTransitives;
        resolveJar = requiresJar;
    }

    public abstract void runAnalysis(String[] args);

}
