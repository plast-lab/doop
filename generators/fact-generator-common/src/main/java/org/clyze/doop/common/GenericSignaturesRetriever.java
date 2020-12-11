package org.clyze.doop.common;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.util.TraceSignatureVisitor;

import java.util.HashSet;
import java.util.Set;

public class GenericSignaturesRetriever extends ClassVisitor {
	private String className;

	public Set<GenericFieldInfo> getGenericFields() {
		return genericFields;
	}

	private final Set<GenericFieldInfo> genericFields;

	GenericSignaturesRetriever(int api) {
		super(api);
		genericFields = new HashSet<>();
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		this.className = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
		if (signature != null) {
			TraceSignatureVisitor traceSignatureVisitor = new TraceSignatureVisitor(0);
			SignatureReader r = new SignatureReader(signature);
			r.acceptType(traceSignatureVisitor);

			genericFields.add(new GenericFieldInfo(traceSignatureVisitor.getDeclaration(), name, this.className.replace("/", ".")));
			//System.out.println(traceSignatureVisitor.getDeclaration() + "\t" + name + "\t" + this.className);
		}
		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
//		if (signature != null) {
//			TraceSignatureVisitor traceSignatureVisitor = new TraceSignatureVisitor(0);
//			SignatureReader r = new SignatureReader(signature);
//			r.acceptType(traceSignatureVisitor);
//
//			System.out.println(traceSignatureVisitor.getDeclaration());
//		}
		return super.visitMethod(access, name, desc, signature, exceptions);
	}

	/*
	class MethodEntryAdapter extends AdviceAdapter {
		MethodEntryAdapter(int api, MethodVisitor mv, int access, String name, String desc) {
			super(api, mv, access, name, desc);
		}
		@Override
		protected void onMethodEnter() {
		}
	}
 */
}
