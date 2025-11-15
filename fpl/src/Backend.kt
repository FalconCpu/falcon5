private lateinit var currentFunction: Function
private var changesMade = false

const val MAX_INLINE_INSTRS = 20

private fun Function.rebuildIndex() {
    prog.removeIf { it is InstrNop }

    for(label in labels)
        label.useCount = 0
    for(reg in regs) {
        reg.useCount = 0
        if (reg is TempReg)
            reg.def = null
    }

    // Update the index of each instruction and label, and increment the use count of labels and registers
    for((index,instr) in prog.withIndex()) {
        instr.index = index
        when(instr) {
            is InstrLabel -> instr.label.index = index
            is InstrJump -> instr.label.useCount++
            is InstrBranch -> instr.label.useCount++
            else -> {}
        }
        for(src in instr.getSrcReg())
            src.useCount++
        val destReg = instr.getDestReg()
        if (destReg is TempReg) {
            if (destReg.def!=null)
                error("TempReg $destReg is not in SSA form")
            destReg.def = instr
        }
    }

    for((index,reg) in regs.withIndex()) {
        reg.index = index
    }
}


fun Instr.changeToNop() {
    if (currentFunction.prog[index] is InstrNop) return
    if (debug)
        println("Changing instruction at $index: '$this' to NOP")
    currentFunction.prog[index] = InstrNop()
    changesMade = true
}

fun Instr.replace(newInstr: Instr) {
    if (debug)
        println("Replacing instruction at $index: '$this' with '$newInstr'")
    currentFunction.prog[index] = newInstr
    changesMade = true
}

fun Reg.isConstant() : Int? {
    if (this !is TempReg) return null
    val def = this.def ?: return null
    if (def is InstrMovLit)
        return def.lit
    if (def is InstrMov)
        return def.src.isConstant()
    return null
}

private fun Int.isPowerOfTwo() = this > 0 && (this and (this - 1)) == 0
private fun Int.log2() = Integer.numberOfTrailingZeros(this)

private val nullOffset = Pair(null,0)

// Check if a register is defined as another register plus an offset constant
// If so, return the base register and the offset
private fun isOffsetFromReg(reg:Reg) : Pair<Reg?,Int> {
    if (reg !is TempReg) return nullOffset
    val def = reg.def ?: return nullOffset
    if (def is InstrAluLit && def.op==BinOp.ADD_I) {
        val (recReg,recOffset) = isOffsetFromReg(def.src)
        if (recReg!=null)
            return Pair(recReg, recOffset + def.lit)
        return Pair(def.src, def.lit)
    }
    return nullOffset
}

private fun MutableMap<Reg,Reg>.inlineMap(r:Reg) = getOrPut(r) {
    when(r) {
        is CpuReg -> r
        is UserReg -> currentFunction.newUserTemp("inline_${r.name}")
        is TempReg -> currentFunction.newTemp("inline_${r.name}")
        is CompoundReg -> {
            val ret = currentFunction.newInlineCompoundReg(r)
            for ((rr,zz) in r.regs.zip( ret.regs))
                this[rr] = zz
            ret
        }
    }
}

private fun MutableMap<Label,Label>.inlineMap(l:Label) = getOrPut(l) {
    currentFunction.newLabel()
}

