lateinit var currentFunc : Function
private var breakStack = mutableListOf<Label>()
private var continueStack = mutableListOf<Label>()

// ======================================================================
//                         Expressions
// ======================================================================

fun TctExpr.codeGenRvalue() : Reg {
    return when (this) {
        is TctVariable -> currentFunc.getReg(sym)

        is TctConstant -> {
            when (value) {
                is IntValue -> currentFunc.addLdImm(value.value)
                is StringValue -> currentFunc.addLea(value)
                is ArrayValue -> currentFunc.addLea(value)
                is ClassValue -> currentFunc.addLea(value)
                is FunctionValue -> currentFunc.addLea(value)
            }
        }

        is TctBinaryExpr -> {
            val regLhs = lhs.codeGenRvalue()
            val regRhs = rhs.codeGenRvalue()
            currentFunc.addAlu(op, regLhs, regRhs)
        }

        is TctFpuExpr -> {
            val regLhs = lhs.codeGenRvalue()
            val regRhs = rhs.codeGenRvalue()
            currentFunc.addFpu(op, regLhs, regRhs)
        }

        is TctIntToRealExpr -> {
            val reg = expr.codeGenRvalue()
            currentFunc.addFpu(FpuOp.ITOF_F, zeroReg, reg)
        }

        is TctCallExpr -> {
            val argRegs = args.map { it.codeGenRvalue() }
            val thisArgReg = thisArg?.codeGenRvalue()
            genCall(func, thisArgReg, argRegs,null)
        }

        is TctErrorExpr -> { zeroReg }  // Dummy return value

        is TctFunctionName -> {
            val value = FunctionValue.create(sym.overloads[0], type)
            currentFunc.addLea(value)
        }

        is TctReturnExpr -> {
            if (currentFunc.returnDestAddr!=null && expr!=null) {
                // Aggregate return value - store into caller-allocated memory
                expr.codeGenAggregateRvalue(currentFunc.returnDestAddr!!)
                return zeroReg  // Dummy return value
            }

            val retReg = expr?.codeGenRvalue()
            var index = 8

            if (retReg is CompoundReg)
                for(reg in retReg.regs)
                    currentFunc.addMov(cpuRegs[index--], reg)
            else if (retReg!=null)
                currentFunc.addMov(cpuRegs[8], retReg)
            currentFunc.addJump(currentFunc.endLabel)
            zeroReg  // Dummy return value
        }

        is TctTypeName -> error("Type name found when expecting rvalue")

        is TctIndexExpr -> {
            val arrayReg = array.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val size = type.getSize()
            if (size == 0)
                Log.error(location, "Cannot index into type '${array.type}'")
            val bounds = if (array.type is TypeInlineArray)
                currentFunc.addLdImm(array.type.size)
            else
                currentFunc.addLoad(arrayReg, lengthSymbol)
            val scaled = if (array.type is TypePointer)
                currentFunc.addAlu(BinOp.MUL_I, indexReg, size)
            else
                currentFunc.addIndex(size, indexReg, bounds)
            val addr = currentFunc.addAlu(BinOp.ADD_I, arrayReg, scaled)

            // Return address for aggregates, or the value for scalars
            if (type.isAggregate())
                addr
            else
                currentFunc.addLoad(size, addr, 0)
        }

        is TctMemberExpr -> {
            val objectReg = objectExpr.codeGenRvalue()

            // If it's an inline array, just do pointer arithmetic to get the address of the first element
            if (member.type is TypeInlineArray || member.type is TypeStruct)
                return currentFunc.addAlu(BinOp.ADD_I, objectReg, member.offset)

            val size = member.type.getSize()
            if (size == 0)
                Log.error(location, "Cannot access member '${member.name}' of type '${objectExpr.type}'")
            currentFunc.addLoad(objectReg, member)
        }

        is TctNewArrayExpr -> {
            allocateArray(size, elementType, arena, clearMem, lambda)
        }

        is TctNewArrayLiteralExpr -> {
            val elementType = (type as TypeArray).elementType
            val sizeExpr = TctConstant(location, IntValue(elements.size, TypeInt))
            val ret = allocateArray(sizeExpr, elementType, arena, false, null)
            val elementSize = elementType.getSize()
            for(index in elements.indices)
                currentFunc.addStore(elementSize, elements[index].codeGenRvalue(), ret, index*elementSize)
            ret
        }

        is TctAndExpr -> {
            val result = currentFunc.newUserTemp()
            val labelEnd = currentFunc.newLabel()

            val lhsReg = lhs.codeGenRvalue()
            currentFunc.addMov(result, lhsReg)
            currentFunc.addBranch(BinOp.EQ_I, result, zeroReg, labelEnd)

            val rhsReg = rhs.codeGenRvalue()
            currentFunc.addMov(result, rhsReg)
            currentFunc.addLabel(labelEnd)
            result
        }

        is TctOrExpr -> {
            val result = currentFunc.newUserTemp()
            val labelEnd = currentFunc.newLabel()

            val lhsReg = lhs.codeGenRvalue()
            currentFunc.addMov(result, lhsReg)
            currentFunc.addBranch(BinOp.NE_I, result, zeroReg, labelEnd)

            val rhsReg = rhs.codeGenRvalue()
            currentFunc.addMov(result, rhsReg)
            currentFunc.addLabel(labelEnd)
            result
        }

        is TctNotExpr -> {
            val reg = expr.codeGenRvalue()
            currentFunc.addAlu(BinOp.LTU_I, reg, 1) // Unsigned less than 1 gives 0 for false, 1 for true
        }

        is TctBreakExpr -> {
            currentFunc.addJump(breakStack.last())
            zeroReg  // Dummy return value
        }
        is TctContinueExpr -> {
            currentFunc.addJump(continueStack.last())
            zeroReg
        }

        is TctIntCompareExpr -> {
            val lhsReg = lhs.codeGenRvalue()
            val rhsReg = rhs.codeGenRvalue()
            currentFunc.addAlu(op, lhsReg, rhsReg)
        }

        is TctNegateExpr -> {
            val reg = expr.codeGenRvalue()
            currentFunc.addAlu(BinOp.SUB_I, zeroReg, reg)
        }

        is TctLambdaExpr -> TODO()
        is TctRangeExpr -> TODO()

        is TctStringCompareExpr -> {
            val lhsReg = lhs.codeGenRvalue()
            val rhsReg = rhs.codeGenRvalue()
            currentFunc.addMov(cpuRegs[1], lhsReg)
            currentFunc.addMov(cpuRegs[2], rhsReg)
            when(op) {
                BinOp.EQ_I -> {
                    currentFunc.addCall(Stdlib.strequal)
                    currentFunc.addCopy(cpuRegs[8])
                }
                BinOp.NE_I -> {
                    currentFunc.addCall(Stdlib.strequal)
                    currentFunc.addAlu(BinOp.LTU_I, cpuRegs[8], 1) // Not equal if result is 0
                }
                BinOp.LT_I, BinOp.LE_I, BinOp.GT_I, BinOp.GE_I -> {
                    currentFunc.addCall(Stdlib.strcmp)
                    currentFunc.addAlu(op, cpuRegs[8], zeroReg) // Compare result with 0
                }
                else -> error("Invalid string comparison operator '$op'")
            }
        }

        is TctNewClassExpr -> {
            // Allocate memory for the new object
            val ret = when(arena) {
                Arena.HEAP -> {
                    val desc = currentFunc.addLea(klass.descriptor)
                    currentFunc.addMov(cpuRegs[1], desc)
                    currentFunc.addCall(Stdlib.mallocObject)
                    currentFunc.addCopy(resultReg)
                }
                else -> TODO()
            }

            // Initialize the object with the constructor
            val argRegs = args.map { it.codeGenRvalue() }
            var index = 1
            currentFunc.addMov(cpuRegs[index++], ret) // 'this' pointer
            for (arg in argRegs)
                currentFunc.addMov(cpuRegs[index++], arg)
            currentFunc.addCall(klass.constructor.function)
            ret
        }

        is TctNullAssertExpr -> {
            expr.codeGenRvalue()
            // TODO - add null check code
        }

        is TctMethodRefExpr -> {
            Log.error(location, "Method references not supported yet")
            zeroReg
        }

        is TctIsExpr ->
            TODO()

        is TctAsExpr -> {
            val reg = expr.codeGenRvalue()
            if (type is TypeEnum) {
                // Do a range check when casting to an enum. We can use the INDEX instruction (normally used for array
                // bounds checks) to check if the value is within the range of enum values.
                val enumSizeReg = currentFunc.addLdImm(type.entries.size)
                currentFunc.addIndex(1, reg, enumSizeReg)
            } else {
                // For other casts simply copy the value to the result register
                currentFunc.addCopy(reg)
            }
        }

        is TctEnumEntryExpr -> {
            val objReg = expr.codeGenRvalue()       // Evaluate the expression as an Int
            val enum = (expr.type as TypeEnum)
            val arrayReg = currentFunc.addLea(enum.values[field]!!)
            val size = field.type.getSize()
            val indexReg = currentFunc.addAlu(BinOp.MUL_I, objReg, size) // Scale by size of each entry
            val addrReg = currentFunc.addAlu(BinOp.ADD_I, arrayReg, indexReg)
            currentFunc.addLoad(size, addrReg, 0)
        }

        is TctMakeErrableExpr -> {
            val valueReg = expr.codeGenRvalue()
            val typeReg = currentFunc.addLdImm(typeIndex)
            currentFunc.newCompoundReg(listOf(typeReg, valueReg))
        }

        is TctIsErrableTypeExpr -> TODO()

        is TctExtractCompoundExpr -> {
            val reg = expr.codeGenRvalue()
            require(reg is CompoundReg)
            reg.regs[index]
        }

        is TctTryExpr -> {
            require (expr.type is TypeErrable)
            val reg = expr.codeGenRvalue()
            require(reg is CompoundReg)
            currentFunc.addBranch(BinOp.NE_I, reg.regs[0], zeroReg, currentFunc.endLabel)
            reg.regs[1]
        }

        is TctVarargExpr -> {
            val ret = currentFunc.stackAlloc(4*exprs.size+4, 4)      // +4 for the length field
            val sizeReg = currentFunc.addLdImm(exprs.size)
            currentFunc.addStore(sizeReg, ret, lengthSymbol)
            for(i in exprs.indices) {
                val v = exprs[i].codeGenRvalue()
                currentFunc.addStore(4, v, ret, i*4)
            }
            ret
        }

        is TctMakeTupleExpr -> {
            val regs = elements.map { it.codeGenRvalue() }
            currentFunc.newCompoundReg(regs)
        }

        is TctNewInlineArrayExpr ->
            allocateInlineArray(arena, false, lambda, type as TypeInlineArray)

        is TctAbortExpr -> {
            val codeReg = abortCode.codeGenRvalue()
            currentFunc.addMov(cpuRegs[1], codeReg)
            currentFunc.addCall(Stdlib.abort)
            zeroReg
        }

        is TctGlobalVarExpr -> {
            if (type is TypeInlineArray)
                // For inline arrays we want to return the address of the array, not load the first element
                currentFunc.addAlu(BinOp.ADD_I, cpuRegs[29], sym.offset)
            else
                currentFunc.addLoad(sym.type.getSize(), cpuRegs[29], sym.offset)
        }

        is TctIndirectCallExpr -> {
            val argRegs = args.map{it.codeGenRvalue()}
            val func = func.codeGenRvalue()
            for(i in argRegs.indices)
                currentFunc.addMov(cpuRegs[i+1], argRegs[i])
            currentFunc.addInstr( InstrIndCall(func, type))
            if (type!=TypeUnit)
                currentFunc.addCopy(cpuRegs[8])
            else
                cpuRegs[0]
        }

        is TctIfExpr -> {
            val resultReg = currentFunc.newUserTemp()
            val trueLabel = currentFunc.newLabel()
            val falseLabel = currentFunc.newLabel()
            val endLabel = currentFunc.newLabel()
            cond.codeGenBool(trueLabel, falseLabel)
            currentFunc.addLabel(trueLabel)
            val trueReg = trueExpr.codeGenRvalue()
            currentFunc.addMov(resultReg, trueReg)
            currentFunc.addJump(endLabel)
            currentFunc.addLabel(falseLabel)
            val falseReg = falseExpr.codeGenRvalue()
            currentFunc.addMov(resultReg, falseReg)
            currentFunc.addLabel(endLabel)
            resultReg
        }

        is TctRealToIntExpr -> {
            val reg = expr.codeGenRvalue()
            currentFunc.addFpu(FpuOp.FTOI_F, zeroReg, reg)
        }

        is TctRealCompareExpr -> {
            TODO()
        }

        is TctMakeStructExpr -> {
            val addr = currentFunc.stackAlloc(type.getSize(),0)
            codeGenAggregateRvalue(addr)
            addr
        }

        is TctStackVariable -> currentFunc.addAlu(BinOp.ADD_I, cpuRegs[31], sym.offset)
    }
}

