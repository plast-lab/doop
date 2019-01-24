package org.clyze.doop.dex;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.clyze.doop.common.Database;
import org.clyze.doop.common.FieldInfo;
import org.clyze.doop.common.FieldOp;
import org.clyze.doop.common.JavaFactWriter;
import org.clyze.doop.common.JavaRepresentation;
import org.clyze.doop.common.PredicateFile;
import org.clyze.doop.common.SessionCounter;
import org.jf.dexlib2.AccessFlags;
import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.dexbacked.DexBackedExceptionHandler;
import org.jf.dexlib2.dexbacked.DexBackedMethod;
import org.jf.dexlib2.dexbacked.DexBackedMethodImplementation;
import org.jf.dexlib2.dexbacked.DexBackedTryBlock;
import org.jf.dexlib2.dexbacked.reference.DexBackedFieldReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedStringReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedTypeReference;
import org.jf.dexlib2.dexbacked.value.DexBackedStringEncodedValue;
import org.jf.dexlib2.dexbacked.value.DexBackedTypeEncodedValue;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.ExceptionHandler;
import org.jf.dexlib2.iface.MethodParameter;
import org.jf.dexlib2.iface.debug.DebugItem;
import org.jf.dexlib2.iface.debug.EndLocal;
import org.jf.dexlib2.iface.debug.RestartLocal;
import org.jf.dexlib2.iface.debug.StartLocal;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.instruction.formats.ArrayPayload;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.immutable.debug.ImmutableEpilogueBegin;
import org.jf.dexlib2.immutable.debug.ImmutableLineNumber;
import org.jf.dexlib2.immutable.debug.ImmutablePrologueEnd;

import java.util.*;
import java.util.stream.Collectors;

import static org.clyze.doop.common.JavaRepresentation.handlerMid;
import static org.clyze.doop.common.JavaRepresentation.numberedInstructionId;
import static org.clyze.doop.common.PredicateFile.*;
import static org.clyze.doop.dex.DexRepresentation.strOfLineNo;
import static org.clyze.doop.dex.DexUtils.*;
import static org.clyze.doop.util.TypeUtils.raiseTypeId;

/**
 * Writes facts for a single method found in a .dex class.
 */
class DexMethodFactWriter extends JavaFactWriter {
    private static final boolean extractRegisterTypes = false;

    private static final boolean debug = false;
    private static final Log logger = debug ? LogFactory.getLog(DexMethodFactWriter.class) : null;

    // The following fields are needed to process the current method.
    private final DexBackedMethod m;
    private final Map<String, MethodSig> cachedMethodDescriptors;
    private final SessionCounter counter = new SessionCounter();
    private final MethodFacts mf = new MethodFacts();
    private final String methId;
    private final NavigableMap<Integer, Integer> lineNumbers = new TreeMap<>();

    private final int localRegCount;
    private final AccessFlags[] flags;
    private final boolean isStatic;
    private final boolean isNative;

    // Address counter (16-bit code units).
    private int currentInstrAddr;
    // Map from address to instruction index.
    private final Map<Integer, Integer> addressToIndex = new HashMap<>();

    // The following fields hold state and are used to detect multi-instruction
    // patterns (e.g. move-result-after-invoke or filled array initialization).
    // * Patterns where two instructions may not be adjacent (and thus refer to
    //   each other using offsets) are handled by a PatternManager.
    // * Patterns of two adjacent instructions need simpler state.

    // Patterns supported by field:
    //   first instruction = any of { FILLED_NEW_ARRAY, FILLED_NEW_ARRAY_RANGE,
    //     INVOKE_DIRECT, INVOKE_VIRTUAL, INVOKE_STATIC, INVOKE_INTERFACE,
    //     INVOKE_SUPER, INVOKE_DIRECT_RANGE, INVOKE_VIRTUAL_RANGE,
    //     INVOKE_STATIC_RANGE, INVOKE_INTERFACE_RANGE, INVOKE_SUPER_RANGE: }
    //   second instruction: any of { MOVE_RESULT, MOVE_RESULT_WIDE, MOVE_RESULT_OBJECT }
    private ObjectReturnInfo objReturnInfo;

    // Pattern supported by field: set-register-R-to-0 + NEW_ARRAY-of-size-R
    private ZeroedRegister zeroedArraySizeRegister;

    // The following two fields essentially support a 3-instruction pattern.
    // Pattern supported by field: NEW_ARRAY + FILL_ARRAY_DATA
    private NewArrayInfo lastNewArrayInfo;
    // Pattern supported by field: FILL_ARRAY_DATA + ARRAY_PAYLOAD
    private final PatternManager<FillArrayInfoEntry> fillArrayInfo = new PatternManager<>();

    // Patterns supported by field:
    // * PACKED_SWITCH + PACKED_SWITCH_PAYLOAD
    // * SPARSE_SWITCH + SPARSE_SWITCH_PAYLOAD
    private final PatternManager<FirstInstructionEntry> pendingSwitchInfo = new PatternManager<>();

    private final Collection<MoveExceptionInfo> exceptionMoves = new LinkedList<>();

    // Instructions that point to addresses that must be resolved.
    private final Collection<RawGoto> gotos = new LinkedList<>();
    private final Collection<RawGoto> ifs = new LinkedList<>();

    DexMethodFactWriter(DexBackedMethod dexMethod, Database _db, boolean moreStrings,
                        Map<String, MethodSig> cachedMethodDescriptors) {
        super(_db, moreStrings);
        this.m = dexMethod;
        this.cachedMethodDescriptors = cachedMethodDescriptors;
        this.methId = DexRepresentation.methodId(m, mf);

        // Process flags.
        this.flags = AccessFlags.getAccessFlagsForMethod(m.getAccessFlags());
        this.isNative = Arrays.asList(flags).contains(AccessFlags.NATIVE);
        this.isStatic = Arrays.asList(flags).contains(AccessFlags.STATIC);
        // Process locals.
        DexBackedMethodImplementation mi = m.getImplementation();
        this.localRegCount = (mi == null) ? 0 : countLocalRegisters(mi);
    }

