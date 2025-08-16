private lateinit var topLevelFunction : Function

// ===========================================================================
//                          Expressions
// ===========================================================================

private fun AstExpr.typeCheckRvalue(scope: AstBlock) : TctExpr {
    return when(val ret = typeCheckExpr(scope)) {
        is TctTypeName -> TctErrorExpr(location,"'${ret.type}' is a type name, not a value")
        else -> ret
    }
}

private fun AstExpr.typeCheckLvalue(scope: AstBlock) : TctExpr {
    val ret = typeCheckExpr(scope)
    when(ret) {
        is TctVariable -> {
            if(!ret.sym.mutable)
                Log.error(location, "'${ret.sym}' is not mutable")
        }

        else -> return TctErrorExpr(location,"expression  is not an Lvalue")
    }
    return ret
}

private fun AstExpr.typeCheckBoolExpr(scope: AstBlock) : TctExpr {
    val ret = typeCheckExpr(scope)
    ret.checkType(TypeBool)
    return ret
}


private fun AstExpr.typeCheckExpr(scope: AstBlock) : TctExpr {
    return when(this) {
        is AstIntLiteral -> {
            TctConstant(location, IntValue(value, TypeInt))
        }

        is AstCharLiteral -> {
            TctConstant(location, IntValue(value, TypeChar))
        }

        is AstStringLiteral -> {
            TctConstant(location, StringValue.create(value))
        }

        is AstIdentifier ->
            when(val sym = scope.lookupSymbol(name)) {
                null -> {
                    Log.error(location, "'${name}' is not defined")
                    val new = VarSymbol(location, name, TypeError, true)
                    scope.addSymbol(new)
                    TctVariable(location, new, TypeError)
                }

                is ConstSymbol -> TctConstant(location, sym.value)
                is FunctionSymbol -> TctFunctionName(location, sym)
                is GlobalVarSymbol -> TODO()
                is TypeNameSymbol -> TctTypeName(location, sym)
                is VarSymbol -> TctVariable(location, sym, sym.type)
            }

        is AstBinaryExpr -> {
            val tctLhs = lhs.typeCheckRvalue(scope)
            val tctRhs = rhs.typeCheckRvalue(scope)
            if (tctLhs.type==TypeError || tctRhs.type==TypeError)
                return TctErrorExpr(location, "")
            val match = operatorTable.find{it.tokenKind==op && it.lhsType==tctLhs.type && it.rhsType==tctRhs.type}
            if (match==null)
                return TctErrorExpr(location, "Invalid operator '${op}' for types '${tctLhs.type}' and '${tctRhs.type}'")
            TctBinaryExpr(location, match.resultOp, tctLhs, tctRhs, match.resultType)
        }

        is AstReturnExpr -> {
            val enclosingFunc = scope.findEnclosingFunction()
                               ?: return TctErrorExpr(location, "Return statement outside of a function")
            val tctExpr = expr?.typeCheckRvalue(scope)
            if (tctExpr==null) {
                if (enclosingFunc.returnType!=TypeUnit)
                    Log.error(location, "Function should return an expression of type '${enclosingFunc.returnType}'")
            } else {
                tctExpr.checkType(enclosingFunc.returnType)
            }
            TctReturnExpr(location, tctExpr)
        }

        is AstCallExpr -> {
            val tctFunc = func.typeCheckRvalue(scope)
            val tctArgs = args.map{ it.typeCheckRvalue(scope) }
            when(tctFunc) {
                is TctErrorExpr -> return tctFunc
                is TctFunctionName -> {
                    val resolvedFunc = tctFunc.sym.resolveOverload(location, tctArgs)
                                     ?: return TctErrorExpr(location, "")  // Error message is reported by resolveOverload()
                    TctCallExpr(location, resolvedFunc, tctArgs)
                }
                else ->
                    return TctErrorExpr(location, "Invalid function call")
            }
        }
    }
}


fun Function.isCallableWithArgs(args: List<TctExpr>) : Boolean {
    if (parameters.size!= args.size) return false
    for ((param, arg) in parameters zip(args))
        if (! arg.type.isAssignableTo(param.type)) return false
    return true
}

fun FunctionSymbol.resolveOverload(location:Location, args: List<TctExpr>) : Function? {
    val resolvedFunc = overloads.filter { it.isCallableWithArgs(args) }
    if (resolvedFunc.isEmpty()) {
        Log.error(location, "No matching function found for call")
        return null
    } else if (resolvedFunc.size>1) {
        Log.error(location, "Ambiguous function call: multiple matching overloads found")
        return null
    } else
        return resolvedFunc.first()
}