private fun allocateArray(sizeExpr:TctExpr, elementType:Type, arena:Arena, initialize:Boolean, lambda:TctLambdaExpr?) : Reg {
    val elementSize = elementType.getSize()
    val sizeReg = sizeExpr.codeGenRvalue()
    val ret = when(arena) {
        Arena.HEAP -> {
            currentFunc.addMov(cpuRegs[1], sizeReg)
            currentFunc.addLdImm(cpuRegs[2], elementSize)
            currentFunc.addCall(Stdlib.mallocArray)
            currentFunc.addCopy(resultReg)
        }
        Arena.STACK -> {
            require(sizeExpr is TctConstant && sizeExpr.value is IntValue) {
                "Stack allocation requires a constant size"
            }
            val sizeInt = sizeExpr.value.value
            val ret = currentFunc.stackAlloc(sizeInt*elementSize+4, 4) // +4 for the length field
            currentFunc.addStore(sizeReg, ret, lengthSymbol)
            ret
        }
        Arena.CONST -> TODO()
    }

    if (initialize) {
        val sizeInBytes = currentFunc.addAlu(BinOp.MUL_I, sizeReg, elementSize)
        currentFunc.addMov(cpuRegs[1], ret)
        currentFunc.addMov(cpuRegs[2], sizeInBytes)
        currentFunc.addCall(Stdlib.bzero)
    }

    if (lambda != null) {
        val itReg = currentFunc.getReg(lambda.itSym)
        val ptrReg = currentFunc.newUserTemp()
        val startLabel = currentFunc.newLabel()
        val condLabel = currentFunc.newLabel()
        currentFunc.addMov(ptrReg, ret)
        currentFunc.addMov(itReg, zeroReg)
        currentFunc.addJump(condLabel)
        currentFunc.addLabel(startLabel)
        val v = lambda.expr.codeGenRvalue()
        currentFunc.addStore(elementSize, v, ptrReg, 0)
        val ptrNext = currentFunc.addAlu(BinOp.ADD_I, ptrReg, elementSize)
        currentFunc.addMov(ptrReg, ptrNext)
        val itNext = currentFunc.addAlu(BinOp.ADD_I, itReg, 1)
        currentFunc.addMov(itReg, itNext)
        currentFunc.addLabel(condLabel)
        currentFunc.addBranch(BinOp.LT_I, itReg, sizeReg, startLabel)
    }
    return ret
}

