// Code generator functions for Long type.
//
// Since the target architecture is 32-bit, we need to split Long values into two 32-bit parts (low and high)
// when generating code.
//
// Longs are passed as CompoundReg types with the MSB in [1] and the LSB in [0].

fun codeGenLongOp(op: BinOp, lhs: TctExpr, rhs: TctExpr) : Reg {
    val regLhs = lhs.codeGenRvalue()
    val regRhs = rhs.codeGenRvalue()

    return when(op) {
        BinOp.ADD_I -> {
            require (regLhs is CompoundReg)
            require (regRhs is CompoundReg)
            val l1 = currentFunc.addAlu(BinOp.ADD_I, regLhs.regs[0], regRhs.regs[0]) // Add low parts
            val carry = currentFunc.addAlu(BinOp.LTU_I, l1, regLhs.regs[0]) // Check for carry from low part
            val l2 = currentFunc.addAlu(BinOp.ADD_I, regLhs.regs[1], regRhs.regs[1]) // Add high parts
            val h = currentFunc.addAlu(BinOp.ADD_I, l2, carry) // Add carry to high part
            currentFunc.newCompoundReg(listOf(l1, h))
        }
        BinOp.SUB_I -> TODO()
        BinOp.MUL_I -> TODO()
        BinOp.DIV_I -> TODO()
        BinOp.MOD_I -> TODO()
        BinOp.AND_I -> {
            require (regLhs is CompoundReg)
            require (regRhs is CompoundReg)
            val l = currentFunc.addAlu(BinOp.AND_I, regLhs.regs[0], regRhs.regs[0]) // AND low parts
            val h = currentFunc.addAlu(BinOp.AND_I, regLhs.regs[1], regRhs.regs[1]) // AND high parts
            currentFunc.newCompoundReg(listOf(l, h))
        }
        BinOp.OR_I -> {
            require (regLhs is CompoundReg)
            require (regRhs is CompoundReg)
            val l = currentFunc.addAlu(BinOp.OR_I, regLhs.regs[0], regRhs.regs[0]) // OR low parts
            val h = currentFunc.addAlu(BinOp.OR_I, regLhs.regs[1], regRhs.regs[1]) // OR high parts
            currentFunc.newCompoundReg(listOf(l, h))
        }
        BinOp.XOR_I -> {
            require (regLhs is CompoundReg)
            require (regRhs is CompoundReg)
            val l = currentFunc.addAlu(BinOp.XOR_I, regLhs.regs[0], regRhs.regs[0]) // XOR low parts
            val h = currentFunc.addAlu(BinOp.XOR_I, regLhs.regs[1], regRhs.regs[1]) // XOR high parts
            currentFunc.newCompoundReg(listOf(l, h))
        }
        BinOp.LSR_I -> {
            require (regLhs is CompoundReg)
            require (regRhs !is CompoundReg)       // Shift amount must be an Int, not a Long
            if (rhs is TctConstant && rhs.value is IntValue) {
                val shiftAmount = rhs.value.value
                if (shiftAmount>=64)
                    currentFunc.newCompoundReg(listOf(zeroReg, zeroReg)) // Shifting by 64 or more results in zero
                else if (shiftAmount >= 32) {
                    // Low part becomes high part shifted by (shiftAmount - 32)
                    val h = currentFunc.addAlu(BinOp.LSR_I, regLhs.regs[1], shiftAmount - 32)
                    currentFunc.newCompoundReg(listOf(h, zeroReg))
                } else {
                    // Shift low part by shiftAmount, and OR with the shifted high part
                    val l = currentFunc.addAlu(BinOp.LSR_I, regLhs.regs[0], shiftAmount)
                    val hShifted = currentFunc.addAlu(BinOp.LSR_I, regLhs.regs[1], shiftAmount)
                    val hToL = currentFunc.addAlu(BinOp.LSL_I, regLhs.regs[1], 32 - shiftAmount)
                    val newLow = currentFunc.addAlu(BinOp.OR_I, l, hToL)
                    currentFunc.newCompoundReg(listOf(newLow, hShifted))
                }
            } else
                TODO("Shift amount must be a constant Int for now")
        }

        BinOp.LSL_I -> {
            require (regLhs is CompoundReg)
            require (regRhs !is CompoundReg)       // Shift amount must be an Int, not a Long
            if (rhs is TctConstant && rhs.value is IntValue) {
                val shiftAmount = rhs.value.value
                if (shiftAmount>=64)
                    currentFunc.newCompoundReg(listOf(zeroReg, zeroReg)) // Shifting by 64 or more results in zero
                else if (shiftAmount >= 32) {
                    // High part becomes low part shifted by (shiftAmount - 32)
                    val l = currentFunc.addAlu(BinOp.LSL_I, regLhs.regs[0], shiftAmount - 32)
                    currentFunc.newCompoundReg(listOf(zeroReg, l))
                } else {
                    // Shift high part by shiftAmount, and OR with the shifted low part
                    val h = currentFunc.addAlu(BinOp.LSL_I, regLhs.regs[1], shiftAmount)
                    val lShifted = currentFunc.addAlu(BinOp.LSR_I, regLhs.regs[0], 32 - shiftAmount)
                    val newHigh = currentFunc.addAlu(BinOp.OR_I, h, lShifted)
                    val newLow = currentFunc.addAlu(BinOp.LSL_I, regLhs.regs[0], shiftAmount)
                    currentFunc.newCompoundReg(listOf(newLow, newHigh))
                }
            } else
                TODO("Shift amount must be a constant Int for now")
        }

        BinOp.ASR_I -> {
            require (regLhs is CompoundReg)
            require (regRhs !is CompoundReg)       // Shift amount must be an Int, not a Long
            if (rhs is TctConstant && rhs.value is IntValue) {
                val shiftAmount = rhs.value.value
                if (shiftAmount>=64)
                    currentFunc.newCompoundReg(listOf(zeroReg, zeroReg)) // Shifting by 64 or more results in zero
                else if (shiftAmount >= 32) {
                    // High part becomes low part shifted by (shiftAmount - 32), with sign extension
                    val sign = currentFunc.addAlu(BinOp.ASR_I, regLhs.regs[1], 31) // Get the sign bit of the high part
                    val l = currentFunc.addAlu(BinOp.ASR_I, regLhs.regs[1], shiftAmount - 32)
                    currentFunc.newCompoundReg(listOf(l, sign)) // Sign-extend the high part into the low part
                } else {
                    // Shift high part by shiftAmount, and OR with the shifted low part
                    val h = currentFunc.addAlu(BinOp.ASR_I, regLhs.regs[1], shiftAmount)
                    val lShifted = currentFunc.addAlu(BinOp.LSR_I, regLhs.regs[0], shiftAmount)
                    val hToL = currentFunc.addAlu(BinOp.LSL_I, regLhs.regs[1], 32 - shiftAmount)
                    val newLow = currentFunc.addAlu(BinOp.OR_I, lShifted, hToL)
                    currentFunc.newCompoundReg(listOf(newLow, h))
                }
            } else
                TODO("Shift amount must be a constant Int for now")
        }
        else -> error("Unsupported long operation $op")
    }
}

