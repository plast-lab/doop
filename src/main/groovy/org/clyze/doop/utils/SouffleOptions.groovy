package org.clyze.doop.utils

import groovy.transform.CompileStatic
import org.clyze.analysis.AnalysisOption
import org.clyze.doop.core.DoopAnalysisFamily

@CompileStatic
class SouffleOptions {
    /** Enable Souffle profiler. */
    boolean profile
    /** Enable debugging mode. */
    boolean debug
    /** Enable Souffle provenance. */
    boolean provenance
    /** Enable live profiler. */
    boolean liveProf
    /** Force logic recompilation. */
    boolean forceRecompile
    /** Only translate to C++. Used to test that Datalog inputs are well-formed. */
    boolean translateOnly
    /** Remove contexts from the final logic (experimental). */
    boolean removeContexts
    /** Use custom functors. */
    boolean useFunctors
    /** Maximum memory to use. */
    String maxMemory
    /** Number of jobs to use. */
    int jobs

    SouffleOptions() {}

    SouffleOptions(Map<String, AnalysisOption> options) {
        this.profile = options.SOUFFLE_PROFILE?.value as boolean
        this.debug = options.SOUFFLE_DEBUG?.value as boolean
        this.provenance = options.SOUFFLE_PROVENANCE?.value as boolean
        this.liveProf = options.SOUFFLE_LIVE_PROFILE?.value as boolean
        this.forceRecompile = options.SOUFFLE_FORCE_RECOMPILE?.value as boolean
        this.translateOnly = options.SOUFFLE_MODE?.value == DoopAnalysisFamily.SOUFFLE_TRANSLATED
        this.removeContexts = options.X_CONTEXT_REMOVER?.value as boolean
        this.useFunctors = options.SOUFFLE_USE_FUNCTORS?.value as boolean
        this.maxMemory = options.MAX_MEMORY?.value as String
        this.jobs = options.SOUFFLE_JOBS?.value as int
    }
}