private fun allocateInlineArray(arena:Arena, initialize:Boolean, lambda:TctLambdaExpr?, type:TypeInlineArray) : Reg {
    val size = type.getSize()
    val ret = when(arena) {
        Arena.HEAP -> {
            currentFunc.addLdImm(cpuRegs[1], size)
            currentFunc.addCall(Stdlib.malloc)
            currentFunc.addCopy(resultReg)
        }
        Arena.STACK -> {
            val ret = currentFunc.stackAlloc(size, 0)
            ret
        }
        Arena.CONST -> TODO()
    }

    if (initialize) {
        currentFunc.addMov(cpuRegs[1], ret)
        currentFunc.addLdImm(cpuRegs[2], size)
        currentFunc.addCall(Stdlib.bzero)
    }

    if (lambda != null) {
        val itReg = currentFunc.getReg(lambda.itSym)
        val ptrReg = currentFunc.newUserTemp()
        val startLabel = currentFunc.newLabel()
        val condLabel = currentFunc.newLabel()
        val sizeReg = currentFunc.addLdImm(type.size)
        val elementSize = type.elementType.getSize()
        currentFunc.addMov(ptrReg, ret)
        currentFunc.addMov(itReg, zeroReg)
        currentFunc.addJump(condLabel)
        currentFunc.addLabel(startLabel)
        val v = lambda.expr.codeGenRvalue()
        currentFunc.addStore(elementSize, v, ptrReg, 0)
        val ptrNext = currentFunc.addAlu(BinOp.ADD_I, ptrReg, elementSize)
        currentFunc.addMov(ptrReg, ptrNext)
        val itNext = currentFunc.addAlu(BinOp.ADD_I, itReg, 1)
        currentFunc.addMov(itReg, itNext)
        currentFunc.addLabel(condLabel)
        currentFunc.addBranch(BinOp.LT_I, itReg, sizeReg, startLabel)
    }
    return ret
}



