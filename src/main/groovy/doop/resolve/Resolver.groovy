package doop.resolve

/**
 * A resolver for jar dependencies.
 *
 * Given a string representation of a jar (name, url, ivy dependency, etc), it resolves the local File that corresponds
 * to the given jar, performing all the steps involved automatically (e.g. fetch, download).
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 22/10/2014
 */
public interface Resolver {
    File resolve(String dependency, ResolutionContext ctx)
}