fun codeGenLongCompare(op: BinOp, lhs: TctExpr, rhs: TctExpr) : Reg {
    val regLhs = lhs.codeGenRvalue()
    val regRhs = rhs.codeGenRvalue()

    require (regLhs is CompoundReg)
    require (regRhs is CompoundReg)

    return when(op) {
        BinOp.EQ_I -> {
            val lowEqual = currentFunc.addAlu(BinOp.EQ_I, regLhs.regs[0], regRhs.regs[0])
            val highEqual = currentFunc.addAlu(BinOp.EQ_I, regLhs.regs[1], regRhs.regs[1])
            currentFunc.addAlu(BinOp.AND_I, lowEqual, highEqual) // Both low and high parts must be equal
        }
        BinOp.NE_I -> {
            val lowNotEqual = currentFunc.addAlu(BinOp.NE_I, regLhs.regs[0], regRhs.regs[0])
            val highNotEqual = currentFunc.addAlu(BinOp.NE_I, regLhs.regs[1], regRhs.regs[1])
            currentFunc.addAlu(BinOp.OR_I, lowNotEqual, highNotEqual) // Either low or high parts must be not equal
        }
        BinOp.LT_I -> TODO()
        BinOp.GT_I -> TODO()
        BinOp.LE_I -> TODO()
        BinOp.GE_I -> TODO()
        else -> error("Unsupported long comparison $op")
    }
}



