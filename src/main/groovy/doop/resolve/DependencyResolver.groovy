package doop.resolve

import doop.Analysis

/**
 * A resolver for analysis' dependencies (jars).
 *
 * Given a string representation of a dependency (file name, url, ivy dependency, etc), it resolves the corresponding
 * local File, performing all the involved steps automatically (e.g. download, copy, etc).
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 22/10/2014
 */
public interface DependencyResolver {
    File resolve(String dependency, Analysis analysis)
}