    public void writeMethod(Collection<FieldOp> fieldOps,
                            Collection<String> definedMethods) {
        // System.out.println("Generating facts for method: " + methId);
        _db.add(METHOD, methId, mf.simpleName, mf.paramsSig, mf.declaringClass, mf.retType, mf.jvmSig, mf.arity);
        definedMethods.add(methId);

        for (Annotation annotation : m.getAnnotations()) {
            String annotType = raiseTypeId(annotation.getType());
            if ("dalvik.annotation.Throws".equals(annotType)) {
                for (String exception : getAnnotationValues(annotation, (ev -> ((DexBackedTypeEncodedValue) ev).getValue())))
                    writeMethodDeclaresException(methId, raiseTypeId(exception));
            } else if ("dalvik.annotation.Signature".equals(annotType)) {
                if (debug) {
                    for (String sig : getAnnotationValues(annotation, (ev -> ((DexBackedStringEncodedValue) ev).getValue())))
                        if (!sig.equals(mf.jvmSig))
                            logger.debug("Ignored supplied method signature: " + sig + " != " + mf.jvmSig);
                }
            }
            writeMethodAnnotation(methId, annotType);
        }

        for (AccessFlags f : flags)
            _db.add(METHOD_MODIFIER, f.toString(), methId);

        int i = 0;
        for (MethodParameter param : m.getParameters()) {
            // For instance methods, offset register index by 1.
            int formalIdx = isStatic ? i : (i + 1);
            String var = DexRepresentation.param(methId, formalIdx);
            String type = raiseTypeId(param.getType());
            writeFormalParam(methId, var, type, i);
            for (Annotation annot : param.getAnnotations())
                _db.add(PARAM_ANNOTATION, methId, str(i), raiseTypeId(annot.getType()));
            i++;
        }

        if (!isStatic) {
            String thisVar = DexRepresentation.thisVarId(methId);
            String type = raiseTypeId(m.getDefiningClass());
            writeThisVar(methId, thisVar, type);
        }
        if (isNative && !"void".equals(mf.retType)) {
            String var = JavaRepresentation.nativeReturnVarOfMethod(methId);
            _db.add(NATIVE_RETURN_VAR, var, methId);
            writeLocal(var, mf.retType, methId);
        }

        DexBackedMethodImplementation mi = m.getImplementation();
        if (mi != null) {
            writeDebugItems(mi.getDebugItems());
            int index = 1;
            for (Instruction instr : mi.getInstructions()) {
                addressToIndex.put(currentInstrAddr, index);
                generateFactsFor(instr, index, fieldOps);
                index++;
                currentInstrAddr += instr.getCodeUnits();
            }
            addressToIndex.put(currentInstrAddr, index);
            fillArrayInfo.checkEverythingConsumed();
            pendingSwitchInfo.checkEverythingConsumed();

            processTryBlocks(mi.getTryBlocks());
            resolveAndWriteBranches();
        }
    }

    /**
     * Process try-catch blocks and their handlers to generate facts about
     * exception handlers.
     *
     * @param tryBlocks    the try-block Dex information
     */
    private void processTryBlocks(Iterable<? extends DexBackedTryBlock> tryBlocks) {
        Collection<Handler> handlers = new LinkedList<>();

        // Step 1: read all try blocks and record exception handler information.
        for (DexBackedTryBlock block : tryBlocks) {
            int startAddr = block.getStartCodeAddress();
            int endAddr = startAddr + block.getCodeUnitCount();
            String previous = null;
            List<? extends DexBackedExceptionHandler> exHandlers = block.getExceptionHandlers();
            // Sort by address, lowest-first, so that "previous" works.
            List<? extends DexBackedExceptionHandler> sortedHandlers = (exHandlers.size() < 2) ? exHandlers : exHandlers.stream().sorted(Comparator.comparingInt(ExceptionHandler::getHandlerCodeAddress)).collect(Collectors.toList());
            for (DexBackedExceptionHandler handler : sortedHandlers) {
                int handlerAddr = handler.getHandlerCodeAddress();
                String t = handler.getExceptionType();
                String excType;
                if (t == null) {
                    excType = "java.lang.Throwable";
                    if (debug)
                        logger.debug("Warning: no exception type found for handler in " + methId + ", using " + excType);
                } else
                    excType = raiseTypeId(t);
                handlers.add(new Handler(startAddr, endAddr, handlerAddr, excType));

                Integer handlerIndex = addressToIndex.get(handlerAddr);
                Integer startIndex = addressToIndex.get(startAddr);
                Integer endIndex = addressToIndex.get(endAddr);
                if (handlerIndex == null || startIndex == null || endIndex == null) {
                    System.err.println("Error: handler {" + handlerIndex + ", " + startIndex + ", " + endIndex + "}");
                    previous = null;
                } else {
                    String insn = instructionId(handlerMid(excType), handlerIndex);
                    writeExceptionHandler(insn, methId, handlerIndex, excType, startIndex, endIndex);
                    if (previous != null)
                        writeExceptionHandlerPrevious(insn, previous);
                    previous = insn;
                }
            }
        }

        // Step 2: match every queued MOVE_EXCEPTION against its handler. This
        // resolves the "formal" of exception handlers and is optional: some
        // handlers may not have MOVE_EXCEPTION opcodes (but e.g., a GOTO).
        Map<Handler, String> handlerInsnId = new HashMap<>();
        for (MoveExceptionInfo mei : exceptionMoves) {
            List<Handler> containingHandlers = Handler.findHandlerStartingAt(handlers, mei.address);
            if (containingHandlers.isEmpty()) {
                System.err.println("Error: no exception handler found for MOVE_EXCEPTION at address " + mei.address + " in " + methId);
                continue;
            }
            String localA = local(mei.reg);
            for (Handler hi : containingHandlers) {
                try {
                    Integer startIndex = hi.getStartIndex(addressToIndex);
                    Integer endIndex = hi.getEndIndex(addressToIndex);
                    Integer handlerIndex = hi.getIndex(addressToIndex);
                    // Sanity check: MOVE_EXCEPTION must be first handler instruction.
                    if (handlerIndex != mei.index)
                        System.err.println("Warning: different handlerIndex " + handlerIndex + "!=" + mei.index + " for handler: " + hi);
                    String insn = instructionId(handlerMid(hi.excType), handlerIndex);
                    handlerInsnId.put(hi, insn);
                    writeExceptionHandlerFormal(insn, localA);
                } catch (Handler.IndexException ex) {
                    System.err.println("Error: " + ex.getMessage());
                }
            }
        }
    }

    private void resolveAndWriteBranches() {
        addressToIndex.forEach((addr, index) ->
            _db.add(DEX_INSTR_ADDR_MAP, methId, str(index), str(addr)));

        for (RawGoto g : gotos) {
            Integer indexTo = addressToIndex.get(g.addrTo);
            if (indexTo == null)
                System.err.println("Warning: cannot resolve goto target " + g.index + " in method " + methId);
            else
                writeGoto(g.insn, indexTo, g.index);
        }

        for (RawGoto g : ifs) {
            Integer indexTo = addressToIndex.get(g.addrTo);
            if (indexTo == null)
                System.err.println("Warning: cannot resolve if target " + g.index + " in method " + methId);
            else
                writeIf(g.insn, g.index, indexTo, methId);
        }
    }

