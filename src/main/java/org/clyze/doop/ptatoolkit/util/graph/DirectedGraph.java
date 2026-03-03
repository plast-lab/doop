package org.clyze.doop.ptatoolkit.util.graph;

import java.util.Collection;

/**
 * Read-only directed graph abstraction.
 *
 * @param <N> the node type
 */
public interface DirectedGraph<N> {

    /**
     * Returns all nodes in the graph.
     *
     * @return all graph nodes
     */
    Collection<N> allNodes();

    /**
     * Returns predecessor nodes of a given node.
     *
     * @param n the queried node
     * @return predecessor nodes
     */
    Collection<N> predsOf(N n);

    /**
     * Returns successor nodes of a given node.
     *
     * @param n the queried node
     * @return successor nodes
     */
    Collection<N> succsOf(N n);
}