private fun inlineFuncCall(callSite:Instr) : List<Instr> {
    require(callSite is InstrCall)
    val regMap = mutableMapOf<Reg,Reg>()
    val labelMap = mutableMapOf<Label,Label>()
    val ret = mutableListOf<Instr>()
    val func = callSite.func
    val args = callSite.args
    val params = (if (func.thisSymbol!=null) listOf(func.thisSymbol) else emptyList()) +
                 func.parameters
    val paramRegs = params.map{regMap.inlineMap(func.getReg(it))} +
            (if (func.returnDestAddr!=null) listOf(regMap.inlineMap(func.returnDestAddr)) else emptyList())
    val retReg = regMap.inlineMap(func.returnRegister)

    // Map the function parameters to the call site arguments
    require(paramRegs.size == args.size)
    for ((param,arg) in paramRegs.zip(args))
        ret.addMov(param, arg)

    for (instr in func.prog) {
        when(instr) {
            is InstrLabel -> ret += InstrLabel(labelMap.inlineMap(instr.label))
            is InstrMov -> ret += InstrMov(regMap.inlineMap(instr.dest), regMap.inlineMap(instr.src))
            is InstrAlu -> ret += InstrAlu(instr.op, regMap.inlineMap(instr.dest), regMap.inlineMap(instr.src1), regMap.inlineMap(instr.src2))
            is InstrAluLit -> ret += InstrAluLit(instr.op, regMap.inlineMap(instr.dest), regMap.inlineMap(instr.src), instr.lit)
            is InstrBranch -> ret += InstrBranch(instr.op, regMap.inlineMap(instr.src1), regMap.inlineMap(instr.src2), labelMap.inlineMap(instr.label))
            is InstrCall -> ret += InstrCall(instr.func, regMap.inlineMap(instr.dest), instr.args.map { regMap.inlineMap(it) })
            is InstrEnd -> {}
            is InstrFpu -> ret += InstrFpu(instr.op, regMap.inlineMap(instr.dest), regMap.inlineMap(instr.src1), regMap.inlineMap(instr.src2))
            is InstrIndCall -> ret += InstrIndCall(regMap.inlineMap(instr.func), regMap.inlineMap(instr.dest), instr.args.map { regMap.inlineMap(it) }, instr.retType)
            is InstrIndex -> ret += InstrIndex(instr.size, regMap.inlineMap(instr.dest), regMap.inlineMap(instr.src), instr.bounds)
            is InstrJump -> ret += InstrJump(labelMap.inlineMap(instr.label))
            is InstrLea -> ret += InstrLea(regMap.inlineMap(instr.dest), instr.src)
            is InstrLineNo -> error("LineNumbers should not be present at inlining")
            is InstrLoad -> ret += InstrLoad(instr.size, regMap.inlineMap(instr.dest), regMap.inlineMap(instr.addr), instr.offset)
            is InstrLoadField -> ret += InstrLoadField(instr.size, regMap.inlineMap(instr.dest), regMap.inlineMap(instr.addr), instr.offset)
            is InstrMovLit -> ret += InstrMovLit(regMap.inlineMap(instr.dest), instr.lit)
            is InstrNop -> ret += InstrNop()
            is InstrNullCheck -> ret += InstrNullCheck(regMap.inlineMap(instr.src))
            is InstrStart -> {}
            is InstrStore -> ret += InstrStore(instr.size, regMap.inlineMap(instr.src), regMap.inlineMap(instr.addr), instr.offset)
            is InstrStoreField -> ret += InstrStoreField(instr.size, regMap.inlineMap(instr.src), regMap.inlineMap(instr.addr), instr.offset)
            is InstrSyscall -> ret += InstrSyscall(instr.num, regMap.inlineMap(instr.dest), instr.args.map { regMap.inlineMap(it) })
            is InstrVCall -> ret += InstrVCall(instr.func, regMap.inlineMap(instr.dest), instr.args.map { regMap.inlineMap(it) })
        }
    }

    if (callSite.dest != zeroReg)
        ret.addMov(callSite.dest, retReg)

    return ret
}

fun MutableList<Instr>.addMov(dest:Reg, src:Reg) {
    if (dest is CompoundReg) {
        require(src is CompoundReg)
        require(dest.regs.size == src.regs.size)
        for (i in src.regs.indices)
            addMov(dest.regs[i], src.regs[i])
    } else
        this += InstrMov(dest, src)
}

private fun Function.lookForInlineCalls() {
    currentFunction = this
    rebuildIndex()
    if (debug) {
        println("\nLooking for inline calls in function ${name}:")
        for (instr in prog)
            println(instr)
    }
    var changesMade = false
    val newProg = mutableListOf<Instr>()
    for(instr in prog) {
        if (instr is InstrCall && instr.func.prog.size <= MAX_INLINE_INSTRS &&
            !instr.func.isVararg && instr.func.qualifier!=TokenKind.EXTERN &&
            instr.func.returnType !is TypeErrable  &&       // Cannot inline functions that can return errors
            !noInline)  // If Inline is disabled, do not inline
        {  // TODO check for recursion
            if (debug)
                println("Inlining call to function ${instr.func.name} at instruction ${instr.index} in function $name")
            newProg.addAll(inlineFuncCall(instr))
            changesMade = true
        } else {
            newProg += instr
        }
    }

    if (changesMade) {
        prog.clear()
        prog.addAll(newProg)
    }

    if (debug) {
            println("After inlining calls in function ${name}:")
            for (instr in prog)
                println(instr)
    }
}


private fun Function.loadParameters(newBody:MutableList<Instr>) {
    // copy parameters from cpu regs into UserRegs
    var index = 1
    if (thisSymbol!=null)
        newBody += InstrMov(getReg(thisSymbol), cpuRegs[index++]) // 'this' pointer
    for(param in parameters) {
        val reg = getReg(param)
        if (reg is CompoundReg)
            for (r in reg.regs)
                newBody += InstrMov(r, cpuRegs[index++])
        else
            newBody += InstrMov(reg, cpuRegs[index++])
    }

    if (returnDestAddr!=null)
        // For an aggregate return type, copy the return address an arg
        newBody += InstrMov(returnDestAddr, cpuRegs[index++])
}

