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

        is TctIndexExpr -> {
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
                is FieldSymbol -> TODO()
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

        is AstIndexExpr -> {
            val arrayExpr = array.typeCheckRvalue(scope)
            val indexExpr = index.typeCheckRvalue(scope)
            if (arrayExpr.type is TypeError || indexExpr.type is TypeError)
                return TctErrorExpr(location, "")
            if (arrayExpr.type !is TypeArray)
                return TctErrorExpr(location, "Cannot index type '${arrayExpr.type}'")
            indexExpr.checkType(TypeInt)
            return TctIndexExpr(location, arrayExpr, indexExpr, arrayExpr.type.elementType)
        }

        is AstMemberExpr -> {
            val tctExpr = objectExpr.typeCheckRvalue(scope)
            return when(tctExpr.type) {
                is TypeError -> TctErrorExpr(location, "")
                is TypeArray -> {
                    if (memberName != "length")
                        TctErrorExpr(location, "Array type '${tctExpr.type}' has no member '${memberName}'")
                    else
                        TctMemberExpr(location, tctExpr, lengthSymbol, TypeInt)
                }

                else -> TctErrorExpr(location, "Cannot access member '${memberName}' of type '${tctExpr.type}'")
            }
        }

        is AstNewExpr -> {
            val type = elementType.resolveType(scope)
            val tcArgs = args.map { it.typeCheckRvalue(scope) }
            val itSym = VarSymbol(location, "it", TypeInt, false)
            val tcLambda = lambda?.typeCheckLambda(scope, itSym)
            when(type) {
                is TypeError -> return TctErrorExpr(location, "")
                is TypeArray -> {
                    if (tcArgs.size != 1)
                        return TctErrorExpr(location, "Array constructor requires exactly one argument")
                    val size = tcArgs[0]
                    size.checkType(TypeInt)
                    if (size is TctConstant && size.value is IntValue && size.value.value < 0)
                        return TctErrorExpr(location, "Cannot create array of negative size")
                    tcLambda?.checkType(type.elementType)
                    val arrayType = TypeArray.create(type.elementType)
                    return TctNewArrayExpr(location, type, size, arena, tcLambda, arrayType)
                }

                else -> return TctErrorExpr(location, "Cannot create new instance of type '${type.name}'")
            }
        }

        is AstArrayLiteralExpr -> {
            val tcArgs = args.map { it.typeCheckRvalue(scope) }
            val type = elementType?.resolveType(scope) ?: if (tcArgs.isNotEmpty()) TypeArray.create(tcArgs[0].type) else
                reportTypeError(location, "Cannot infer type for array literal")
            if (type !is TypeArray)
                return TctErrorExpr(location, "Cannot create array literal of type '${type.name}'")
            for(item in tcArgs)
                item.checkType(type.elementType)
            if (arena==Arena.CONST) {
                val e = tcArgs.find {it !is TctConstant}
                if (e != null)
                    return TctErrorExpr(e.location, "Cannot create constant array literal with non-constant element")
                TODO("Constant array literals not implemented yet")
            }
            return TctNewArrayLiteralExpr(location, tcArgs, arena, type)
        }

        is AstNegateExpr -> {
            val tcExpr = expr.typeCheckRvalue(scope)
            if (tcExpr.type is TypeError)
                return TctErrorExpr(location, "")
            if (tcExpr.type != TypeInt)
                return TctErrorExpr(location, "Cannot negate type '${tcExpr.type}'")
            if (tcExpr is TctConstant && tcExpr.value is IntValue)
                return TctConstant(location, IntValue(-tcExpr.value.value, TypeInt))
            return TctNegateExpr(location, tcExpr)
        }

        is AstNotExpr -> TODO()
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

fun AstLambdaExpr.typeCheckLambda(scope:AstBlock, itSym:VarSymbol) : TctLambdaExpr {
    // Currently we only support vary basic lambda. They must take a single 'it' parameter
    // And consist of a single expression.
    require(body.size==1)
    val stmt = body[0]
    require(stmt is AstExpressionStmt) { "Lambda body must consist of a single expression statement" }

    addSymbol(itSym)
    val tcExpr = stmt.expr.typeCheckRvalue(this)
    return TctLambdaExpr(location, tcExpr, itSym, tcExpr.type)
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
        tctInitializer?.checkType(type)
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

    is AstLambdaExpr -> TODO()
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
        is AstArrayType -> {
            val elementType = elementType.resolveType(scope)
            return TypeArray.create(elementType)
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