    private void writeDebugItems(Iterable<? extends DebugItem> debugItems) {
        for (DebugItem di : debugItems) {
            if (di instanceof StartLocal) {
                StartLocal sl = (StartLocal)di;
                // writeRegisterType(sl.getRegister(), raiseTypeId(sl.getType()));
            } else if (di instanceof EndLocal) {
                EndLocal el = (EndLocal)di;
                // writeRegisterType(el.getRegister(), raiseTypeId(el.getType()));
            } else if (di instanceof RestartLocal) {
                RestartLocal rl = (RestartLocal)di;
                int reg = rl.getRegister();
                // writeRegisterType(reg, raiseTypeId(rl.getType()));
            } else if (di instanceof ImmutableLineNumber) {
                ImmutableLineNumber lineNo = ((ImmutableLineNumber) di);
                lineNumbers.put(lineNo.getCodeAddress(), lineNo.getLineNumber());
            } else if (di instanceof ImmutablePrologueEnd) {
                int addr = di.getCodeAddress();
                if (addr != 0) {
                    this.currentInstrAddr = addr;
                    System.out.println(methId + ", prologue end: " + addr + " != 0");
                }
            } else if (di instanceof ImmutableEpilogueBegin) {
                int addr = di.getCodeAddress();
                if (addr != 0)
                    System.err.println("(UNUSED) Epilogue begin: " + addr + " != 0");
            } else
                System.err.println("Warning: Unknown debug item class: " + di.getClass());
        }
    }

    private void writeRegisterType(int reg, String regType) { // , Map<Integer, String> regTypes) {
        // regTypes.put(reg, regType);
        writeLocal(local(reg), regType, methId);
    }

    private int countLocalRegisters(DexBackedMethodImplementation mi) {
        int paramRegCount = 0;
        for (MethodParameter mp : mi.method.getParameters())
            paramRegCount += regSizeOf(mp.getType());
        if (!isStatic)
            paramRegCount++;
        return mi.getRegisterCount() - paramRegCount;
    }