fun TctExpr.codeGenBool(trueLabel: Label, falseLabel: Label) {
    when (this) {
        is TctIntCompareExpr -> {
            val lhsReg = lhs.codeGenRvalue()
            val rhsReg = rhs.codeGenRvalue()
            currentFunc.addBranch(op, lhsReg, rhsReg, trueLabel)
            currentFunc.addJump(falseLabel)
        }

        is TctStringCompareExpr -> {
            val lhsReg = lhs.codeGenRvalue()
            val rhsReg = rhs.codeGenRvalue()
            currentFunc.addMov(cpuRegs[1], lhsReg)
            currentFunc.addMov(cpuRegs[2], rhsReg)
            when(op) {
                BinOp.EQ_I -> {
                    currentFunc.addCall(Stdlib.strequal)
                    currentFunc.addBranch(BinOp.NE_I, cpuRegs[8], zeroReg, trueLabel)
                    currentFunc.addJump(falseLabel)
                }
                BinOp.NE_I -> {
                    currentFunc.addCall(Stdlib.strequal)
                    currentFunc.addBranch(BinOp.EQ_I, cpuRegs[8], zeroReg, trueLabel)
                    currentFunc.addJump(falseLabel)
                }
                BinOp.LT_I, BinOp.LE_I, BinOp.GT_I, BinOp.GE_I -> {
                    currentFunc.addCall(Stdlib.strcmp)
                    currentFunc.addBranch(op, cpuRegs[8], zeroReg, trueLabel)
                    currentFunc.addJump(falseLabel)
                }
                else -> Log.error(location, "Invalid string comparison operator '$op'")
            }
        }

        is TctRealCompareExpr -> {
            val lhsReg = lhs.codeGenRvalue()
            val rhsReg = rhs.codeGenRvalue()
            val cmp = currentFunc.addFpu(FpuOp.CMP_F, lhsReg, rhsReg)
            when(op) {
                BinOp.EQ_I -> currentFunc.addBranch(BinOp.EQ_I, cmp, zeroReg, trueLabel)
                BinOp.NE_I -> currentFunc.addBranch(BinOp.NE_I, cmp, zeroReg, trueLabel)
                BinOp.LT_I -> currentFunc.addBranch(BinOp.LT_I, cmp, zeroReg, trueLabel)
                BinOp.GT_I -> currentFunc.addBranch(BinOp.GT_I, cmp, zeroReg, trueLabel)
                BinOp.LE_I -> currentFunc.addBranch(BinOp.LE_I, cmp, zeroReg, trueLabel)
                BinOp.GE_I -> currentFunc.addBranch(BinOp.GE_I, cmp, zeroReg, trueLabel)
                else -> Log.error(location, "Invalid real comparison operator '$op'")
            }
            currentFunc.addJump(falseLabel)
        }

        is TctNotExpr -> {
            expr.codeGenBool(falseLabel, trueLabel)
        }

        is TctAndExpr -> {
            val labelMid = currentFunc.newLabel()
            lhs.codeGenBool(labelMid, falseLabel)
            currentFunc.addLabel(labelMid)
            rhs.codeGenBool(trueLabel, falseLabel)
        }

        is TctOrExpr -> {
            val labelMid = currentFunc.newLabel()
            lhs.codeGenBool(trueLabel, labelMid)
            currentFunc.addLabel(labelMid)
            rhs.codeGenBool(trueLabel, falseLabel)
        }

        is TctIsExpr -> {
            val reg = expr.codeGenRvalue()
            if (expr.type is TypeNullable)
                currentFunc.addBranch(BinOp.EQ_I, reg, zeroReg, falseLabel)
            val typeRReg = currentFunc.addLea(typeExpr.genericType.descriptor)

            // Get the type field of the object. Need to copy it to a user reg to keep things SSA
            val tmpReg = currentFunc.newUserTemp()
            val typeLReg = currentFunc.addLoad(4, reg, -4)   // object's descriptor
            currentFunc.addMov(tmpReg, typeLReg)

            // Loop through the parent chain
            val loopLabel = currentFunc.newLabel()
            currentFunc.addLabel(loopLabel)
            currentFunc.addBranch(BinOp.EQ_I, tmpReg, typeRReg, trueLabel)
            val parent = currentFunc.addLoad(4, tmpReg, 8)   // offset 4 = parent
            currentFunc.addMov(tmpReg, parent)
            currentFunc.addBranch(BinOp.NE_I, tmpReg, zeroReg, loopLabel)
            currentFunc.addJump(falseLabel)
        }

        is TctIsErrableTypeExpr -> {
            val reg = expr.codeGenRvalue()
            require(reg is CompoundReg)
            if (this.typeIndex==0) {
                currentFunc.addBranch(BinOp.EQ_I, reg.regs[0], zeroReg, trueLabel)
                currentFunc.addJump(falseLabel)
            } else {
                currentFunc.addBranch(BinOp.NE_I, reg.regs[0], zeroReg, trueLabel)
                currentFunc.addJump(falseLabel)
            }
        }

        else -> {
            val reg = codeGenRvalue()
            currentFunc.addBranch(BinOp.EQ_I, reg, zeroReg, falseLabel)
            currentFunc.addJump(trueLabel)
        }
    }
}

