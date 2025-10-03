private lateinit var currentFunction: Function
private var changesMade = false

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

    for((index,reg) in regs.withIndex())
        reg.index = index
}


fun Instr.changeToNop() {
    if (currentFunction.prog[index] is InstrNop) return
    currentFunction.prog[index] = InstrNop()
    changesMade = true
}

fun Instr.replace(newInstr: Instr) {
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

var unreachable = false

fun Instr.peephole() {
    if (this is InstrLabel)
        unreachable = false
    if (unreachable)
        return changeToNop()

    when(this) {
        is InstrMov -> {
            // Remove an instruction that is never read
            if (dest.useCount == 0 && dest !is CpuReg)
                return changeToNop()

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

            // Look for a sequence 'add t1, x, imm; ld t2, [t1+offset]' and replace it with 'ld t2, [x+offset+imm]'
            val prevInstr = currentFunction.prog[index-1]
            if (prevInstr is InstrAluLit && prevInstr.op==BinOp.ADD_I && prevInstr.dest==addr) {
                val newOffset = prevInstr.lit + offset
                if (newOffset in -0x800..0x7FF)
                    return replace(InstrLoad(size, dest, prevInstr.src, newOffset))
            }
        }

        is InstrStore -> {
            // Look for a sequence 'add t1, x, imm; st t2, [t1+offset]' and replace it with 'st t2, [x+offset+imm]'
            val prevInstr = currentFunction.prog[index-1]
            if (prevInstr is InstrAluLit && prevInstr.op==BinOp.ADD_I && prevInstr.dest==addr) {
                val newOffset = prevInstr.lit + offset
                if (newOffset in -0x800..0x7FF)
                    return replace(InstrStore(size, src, prevInstr.src, newOffset))
            }
        }

        is InstrJump -> {
            // Remove a jump instruction that jumps to the next instruction
            if (label.index == index + 1)
                changeToNop()
            else
                unreachable = true  // Mark code after a jump as unreachable until a label is encountered
        }

        is InstrLabel -> {
            // Remove a label instruction that is never used
            if (label.useCount == 0)
                changeToNop()
        }

        is InstrBranch -> {
            val nextInstr = currentFunction.prog[index + 1]
            if (label.index == index + 1) {
                // Remove a branch instruction that jumps to the next instruction
                changeToNop()
            } else if (label.index == index + 2 && nextInstr is InstrJump) {
                // Replace a branch over a jump
                replace(InstrBranch(op.invertBranch(), src1, src2, nextInstr.label))
                nextInstr.changeToNop()
            } else if (src1.isConstant()==0) {
                replace(InstrBranch(op, zeroReg, src2, label))
            } else if (src2.isConstant()==0)
                replace(InstrBranch(op, src1, zeroReg, label))
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



fun Function.runBackend() {
    currentFunction = this

    runPeephole()
    CommonSubexpr(this).run()
    this.rebuildIndex()
    val liveMap = LiveMap(this)
    RegisterAllocator(this, liveMap).run()
    runPeephole()
}


fun runBackend() {
    for(func in allFunctions)
        func.runBackend()
}