package doop
/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 19/7/2014
 *
 * A DOOP analysis option
 */
class AnalysisOption<T>  {

    /**
     * The id of the option as used internally by the code (e.g. by the preprocessor)
     */
    String id

    /**
     * The description of the option (for the end-user)
     */
    String description

    /**
     * The value of the option
     */
    T value

    /**
     * Indicates whether the option affects the preprocessor
     */
    boolean forPreprocessor = false

    /**
     * Indicates whether the option can be inserted by the user
     */
    boolean definedByUser = false

    /**
     * The name of the option (for the user)
     */
    String name = null

    /**
     * The name of the option's arg value. If null, the option does not take arguments (it is a flag/boolean option).
     */
    String argName = null

    @Override
    String toString() {
        return getId() + "=" + getValue()
    }
}