    private void generateFactsFor(Instruction instr, int index,
                                  Collection<FieldOp> fieldOps) {
        Opcode op = instr.getOpcode();
        if (debug)
            logger.debug("Opcode " + op.name + " | Instruction class: " + instr.getClass());
        switch (op) {
            case CONST_4:
            case CONST_16:
            case CONST:
            case CONST_HIGH16: {
                int reg = ((OneRegisterInstruction)instr).getRegisterA();
                int lit = ((NarrowLiteralInstruction)instr).getNarrowLiteral();
                writeAssignNumConstant(reg, String.valueOf(lit), index);
                if (lit == 0)
                    zeroedArraySizeRegister = new ZeroedRegister(index, reg);
                else if ((zeroedArraySizeRegister != null) && ( zeroedArraySizeRegister.reg == reg))
                    // If register is set to non-zero, clear existing information.
                    zeroedArraySizeRegister = null;
                break;
            }
            case CONST_WIDE:
            case CONST_WIDE_16:
            case CONST_WIDE_32: {
                int reg = ((OneRegisterInstruction)instr).getRegisterA();
                long lit = ((WideLiteralInstruction)instr).getWideLiteral();
                writeAssignNumConstant(reg, String.valueOf(lit), index);
                break;
            }
            case CONST_WIDE_HIGH16: {
                int reg = ((OneRegisterInstruction) instr).getRegisterA();
                @SuppressWarnings("UnnecessaryLocalVariable") long signExtendedVal64 = ((LongHatLiteralInstruction) instr).getHatLiteral();
                writeAssignNumConstant(reg, String.valueOf(signExtendedVal64), index);
                break;
            }

            case NEW_INSTANCE: {
                int reg = ((OneRegisterInstruction)instr).getRegisterA();
                TypeReference typeRef = (TypeReference)((ReferenceInstruction)instr).getReference();
                String type = raiseTypeId(typeRef.getType());
                writeAssignHeapAllocation(reg, type, index, instructionId("assign", index), false, null);
                break;
            }
            case CONST_STRING:
            case CONST_STRING_JUMBO: {
                OneRegisterInstruction ori = (OneRegisterInstruction)instr;
                ReferenceInstruction ri = (ReferenceInstruction)instr;
                int reg = ori.getRegisterA();
                String content = ((DexBackedStringReference)ri.getReference()).getString();
                writeAssignStringConstant(reg, content, index);
                break;
            }
            case INVOKE_DIRECT:
            case INVOKE_VIRTUAL:
            case INVOKE_STATIC:
            case INVOKE_INTERFACE:
            case INVOKE_SUPER:
            case INVOKE_DIRECT_RANGE:
            case INVOKE_VIRTUAL_RANGE:
            case INVOKE_STATIC_RANGE:
            case INVOKE_INTERFACE_RANGE:
            case INVOKE_SUPER_RANGE:
                writeInvoke(instr, op, index);
                break;
            case SPUT:
            case SPUT_WIDE:
            case SPUT_OBJECT:
            case SPUT_BOOLEAN:
            case SPUT_BYTE:
            case SPUT_CHAR:
            case SPUT_SHORT:
                writeLoadOrStoreSField(instr, index, false, fieldOps);
                break;
            case NEW_ARRAY:
                writeNewArray(instr, index);
                break;
            case APUT:
            case APUT_WIDE:
            case APUT_OBJECT:
            case APUT_BOOLEAN:
            case APUT_BYTE:
            case APUT_CHAR:
            case APUT_SHORT:
                writeLoadOrStoreArrayIndex((ThreeRegisterInstruction)instr, index, STORE_ARRAY_INDEX);
                break;
            case AGET:
            case AGET_WIDE:
            case AGET_OBJECT:
            case AGET_BOOLEAN:
            case AGET_BYTE:
            case AGET_CHAR:
            case AGET_SHORT:
                writeLoadOrStoreArrayIndex((ThreeRegisterInstruction)instr, index, LOAD_ARRAY_INDEX);
                break;
            case ARRAY_LENGTH:
                writeAssignUnop((TwoRegisterInstruction)instr, index);
                break;
            case FILL_ARRAY_DATA:
                handleFillArrayData(instr, index);
                break;
            case ARRAY_PAYLOAD:
                handleArrayPayload((ArrayPayload)instr);
                break;
            case FILLED_NEW_ARRAY:
            case FILLED_NEW_ARRAY_RANGE:
                writeFilledNewArray(instr, index, op);
                break;
            case RETURN_VOID:
                writeReturnVoid(index);
                break;
            case RETURN:
            case RETURN_WIDE:
            case RETURN_OBJECT:
                writeReturn(((OneRegisterInstruction)instr).getRegisterA(), index);
                break;
            case SGET:
            case SGET_WIDE:
            case SGET_OBJECT:
            case SGET_BOOLEAN:
            case SGET_BYTE:
            case SGET_CHAR:
            case SGET_SHORT:
                writeLoadOrStoreSField(instr, index, true, fieldOps);
                break;
            case CONST_CLASS:
                writeAssignClassConstant(instr, index);
                break;
            case MOVE_RESULT:
            case MOVE_RESULT_WIDE:
            case MOVE_RESULT_OBJECT:
                writeMoveResult(instr, index, op);
                break;
            case IPUT:
            case IPUT_WIDE:
            case IPUT_OBJECT:
            case IPUT_BOOLEAN:
            case IPUT_BYTE:
            case IPUT_CHAR:
            case IPUT_SHORT:
                writeLoadOrStoreIField(instr, index, false, fieldOps);
                break;
            case IGET:
            case IGET_WIDE:
            case IGET_OBJECT:
            case IGET_BOOLEAN:
            case IGET_BYTE:
            case IGET_CHAR:
            case IGET_SHORT:
                writeLoadOrStoreIField(instr, index, true, fieldOps);
                break;
            case MOVE:
            case MOVE_FROM16:
            case MOVE_16:
            case MOVE_WIDE:
            case MOVE_WIDE_FROM16:
            case MOVE_WIDE_16:
            case MOVE_OBJECT:
            case MOVE_OBJECT_FROM16:
            case MOVE_OBJECT_16:
                writeAssignLocal((TwoRegisterInstruction) instr, index);
                break;
            case INSTANCE_OF:
                writeAssignInstanceOf((TwoRegisterInstruction)instr, (ReferenceInstruction)instr, index);
                break;
            case THROW:
                writeThrow(((OneRegisterInstruction)instr).getRegisterA(), index);
                break;
            case MONITOR_ENTER:
                writeEnterMonitor(((OneRegisterInstruction)instr).getRegisterA(), index);
                break;
            case MONITOR_EXIT:
                writeExitMonitor(((OneRegisterInstruction)instr).getRegisterA(), index);
                break;
            case IF_EQZ:
            case IF_NEZ:
            case IF_LTZ:
            case IF_GEZ:
            case IF_GTZ:
            case IF_LEZ: {
                int reg = ((OneRegisterInstruction)instr).getRegisterA();
                writeIf(instr, reg, -1, op, index);
                break;
            }
            case IF_EQ:
            case IF_NE:
            case IF_LT:
            case IF_GE:
            case IF_GT:
            case IF_LE: {
                TwoRegisterInstruction tri = (TwoRegisterInstruction)instr;
                int regA = tri.getRegisterA();
                int regB = tri.getRegisterB();
                writeIf(instr, regA, regB, op, index);
                break;
            }
            case PACKED_SWITCH:
                writeSwitchKey(instr, index, TABLE_SWITCH);
                break;
            case SPARSE_SWITCH:
                writeSwitchKey(instr, index, LOOKUP_SWITCH);
                break;
            case PACKED_SWITCH_PAYLOAD:
                writeSwitchTargets(instr, TABLE_SWITCH_TARGET);
                break;
            case SPARSE_SWITCH_PAYLOAD:
                writeSwitchTargets(instr, LOOKUP_SWITCH_TARGET);
                break;
            case CHECK_CAST:
                writeAssignCast((OneRegisterInstruction)instr, (ReferenceInstruction)instr, index);
                break;
            case ADD_INT:
            case SUB_INT:
            case RSUB_INT:
            case MUL_INT:
            case DIV_INT:
            case REM_INT:
            case AND_INT:
            case OR_INT:
            case XOR_INT:
            case SHL_INT:
            case SHR_INT:
            case USHR_INT:
            case ADD_LONG:
            case SUB_LONG:
            case MUL_LONG:
            case DIV_LONG:
            case REM_LONG:
            case AND_LONG:
            case OR_LONG:
            case XOR_LONG:
            case SHL_LONG:
            case SHR_LONG:
            case USHR_LONG:
            case ADD_FLOAT:
            case SUB_FLOAT:
            case MUL_FLOAT:
            case DIV_FLOAT:
            case REM_FLOAT:
            case ADD_DOUBLE:
            case SUB_DOUBLE:
            case MUL_DOUBLE:
            case DIV_DOUBLE:
            case REM_DOUBLE:
            case CMPL_FLOAT:
            case CMPG_FLOAT:
            case CMPL_DOUBLE:
            case CMPG_DOUBLE:
            case CMP_LONG: {
                ThreeRegisterInstruction tri = (ThreeRegisterInstruction)instr;
                int regDest = tri.getRegisterA();
                int regSource1 = tri.getRegisterB();
                int regSource2 = tri.getRegisterC();
                writeBinopThreeReg(regDest, regSource1, regSource2, op, index);
                break;
            }
            case ADD_INT_2ADDR:
            case SUB_INT_2ADDR:
            case MUL_INT_2ADDR:
            case DIV_INT_2ADDR:
            case REM_INT_2ADDR:
            case AND_INT_2ADDR:
            case OR_INT_2ADDR:
            case XOR_INT_2ADDR:
            case SHL_INT_2ADDR:
            case SHR_INT_2ADDR:
            case USHR_INT_2ADDR:
            case ADD_LONG_2ADDR:
            case SUB_LONG_2ADDR:
            case MUL_LONG_2ADDR:
            case DIV_LONG_2ADDR:
            case REM_LONG_2ADDR:
            case AND_LONG_2ADDR:
            case OR_LONG_2ADDR:
            case XOR_LONG_2ADDR:
            case SHL_LONG_2ADDR:
            case SHR_LONG_2ADDR:
            case USHR_LONG_2ADDR:
            case ADD_FLOAT_2ADDR:
            case SUB_FLOAT_2ADDR:
            case MUL_FLOAT_2ADDR:
            case DIV_FLOAT_2ADDR:
            case REM_FLOAT_2ADDR:
            case ADD_DOUBLE_2ADDR:
            case SUB_DOUBLE_2ADDR:
            case MUL_DOUBLE_2ADDR:
            case DIV_DOUBLE_2ADDR:
            case REM_DOUBLE_2ADDR: {
                TwoRegisterInstruction tri = (TwoRegisterInstruction)instr;
                int regDestAndSource1 = tri.getRegisterA();
                int regSource2 = tri.getRegisterB();
                writeBinopThreeReg(regDestAndSource1, regDestAndSource1, regSource2, op, index);
                break;
            }
            case ADD_INT_LIT16:
            case MUL_INT_LIT16:
            case DIV_INT_LIT16:
            case REM_INT_LIT16:
            case AND_INT_LIT16:
            case OR_INT_LIT16:
            case XOR_INT_LIT16:
            case ADD_INT_LIT8:
            case RSUB_INT_LIT8:
            case MUL_INT_LIT8:
            case DIV_INT_LIT8:
            case REM_INT_LIT8:
            case AND_INT_LIT8:
            case OR_INT_LIT8:
            case XOR_INT_LIT8:
            case SHL_INT_LIT8:
            case SHR_INT_LIT8:
            case USHR_INT_LIT8:
                writeBinopTwoRegPlusLit((TwoRegisterInstruction)instr, op, index);
                break;
            case MOVE_EXCEPTION:
                exceptionMoves.add(new MoveExceptionInfo(((OneRegisterInstruction)instr).getRegisterA(), currentInstrAddr, index));
                break;
            case GOTO:
            case GOTO_16:
            case GOTO_32:
                queueGoto(index, absoluteAddr((OffsetInstruction)instr));
                break;
            case NEG_INT:
            case NOT_INT:
            case NEG_LONG:
            case NOT_LONG:
            case NEG_FLOAT:
            case NEG_DOUBLE:
            case INT_TO_LONG:
            case INT_TO_FLOAT:
            case INT_TO_DOUBLE:
            case LONG_TO_INT:
            case LONG_TO_FLOAT:
            case LONG_TO_DOUBLE:
            case FLOAT_TO_INT:
            case FLOAT_TO_LONG:
            case FLOAT_TO_DOUBLE:
            case DOUBLE_TO_INT:
            case DOUBLE_TO_LONG:
            case DOUBLE_TO_FLOAT:
            case INT_TO_BYTE:
            case INT_TO_CHAR:
            case INT_TO_SHORT:
                writeAssignUnop((TwoRegisterInstruction)instr, index);
                break;
            case NOP:
                break;
            default:
                if (op.odexOnly())
                    System.out.println("Ignoring unsupported ODEX instruction " + op + " in method " + methId);
                else {
                    System.err.println("Unknown instruction type: " + op);
                    throw new RuntimeException("Quit! [methId = " + methId + ", lineNo = " + findLineForInstructionIndex(index) + "]");
                }
        }
    }

