private lateinit var topLevelFunction : Function
private var currentLoop : AstBlock? = null
private var pathContext = emptyPathContext

// keep track of the path context for break statements
private var breakContext = mutableListOf<PathContext>()
private var continueContext = mutableListOf<PathContext>()

// Only report the first unreachable statement
private var firstUnreachableStatement = true

// For loops, we need to make at least two passes through the type checker to get the pathContext right
// On the second pass disable reporting duplicate variable declarations (as they were allocated in the first pass)
var secondTypecheckPass = false



// ===========================================================================
//                          Expressions
// ===========================================================================

private fun AstExpr.typeCheckRvalue(scope: AstBlock, allowTypes:Boolean=false) : TctExpr {
    val ret = typeCheckExpr(scope)

    when(ret) {
        is TctVariable -> {
            if (ret.sym in pathContext.uninitializedVariables)
                Log.error(location, "'${ret.sym.name}' is uninitialized")
            else if (ret.sym in pathContext.maybeUninitializedVariables)
                Log.error(location, "'${ret.sym.name}' may be uninitialized")
            val refinedType = pathContext.refinedTypes[ret.sym]
            if (refinedType!=null)
                return TctVariable(location, ret.sym, refinedType)
        }

        is  TctTypeName->
            if (!allowTypes)
                return TctErrorExpr(location,"'${ret.type}' is a type name, not a value")

        else -> {}
    }

    return ret
}

private fun AstExpr.typeCheckLvalue(scope: AstBlock) : TctExpr {
    val ret = typeCheckExpr(scope)
    when(ret) {
        is TctVariable -> {
            if(!ret.sym.mutable && ret.sym !in pathContext.uninitializedVariables)
                Log.error(location, "'${ret.sym}' is not mutable")
            pathContext = pathContext.initialize(ret.sym)
        }

        is TctIndexExpr -> {
        }

        else -> return TctErrorExpr(location,"expression  is not an Lvalue")
    }
    return ret
}

