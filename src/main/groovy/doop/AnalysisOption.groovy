package doop
/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 19/7/2014
 *
 * A DOOP analysis option
 */
class AnalysisOption<T>  {
    /**
     * The name of the option as used internally by the code (e.g. by the preprocessor)
     */
    String name

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
     * Indicates whether the option can be inserted by the command line
     */
    boolean cli = false

    /**
     * The name of the option for the cli
     */
    String cliName = null

    @Override
    String toString() {
        return getName() + "=" + getValue()
    }
}
