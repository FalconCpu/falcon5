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
    }
}

// ======================================================================
//                          Top Level
// ======================================================================

fun TctTop.codeGen() {
    currentFunc = this.function     // Create a function to represent top level assignments
    this.codeGenStmt()
}
