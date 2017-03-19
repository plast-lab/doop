#!/bin/bash

outDir=$1
echo ">>> $outDir"

export() {
    for rel in $*
    do
        # do translation of relation names into Souffle convetional format
        relName=`echo $rel | tr : _ `

        echo $relName

        rm -f $outDir/$relName.csv
        bloxbatch -db last-analysis -keepDerivedPreds -keepDerivedOnlyPreds -exportCsv $rel -exportDataDir $outDir -exportDelimiter '\t'

        if [ -e $outDir/$relName.csv ]
        then
            tail -n +2 $outDir/$relName.csv > $outDir/$relName.facts
            rm $outDir/$relName.csv
        else
            echo $rel not exported!
        fi
    done
}

exportByQuery() {
    # first arg is relation's arity, second is relation to export

    rel=$2
    # do translation of relation names into Souffle convetional format
    relFname=`echo $rel | tr : _ `

    echo $relFname

    # set -x
    # Translate CSV files into TSV, but with care to only replace
    # commas followed by space and a letter or underscore or '<' or '*'. Also prune leading spaces.
    if [ $1 == "1" ]
    then
        bloxbatch -db last-analysis -query "_(z) <- $rel(z)." | sed -e 's/^\s*//g' | sed -e 's/, \([_<*a-zA-Z]\)/\t\1/g' > $outDir/$relFname.facts
    elif [ $1 == "2" ]
    then
        bloxbatch -db last-analysis -query "_(y,z) <- $rel(y,z)." | sed -e 's/^\s*//g' | sed -e 's/, \([_<*a-zA-Z]\)/\t\1/g' > $outDir/$relFname.facts
    elif [ $1 == "3" ]
    then
        bloxbatch -db last-analysis -query "_(x,y,z) <- $rel(x,y,z)." | sed -e 's/^\s*//g' | sed -e 's/, \([_<*a-zA-Z]\)/\t\1/g' > $outDir/$relFname.facts
    elif [ $1 == "4" ]
    then
        bloxbatch -db last-analysis -query "_(w,x,y,z) <- $rel(w,x,y,z)." | sed -e 's/^\s*//g' | sed -e 's/, \([_<*a-zA-Z]\)/\t\1/g' > $outDir/$relFname.facts
    fi
}

export ActualParam ApplicationClass ArrayType \
AssignCast:From AssignCast:Insn \
AssignLocal AssignLocal:From AssignLocal:Insn \
AssignInstruction:To AssignNull:Insn \
AssignReturnValue \
BasicBlockBegin BasicBlockHead BasicBlockEnd ExceptionHandlerFirstInstruction \
ExistsPreviousPredecessorToSameBB ExistsPreviousReturn \
FieldInstruction:Signature FieldModifier \
FieldSignature:DeclaringClass FieldSignature:Type FormalParam \
Instruction:Index Instruction:Method IsJumpTarget \
LoadArrayIndex:Base LoadArrayIndex:To \
LoadInstanceField LoadInstanceField:Base LoadInstanceField:To LoadStaticField:To \
MainClass MainMethodArgHeap \
MainMethodArgsArray MayPredecessorBBModuloThrow MaySuccessorBBModuloThrow \
MethodInvocation MethodInvocation:Method MethodLookup \
MethodSignature:DeclaringType MethodSignature:Descriptor \
MethodSignature:SimpleName Modifier:final Modifier:static MonitorInstruction \
NextInSamePhiNode NextPredecessorToSameBB NextReturn PhiNodeHead PrevInSameBasicBlock Reachable \
ReferenceType ReturnInstruction ReturnNonvoid:Var ReturnVar SpecialMethodInvocation:Base \
SpecialMethodInvocation:Insn StaticMethodInvocation:Insn \
StoreArrayIndex:Base StoreArrayIndex:From \
StoreInstanceField StoreInstanceField:Base StoreInstanceField:From StoreStaticField:From \
SubtypeOf ThisVar \
Var:DeclaringMethod Var:Type \
VirtualMethodInvocation:Base VirtualMethodInvocation:Descriptor VirtualMethodInvocation:SimpleName

exportByQuery 3 AssignHeapAllocation
exportByQuery 3 AssignNormalHeapAllocation
exportByQuery 3 AssignContextInsensitiveHeapAllocation 
exportByQuery 2 HeapAllocation:Merge
exportByQuery 1 HeapAllocation:Null
exportByQuery 2 HeapAllocation:Type