private fun expandCallInstr(callInstr: Instr, newBody:MutableList<Instr>) {
    val args = when (callInstr) {
        is InstrCall -> callInstr.args
        is InstrVCall -> callInstr.args
        is InstrIndCall -> callInstr.args
        else -> error("Got ${callInstr.javaClass} when expected InstrCall")
    }
    val retReg = when (callInstr) {
        is InstrCall -> callInstr.dest
        is InstrVCall -> callInstr.dest
        is InstrIndCall -> callInstr.dest
        else -> error("Got ${callInstr.javaClass} when expected InstrCall")
    }

    assert(args.size <= 7)
    var index = 1
    for (arg in args) {
        if (arg is CompoundReg)
            for (r in arg.regs)
                newBody += InstrMov(cpuRegs[index++], r)
        else
            newBody += InstrMov(cpuRegs[index++], arg)
    }
    newBody += when(callInstr) {
        is InstrCall -> InstrCall(callInstr.func, zeroReg, emptyList())
        is InstrVCall -> InstrVCall(callInstr.func, zeroReg, emptyList())
        is InstrIndCall -> InstrIndCall(callInstr.func, zeroReg, emptyList(), callInstr.retType)
        else -> error("Got ${callInstr.javaClass} when expected Instr Call")
    }
    index = 8
    if (retReg is CompoundReg)
        for (i in retReg.regs.indices)
            newBody += InstrMov(retReg.regs[i], cpuRegs[index--])
    else if (retReg != zeroReg)
        newBody += InstrMov(retReg, cpuRegs[index])
}

private fun Function.lowerFunctionCalls() {
    // Insert the register moves before and after each function call to move arguments into the correct registers
    val newBody = mutableListOf<Instr>()

    for (instr in prog)
        when(instr) {
            is InstrStart -> {
                newBody += instr
                loadParameters(newBody)
            }

            is InstrEnd -> {
                val retReg = instr.src
                if (returnType!=TypeUnit) {
                    var index = 8
                    if (retReg is CompoundReg)
                        for (i in retReg.regs)
                            newBody += InstrMov(cpuRegs[index--], i)
                    else if (retReg != zeroReg)
                        newBody += InstrMov(cpuRegs[8], retReg)
                }
                newBody += InstrLabel(exitLabel)
                newBody += InstrEnd(zeroReg)
            }

            is InstrCall -> expandCallInstr(instr, newBody)
            is InstrVCall -> expandCallInstr(instr, newBody)
            is InstrIndCall -> expandCallInstr(instr, newBody)
            else -> newBody += instr
        }

    prog.clear()
    prog.addAll(newBody)
    if (debug) {
        println("After lowering function calls in function $name:")
        for (instr in prog)
            println(instr)
    }
}


var unreachable = false

