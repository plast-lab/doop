#!/bin/bash

export() {
	outDir=$1
	shift 1

	echo ">>> $outDir"

	for rel in $*
	do
	   # do translation of relation names into Souffle convetional format
	   relName=`echo $rel | tr : _ `

	   echo $relName

	   rm -f $outDir/$relName.csv
	   bloxbatch -db last-analysis -keepDerivedPreds -exportCsv $rel -exportDataDir $outDir -exportDelimiter '\t'
	   if [ -e $outDir/$relName.csv ]
	   then
		 tail -n +2 $outDir/$relName.csv > $outDir/$relName.facts
		 rm $outDir/$relName.csv
	   else
		 echo $rel not exported!
	   fi
	done
}

export ActualParam ApplicationClass ArrayType AssignCast:From \
AssignCast:Insn AssignContextInsensitiveHeapAllocation \
AssignHeapAllocation AssignLocal:From AssignLocal:Insn  \
AssignNormalHeapAllocation AssignInstruction:To AssignNull:Insn \
AssignReturnValue \
BasicBlockBegin BasicBlockHead BasicBlockEnd ExceptionHandlerFirstInstruction \
ExistsPreviousPredecessorToSameBB ExistsPreviousReturn \
FieldInstruction:Signature FieldModifier \
FieldSignature:DeclaringClass FieldSignature:Type FormalParam HeapAllocation:Merge \
HeapAllocation:Null \
HeapAllocation:Type Instruction:Index Instruction:Method IsJumpTarget \
LoadArrayIndex:Base LoadArrayIndex:To LoadInstanceField:Base LoadInstanceField:To LoadStaticField:To \
MainClass MainMethodArgHeap  \
MainMethodArgsArray MayPredecessorBBModuloThrow MaySuccessorBBModuloThrow \
MethodInvocation MethodInvocation:Signature MethodLookup \
MethodSignature:DeclaringType MethodSignature:Descriptor \
MethodSignature:SimpleName Modifier:final Modifier:static MonitorInstruction \
NextInSamePhiNode NextPredecessorToSameBB NextReturn PhiNodeHead PrevInSameBasicBlock Reachable \
ReferenceType ReturnInstruction ReturnNonvoid:Var ReturnVar SpecialMethodInvocation:Base \
SpecialMethodInvocation:Insn StaticMethodInvocation:Insn \
StoreArrayIndex:Base StoreArrayIndex:From \
StoreInstanceField:Base StoreInstanceField:From StoreStaticField:From \
SubtypeOf ThisVar \
Var:DeclaringMethod Var:Type VirtualMethodInvocation:Base \
VirtualMethodInvocation:Descriptor VirtualMethodInvocation:SimpleName