    /**
     * Write an if instruction.
     *
     * @param instr      the if instruction
     * @param regL       the left (or single) operand
     * @param regR       the right operand (-1 for none)
     * @param index      the instruction index
     */
    private void writeIf(Instruction instr, int regL, int regR, Opcode op, int index) {
        String insn = instructionId("if", index);
        ifs.add(new RawGoto(insn, index, absoluteAddr((OffsetInstruction)instr)));
        writeIfVar(insn, L_OP, local(regL));
        if (regR != -1)
            writeIfVar(insn, R_OP, local(regR));

        switch (op) {
        case IF_EQ:  case IF_EQZ:  writeOperatorAt(insn, "=="); break;
        case IF_NE:  case IF_NEZ:  writeOperatorAt(insn, "!="); break;
        case IF_LT:  case IF_LTZ:  writeOperatorAt(insn,  "<"); break;
        case IF_GE:  case IF_GEZ:  writeOperatorAt(insn, ">="); break;
        case IF_GT:  case IF_GTZ:  writeOperatorAt(insn,  ">"); break;
        case IF_LE:  case IF_LEZ:  writeOperatorAt(insn, "<="); break;
        }
    }

    private void handleFillArrayData(Instruction instr, int index) {
        int regA = ((OneRegisterInstruction) instr).getRegisterA();
        // Sanity check: fill-array-data should appear at most 2 instructions
        // after a new-array instruction.
        if (lastNewArrayInfo.index > (index + 2))
            System.err.println("Warning: suspicious fill-array-data + new-array pattern in " + methId);
        fillArrayInfo.registerFirstInstructionData(new FillArrayInfoEntry(absoluteAddr((OffsetInstruction)instr), regA, index, lastNewArrayInfo));
        // Consume the information.
        this.lastNewArrayInfo = null;
    }

