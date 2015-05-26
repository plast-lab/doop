package doop.core
/**
 * @author: Kostas Saidis (saiko@di.uoa.gr)
 * Date: 19/7/2014
 *
 * A DOOP analysis option
 */
class AnalysisOption<T>  {

    /**
     * The id of the option as used internally by the code (e.g. by the preprocessor, the web form, etc)
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
     * The type of the preprocessor flag (ignored when forPreprocessor is false)
     */
    PreprocessorFlag flagType = PreprocessorFlag.NORMAL_FLAG

    /**
     * Indicates whether the option can be specified by the user in the web UI
     */
    boolean webUI = false

    /**
     * The name of the option (for the end-user)
     */
    String name = null

    /**
     * Indicates whether the option can be specified by the user in the command line interface
     */
    boolean cli = false

    /**
     * The name of the option's arg value. If null, the option does not take arguments (it is a flag/boolean option).
     */
    String argName = null
    
    /**
     * Indicates whether the option is "advanced". Advanced options are treated differently by the UIs.
     */
    boolean isAdvanced = false

    /**
     * Indicates whether the options is a file.
     */
    boolean isFile = false

    @Override
    String toString() {
        return getId() + "=" + getValue()
    }
}
