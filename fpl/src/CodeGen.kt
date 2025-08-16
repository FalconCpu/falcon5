lateinit var currentFunc : Function

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
            val elementSize = type.getSize()
            for(index in elements.indices)
                currentFunc.addStore(elementSize, elements[index].codeGenRvalue(), ret, index*elementSize)
            ret
        }

        is TctNegateExpr -> TODO()
        is TctNotExpr -> TODO()
        is TctLambdaExpr -> TODO()
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
        Arena.STACK -> TODO()
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
            currentFunc.addJump(condLabel)
            currentFunc.addLabel(startLabel)
            for(stmt in body)
                stmt.codeGenStmt()
            currentFunc.addLabel(condLabel)
            condition.codeGenBool(startLabel, endLabel)
            currentFunc.addLabel(endLabel)
        }

        is TctRepeatStmt -> {
            val startLabel = currentFunc.newLabel()
            val endLabel = currentFunc.newLabel()
            val condLabel = currentFunc.newLabel()
            currentFunc.addLabel(startLabel)
            for(stmt in body)
                stmt.codeGenStmt()
            currentFunc.addLabel(condLabel)
            condition.codeGenBool(endLabel, startLabel)
            currentFunc.addLabel(endLabel)
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
    }
}

// ======================================================================
//                          Top Level
// ======================================================================

fun TctTop.codeGen() {
    currentFunc = this.function     // Create a function to represent top level assignments
    this.codeGenStmt()
}