    private void handleArrayPayload(ArrayPayload payload) {
        List<Number> numbers = payload.getArrayElements();
        int numbersSize = numbers.size();
        try {
            // Retrieve the entry of the original instruction that references
            // this payload. We assume the original instruction has already been
            // processed and thus the information exists: "though not required, it
            // is expected that most tools will choose to emit these instructions at
            // the ends of methods ..." (https://source.android.com/devices/tech/dalvik/dalvik-bytecode)
            FillArrayInfoEntry entry = fillArrayInfo.getOriginalEntryForTarget(currentInstrAddr);
            int regDest = entry.reg;
            int originalIndex = entry.index;
            String insn = instructionId("assign", originalIndex);
            String heapId = entry.newArrayInfo.heapId;
            for (int idx = 0; idx < numbersSize; idx++)
                _db.add(ARRAY_INITIAL_VALUE_FROM_CONST, insn, str(originalIndex), local(regDest), str(idx), numbers.get(idx).toString(), heapId, methId);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
    }


    /**
     * Handles move-result opcodes (either method invocation results or creation of
     * filled arrays).
     *
     * @param instr    the Dex instruction
     * @param index    the instruction index
     * @param op       the move-result opcode
     */
    private void writeMoveResult(Instruction instr, int index, Opcode op) {
        if (this.objReturnInfo == null) {
            // Sanity check: return-object information must be present.
            System.err.println("Internal error: result already consumed in method " +
                    methId + ", index = " + index);
            return;
        } else if (index != (objReturnInfo.index + 1)) {
            // Sanity check: a MOVE_RESULT* opcode must directly follow the
            // instruction that left the information about returning an object.
            System.err.println("Opcode " + op + " at index " + index +
                    " does not directly follow instruction " + objReturnInfo.index);
            return;
        }

        if (debug)
            logger.debug("Consuming object return info: " + objReturnInfo);

        int regDest = ((OneRegisterInstruction) instr).getRegisterA();
        switch (objReturnInfo.op) {
            case FILLED_NEW_ARRAY:
            case FILLED_NEW_ARRAY_RANGE: {
                    String insn = instructionId("assign", index);
                    boolean isEmpty = (objReturnInfo.argRegs.length == 0);
                    String[] heap = new String[1];
                    writeAssignHeapAllocation(regDest, objReturnInfo.retType, index, insn, isEmpty, heap);
                    writeInitialArrayValues(insn, index, regDest, objReturnInfo.argRegs, heap);
                }
                break;
            case INVOKE_DIRECT:
            case INVOKE_VIRTUAL:
            case INVOKE_STATIC:
            case INVOKE_INTERFACE:
            case INVOKE_SUPER:
            case INVOKE_DIRECT_RANGE:
            case INVOKE_VIRTUAL_RANGE:
            case INVOKE_STATIC_RANGE:
            case INVOKE_INTERFACE_RANGE:
            case INVOKE_SUPER_RANGE: {
                String insn = this.objReturnInfo.insn;
                _db.add(ASSIGN_RETURN_VALUE, insn, local(regDest));
                // Sanity check.
                if (this.objReturnInfo.retType == null)
                    System.err.println("Warning: no return type in " + objReturnInfo);
                break;
            }
            default:
                System.err.println("Unsupported opcode for returned object: " + objReturnInfo.op);
        }
        // Consume information so that sanity checking can find problems.
        this.objReturnInfo = null;
    }

    /**
     * Initialize array elements (filled-new-array opcodes).
     *
     * @param insn       the instruction id (one of the move-result opcodes)
     * @param regDest    the target register pointing to the array
     * @param argRegs    the sequence of initial values
     * @param heap       a single-element array containing the heap id
     */
    private void writeInitialArrayValues(String insn, int regDest, int index,
                                         int[] argRegs, String[] heap) {
        for (int idx = 0; idx < argRegs.length; idx++)
            _db.add(ARRAY_INITIAL_VALUE_FROM_LOCAL, insn, str(index), local(regDest), str(idx), local(argRegs[idx]), heap[0], methId);
    }

    private void writeSwitchTargets(Instruction instr, PredicateFile predicateFile) {
        FirstInstructionEntry entry = pendingSwitchInfo.getOriginalEntryForTarget(currentInstrAddr);
        String insn = instructionId("switch", entry.index);
        for (SwitchElement elem : ((SwitchPayload) instr).getSwitchElements()) {
            int branchAddr = entry.address + elem.getOffset();
            _db.add(predicateFile, insn, str(elem.getKey()), str(branchAddr));
        }
    }

    private void writeSwitchKey(Instruction instr, int index, PredicateFile predicateFile) {
        int testRegister = ((OneRegisterInstruction)instr).getRegisterA();
        String insn = instructionId("switch", index);
        _db.add(predicateFile, insn, str(index), local(testRegister), methId);
        this.pendingSwitchInfo.registerFirstInstructionData(new FirstInstructionEntry(absoluteAddr((OffsetInstruction)instr), index));
    }

    private void writeLoadOrStoreArrayIndex(ThreeRegisterInstruction tri, int index, PredicateFile predicateFile) {
        int valReg = tri.getRegisterA();
        int arrayReg = tri.getRegisterB();
        int indexReg = tri.getRegisterC();
        String insn = instructionId("assign", index);
        _db.add(predicateFile, insn, str(index), local(valReg), local(arrayReg), methId);
        _db.add(ARRAY_INSN_INDEX, insn, local(indexReg));
    }

    private void writeBinopTwoRegPlusLit(TwoRegisterInstruction tri, Opcode op, int index) {
        int regDest = tri.getRegisterA();
        int regSource = tri.getRegisterB();
        String insn = instructionId("assign", index);
        writeAssignBinop(insn, index, local(regDest), methId);
        writeAssignOperFrom(insn, L_OP, local(regSource));
        writeStatementType(insn, op);
    }

    private void writeBinopThreeReg(int regDest, int regSource1, int regSource2, Opcode op, int index) {
        String insn = instructionId("assign", index);
        writeAssignBinop(insn, index, local(regDest), methId);
        writeAssignOperFrom(insn, L_OP, local(regSource1));
        writeAssignOperFrom(insn, R_OP, local(regSource2));
        writeStatementType(insn, op);
    }

    /**
     * Given an instruction and its opcode, write facts about the instruction type.
     *
     * @param insn   the instruction id
     * @param op     the instruction opcode
     */
    private void writeStatementType(String insn, Opcode op) {
        String type = null;
        switch (op) {
        case ADD_INT: case SUB_INT: case  MUL_INT : case  DIV_INT:
        case REM_INT: case AND_INT: case   OR_INT : case  XOR_INT:
        case SHL_INT: case SHR_INT: case USHR_INT : case RSUB_INT:
        case ADD_INT_2ADDR: case  SUB_INT_2ADDR: case MUL_INT_2ADDR:
        case DIV_INT_2ADDR: case  REM_INT_2ADDR: case AND_INT_2ADDR:
        case  OR_INT_2ADDR: case  XOR_INT_2ADDR: case SHL_INT_2ADDR:
        case SHR_INT_2ADDR: case USHR_INT_2ADDR:
        case ADD_INT_LIT16: case MUL_INT_LIT16: case DIV_INT_LIT16:
        case REM_INT_LIT16: case AND_INT_LIT16: case  OR_INT_LIT16:
        case XOR_INT_LIT16:
        case ADD_INT_LIT8: case RSUB_INT_LIT8: case  MUL_INT_LIT8:
        case DIV_INT_LIT8: case  REM_INT_LIT8: case  AND_INT_LIT8:
        case  OR_INT_LIT8: case  XOR_INT_LIT8: case  SHL_INT_LIT8:
        case SHR_INT_LIT8: case USHR_INT_LIT8:
            type = "int"; break;
        case ADD_LONG: case SUB_LONG: case  MUL_LONG : case DIV_LONG:
        case REM_LONG: case AND_LONG: case   OR_LONG : case XOR_LONG:
        case SHL_LONG: case SHR_LONG: case USHR_LONG : case CMP_LONG:
        case ADD_LONG_2ADDR: case  SUB_LONG_2ADDR: case MUL_LONG_2ADDR:
        case DIV_LONG_2ADDR: case  REM_LONG_2ADDR: case AND_LONG_2ADDR:
        case  OR_LONG_2ADDR: case  XOR_LONG_2ADDR: case SHL_LONG_2ADDR:
        case SHR_LONG_2ADDR: case USHR_LONG_2ADDR:
            type = "long"; break;
        case ADD_FLOAT: case  SUB_FLOAT : case  MUL_FLOAT : case DIV_FLOAT:
        case REM_FLOAT: case CMPL_FLOAT : case CMPG_FLOAT :
        case ADD_FLOAT_2ADDR: case SUB_FLOAT_2ADDR: case MUL_FLOAT_2ADDR:
        case DIV_FLOAT_2ADDR: case REM_FLOAT_2ADDR:
            type = "float"; break;
        case ADD_DOUBLE: case  SUB_DOUBLE : case  MUL_DOUBLE: case DIV_DOUBLE:
        case REM_DOUBLE: case CMPL_DOUBLE : case CMPG_DOUBLE:
        case ADD_DOUBLE_2ADDR: case SUB_DOUBLE_2ADDR: case MUL_DOUBLE_2ADDR:
        case DIV_DOUBLE_2ADDR: case REM_DOUBLE_2ADDR:
            type = "double"; break;
        }
        if (type == null)
            System.err.println("Cannot determine statement type for instruction " + insn);
        else
            _db.add(STATEMENT_TYPE, insn, type);
    }

    private void writeAssignCast(OneRegisterInstruction ori, ReferenceInstruction ri, int index) {
        int reg = ori.getRegisterA();
        String insn = instructionId("assign", index);
        String typeName = raiseTypeId(((DexBackedTypeReference)ri.getReference()).getType());
        _db.add(ASSIGN_CAST, insn, str(index), local(reg), local(reg), typeName, methId);
    }

    private void queueGoto(int index, int addrTo) {
        gotos.add(new RawGoto(instructionId("goto", index), index, addrTo));
    }

    private void writeGoto(String insn, int indexTo, int index) {
        _db.add(GOTO, insn, str(index), str(indexTo), methId);
    }

    private void writeExitMonitor(int registerA, int index) {
        String insn = instructionId("exit-monitor", index);
        _db.add(EXIT_MONITOR, insn, str(index), local(registerA), methId);
    }

    private void writeEnterMonitor(int registerA, int index) {
        String insn = instructionId("enter-monitor", index);
        _db.add(ENTER_MONITOR, insn, str(index), local(registerA), methId);
    }

    private void writeAssignUnop(TwoRegisterInstruction tri, int index) {
        String insn = instructionId("assign", index);
        writeAssignUnop(insn, index, local(tri.getRegisterA()), methId);
        writeAssignOperFrom(insn, L_OP, local(tri.getRegisterB()));
    }

    private void writeThrow(int reg, int index) {
        String name = str(reg);
        String insn = numberedInstructionId(methId, name, counter);
        _db.add(THROW, insn, str(index), local(reg), methId);
    }

    private void writeAssignInstanceOf(TwoRegisterInstruction tri, ReferenceInstruction ri, int index) {
        String insn = instructionId("assign", index);
        String to = local(tri.getRegisterA());
        String from = local(tri.getRegisterB());
        String className = raiseTypeId(((DexBackedTypeReference)ri.getReference()).getType());
        _db.add(ASSIGN_INSTANCE_OF, insn, str(index), from, to, className, methId);
    }

    private void writeAssignLocal(TwoRegisterInstruction tri, int index) {
        String to = local(tri.getRegisterA());
        String from = local(tri.getRegisterB());
        writeAssignLocal(instructionId("assign", index), index, from, to, methId);
    }

    private String local(int reg) {
        // System.out.println(methId + ", localsCount=" + this.localsCount + ",  reg=" + reg);
        if (reg < localRegCount)
            return DexRepresentation.local(methId, reg);
        else
            return DexRepresentation.param(methId, reg - localRegCount);
    }

    private String instructionId(String kind, int index) {
        return JavaRepresentation.instructionId(methId, kind, index);
    }

    private void writeLoadOrStoreIField(Instruction instr, int index,
                                        boolean isLoad, Collection<FieldOp> fieldOps) {
        TwoRegisterInstruction tri = (TwoRegisterInstruction)instr;
        int regA = tri.getRegisterA();
        int regB = tri.getRegisterB();
        PredicateFile target= isLoad ? LOAD_INST_FIELD : STORE_INST_FIELD;
        writeFieldOp(instr, target, local(regA), local(regB), index, fieldOps);
    }

    private void writeLoadOrStoreSField(Instruction instr, int index,
                                        boolean isLoad, Collection<FieldOp> fieldOps) {
        int reg = ((OneRegisterInstruction)instr).getRegisterA();
        PredicateFile target= isLoad ? LOAD_STATIC_FIELD : STORE_STATIC_FIELD;
        writeFieldOp(instr, target, local(reg), null, index, fieldOps);
    }

    private void writeFieldOp(Instruction instr, PredicateFile target,
                              String localA, String localB, int index,
                              Collection<FieldOp> fieldOps) {
        String insn = instructionId("assign", index);
        Reference fieldRef = ((ReferenceInstruction)instr).getReference();
        FieldInfo fi = new DexFieldInfo((DexBackedFieldReference)fieldRef);
        fieldOps.add(new FieldOp(target, insn, str(index), localA, localB, fi, methId));
    }

    private void writeAssignClassConstant(Instruction instr, int index) {
        String insn = instructionId("assign", index);
        String jvmClassName = jvmTypeOf((ReferenceInstruction)instr);
        String className = raiseTypeId(jvmClassName);
        String heapId = JavaRepresentation.classConstant(className);
        writeClassHeap(heapId, className);
        String lineNo = strOfLineNo(findLineForInstructionIndex(index));
        int reg = ((OneRegisterInstruction)instr).getRegisterA();
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heapId, local(reg), methId, lineNo);
    }