fun TctExpr.codeGenLvalue(value:Reg, op:TokenKind) {
    if (value is CompoundReg && op!=TokenKind.EQ)
        error("Cannot use compound assignment operator '$op' with union types")

    when(this) {
        is TctVariable -> {
            val dest = currentFunc.getReg(sym)
            if (value is CompoundReg && dest is CompoundReg) {
                assert(dest.regs.size == value.regs.size)
                for(i in dest.regs.indices)
                    currentFunc.addMov(dest.regs[i], value.regs[i])
            } else if (value is CompoundReg)
                error("Cannot assign union value to non-union variable '${sym.name}'")
            else if (dest is CompoundReg)
                error("Cannot assign non-union value to union variable '${sym.name}'")
            else {
                val newValue = if (op==TokenKind.EQ) value else applyCompoundOp(op, dest, value)
                currentFunc.addMov(dest, newValue)
            }
        }

        is TctIndexExpr -> {
            val arrayReg = array.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val size = type.getSize()
            if (size == 0)
                Log.error(location, "Cannot index into type '${array.type}'")
            val bounds = if (array.type is TypeInlineArray)
                currentFunc.addLdImm(array.type.size)
            else if (array.type is TypePointer)
                currentFunc.addLdImm(0x7FFFFFFF) // Arbitrary large value - we can't do bounds checking on pointers
            else
                currentFunc.addLoad(arrayReg, lengthSymbol)
            val scaled = currentFunc.addIndex(size, indexReg, bounds)
            val addr = currentFunc.addAlu(BinOp.ADD_I, arrayReg, scaled)
            if (value is CompoundReg) {
                for(i in value.regs.indices)
                    currentFunc.addStore(4, value.regs[i], addr, i*4)
            } else {
                val newValue = if (op==TokenKind.EQ) value else applyCompoundOp(op, currentFunc.addLoad(size, addr, 0), value)
                currentFunc.addStore(size, newValue, addr, 0)
            }
        }

        is TctMemberExpr -> {
            val objectReg = objectExpr.codeGenRvalue()
            val size = member.type.getSize()
            if (size == 0)
                Log.error(location, "Cannot access member '${member.name}' of type '${objectExpr.type}'")
            if (value is CompoundReg) {
                for(i in value.regs.indices)
                    currentFunc.addStore(4, value.regs[i],objectReg, member.offset+4*i)
            } else {
                val newValue = if (op==TokenKind.EQ) value else applyCompoundOp(op, currentFunc.addLoad(objectReg, member), value)
                currentFunc.addStore(newValue, objectReg, member)
            }
        }

        is TctGlobalVarExpr -> {
            if (value is CompoundReg)
                error("Cannot assign union value to global variable '${sym.name}'")
            val newValue = if (op==TokenKind.EQ) value else
                applyCompoundOp(op, currentFunc.addLoad(sym.type.getSize(), cpuRegs[29], sym.offset), value)
            currentFunc.addStore(sym.type.getSize(), newValue, cpuRegs[29], sym.offset)
        }

        else ->
            error("Not an lvalue ${this.javaClass}")
    }
}

fun applyCompoundOp(op:TokenKind, reg1:Reg, reg2:Reg) : Reg {
    return when(op) {
        TokenKind.PLUSEQ -> currentFunc.addAlu(BinOp.ADD_I, reg1, reg2)
        TokenKind.MINUSEQ -> currentFunc.addAlu(BinOp.SUB_I, reg1, reg2)
        else -> error("Invalid compound assignment operator '$op'")
    }
}

fun genCall(func:Function, thisArg:Reg?, args: List<Reg>, destReg:Reg?) : Reg {
    assert(args.size == func.parameters.size)
    if (thisArg==null && func.thisSymbol!=null)
        error("Function '${func.name}' requires 'this' argument but none was provided")

    // Copy arguments into CPU registers and call the function
    var index = 1
    if (func.thisSymbol!=null)
        currentFunc.addMov(cpuRegs[index++], thisArg!!)
    for (arg in args)
        if (arg is CompoundReg)
            for(reg in arg.regs)
                currentFunc.addMov(cpuRegs[index++], reg)
        else
            currentFunc.addMov(cpuRegs[index++], arg)
    if (destReg!=null)
        currentFunc.addMov(cpuRegs[8], destReg)

    if (func.syscallNumber!=-1)
        currentFunc.addInstr( InstrSyscall(func.syscallNumber) )
    else if (func.virtualFunctionNumber==-1)
        currentFunc.addCall(func)
    else
        currentFunc.addVCall(func)

    // Get the return value from the CPU register
    return if (func.returnType is TypeErrable) {
        val typeReg = currentFunc.addCopy(cpuRegs[8])
        val valueReg = currentFunc.addCopy(cpuRegs[7])
        currentFunc.newCompoundReg(listOf(typeReg, valueReg))
    } else if (func.returnType is TypeTuple) {
        val regs = mutableListOf<Reg>()
        for(i in func.returnType.elementTypes.indices) {
            val r = currentFunc.addCopy(cpuRegs[8-i])
            regs.add(r)
        }
        currentFunc.newCompoundReg(regs)
    } else if (func.returnType!= TypeUnit)
        currentFunc.addCopy(resultReg)
    else
        zeroReg  // Dummy return value
}