fun Instr.peephole() {
    if (this is InstrLabel)
        unreachable = false
    if (unreachable)
        return changeToNop()

    when(this) {
        is InstrMov -> {
            // Remove an instruction that is never read
            if (dest.useCount == 0 && dest !is CpuReg) {
                if (debug)
                    println("Removing MOV to $dest that is never read")
                return changeToNop()
            }

            // Remove an instruction that moves the same register
            if (src==dest)
                return changeToNop()
        }

        is InstrAlu -> {
            // Remove an instruction that is never read
            if (dest.useCount == 0 && dest !is CpuReg)
                return changeToNop()

            // Replace an instruction that performs a constant operation with a literal
            val rhsConst = src2.isConstant()
            if (rhsConst!=null && rhsConst in -0x1000..0xFFF)
                return replace(InstrAluLit(op, dest, src1, rhsConst))

            if (op.isCommutative()) {
                val lhsConst = src1.isConstant()
                if (lhsConst != null && lhsConst in -0x1000..0xFFF)
                    return replace(InstrAluLit(op, dest, src2, lhsConst))
            }
        }

        is InstrAluLit -> {
            // Remove an instruction that is never read
            if (dest.useCount == 0 && dest !is CpuReg)
                return changeToNop()

            if ((op==BinOp.ADD_I || op==BinOp.OR_I || op==BinOp.XOR_I || op==BinOp.SUB_I ||
                 op==BinOp.LSL_I || op==BinOp.LSR_I || op==BinOp.ASR_I) && lit==0)
                    return replace(InstrMov(dest, src))
            if ((op==BinOp.MUL_I || op==BinOp.DIV_I) && lit==1)
                return replace(InstrMov(dest, src))
            if ((op==BinOp.MUL_I || op==BinOp.AND_I) && lit==0)
                return replace(InstrMovLit(dest, 0))
            if (op==BinOp.AND_I && lit==-1)
                return replace(InstrMov(dest, src))
            if (op==BinOp.MUL_I && lit.isPowerOfTwo())
                return replace(InstrAluLit(BinOp.LSL_I, dest, src, lit.log2()))
            if (op==BinOp.DIV_I && lit.isPowerOfTwo())
                return replace(InstrAluLit(BinOp.ASR_I, dest, src, lit.log2()))
            if (op==BinOp.MOD_I && lit.isPowerOfTwo())
                return replace(InstrAluLit(BinOp.AND_I, dest, src, lit - 1))
            val srcLit = src.isConstant()
            if (srcLit!=null) {
                val result = op.evaluate(srcLit, lit)
                return replace(InstrMovLit(dest, result))
            }
        }

        is InstrIndex -> {
            // Remove an instruction that is never read
            if (dest.useCount == 0 && dest !is CpuReg)
                return changeToNop()

            val indexConst = src.isConstant()
            val boundsConst = bounds.isConstant()
            if (boundsConst!=null && indexConst!=null) {
                val offset = indexConst * size
                if (indexConst !in 0..<boundsConst)
                    Log.error(nullLocation, "Array index out of bounds: $indexConst not in [0..${boundsConst-1}]")
                return replace(InstrMovLit(dest, offset))
            }
        }


        is InstrMovLit -> {
            // Remove an instruction that is never read
            if (dest.useCount == 0 && dest !is CpuReg)
                return changeToNop()
        }

        is InstrLoad -> {
            // Remove an instruction that is never read
            if (dest.useCount == 0 && dest !is CpuReg)
                return changeToNop()

            // Look for a sequence 'add t1, x, imm; ld t2, [t1+offset]' and replace it with 'ld t2, [x+offset+imm]' val srcInstr = addr.def
            val (addrReg,addrOffset) = isOffsetFromReg(addr)
            if (addrReg!=null) {
                val newOffset = addrOffset + offset
                if (newOffset in -0x800..0x7FF)
                    return replace(InstrLoad(size, dest, addrReg, newOffset))
            }
        }

        is InstrLoadField -> {
            // Remove an instruction that is never read
            if (dest.useCount == 0 && dest !is CpuReg)
                return changeToNop()

            // Look for a sequence 'add t1, x, imm; ld t2, [t1+offset]' and replace it with 'ld t2, [x+offset+imm]' val srcInstr = addr.def
            val (addrReg,addrOffset) = isOffsetFromReg(addr)
            if (addrReg!=null) {
                val newOffset = addrOffset + offset.offset
                if (newOffset in -0x800..0x7FF)
                    return replace(InstrLoad(size, dest, addrReg, newOffset))
            }
        }

        is InstrStore -> {
            // Look for a sequence 'add t1, x, imm; st t2, [t1+offset]' and replace it with 'st t2, [x+offset+imm]'
            val (addrReg,addrOffset) = isOffsetFromReg(addr)
            if (addrReg!=null) {
                val newOffset = addrOffset + offset
                if (newOffset in -0x800..0x7FF)
                    return replace(InstrStore(size, src, addrReg, newOffset))
            }
            // Look for a nearby load that fetches the same address as this store
            for(i in 0..8) {
                val x =currentFunction.prog[index+ i]
                if (x is InstrJump || x is InstrBranch || x is InstrLabel || x is InstrCall || x is InstrVCall || x is InstrIndCall || x is InstrEnd)
                    break
                if (x is InstrLoad && x.size == this.size && x.addr == this.addr && x.offset == this.offset)
                    x.replace(InstrMov(x.dest, this.src))
            }
        }

        is InstrStoreField -> {
            // Look for a sequence 'add t1, x, imm; st t2, [t1+offset]' and replace it with 'st t2, [x+offset+imm]'
            val (addrReg,addrOffset) = isOffsetFromReg(addr)
            if (addrReg!=null) {
                val newOffset = addrOffset + offset.offset
                if (newOffset in -0x800..0x7FF)
                    return replace(InstrStore(size, src, addrReg, newOffset))
            }
            // Look for a nearby load that fetches the same address as this store
            for(i in 0..8) {
                val x =currentFunction.prog[index+ i]
                if (x is InstrJump || x is InstrBranch || x is InstrLabel || x is InstrCall || x is InstrVCall || x is InstrIndCall || x is InstrEnd)
                    break
                if (x is InstrLoadField && x.size == this.size && x.addr == this.addr && x.offset == this.offset)
                    x.replace(InstrMov(x.dest, this.src))
            }
        }

        is InstrJump -> {
            val targetInstr = currentFunction.prog[label.index+1]
            // Remove a jump instruction that jumps to the next instruction
            if (label.index == index + 1)
                changeToNop()
            else if (label.index==index+2 && currentFunction.prog[index+1] is InstrLabel)
                changeToNop()
            else if (targetInstr is InstrJump) {
                // Replace a jump to a jump
                replace(InstrJump(targetInstr.label))
            } else
                unreachable = true  // Mark code after a jump as unreachable until a label is encountered
        }

        is InstrLabel -> {
            // Remove a label instruction that is never used
            if (label.useCount == 0)
                changeToNop()
        }

        is InstrBranch -> {
            val nextInstr = currentFunction.prog[index + 1]
            val targetInstr = currentFunction.prog[label.index+1]
            if (label.index == index + 1) {
                // Remove a branch instruction that jumps to the next instruction
                changeToNop()
            } else if (label.index == index + 2 && nextInstr is InstrJump) {
                // Replace a branch over a jump
                replace(InstrBranch(op.invertBranch(), src1, src2, nextInstr.label))
                nextInstr.changeToNop()
            } else if (src1.isConstant() == 0) {
                replace(InstrBranch(op, zeroReg, src2, label))
            } else if (src2.isConstant() == 0) {
                replace(InstrBranch(op, src1, zeroReg, label))
            } else if (targetInstr is InstrJump) {
                // Replace a branch to a jump
                replace(InstrBranch(op, src1, src2, targetInstr.label))
            }

        }

        else -> {}
    }
}