    private void writeReturnVoid(int index) {
        String insn = instructionId("return-void", index);
        _db.add(RETURN_VOID, insn, str(index), methId);
    }

    private void writeReturn(int reg, int index) {
        String insn = instructionId("return", index);
        _db.add(RETURN, insn, str(index), local(reg), methId);
    }

    private void writeNewArray(Instruction instr, int index) {
        TwoRegisterInstruction tri = (TwoRegisterInstruction)instr;
        int regDest = tri.getRegisterA();
        int regSize = tri.getRegisterB();
        ReferenceInstruction ri = (ReferenceInstruction)instr;
        TypeReference typeRef = (DexBackedTypeReference)ri.getReference();
        String arrayType = raiseTypeId(typeRef.getType());
        boolean isEmpty = arraySizeIsZero(regSize, index);
        String[] heapIdBox = new String[1];
        writeAssignHeapAllocation(regDest, arrayType, index, instructionId("assign", index), isEmpty, heapIdBox);
        writeArrayTypes(arrayType);
        this.lastNewArrayInfo = new NewArrayInfo(index, heapIdBox[0]);
    }

    private void writeFilledNewArray(Instruction instr, int index, Opcode op) {
        String insn = instructionId("assign", index);
        String arrayType = raiseTypeId(jvmTypeOf((ReferenceInstruction)instr));
        String componentType = writeArrayTypes(arrayType);
        if (componentType == null)
            return;
        int arraySize = ((VariableRegisterInstruction)instr).getRegisterCount();
        String[] paramTypes = Collections.nCopies(arraySize, componentType).toArray(new String[arraySize]);
        MethodSig mSig = new MethodSig(arrayType, paramTypes);

        // Remember information to be used by subsequent move-result-object
        this.objReturnInfo = new ObjectReturnInfo(insn, regsFor(instr), mSig, true, op, index);
    }

