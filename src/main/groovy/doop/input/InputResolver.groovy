package doop.input

/**
 * A resolver for inputs.
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 23/3/2015
 */
interface InputResolver {
    void resolve(String input, InputResolutionContext ctx);
    String name();
}