private fun BinOp.invertBranch() : BinOp = when(this) {
    BinOp.EQ_I -> BinOp.NE_I
    BinOp.NE_I -> BinOp.EQ_I
    BinOp.LT_I -> BinOp.GE_I
    BinOp.GT_I -> BinOp.LE_I
    BinOp.LE_I -> BinOp.GT_I
    BinOp.GE_I -> BinOp.LT_I
    else -> error("Cannot invert branch condition $this")
}



// Run the peephole optimization pass - return true if any changes were made
private fun runPeephole() {
    unreachable = false
    do {
        changesMade = false
        currentFunction.rebuildIndex()
        for(instr in currentFunction.prog)
            instr.peephole()
    } while (changesMade)
}

fun genCodeFuncCallLowering(instr:Instr, newProg:MutableList<Instr>) {
    val args = instr.getSrcReg()
    val retReg = instr.getDestReg()
    val retType = when (instr) {
        is InstrCall -> instr.func.returnType
        is InstrVCall -> instr.func.returnType
        is InstrIndCall -> instr.retType
        else -> error("Expected InstrCall, got ${instr.javaClass}")
    }
    assert(args.size<=7)
    var index = 1
    for (i in args)
        if (i is CompoundReg)
            for (r in i.regs)
                newProg += InstrMov(cpuRegs[index++], r)
        else
            newProg += InstrMov(cpuRegs[index++], i)

    newProg += when(instr) {
        is InstrCall -> InstrCall(instr.func, zeroReg, emptyList())
        is InstrVCall -> InstrVCall(instr.func, zeroReg, emptyList())
        is InstrIndCall -> InstrIndCall(instr.func, zeroReg, emptyList(), instr.retType)
        else -> error("Expected InstrCall, got ${instr.javaClass}")
    }


    if (retType is TypeStruct) {
        // Do nothing, already handled
    } else if (retReg is CompoundReg)
        for((index,reg) in retReg.regs.withIndex())
            newProg += InstrMov(reg, cpuRegs[8 - index])
    else if (retReg!=null && retReg!=zeroReg)
        newProg += InstrMov(retReg, cpuRegs[8])
}

fun Function.runBackend() {
    if (debug) {
        println("\n\nRunning backend on function $name")
        for (instr in prog)
            println(instr)
    }
    currentFunction = this
    runPeephole()
    CommonSubexpr(this).run()
    runPeephole()
    InstrScheduler.runScheduler(this)
    this.rebuildIndex()
    val liveMap = LiveMap(this)
    RegisterAllocator(this, liveMap).run()
    runPeephole()
}


fun runBackend() {
    for(func in allFunctions)
        func.lookForInlineCalls()

    for(func in allFunctions)
        func.lowerFunctionCalls()

    for(func in allFunctions)
        func.runBackend()
}