    private String writeArrayTypes(String arrayType) {
        int bracketIndex = arrayType.indexOf('[');
        if (bracketIndex == -1) {
            System.err.println("Warning: not an array type: " + arrayType);
            return null;
        }
        String componentType = arrayType.substring(0, bracketIndex);
        writeArrayTypes(arrayType, componentType);
        return componentType;
    }

    private void writeInvoke(Instruction instr, Opcode op, int index) {
        ReferenceInstruction ri = (ReferenceInstruction)instr;
        DexBackedMethodReference mRef = (DexBackedMethodReference)ri.getReference();
        MethodSig mSig = newOrCachedMethodSig(mRef);
        boolean isStaticInvoke = (op == Opcode.INVOKE_STATIC) || (op == Opcode.INVOKE_STATIC_RANGE);
        String insn = numberedInstructionId(methId, mSig.getMid(), counter);

        // Remember information to be used by subsequent move-result-object
        this.objReturnInfo = new ObjectReturnInfo(insn, regsFor(instr), mSig, isStaticInvoke, op, index);

        Integer lineNoInteger = findLineForInstructionIndex(index);
        String lineNo = strOfLineNo(lineNoInteger);

        int argStartPos = 0;
        String base = null;
        if (objReturnInfo.baseReg != null) {
            base = local(objReturnInfo.baseReg);
            writeActualParam(0, insn, base);
            argStartPos = 1;
        }

//        System.out.println("argRegs = " + objReturnInfo.argRegs);
        int[] argRegs = objReturnInfo.argRegs;
        for (int argPos = 0; argPos < argRegs.length; argPos++)
            writeActualParam(argStartPos + argPos, insn, local(argRegs[argPos]));

        if (lineNoInteger != null)
            _db.add(METHOD_INV_LINE, insn, lineNo);

        switch (op) {
            case INVOKE_DIRECT:
            case INVOKE_DIRECT_RANGE:
                _db.add(SPECIAL_METHOD_INV, insn, str(index), mSig.sig, base, methId);
                break;
            case INVOKE_STATIC:
            case INVOKE_STATIC_RANGE:
                _db.add(STATIC_METHOD_INV, insn, str(index), mSig.sig, methId);
                break;
            case INVOKE_VIRTUAL:
            case INVOKE_VIRTUAL_RANGE:
            case INVOKE_INTERFACE:
            case INVOKE_INTERFACE_RANGE:
                _db.add(VIRTUAL_METHOD_INV, insn, str(index), mSig.sig, base, methId);
                break;
            case INVOKE_SUPER:
            case INVOKE_SUPER_RANGE:
                _db.add(SUPER_METHOD_INV, insn, str(index), mSig.sig, base, methId);
                break;
            default:
                throw new RuntimeException("Internal error: cannot handle invocation type " + op);
        }
    }

    private void writeAssignStringConstant(int reg, String content, int index) {
        String heapId = writeStringConstant(content);
        String insn = instructionId("assign", index);
        String lineNo = strOfLineNo(findLineForInstructionIndex(index));
        String var = local(reg);
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heapId, var, methId, lineNo);

        if (extractRegisterTypes)
            writeLocal(var, "java.lang.String", methId);
    }

    /**
     * Write an assignment of a heap to a local.
     *
     * @param reg            the register of the local
     * @param type           the type of the allocation
     * @param index          the index of the allocating instruction
     * @param insn           the instruction id
     * @param isEmptyArray   if the allocation is a 0-size array
     * @param heapBox        a single-element array to hold the heap id (or null)
     */
    private void writeAssignHeapAllocation(int reg, String type, int index,
                                           String insn, boolean isEmptyArray,
                                           String[] heapBox) {
        String heap = JavaRepresentation.heapAllocId(methId, type, counter);
        if (heapBox != null)
            heapBox[0] = heap;

        _db.add(NORMAL_HEAP, heap, type);
        String lineNo = strOfLineNo(findLineForInstructionIndex(index));
        String var = local(reg);
        _db.add(ASSIGN_HEAP_ALLOC, insn, str(index), heap, var, methId, lineNo);

        if (isEmptyArray)
            _db.add(EMPTY_ARRAY, heap);

        if (extractRegisterTypes)
            writeLocal(var, type, methId);
    }

    private boolean arraySizeIsZero(int arraySizeReg, int index) {
        boolean b = ((arraySizeReg > -1) &&
                (zeroedArraySizeRegister != null) &&
                (index == (zeroedArraySizeRegister.index + 1)) &&
                (arraySizeReg == zeroedArraySizeRegister.reg));
        // Consume zeroed register information.
        zeroedArraySizeRegister = null;
        return b;
    }

    private void writeAssignNumConstant(int reg, String constant, int index) {
        String insn = instructionId("assign", index);
        _db.add(ASSIGN_NUM_CONST, insn, str(index), constant, local(reg), methId);
    }

    private MethodSig newOrCachedMethodSig(DexBackedMethodReference ref) {
        return cachedMethodDescriptors.computeIfAbsent(ref.toString(), x -> new MethodSig(ref));
    }

    private Integer findLineForInstructionIndex(int index) {
        Map.Entry<Integer, Integer> entry = lineNumbers.floorEntry(index);
        return (entry == null) ? null : entry.getValue();
    }

    private int absoluteAddr(OffsetInstruction instr) {
        return currentInstrAddr + instr.getCodeOffset();
    }
}
