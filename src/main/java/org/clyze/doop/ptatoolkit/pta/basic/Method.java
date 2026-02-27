package org.clyze.doop.ptatoolkit.pta.basic;

import java.util.Collection;

/**
 * The abstraction of method.
 */
public abstract class Method extends AttributeElement {

	private final Collection<Variable> params;
	private final Collection<Variable> retVars;
	private final boolean isPrivate;
	private final boolean isImplicitReachable;

	/**
	 * Constructor for Method that initializes the parameters, return variables, and method attributes.
	 * @param params the parameters of the method
	 * @param retVars the return variables of the method
	 * @param isPrivate indicates whether the method is private
	 * @param isImplicitReachable indicates whether the method is implicitly reachable
	 */
	protected Method(Collection<Variable> params,
					 Collection<Variable> retVars,
					 boolean isPrivate,
	                 boolean isImplicitReachable) {
		this.params = params;
		this.retVars = retVars;
		this.isPrivate = isPrivate;
		this.isImplicitReachable = isImplicitReachable;
	}

	public Collection<Variable> getParameters() {
		return params;
	}

	/**
	 *
	 * @return all parameters. For instance methods, this variable
	 * will also be returned.
	 */
	public abstract Collection<Variable> getAllParameters();

	/**
	 * Returns the return variables of the method. A method may have multiple return variables, which represent the possible return values of the method. These return variables are important for points-to analysis, as they help to determine the possible values that can be returned by the method and how they may affect the program's behavior.
	 * @return the return variables of the method
	 */
	public Collection<Variable> getRetVars() {
		return retVars;
	}

	/**
	 * Indicates whether the method is an instance method. For instance methods, the first parameter is the receiver variable, which represents the object on which the method is called. For static methods, there is no receiver variable.
	 * @return true if the method is an instance method, false if it is a static method
	 */
	public abstract boolean isInstance();

	/**
	 * Indicates whether the method is a static method. For static methods, there is no receiver variable, and the method can be called without an instance of the class. For instance methods, the first parameter is the receiver variable, which represents the object on which the method is called.
	 * @return true if the method is a static method, false if it is an instance method
	 */
	public boolean isStatic() {
		return !isInstance();
	}

	/**
	 * Indicates whether the method is private.
	 * @return true if the method is private, false otherwise
	 */
	public boolean isPrivate() { return isPrivate; }

	/**
	 * Indicates whether the method is implicitly reachable. An implicitly reachable method is a method that can be reached through reflection or other means, even if it is not explicitly called in the code. This attribute is important for points-to analysis, as it helps to identify methods that may be invoked indirectly and should be included in the analysis.
	 * @return true if the method is implicitly reachable, false otherwise
	 */
	public boolean isImplicitReachable() { return isImplicitReachable; }
}