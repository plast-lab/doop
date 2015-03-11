package doop.resolve

/**
 * A dependency: a file that will ultimately reside in the local file system.
 *
 * Dependencies should be resolved before being used.
 *
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 24/10/2014
 */
public interface Dependency {
    String dependency()
    File resolve()
}