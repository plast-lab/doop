package org.clyze.deepdoop.actions

interface IVisitable {
	// Enabling double dispatch
	def <T> T accept (IVisitor<T> v)
}