// ======================================================================
//                         Statements
// ======================================================================

fun TctStmt.codeGenStmt() {
    currentFunc.addLineInfo(location.filename, location.firstLine)
    when (this) {
        is TctFile -> {
            for(stmt in body)
                stmt.codeGenStmt()
        }

        is TctFunctionDefStmt -> {
            // Create a new function and save the old one
            val oldFunc = currentFunc
            currentFunc = function
            currentFunc.addInstr(InstrStart())

            // copy parameters from cpu regs into UserRegs
            var index = 1
            if (function.thisSymbol!=null)
                currentFunc.addMov(currentFunc.getReg(function.thisSymbol), cpuRegs[index++]) // 'this' pointer
            for(param in function.parameters)
                when (param.type) {
                    is TypeTuple -> {
                        val regs = param.type.elementTypes.map { currentFunc.addCopy(cpuRegs[index++]) }
                        currentFunc.symToReg[param] = currentFunc.newCompoundReg(regs)
                    }

                    is TypeErrable -> {
                        val typeReg = currentFunc.addCopy(cpuRegs[index++])
                        val valueReg = currentFunc.addCopy(cpuRegs[index++])
                        currentFunc.symToReg[param] = currentFunc.newCompoundReg(listOf(typeReg, valueReg))
                    }

                    else
                        -> currentFunc.addMov(currentFunc.getReg(param), cpuRegs[index++])
                }

            if (currentFunc.returnDestAddr!=null) {
                // For an aggregate return type, copy the return address from cpu reg 8
                currentFunc.addMov(currentFunc.returnDestAddr!!, cpuRegs[8])
            }

            // Generate code for the function body
            for(stmt in body)
                stmt.codeGenStmt()
            // Finish the function and restore the old one
            currentFunc.addLabel(currentFunc.endLabel)
            currentFunc.addInstr(InstrEnd())
            currentFunc = oldFunc
        }

        is TctTop -> {
            // Generate code for the top level statements

            for (stmt in body)
                stmt.codeGenStmt()
            currentFunc.addInstr(InstrEnd())
        }

        is TctEmptyStmt -> {}

        is TctExpressionStmt -> {
            expr.codeGenRvalue()
        }

        is TctVarDeclStmt -> {
            val regInit = initializer?.codeGenRvalue()
            when(sym) {
                is VarSymbol ->{
                    if (regInit is CompoundReg) {
                        val dest = currentFunc.getReg(sym)
                        require(dest is CompoundReg)
                        assert(dest.regs.size == regInit.regs.size)
                        for (i in dest.regs.indices)
                            currentFunc.addMov(dest.regs[i], regInit.regs[i])
                    } else if (regInit != null)
                        currentFunc.addMov(currentFunc.getReg(sym), regInit)
                }

                is GlobalVarSymbol -> {
                    if (regInit is CompoundReg)
                        error("Cannot initialize global variable '${sym.name}' with union type")
                    else if (regInit != null)
                        currentFunc.addStore(sym.type.getSize(), regInit, cpuRegs[29], sym.offset)
                }

                else -> error("Invalid symbol type in variable declaration: ${sym.javaClass}")
            }
        }

        is TctDestructuringDeclStmt -> {
            val regInit = initializer.codeGenRvalue()
            require(regInit is CompoundReg)
            require(syms.size == regInit.regs.size)
            for(i in syms.indices)
                currentFunc.addMov(currentFunc.getReg(syms[i]), regInit.regs[i])
        }


        is TctWhileStmt -> {
            val startLabel = currentFunc.newLabel()
            val endLabel = currentFunc.newLabel()
            val condLabel = currentFunc.newLabel()
            continueStack.add(condLabel)
            breakStack.add(endLabel)
            currentFunc.addJump(condLabel)
            currentFunc.addLabel(startLabel)
            for(stmt in body)
                stmt.codeGenStmt()
            currentFunc.addLabel(condLabel)
            condition.codeGenBool(startLabel, endLabel)
            currentFunc.addLabel(endLabel)
            continueStack.removeAt(continueStack.lastIndex)
            breakStack.removeAt(breakStack.lastIndex)
        }

        is TctRepeatStmt -> {
            val startLabel = currentFunc.newLabel()
            val endLabel = currentFunc.newLabel()
            val condLabel = currentFunc.newLabel()
            continueStack.add(startLabel)
            breakStack.add(endLabel)
            currentFunc.addLabel(startLabel)
            for(stmt in body)
                stmt.codeGenStmt()
            currentFunc.addLabel(condLabel)
            condition.codeGenBool(endLabel, startLabel)
            currentFunc.addLabel(endLabel)
            continueStack.removeAt(continueStack.lastIndex)
            breakStack.removeAt(breakStack.lastIndex)
        }

        is TctAssignStmt -> {
            val rhsReg = rhs.codeGenRvalue()
            if (lhs is TctMakeTupleExpr) {
                require(rhsReg is CompoundReg)
                require(lhs.elements.size == rhsReg.regs.size)
                for(i in lhs.elements.indices)
                    lhs.elements[i].codeGenLvalue(rhsReg.regs[i], op)
            } else
                lhs.codeGenLvalue(rhsReg, op)
        }

        is TctAssignAggregateStmt -> {
            val lhsAddr = lhs.codeGenAggregateLvalue()
            rhs.codeGenAggregateRvalue(lhsAddr)
        }

        is TctIfClause -> TODO()
        is TctIfStmt -> {
            val clauseLabels = body.associateWith{currentFunc.newLabel()}  // Labels for each clause
            val endLabel = currentFunc.newLabel()
            var nextCond = currentFunc.newLabel()
            // Generate code for the conditions
            for (clause in body.filterIsInstance<TctIfClause>()) {
                val clauseLabel = clauseLabels.getValue(clause)
                if (clause.condition == null)
                    currentFunc.addJump(clauseLabel)
                else
                    clause.condition.codeGenBool(clauseLabel, nextCond)
                currentFunc.addLabel(nextCond)
                nextCond = currentFunc.newLabel()
            }
            currentFunc.addJump(endLabel)

            // Generate code for the body of each clause
            for (clause in body) {
                currentFunc.addLabel(clauseLabels.getValue(clause))
                for (stmt in (clause as TctIfClause).body)
                    stmt.codeGenStmt()
                currentFunc.addJump(endLabel)
            }
            currentFunc.addLabel(endLabel)
        }

        is TctForRangeStmt -> {
            val labelStart = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val labelCont = currentFunc.newLabel()
            val endReg = range.end.codeGenRvalue()
            val symReg = currentFunc.getReg(index)
            val startReg = range.start.codeGenRvalue()
            continueStack.add(labelCont)
            breakStack.add(labelEnd)
            currentFunc.addMov(symReg, startReg)
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            for (stmt in body)
                stmt.codeGenStmt()
            currentFunc.addLabel(labelCont)
            val inc = when (range.op) {
                BinOp.LT_I, BinOp.LE_I  -> 1
                else -> -1
            }
            val nextReg = currentFunc.addAlu(BinOp.ADD_I, symReg, inc)
            currentFunc.addMov(symReg, nextReg)
            currentFunc.addLabel(labelCond)
            currentFunc.addBranch(range.op, symReg, endReg, labelStart)
            currentFunc.addLabel(labelEnd)
            continueStack.removeAt(continueStack.lastIndex)
            breakStack.removeAt(breakStack.lastIndex)
        }

        is TctForArrayStmt -> {
            val labelStart = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val labelCont = currentFunc.newLabel()
            val arrayStart = array.codeGenRvalue()
            val arraySize = if (array.type is TypeInlineArray)
                currentFunc.addLdImm(array.type.size)
            else
                currentFunc.addLoad(arrayStart,lengthSymbol)
            val elementSize = when(array.type) {
                is TypeInlineArray -> array.type.elementType.getSize()
                is TypeArray -> array.type.elementType.getSize()
                is TypeString -> 1
                else -> error("Cannot determine element type")
            }
            val sizeInBytes = currentFunc.addAlu(BinOp.MUL_I, arraySize, elementSize)
            val endReg = currentFunc.addAlu(BinOp.ADD_I, arrayStart, sizeInBytes)
            val ptrReg = currentFunc.newUserTemp()
            currentFunc.addMov(ptrReg, arrayStart)
            val symReg = currentFunc.getReg(index)
            continueStack.add(labelCont)
            breakStack.add(labelEnd)
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            val v = if (index.type.isAggregate()) ptrReg else currentFunc.addLoad(elementSize, ptrReg, 0)
            currentFunc.addMov(symReg, v)
            for (stmt in body)
                stmt.codeGenStmt()
            currentFunc.addLabel(labelCont)
            val nextPtr = currentFunc.addAlu(BinOp.ADD_I, ptrReg, elementSize)
            currentFunc.addMov(ptrReg, nextPtr)
            currentFunc.addLabel(labelCond)
            currentFunc.addBranch(BinOp.LT_I, ptrReg, endReg, labelStart)
            currentFunc.addLabel(labelEnd)
            continueStack.removeAt(continueStack.lastIndex)
            breakStack.removeAt(breakStack.lastIndex)
        }

        is TctForIterableStmt ->  {
            require(iterable.type is TypeClassInstance)
            val instance = iterable.codeGenRvalue()

            val labelStart = currentFunc.newLabel()
            val labelEnd = currentFunc.newLabel()
            val labelCond = currentFunc.newLabel()
            val labelCont = currentFunc.newLabel()
            val index = currentFunc.newUserTemp()
            currentFunc.addMov(index, zeroReg)
            val lengthReg = currentFunc.addLoad(instance, lengthSym)
            val symReg = currentFunc.getReg(this.loopVar)
            continueStack.add(labelCont)
            breakStack.add(labelEnd)
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            // call get method
            currentFunc.addMov(cpuRegs[1], instance)
            currentFunc.addMov(cpuRegs[2], index)
            val v = currentFunc.addCall(getMethod.function)
            currentFunc.addMov(symReg, cpuRegs[8])
            for (stmt in body)
                stmt.codeGenStmt()
            currentFunc.addLabel(labelCont)
            val incIndex = currentFunc.addAlu(BinOp.ADD_I, index, 1)
            currentFunc.addMov(index, incIndex)
            currentFunc.addLabel(labelCond)
            currentFunc.addBranch(BinOp.LT_I, index, lengthReg, labelStart)
            currentFunc.addLabel(labelEnd)
            continueStack.removeAt(continueStack.lastIndex)
            breakStack.removeAt(breakStack.lastIndex)
        }

        is TctClassDefStmt -> {
            // Create a new function and save the old one
            val oldFunc = currentFunc
            currentFunc = klass.constructor.function
            currentFunc.addInstr(InstrStart())
            // copy parameters from cpu regs into UserRegs
            val thisReg = currentFunc.getReg(currentFunc.thisSymbol!!)
            var index = 1
            currentFunc.addMov(thisReg, cpuRegs[index++]) // 'this' pointer
            for(param in currentFunc.parameters)
                currentFunc.addMov( currentFunc.getReg(param), cpuRegs[index++])
            // Generate code for the function body
            for(init in this.initializers) {
                val reg = init.value.codeGenRvalue()
                val fieldReg = init.field
                if (fieldReg!=null)
                    if (fieldReg.type.isAggregate())
                        genMemCopy(currentFunc.addAlu(BinOp.ADD_I, thisReg, fieldReg.offset), reg, fieldReg.type.getSize())
                    else
                        currentFunc.addStore(reg, thisReg, fieldReg)
            }
            // Finish the function and restore the old one
            currentFunc.addLabel(currentFunc.endLabel)
            currentFunc.addInstr(InstrEnd())
            currentFunc = oldFunc

            // Look for any methods
            for (stmt in methods)
                stmt.codeGenStmt()
        }

        is TctFreeStmt -> {
            val reg = expr.codeGenRvalue()
            val label = currentFunc.newLabel()


            if (expr.type is TypeNullable)
                currentFunc.addBranch(BinOp.EQ_I, reg, zeroReg, label)

            // Call the destructor if there is one
            val tt = if (expr.type is TypeNullable) expr.type.elementType else expr.type
            if (tt is TypeClassInstance) {
                val destructor = tt.genericType.destructor
                if (destructor!=null) {
                    currentFunc.addMov(cpuRegs[1], reg)
                    currentFunc.addCall(destructor.function)
                }
            }

            currentFunc.addMov(cpuRegs[1], reg)
            currentFunc.addCall(Stdlib.free)
            currentFunc.addLabel(label)
        }

        is TctWhenCase -> TODO()
        is TctWhenStmt -> {
            val exprReg = expr.codeGenRvalue()
            val cases = body.filterIsInstance<TctWhenCase>()
            val endLabel = currentFunc.newLabel()
            val caseLabels = mutableListOf<Label>()
            var nextLabel = currentFunc.newLabel()

            if (expr.type is TypeString)
                TODO("String 'when' not implemented yet")

            for (case in cases) {
                val caseLabel = currentFunc.newLabel()
                caseLabels += caseLabel
                if (case.matchExprs.isEmpty())
                    currentFunc.addJump(caseLabel)  // 'else' clause
                else for (expr in case.matchExprs) {
                    val matchReg = expr.codeGenRvalue()
                    currentFunc.addBranch(BinOp.EQ_I, exprReg, matchReg, caseLabel)
                }
            }

            currentFunc.addJump(endLabel)
            for ((case,label) in cases.zip(caseLabels)) {
                currentFunc.addLabel(label)
                for(stmt in case.body)
                    stmt.codeGenStmt()
                currentFunc.addJump(endLabel)
            }
            currentFunc.addLabel(endLabel)
        }

        is TctStructVarDeclStmt -> {
            sym.offset = currentFunc.stackAlloc(sym.type.getSize())
            if (initializer!=null) {
                val addrReg = currentFunc.addAlu(BinOp.ADD_I, cpuRegs[31], sym.offset)
                initializer.codeGenAggregateRvalue(addrReg)
            }
        }
    }
}