private fun AstExpr.typeCheckBoolExpr(scope: AstBlock) : BranchPathContext {
    return when (this) {
        is AstCompareExpr -> {
            val expr = typeCheckExpr(scope)
            BranchPathContext(pathContext, pathContext, expr)
        }

        is AstEqualityExpr -> {
            val tcExpr = typeCheckExpr(scope)
            var truePath = pathContext
            var falsePath = pathContext
            if (tcExpr is TctIntCompareExpr) {
                val lhs = tcExpr.lhs
                val rhs = tcExpr.rhs

                // Handle types of form a=null
                if (lhs.type is TypeNullable && rhs.type is TypeNull) {
                    truePath = pathContext.refineType(lhs, TypeNull)
                    falsePath = pathContext.refineType(lhs, lhs.type.elementType)
                }
                if (rhs.type is TypeNullable && lhs.type is TypeNull) {
                    truePath = pathContext.refineType(rhs, TypeNull)
                    falsePath = pathContext.refineType(rhs, rhs.type.elementType)
                }
                // handle checks of form a? = b
                if (lhs.type is TypeNullable && rhs.type==lhs.type.elementType)
                    truePath = pathContext.refineType(lhs, rhs.type)
                if (rhs.type is TypeNullable && lhs.type==rhs.type.elementType)
                    truePath = pathContext.refineType(rhs, lhs.type)
            }
            if (op==TokenKind.EQ)
                BranchPathContext(truePath, falsePath, tcExpr)
            else
                BranchPathContext(falsePath, truePath, tcExpr)
        }

        is AstIsExpr -> {
            val tcExpr = typeCheckExpr(scope)
            var truePath = pathContext
            var falsePath = pathContext

            if (tcExpr is TctIsExpr)
                truePath = pathContext.refineType(tcExpr.expr, tcExpr.typeExpr)

            BranchPathContext(truePath, falsePath, tcExpr)
        }

        is AstAndExpr -> {
            val tcLhs = lhs.typeCheckBoolExpr(scope)
            pathContext = tcLhs.trueBranch
            val tcRhs = rhs.typeCheckBoolExpr(scope)
            val expr = TctAndExpr(location, tcLhs.expr, tcRhs.expr)
            BranchPathContext(tcRhs.trueBranch,
                              listOf(tcLhs.falseBranch, tcRhs.falseBranch).merge(),
                              expr)
        }

        is AstOrExpr -> {
            val tcLhs = lhs.typeCheckBoolExpr(scope)
            pathContext = tcLhs.falseBranch
            val tcRhs = rhs.typeCheckBoolExpr(scope)
            val expr = TctOrExpr(location, tcLhs.expr, tcRhs.expr)
            BranchPathContext(listOf(tcLhs.trueBranch, tcRhs.trueBranch).merge(),
                              tcRhs.falseBranch,
                              expr)
        }

        else -> {
            // For all other expressions, we type check them as rvalues
            // and then check if they are boolean expressions.
            val expr = typeCheckRvalue(scope)
            expr.checkType(TypeBool)
            BranchPathContext(pathContext, pathContext, expr)
        }
    }
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
                is FieldSymbol -> {
                    val func = scope.findEnclosingFunction()!!
                    if (func.thisSymbol!=null)
                        TctMemberExpr(location, TctVariable(location, func.thisSymbol, func.thisSymbol.type), sym, sym.type)
                    else
                        TctErrorExpr(location, "Field '${sym.name}' cannot be accessed outside of a class context")
                }
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
            pathContext = unreachablePathContext
            TctReturnExpr(location, tctExpr)
        }

        is AstCallExpr -> {
            val tctFunc = func.typeCheckRvalue(scope)
            val tctArgs = args.map{ it.typeCheckRvalue(scope) }
            if (tctArgs.any { it.type is TypeError })
                return TctErrorExpr(location, "")
            when(tctFunc) {
                is TctErrorExpr -> return tctFunc
                is TctFunctionName -> {
                    val resolvedFunc = tctFunc.sym.resolveOverload(location, tctArgs)
                                     ?: return TctErrorExpr(location, "")  // Error message is reported by resolveOverload()
                    val currentFunc = scope.findEnclosingFunction()
                    val thisSym = currentFunc?.thisSymbol
                    val thisArg = if (resolvedFunc.thisSymbol != null) {
                        if (thisSym == null)
                            return TctErrorExpr(location, "Function '${resolvedFunc.name}' requires 'this' argument but is not in a class context")
                        else if (thisSym.type != resolvedFunc.thisSymbol.type)
                            return TctErrorExpr(location, "Function '${resolvedFunc.name}' requires 'this' argument of type '${resolvedFunc.thisSymbol.type}' but got '${thisSym.type}'")
                        else
                            TctVariable(location, thisSym, thisSym.type)
                    } else {
                        null
                    }
                    TctCallExpr(location, thisArg, resolvedFunc, tctArgs)
                }
                is TctMethodRefExpr -> {
                    val resolvedFunc = tctFunc.methodSym.resolveOverload(location, tctArgs)
                        ?: return TctErrorExpr(location, "")  // Error message is reported by resolveOverload()
                    TctCallExpr(location, tctFunc.objectExpr, resolvedFunc, tctArgs)
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
            val tctExpr = objectExpr.typeCheckRvalue(scope, allowTypes = true)

            if (tctExpr is TctTypeName)
                return typecheckStaticMember(tctExpr)

            return when(tctExpr.type) {
                is TypeError -> TctErrorExpr(location, "")
                is TypeArray -> {
                    if (memberName != "length")
                        TctErrorExpr(location, "Array type '${tctExpr.type}' has no member '${memberName}'")
                    else
                        TctMemberExpr(location, tctExpr, lengthSymbol, TypeInt)
                }

                is TypeString -> {
                    if (memberName != "length")
                        TctErrorExpr(location, "String has no member '${memberName}'")
                    else
                        TctMemberExpr(location, tctExpr, lengthSymbol, TypeInt)
                }

                is TypeClass -> {
                    when(val field = tctExpr.type.lookup(memberName)) {
                        null -> TctErrorExpr(location, "Class '${tctExpr.type.name}' has no member '${memberName}'")
                        is FieldSymbol -> TctMemberExpr(location, tctExpr, field, field.type)
                        is FunctionSymbol -> TctMethodRefExpr(location, tctExpr, field, TypeNothing)
                        else -> error("Unexpected symbol type '${field.getDescription()}' for member '${memberName}' of class '${tctExpr.type.name}'")
                    }
                }

                is TypeNullable ->
                    TctErrorExpr(location, "Cannot access member as expression may be null")

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
                    if (arena==Arena.STACK && size !is TctConstant)
                        return TctErrorExpr(location, "Local arrays must have a constant size")
                    if (size is TctConstant && size.value is IntValue && size.value.value < 0)
                        return TctErrorExpr(location, "Cannot create array of negative size")
                    tcLambda?.checkType(type.elementType)
                    val arrayType = TypeArray.create(type.elementType)
                    return TctNewArrayExpr(location, type, size, arena, tcLambda, arrayType)
                }

                is TypeClass -> {
                    if (!type.constructor.isCallableWithArgs(tcArgs)) {
                        val argsStr = tcArgs.joinToString(",") { it.type.name }
                        val paramStr = type.constructor.parameters.joinToString(",") { it.type.name }
                        return TctErrorExpr(location,"Constructor '$type' called with ($argsStr) when expecting ($paramStr)")
                    }
                    TctNewClassExpr(location, type, tcArgs, arena, type)
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
                val values = tcArgs.map { (it as TctConstant).value }
                val arrayValue = ArrayValue.create(values, type)
                return TctConstant(location, arrayValue)
            } else
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

        is AstRangeExpr -> {
            val tcStart = start.typeCheckRvalue(scope)
            val tcEnd = end.typeCheckRvalue(scope)
            if (tcStart.type is TypeError || tcEnd.type is TypeError)
                return TctErrorExpr(location, "")
            if (tcStart.type!=TypeInt && tcStart.type!=TypeChar)
                return TctErrorExpr(location, "Range start must be of type Int or Char, got '${tcStart.type}'")
            tcEnd.checkType(tcStart.type)
            val op = when(op) {
                TokenKind.LT -> BinOp.LT_I
                TokenKind.LTE -> BinOp.LE_I
                TokenKind.GT -> BinOp.GT_I
                TokenKind.GTE -> BinOp.GE_I
                else -> return TctErrorExpr(location, "Invalid range operator '${op}'")
            }
            TctRangeExpr(location, tcStart, tcEnd, op, TypeRange.create(tcStart.type))
        }

        is AstAndExpr -> {
            val tctLhs = lhs.typeCheckBoolExpr(scope)
            val tctRhs = rhs.typeCheckBoolExpr(scope)
            if (tctLhs.expr.type==TypeError || tctRhs.expr.type==TypeError)
                return TctErrorExpr(location, "")
            TctAndExpr(location, tctLhs.expr, tctRhs.expr)
        }

        is AstNotExpr -> {
            val tctExpr = expr.typeCheckBoolExpr(scope)
            if (tctExpr.expr.type is TypeError)
                return TctErrorExpr(location, "")
            TctNotExpr(location, tctExpr.expr)
        }

        is AstOrExpr -> {
            val tctLhs = lhs.typeCheckBoolExpr(scope)
            val tctRhs = rhs.typeCheckBoolExpr(scope)
            if (tctLhs.expr.type==TypeError || tctRhs.expr.type==TypeError)
                return TctErrorExpr(location, "")
            TctOrExpr(location, tctLhs.expr, tctRhs.expr)
        }

        is AstBreakExpr -> {
            if (currentLoop == null)
                Log.error(location, "Break statement outside of a loop")
            breakContext += pathContext
            pathContext = unreachablePathContext
            TctBreakExpr(location)
        }

        is AstContinueExpr -> {
            if (currentLoop == null)
                Log.error(location, "Continue statement outside of a loop")
            continueContext += pathContext
            pathContext = unreachablePathContext
            TctContinueExpr(location)
        }

        is AstEqualityExpr -> {
            val tcLhs = lhs.typeCheckRvalue(scope)
            val tcRhs = rhs.typeCheckRvalue(scope)
            if (tcLhs.type is TypeError || tcRhs.type is TypeError)
                return TctErrorExpr(location, "")
            if (!tcLhs.type.isAssignableTo(tcRhs.type) && !tcRhs.type.isAssignableTo(tcLhs.type))
                return TctErrorExpr(location, "Cannot compare types '${tcLhs.type}' and '${tcRhs.type}'")
            val aluOp = op.toCompareOp()
            when(tcLhs.type) {
                TypeString -> TctStringCompareExpr(location, aluOp, tcLhs, tcRhs)
                else -> TctIntCompareExpr(location, aluOp, tcLhs, tcRhs)
            }
        }

        is AstCompareExpr -> {
            val tcLhs = lhs.typeCheckRvalue(scope)
            val tcRhs = rhs.typeCheckRvalue(scope)
            if (tcLhs.type is TypeError || tcRhs.type is TypeError)
                return TctErrorExpr(location, "")
            if (tcLhs.type != tcRhs.type)
                return TctErrorExpr(location, "Cannot compare types '${tcLhs.type}' and '${tcRhs.type}'")
            val aluOp = op.toCompareOp()
            when(tcLhs.type) {
                TypeInt, TypeChar, TypeBool -> TctIntCompareExpr(location, aluOp, tcLhs, tcRhs)
                TypeString -> TctStringCompareExpr(location, aluOp, tcLhs, tcRhs)
                else -> TctErrorExpr(location, "Cannot compare type '${tcLhs.type}' with operator '${op}'")
            }
        }

        is AstNullAssertExpr -> {
            val tctExpr = expr.typeCheckRvalue(scope)
            if (tctExpr.type is TypeError)
                return TctErrorExpr(location, "")
            return if (tctExpr.type is TypeNullable)
                TctNullAssertExpr(location, tctExpr, tctExpr.type.elementType)
            else
                TctErrorExpr(location, "Cannot assert non-null on type '${tctExpr.type}'")
        }

        is AstIsExpr -> {
            val tctExpr = expr.typeCheckRvalue(scope)
            if (tctExpr.type is TypeError)
                return TctErrorExpr(location, "")
            val typeL = if (tctExpr.type is TypeNullable) tctExpr.type.elementType else tctExpr.type
            if (typeL !is TypeClass)
                return TctErrorExpr(location, "Got type '${tctExpr.type}' when expecting a class type")

            val typeR = typeExpr.resolveType(scope)
            if (typeR is TypeError)
                return TctErrorExpr(location, "")
            if (typeR !is TypeClass)
                return TctErrorExpr(typeExpr.location, "Got type '$typeR' when expecting a class type")
            if (!typeL.isSuperClassOf(typeR))
                return TctErrorExpr(location, "Is expression is always false, '${typeL.name}' is not a super class of '${typeR.name}'")
            if (typeL==typeR && tctExpr.type !is TypeNullable)
                return TctErrorExpr(location, "Is expression is always true")
            return TctIsExpr(location, tctExpr, typeR)
        }

        is AstAsExpr -> {
            val tctExpr = expr.typeCheckRvalue(scope)
            if (tctExpr.type is TypeError)        return TctErrorExpr(location, "")
            val typeR = typeExpr.resolveType(scope)
            if (typeR is TypeError)               return TctErrorExpr(location, "")

            return if ( (tctExpr.type is TypeEnum && typeR is TypeInt) ||
                (tctExpr.type is TypeInt && typeR is TypeEnum))
                TctAsExpr(location, tctExpr, typeR)
            else
                TctErrorExpr(location, "Cannot cast type '${tctExpr.type}' to '${typeR.name}'")
        }
    }
}

private fun AstMemberExpr.typecheckStaticMember(tctExpr: TctExpr): TctExpr {
    if (tctExpr.type is TypeEnum) {
        // Special case enumName.values -> range of enum values
        if (memberName == "values") {
            val zeroExpr = TctConstant(location, IntValue(0, tctExpr.type))
            val lengthExpr = TctConstant(location, IntValue(tctExpr.type.values.size, tctExpr.type))
            return TctRangeExpr(location, zeroExpr, lengthExpr, BinOp.LT_I, TypeRange.create(tctExpr.type))
        }

        val sym = tctExpr.type.lookup(memberName)
        return if (sym is ConstSymbol) {
            TctConstant(location, sym.value)
        } else {
            TctErrorExpr(location, "Enum '${tctExpr.type.name}' has no member '${memberName}'")
        }
    } else
        return TctErrorExpr(location, "Cannot access member '${memberName}' of type '${tctExpr.type}'")
}


fun TokenKind.toCompareOp() = when(this) {
    TokenKind.LT  -> BinOp.LT_I
    TokenKind.GT  -> BinOp.GT_I
    TokenKind.EQ  -> BinOp.EQ_I
    TokenKind.NEQ -> BinOp.NE_I
    TokenKind.LTE -> BinOp.LE_I
    TokenKind.GTE -> BinOp.GE_I
    else -> error("Invalid binary operator $this")
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
        val longName = name + args.joinToString(separator = ",", prefix = "(", postfix = ")") { it.type.name }
        val candidates = overloads.joinToString(separator = "\n") { it.name }
        Log.error(location, "No function found for $longName candidates are:-\n$candidates")
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

private fun AstStmt.typeCheckStmt(scope: AstBlock) : TctStmt {
    if (pathContext.unreachable && firstUnreachableStatement && this !is AstFunctionDefStmt && this !is AstClassDefStmt && this !is AstFile) {
        Log.error(location, "Statement is unreachable")
        firstUnreachableStatement = false
    }

    return when (this) {
        is AstExpressionStmt -> {
            val tctExpr = expr.typeCheckRvalue(scope)
            TctExpressionStmt(location, tctExpr)
        }

        is AstVarDeclStmt -> {
            val tctInitializer = initializer?.typeCheckRvalue(scope)
            val type = astType?.resolveType(scope) ?: tctInitializer?.type ?: reportTypeError(
                location,
                "Cannot infer type for variable '${name}'"
            )
            if (type is TypeUnit)
                Log.error(location, "Variable '${name}' cannot be of type Unit")
            val sym = VarSymbol(location, name, type, mutable)
            scope.addSymbol(sym)
            tctInitializer?.checkType(type)
            if (tctInitializer == null)
                pathContext = pathContext.addUninitialized(sym)
            TctVarDeclStmt(location, sym, tctInitializer)
        }

        is AstFunctionDefStmt -> {
            pathContext = emptyPathContext
            firstUnreachableStatement = true
            val tctBody = body.map { it.typeCheckStmt(this) }
            if (!pathContext.unreachable && function.returnType!=TypeUnit)
                Log.error(location, "Function '${name}' must return a value along all paths")
            TctFunctionDefStmt(location, name, function, tctBody)
        }

        is AstEmptyStmt -> TctEmptyStmt(location)

        is AstFile -> TctFile(location, body.map { it.typeCheckStmt(this) })

        is AstTop -> TctTop(location, topLevelFunction, body.map { it.typeCheckStmt(this) })

        is AstWhileStmt -> {
            val oldLoop = currentLoop
            val oldBreakContext = breakContext
            val oldContinueContext = continueContext
            val oldSecondTypeCheckPass = secondTypecheckPass
            currentLoop = this
            val tctCondition = condition.typeCheckBoolExpr(scope)
            pathContext = tctCondition.trueBranch
            // make a first pass through the body to get the pathContext for the loop path
            body.map { it.typeCheckStmt(this) }
            secondTypecheckPass = true
            pathContext = (listOf(pathContext, tctCondition.trueBranch)+continueContext).merge()
            breakContext = mutableListOf()
            continueContext = mutableListOf()
            val tctBody = body.map { it.typeCheckStmt(this) }

            // determine the pathContext for after the loop
            val mergePaths = if (tctCondition.expr.isAlwaysTrue())
                breakContext
            else
                breakContext + pathContext + tctCondition.falseBranch
            pathContext = mergePaths.merge()
            breakContext = oldBreakContext
            continueContext = oldContinueContext
            secondTypecheckPass = oldSecondTypeCheckPass
            currentLoop = oldLoop
            TctWhileStmt(location, tctCondition.expr, tctBody)
        }

        is AstRepeatStmt -> {
            val oldLoop = currentLoop
            val oldBreakContext = breakContext
            val oldContinueContext = continueContext
            val oldSecondTypeCheckPass = secondTypecheckPass
            currentLoop = this
            breakContext = mutableListOf()
            continueContext = mutableListOf()
            val entryPathContext = pathContext
            body.map { it.typeCheckStmt(this) }   // dummy pass to get the pathContext for the loop body
            val tctCondition = condition.typeCheckBoolExpr(scope)
            secondTypecheckPass = true
            pathContext = (listOf(entryPathContext, tctCondition.falseBranch)+continueContext).merge()
            breakContext = mutableListOf()
            continueContext = mutableListOf()
            val tctBody = body.map { it.typeCheckStmt(this) }
            val mergePaths = if (tctCondition.expr.isAlwaysFalse())
                breakContext
            else
                breakContext + pathContext + tctCondition.trueBranch
            pathContext = mergePaths.merge()
            breakContext = oldBreakContext
            continueContext = oldContinueContext
            secondTypecheckPass = oldSecondTypeCheckPass
            currentLoop = oldLoop
            TctRepeatStmt(location, tctCondition.expr, tctBody)
        }

        is AstForStmt -> {
            val oldLoop = currentLoop
            val oldBreakContext = breakContext
            val oldContinueContext = continueContext
            val oldSecondTypeCheckPass = secondTypecheckPass
            val entryPathContext = pathContext
            currentLoop = this

            val tcRange = range.typeCheckRvalue(scope)
            val type = indexType?.resolveType(scope) ?: when (tcRange.type) {
                is TypeError -> TypeError
                is TypeRange -> tcRange.type.elementType
                is TypeArray -> tcRange.type.elementType
                else -> reportTypeError(location, " Cannot iterate over type '${tcRange.type}'")
            }

            val sym = VarSymbol(location, indexName, type, false)
            addSymbol(sym)

            breakContext = mutableListOf()
            continueContext = mutableListOf()
            body.map { it.typeCheckStmt(this) }

            secondTypecheckPass = true
            breakContext = mutableListOf()
            continueContext = mutableListOf()
            pathContext = (listOf(entryPathContext, pathContext)+continueContext).merge()
            val tcBody = body.map { it.typeCheckStmt(this) }

            currentLoop = oldLoop
            val ret = if (type == TypeError)
                TctEmptyStmt(location) // Error already reported
            else if (tcRange is TctRangeExpr) {
                tcRange.start.checkType(type)
                TctForRangeStmt(location, sym, tcRange, tcBody)
            } else if (tcRange.type is TypeArray) {
                if (!tcRange.type.elementType.isAssignableTo(type))
                    reportTypeError(
                        location,
                        "Cannot iterate over array of type '${tcRange.type}' with index type '${type}'"
                    )
                TctForArrayStmt(location, sym, tcRange, tcBody)
            } else
                TODO()
            pathContext = (breakContext+pathContext).merge()
            breakContext = oldBreakContext
            continueContext = oldContinueContext
            secondTypecheckPass = oldSecondTypeCheckPass
            currentLoop = oldLoop
            ret
        }


        is AstAssignStmt -> {
            val rhs = rhs.typeCheckRvalue(scope)
            val lhs = lhs.typeCheckLvalue(scope)
            rhs.checkType(lhs.type)
            if (rhs.type != lhs.type)
                pathContext = pathContext.refineType(lhs, rhs.type)
            TctAssignStmt(location, op, lhs, rhs)
        }

        is AstIfClause -> error("Should not be type checking AstIfClause directly, it should be part of AstIfStmt")

        is AstIfStmt -> {
            val clauses = mutableListOf<TctIfClause>()
            val mergeContexts = mutableListOf<PathContext>()
            for (clause in body.filterIsInstance<AstIfClause>()) {
                val tctCondition = clause.condition?.typeCheckBoolExpr(scope)
                pathContext = tctCondition?.trueBranch ?: pathContext
                val tctBody = clause.body.map { it.typeCheckStmt(this) }
                mergeContexts += pathContext
                clauses += TctIfClause(location, tctCondition?.expr, tctBody)
                pathContext = tctCondition?.falseBranch ?: pathContext
            }
            if (clauses.none { it.condition == null })
                mergeContexts += pathContext // Allow for fall-through if there is no else clause
            pathContext = mergeContexts.merge()
            TctIfStmt(location, clauses)
        }


        is AstClassDefStmt -> {
            val methods = body.filterIsInstance<AstFunctionDefStmt>().map { it.typeCheckStmt(this) as TctFunctionDefStmt }
            TctClassDefStmt(location, klass, initializers, methods)
        }

        is AstLambdaExpr -> error("Lambda expressions should not be type checked here, they should be handled in the expression type checking phase")

        is AstEnumDefStmt -> TctEmptyStmt(location)
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
        is AstArrayType -> {
            val elementType = elementType.resolveType(scope)
            return TypeArray.create(elementType)
        }
        is AstNullableType -> {
            val elementType = elementType.resolveType(scope)
            return TypeNullable.create(elementType)
        }
    }
}


// ===========================================================================
//                          Top Level
// ===========================================================================

private fun AstBlock.setParent(parent: AstBlock?) {
    this.parent = parent

    if (this is AstClassDefStmt)
        constructorScope.parent = this

    for(blk in body.filterIsInstance<AstBlock>())
        blk.setParent(this)
}

private fun AstParameter.createSymbol(scope:AstBlock) : VarSymbol {
    val type = type.resolveType(scope)
    return VarSymbol(location, name, type, false)
}


// Find class definitions
private fun AstBlock.findClassDefinitions(scope:AstBlock) {
    if (this is AstClassDefStmt) {
        var superClass = astSuperClass?.resolveType(scope)
        if (superClass!=null && superClass !is TypeClass) {
            reportTypeError(location, "Superclass '$superClass' is not a class")
            superClass = null
        }

        klass = TypeClass(name, superClass)
        val sym = TypeNameSymbol(location, name, klass)
        scope.addSymbol(sym)
    } else if (this is AstEnumDefStmt) {
        enum = TypeEnum(name)
        val sym = TypeNameSymbol(location, name, enum)
        scope.addSymbol(sym)
    }



    // Walk the tree
    for (stmt in body.filterIsInstance<AstBlock>())
        stmt.findClassDefinitions(this)
}

// Build the Function Nodes
private fun AstBlock.findFunctionDefinitions(scope:AstBlock) {
    when (this) {
        is AstFunctionDefStmt -> {
            val paramsSymbols = params.map { it.createSymbol(this) }
            val returnType = retType?.resolveType(this) ?: TypeUnit
            val longName = (if (scope is AstClassDefStmt) scope.klass.name + "/" else "") +
                name + paramsSymbols.joinToString(separator = ",", prefix = "(", postfix = ")") { it.type.name }
            val thisSym = if (scope is AstClassDefStmt) VarSymbol(location, "this", scope.klass, false) else null
            function = Function(location, longName, thisSym, paramsSymbols, returnType, qualifier)
            for (sym in paramsSymbols)
                addSymbol(sym)
            if (thisSym != null)
                addSymbol(thisSym)
            if (qualifier==TokenKind.VIRTUAL) {
                if (scope is AstClassDefStmt) {
                    function.virtualFunctionNumber = scope.klass.virtualFunctions.size
                    scope.klass.virtualFunctions.add(function)
                } else
                    Log.error(location, "Virtual function '${name}' must be defined in a class context")
            }
            if (qualifier==TokenKind.OVERRIDE)
                scope.addFunctionOverride(location, name, function)
            else
                scope.addFunctionOverload(location, name, function)
        }

        is AstClassDefStmt -> {
            // Create the constructor function
            val paramsSymbols = constructorParams.map { it.createSymbol(this) }
            val longName = "$name/constructor"
            val thisSym = VarSymbol(location, "this", klass, false)
            klass.constructor = Function(location, longName, thisSym, paramsSymbols, TypeUnit, TokenKind.EOL)
            for (sym in paramsSymbols)
                constructorScope.addSymbol(sym)

            // Add any methods from the superclass
            val superclass = klass.superClass
            if (superclass != null) {
                klass.virtualFunctions.addAll(superclass.virtualFunctions)
                for (sym in superclass.fields.filterIsInstance<FunctionSymbol>()) {
                    val clone = sym.clone()
                    klass.add(clone)
                    addSymbol(clone)
                }
            }
        }

        else -> {}
    }
    for (blk in body.filterIsInstance<AstBlock>())
        blk.findFunctionDefinitions(this)
}

private fun AstBlock.findClassFields(scope:AstBlock) {
    if (this is AstClassDefStmt) {
        // Inherit any fields from the superclass
        val superclass = klass.superClass
        if (superclass != null) {
            for(field in superclass.fields.filterIsInstance<FieldSymbol>()) {
                addSymbol(field)
                klass.add(field)
            }
        }

        // Constructor parameters could be marked val or var -> in which case they are fields as well
        for((index,param) in constructorParams.withIndex()) {
            if (param.kind == TokenKind.VAL || param.kind == TokenKind.VAR) {
                val mutable = param.kind == TokenKind.VAR
                val paramSym = klass.constructor.parameters[index]
                val sym = FieldSymbol(param.location, param.name, paramSym.type, mutable)
                addSymbol(sym)
                klass.add(sym)

                // Create an initializer for the field
                val paramExpr = TctVariable(param.location, paramSym, paramSym.type)
                paramExpr.checkType(sym.type)
                initializers += TctFieldInitializer(sym, paramExpr)
            }
        }

        // Call the superclass constructor
        if (superclass!=null) {
            val tcArgs = superClassArgs.map { it.typeCheckRvalue(constructorScope) }
            if (!superclass.constructor.isCallableWithArgs(tcArgs)) {
                val argsStr = tcArgs.joinToString(",") { it.type.name }
                val paramStr = superclass.constructor.parameters.joinToString(",") { it.type.name }
                reportTypeError(location,
                    "Superclass constructor '$superclass' called with ($argsStr) when expecting ($paramStr)"
                )
            }
            val thisExpr = TctVariable(location, klass.constructor.thisSymbol!!, klass)
            val superCall = TctCallExpr(location, thisExpr, superclass.constructor, tcArgs)
            initializers += TctFieldInitializer(null, superCall)
        }

        // Find all the fields in the class body
        for (stmt in body.filterIsInstance<AstVarDeclStmt>()) {
            val init = stmt.initializer?.typeCheckRvalue(constructorScope)
            val type = stmt.astType?.resolveType(this) ?:
                       init?.type ?:
                       reportTypeError(stmt.location, "Cannot determine type for '${stmt.name}'")
            val sym = FieldSymbol(stmt.location, stmt.name, type, stmt.mutable)
            addSymbol(sym)
            klass.add(sym)
            if (init!=null) {
                init.checkType(type)
                initializers += TctFieldInitializer(sym, init)
            }
        }
    } else if (this is AstEnumDefStmt) {
        // Create the enum values
        for (value in values) {
            val index = enum.values.size
            val sym = ConstSymbol(value.location, value.name, enum, IntValue(index,enum))
            val duplicate = enum.lookup(value.name)
            if (duplicate != null)
                Log.error(value.location, "Duplicate enum value '${value.name}' in enum '${name}'")
            enum.values += sym
            addSymbol(sym)
        }
    }

    // Walk the tree
    for (blk in body.filterIsInstance<AstBlock>())
        blk.findClassFields(this)
}


fun AstTop.typeCheck() : TctTop {
    pathContext = emptyPathContext
    topLevelFunction = Function(location, "topLevel", null, emptyList(), TypeUnit, TokenKind.EOL)

    // Make multiple passes over the AST to resolve types and symbols

    // First Walk the AST, setting each node's parent
    setParent(null)

    // Then again to Find all the class definitions
    findClassDefinitions(this)

    // And find all function declarations
    findFunctionDefinitions(this)

    // find all Class fields
    findClassFields(this)

    // And finally, type check all the statements
    val ret = typeCheckStmt(this)
    return ret as TctTop
}