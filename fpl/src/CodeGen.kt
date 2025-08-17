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
            }
        }

        is TctBinaryExpr -> {
            val regLhs = lhs.codeGenRvalue()
            val regRhs = rhs.codeGenRvalue()
            currentFunc.addAlu(op, regLhs, regRhs)
        }

        is TctCallExpr -> {
            val argRegs = args.map { it.codeGenRvalue() }
            genCall(func, argRegs)
        }

        is TctErrorExpr -> { zeroReg }  // Dummy return value

        is TctFunctionName -> TODO("Function pointers not supported yet")

        is TctReturnExpr -> {
            val retReg = expr?.codeGenRvalue()
            if (retReg!= null)
                currentFunc.addMov(resultReg, retReg)
            currentFunc.addJump(currentFunc.endLabel)
            zeroReg  // Dummy return value
        }

        is TctTypeName -> error("Type name found when expecting rvalue")

        is TctIndexExpr -> {
            val arrayReg = array.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val size = array.type.getSize()
            if (size == 0)
                Log.error(location, "Cannot index into type '${array.type}'")
            val bounds = currentFunc.addLoad(arrayReg, lengthSymbol)
            val scaled = currentFunc.addIndex(size, indexReg, bounds)
            val addr = currentFunc.addAlu(BinOp.ADD_I, arrayReg, scaled)
            currentFunc.addLoad(size, addr, 0)
        }

        is TctMemberExpr -> {
            val objectReg = objectExpr.codeGenRvalue()
            val size = member.type.getSize()
            if (size == 0)
                Log.error(location, "Cannot access member '${member.name}' of type '${objectExpr.type}'")
            currentFunc.addLoad(objectReg, member)
        }

        is TctNewArrayExpr -> {
            allocateArray(size, elementType, arena, lambda==null, lambda)
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
        is TctNegateExpr -> TODO()
        is TctLambdaExpr -> TODO()
        is TctRangeExpr -> TODO()
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




fun TctExpr.codeGenBool(trueLabel: Label, falseLabel: Label) {
    when (this) {
        is TctBinaryExpr -> {
            if (op.isCompare()) {
                val lhsReg = lhs.codeGenRvalue()
                val rhsReg = rhs.codeGenRvalue()
                currentFunc.addBranch(op, lhsReg, rhsReg, trueLabel)
                currentFunc.addJump(falseLabel)
            } else {
                val reg = codeGenRvalue()
                currentFunc.addBranch(BinOp.EQ_I, reg, zeroReg, falseLabel)
                currentFunc.addJump(trueLabel)
            }
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

        else -> {
            val reg = codeGenRvalue()
            currentFunc.addBranch(BinOp.EQ_I, reg, zeroReg, falseLabel)
            currentFunc.addJump(trueLabel)
        }

    }
}

fun TctExpr.codeGenLvalue(value:Reg) {
    when(this) {
        is TctVariable -> {
            currentFunc.addMov(currentFunc.getReg(sym), value)
        }

        is TctIndexExpr -> {
            val arrayReg = array.codeGenRvalue()
            val indexReg = index.codeGenRvalue()
            val size = array.type.getSize()
            if (size == 0)
                Log.error(location, "Cannot index into type '${array.type}'")
            val bounds = currentFunc.addLoad(arrayReg, lengthSymbol)
            val scaled = currentFunc.addIndex(size, indexReg, bounds)
            val addr = currentFunc.addAlu(BinOp.ADD_I, arrayReg, scaled)
            currentFunc.addStore(size, value, addr, 0)
        }

        is TctMemberExpr -> {
            val objectReg = objectExpr.codeGenRvalue()
            val size = member.type.getSize()
            if (size == 0)
                Log.error(location, "Cannot access member '${member.name}' of type '${objectExpr.type}'")
            currentFunc.addStore(value,objectReg, member)
        }



        else ->
            error("Not an lvalue ${this.javaClass}")
    }
}



fun genCall(func:Function, args: List<Reg>) : Reg {
    assert(args.size == func.parameters.size)

    // Copy arguments into CPU registers and call the function
    for ((index, arg) in args.withIndex())
        currentFunc.addMov(cpuRegs[index+1], arg)
    currentFunc.addCall(func)

    // Get the return value from the CPU register
    return if (func.returnType!= TypeUnit)
        currentFunc.addCopy(resultReg)
    else
        zeroReg  // Dummy return value
}


// ======================================================================
//                         Statements
// ======================================================================

fun TctStmt.codeGenStmt() {
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
            for((index,param) in function.parameters.withIndex())
                currentFunc.addMov( currentFunc.getReg(param), cpuRegs[index+1])
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
        }

        is TctEmptyStmt -> {}

        is TctExpressionStmt -> {
            expr.codeGenRvalue()
        }

        is TctVarDeclStmt -> {
            val regInit = initializer?.codeGenRvalue()
            if (regInit!= null)
                currentFunc.addMov(currentFunc.getReg(sym), regInit)
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
            lhs.codeGenLvalue(rhsReg)
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
            val arraySize = currentFunc.addLoad(arrayStart,lengthSymbol)
            val elementSize = (array.type as TypeArray).elementType.getSize()
            val sizeInBytes = currentFunc.addAlu(BinOp.MUL_I, arraySize, elementSize)
            val endReg = currentFunc.addAlu(BinOp.ADD_I, arrayStart, sizeInBytes)
            val ptrReg = currentFunc.newUserTemp()
            currentFunc.addMov(ptrReg, arrayStart)
            val symReg = currentFunc.getReg(index)
            continueStack.add(labelCont)
            breakStack.add(labelEnd)
            currentFunc.addJump(labelCond)
            currentFunc.addLabel(labelStart)
            val v = currentFunc.addLoad(elementSize, ptrReg, 0)
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
    }
}

// ======================================================================
//                          Top Level
// ======================================================================

fun TctTop.codeGen() {
    currentFunc = this.function     // Create a function to represent top level assignments
    this.codeGenStmt()
}