// ======================================================================
//                          Struct Expressions
// ======================================================================

fun TctExpr.codeGenAggregateRvalue(dest:Reg) {
    // Evaluate an aggregate expression placing the result at address 'dest'
    when(this) {
        is TctMakeStructExpr -> {
            val structType = this.type as TypeStruct
            for(index in fieldValues.indices) {
                val field = structType.fields[index]
                val value = fieldValues[index].codeGenRvalue()
                currentFunc.addStore(value, dest, field)
            }
        }

        is TctStackVariable -> {
            val srcAddr = currentFunc.addAlu(BinOp.ADD_I, cpuRegs[31], sym.offset)
            genMemCopy(dest, srcAddr, type.getSize())
        }

        is TctCallExpr -> {
            val argRegs = args.map { it.codeGenRvalue() }
            val thisArgReg = thisArg?.codeGenRvalue()
            genCall(func, thisArgReg, argRegs,dest)
        }

        else -> Log.error(location, "Cannot use expression of type '${this.type}' as an aggregate")
    }
}

fun TctExpr.codeGenAggregateLvalue() : Reg {
    when(this) {
        is TctStackVariable -> {
            return currentFunc.addAlu(BinOp.ADD_I, cpuRegs[31], sym.offset)
        }

        is TctIndexExpr -> {
            require(type is TypeStruct)
            val arrayReg = array.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val size = type.getSize()
            val bounds = if (array.type is TypeInlineArray)
                currentFunc.addLdImm(array.type.size)
            else if (array.type is TypePointer)
                currentFunc.addLdImm(0x7FFFFFFF) // Arbitrary large value - we can't do bounds checking on pointers
            else
                currentFunc.addLoad(arrayReg, lengthSymbol)
            val dummy = currentFunc.addIndex(1, indexReg, bounds)   // Check bounds
            val scaled = currentFunc.addAlu(BinOp.MUL_I, dummy, size)
            return currentFunc.addAlu(BinOp.ADD_I, arrayReg, scaled)
        }

        else -> error("Cannot use expression of type '${this.type}' as an aggregate at $location" )
    }
}

fun genMemCopy(dest:Reg, src:Reg, size:Int) {
    require(size and 3 == 0) { "memcpy size must be a multiple of 4" }
    for(i in 0 until size step 4) {
        val v = currentFunc.addLoad(4, src, i)
        currentFunc.addStore(4, v, dest, i)
    }
}



// ======================================================================
//                          Top Level
// ======================================================================

fun TctTop.codeGen() {
    currentFunc = this.function     // Create a function to represent top level assignments
    this.codeGenStmt()
}
