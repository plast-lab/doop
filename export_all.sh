#!/bin/bash
./export.sh ActualParam ApplicationClass AssignCast:From \
AssignCast:Insn AssignContextInsensitiveHeapAllocation \
AssignHeapAllocation AssignLocal:From AssignLocal:Insn  \
AssignNormalHeapAllocation AssignInstruction:To AssignNull:Insn \
AssignReturnValue FormalParam HeapAllocation:Merge HeapAllocation:Null \
HeapAllocation:Type Instruction:Index Instruction:Method IsJumpTarget \
MainClass MainMethodArgHeap  \
MainMethodArgsArray MethodInvocation:Signature MethodLookup \
MethodSignature:DeclaringType MethodSignature:Descriptor \
MethodSignature:SimpleName NextInSamePhiNode PhiNodeHead Reachable \
ReturnVar SpecialMethodInvocation:Base SubtypeOf ThisVar \
Var:DeclaringMethod Var:Type VirtualMethodInvocation:Base \
VirtualMethodInvocation:Descriptor VirtualMethodInvocation:SimpleName 
#./export2.sh 1 Reachable
#./export2.sh 2 VirtualMethodInvocation:SimpleName
#./export2.sh 2 VirtualMethodInvocation:Descriptor
#./export2.sh 2 SubtypeOf
#./export2.sh 2 HeapAllocation:Merge
#./export2.sh 2 PhiNodeHead
#./export2.sh 2 NextInSamePhiNode
#./export2.sh 3 AssignContextInsensitiveHeapAllocation
#./export2.sh 3 AssignNormalHeapAllocation
#./export2.sh 4 MethodLookup