// ===========================================================================
//                          Statements
// ===========================================================================

private fun AstStmt.typeCheckStmt(scope: AstBlock) : TctStmt = when(this) {
    is AstExpressionStmt -> {
        val tctExpr = expr.typeCheckRvalue(scope)
        TctExpressionStmt(location, tctExpr)
    }

    is AstVarDeclStmt -> {
        val tctInitializer = initializer?.typeCheckRvalue(scope)
        val type = astType?.resolveType(scope) ?: tctInitializer?.type ?: reportTypeError(location, "Cannot infer type for variable '${name}'")
        val sym = VarSymbol(location, name, type, mutable)
        scope.addSymbol(sym)
        TctVarDeclStmt(location, sym, tctInitializer)
    }

    is AstFunctionDefStmt -> {
        val tctBody = body.map{ it.typeCheckStmt(this) }
        TctFunctionDefStmt(location, name, function, tctBody)
    }

    is AstEmptyStmt -> TctEmptyStmt(location)

    is AstFile -> TctFile(location, body.map{ it.typeCheckStmt(this) } )

    is AstTop -> TctTop(location, topLevelFunction, body.map{ it.typeCheckStmt(this) } )

    is AstWhileStmt -> {
        val tctCondition = condition.typeCheckBoolExpr(scope)
        val tctBody = body.map{ it.typeCheckStmt(this) }
        TctWhileStmt(location, tctCondition, tctBody)
    }

    is AstRepeatStmt -> {
        val tctCondition = condition.typeCheckBoolExpr(scope)
        val tctBody = body.map{ it.typeCheckStmt(this) }
        TctRepeatStmt(location, tctCondition, tctBody)
    }

    is AstAssignStmt -> {
        val rhs = rhs.typeCheckRvalue(scope)
        val lhs = lhs.typeCheckLvalue(scope)
        rhs.checkType(lhs.type)
        TctAssignStmt(location, op, lhs, rhs)
    }

    is AstIfClause -> {
        val tctCondition = condition?.typeCheckBoolExpr(scope)
        val tctBody = body.map { it.typeCheckStmt(this) }
        TctIfClause(location, tctCondition, tctBody)
    }

    is AstIfStmt -> {
        val clauses = body.map { it.typeCheckStmt(this) as TctIfClause }
        TctIfStmt(location,clauses)
    }
}


// ===========================================================================
//                          Types
// ===========================================================================

private fun AstType.resolveType(scope:AstBlock) : Type {
    when(this) {
        is AstTypeIdentifier -> {
            return when (val sym = scope.lookupSymbol(name)) {
                null -> reportTypeError(location, "Unresolved identifier: '$name'")
                is TypeNameSymbol -> sym.type
                else -> reportTypeError(location, "'${sym.name}' is a ${sym.getDescription()} not a type")
            }
        }
    }
}


// ===========================================================================
//                          Top Level
// ===========================================================================

private fun AstBlock.setParent(parent: AstBlock?) {
    this.parent = parent
    for(blk in body.filterIsInstance<AstBlock>())
        blk.setParent(this)
}

private fun AstParameter.createSymbol(scope:AstBlock) : VarSymbol {
    val type = type.resolveType(scope)
    return VarSymbol(location, name, type, false)
}


// Build the Function Nodes
private fun AstBlock.findFunctionDefinitions(scope:AstBlock) {
    if (this is AstFunctionDefStmt) {
        val paramsSymbols = params.map { it.createSymbol(this) }
        val returnType = retType?.resolveType(this) ?: TypeUnit
        val longName = name + paramsSymbols.joinToString(separator = ",", prefix = "(", postfix = ")") { it.type.name }
        function = Function(location, longName, paramsSymbols, returnType, isExtern)
        for(sym in paramsSymbols)
            addSymbol(sym)
        scope.addFunctionOverload(location, name, function)

    } else {
        for(blk in body.filterIsInstance<AstBlock>())
            blk.findFunctionDefinitions(this)
    }
}


fun AstTop.typeCheck() : TctTop {
    topLevelFunction = Function(location, "topLevel", emptyList(), TypeUnit, false)

    // Walk the AST, setting each node's parent
    setParent(null)

    // Find all function declarations
    findFunctionDefinitions(this)

    val ret = typeCheckStmt(this)
    return ret as TctTop
}