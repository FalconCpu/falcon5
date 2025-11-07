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

var insideUnsafe = false


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
            if (ret.type is TypeErrable && refinedType!=null && refinedType !is TypeErrable)
                return TctExtractCompoundExpr(location, ret, 1,refinedType)
            if (refinedType!=null)
                return TctVariable(location, ret.sym, refinedType)
        }

        is TctGlobalVarExpr -> {
            val refinedType = pathContext.refinedTypes[ret.sym]
            if (ret.type is TypeErrable && refinedType!=null && refinedType !is TypeErrable)
                return TctExtractCompoundExpr(location, ret, 1,refinedType)
            if (refinedType!=null)
                return TctGlobalVarExpr(location, ret.sym, refinedType)
        }

        is TctMemberExpr -> {
            val sym = ret.getSymbol()
            val refinedType = pathContext.refinedTypes[sym]

            if (ret.type is TypeErrable && refinedType!=null && refinedType !is TypeErrable)
                return TctExtractCompoundExpr(location, ret, 1,refinedType)
            if (refinedType!=null)
                return TctMemberExpr(location, ret.objectExpr, ret.member, refinedType)
        }

        is  TctTypeName->
            if (!allowTypes)
                return TctErrorExpr(location,"'${ret.type}' is a type name, not a value")

        else -> {}
    }

    return ret
}

private fun TctExpr.checkIsLvalue() {
    when(this) {
        is TctVariable -> {
            if(!sym.mutable && sym !in pathContext.uninitializedVariables)
                Log.error(location, "'$sym' is not mutable")
            pathContext = pathContext.initialize(sym)
        }

        is TctIndexExpr -> {
        }

        is TctMemberExpr -> {
            if (!member.mutable)
                Log.error(location,"Field $member is not mutable")
        }

        is TctErrorExpr -> {}

        is TctGlobalVarExpr -> {
            if (!sym.mutable)
                Log.error(location,"Global variable $sym is not mutable")
            pathContext = pathContext.initialize(sym)
        }

        is TctMakeTupleExpr -> {
            elements.forEach { it.checkIsLvalue() }
        }

        is TctStackVariable -> {
            if(!sym.mutable && sym !in pathContext.uninitializedVariables)
                Log.error(location, "'$sym' is not mutable")
            pathContext = pathContext.initialize(sym)
        }

        else -> Log.error(location,"expression  is not an Lvalue")
    }

}


private fun AstExpr.typeCheckLvalue(scope: AstBlock) : TctExpr {
    val ret = typeCheckExpr(scope, isLvalue = true)
    ret.checkIsLvalue()
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
            if (tcExpr is TctIsErrableTypeExpr) {
                val tcType = (tcExpr.expr.type as TypeErrable).okType
                if (tcExpr.typeIndex == 0) {
                    truePath = pathContext.refineType(tcExpr.expr, tcType)
                    falsePath = pathContext.refineType(tcExpr.expr, errorEnum!!)
                } else {
                    falsePath = pathContext.refineType(tcExpr.expr, tcType)
                    truePath = pathContext.refineType(tcExpr.expr, errorEnum!!)
                }
            }

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
            val expr = typeCheckRvalue(scope).checkType(TypeBool)
            BranchPathContext(pathContext, pathContext, expr)
        }
    }
}


private fun AstExpr.typeCheckExpr(scope: AstBlock, isLvalue:Boolean=false) : TctExpr {
    return when(this) {
        is AstIntLiteral -> {
            TctConstant(location, IntValue(value, TypeInt))
        }

        is AstRealLiteral -> {
            TctConstant(location, IntValue(value.toBits(), TypeReal))
        }

        is AstCharLiteral -> {
            TctConstant(location, IntValue(value, TypeChar))
        }

        is AstStringLiteral -> {
            TctConstant(location, StringValue.create(value))
        }

        is AstIdentifier ->
            when(val sym = scope.lookupSymbol(name, location)) {
                null -> {
                    Log.error(location, "'${name}' is not defined")
                    val new = VarSymbol(location, name, TypeError, true)
                    scope.addSymbol(new)
                    TctVariable(location, new, TypeError)
                }

                is ConstSymbol -> TctConstant(location, sym.value)
                is FunctionSymbol -> {
                    val type = if (sym.overloads.size==1)
                        TypeFunction.create(sym.overloads[0])
                    else
                        TypeNothing
                    TctFunctionName(location, sym, type)
                }
                is GlobalVarSymbol -> TctGlobalVarExpr(location, sym, sym.type)
                is TypeNameSymbol -> TctTypeName(location, sym)
                is VarSymbol -> TctVariable(location, sym, sym.type)
                is FieldSymbol -> {
                    val func = scope.findEnclosingFunction()!!
                    if (func.thisSymbol!=null)
                        TctMemberExpr(location, TctVariable(location, func.thisSymbol, func.thisSymbol.type), sym, sym.type)
                    else
                        TctErrorExpr(location, "Field '${sym.name}' cannot be accessed outside of a class context")
                }
                is StackVarSymbol -> TctStackVariable(location, sym, sym.type)
                is AccessSymbol -> error("Access symbols should not appear in AST")
            }

        is AstBinaryExpr -> {
            val tctLhs = lhs.typeCheckRvalue(scope)
            val tctRhs = rhs.typeCheckRvalue(scope)
            if (tctLhs.type==TypeError || tctRhs.type==TypeError)
                return TctErrorExpr(location, "")
            if (tctLhs.type is TypeReal || tctRhs.type is TypeReal)
                return binopRealTypeCheck(location, op, tctLhs, tctRhs)
            val match = operatorTable.find{it.tokenKind==op && it.lhsType==tctLhs.type && it.rhsType==tctRhs.type}
            if (match==null)
                return TctErrorExpr(location, "Invalid operator '${op}' for types '${tctLhs.type}' and '${tctRhs.type}'")
            if (tctLhs is TctConstant && tctRhs is TctConstant) {
                val lhsValue = (tctLhs.value as IntValue).value
                val rhsValue = (tctRhs.value as IntValue).value
                val result = match.resultOp.evaluate(lhsValue, rhsValue)
                return TctConstant(location, IntValue(result, match.resultType))
            }
            TctBinaryExpr(location, match.resultOp, tctLhs, tctRhs, match.resultType)
        }

        is AstReturnExpr -> {
            val enclosingFunc = scope.findEnclosingFunction()
                               ?: return TctErrorExpr(location, "Return statement outside of a function")
            val tctExpr = expr?.typeCheckRvalue(scope)
            val ret = if (tctExpr==null) {
                if (enclosingFunc.returnType!=TypeUnit)
                    Log.error(location, "Function should return an expression of type '${enclosingFunc.returnType}'")
                null
            } else {
                tctExpr.checkType(enclosingFunc.returnType)
            }
            pathContext = unreachablePathContext
            TctReturnExpr(location, ret)
        }

        is AstCallExpr -> {
            val tctFunc = func.typeCheckExpr(scope)
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
                    val splitVarargs = splitOffVarargs(tctArgs, resolvedFunc)
                    TctCallExpr(location, thisArg, resolvedFunc.function, splitVarargs, resolvedFunc.returnType)
                }
                is TctMethodRefExpr -> {
                    val resolvedFunc = tctFunc.methodSym.resolveOverload(location, tctArgs)
                        ?: return TctErrorExpr(location, "")  // Error message is reported by resolveOverload()
                    TctCallExpr(location, tctFunc.objectExpr, resolvedFunc.function, tctArgs, resolvedFunc.returnType)
                }
                is TctTypeName -> {
                    if (tctFunc.type is TypeStruct) {
                        val structType = tctFunc.type
                        if (tctArgs.size!=structType.fields.size)
                            return TctErrorExpr(location,"Got ${tctArgs.size} arguments when expecting ${structType.fields.size}")
                        for(i in tctArgs.indices)
                            tctArgs[i].checkType(structType.fields[i].type)
                        TctMakeStructExpr(location, structType, tctArgs)
                    } else
                        TctErrorExpr(location, "Type '${tctFunc.type}' cannot be called like a function")
                }

                else -> if (tctFunc.type is TypeFunction) {
                    if (tctArgs.size!=tctFunc.type.parameterType.size)
                        return TctErrorExpr(location,"Got ${tctArgs.size} arguments when expecting ${tctFunc.type.parameterType.size}")
                    for(i in tctArgs.indices)
                        tctArgs[i].checkType(tctFunc.type.parameterType[i])
                    TctIndirectCallExpr(location, tctFunc, tctArgs, tctFunc.type.returnType)
                } else
                    return TctErrorExpr(location, "Invalid function call ${tctFunc.type}")
            }
        }

        is AstIndexExpr -> {
            val arrayExpr = array.typeCheckRvalue(scope)
            val indexExpr = index.typeCheckRvalue(scope).checkType(TypeInt)
            if (arrayExpr.type is TypeError || indexExpr.type is TypeError)
                return TctErrorExpr(location, "")
            val elementType = when (arrayExpr.type) {
                is TypeArray -> arrayExpr.type.elementType
                is TypeString -> TypeChar
                is TypeInlineArray -> arrayExpr.type.elementType
                is TypePointer -> arrayExpr.type.elementType
                else -> reportTypeError(location, "Cannot index type '${arrayExpr.type}'")
            }
            if (arrayExpr.type is TypeInlineArray) {
                if (indexExpr is TctConstant && indexExpr.value is IntValue) {
                    val idx = indexExpr.value.value
                    if (idx < 0 || idx >= arrayExpr.type.size)
                        Log.error(location, "Index $idx out of bounds for InlineArray of size ${arrayExpr.type.size}")
                }
            }
            return TctIndexExpr(location, arrayExpr, indexExpr, elementType)
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

                is TypeClassInstance -> {
                    when(val field = tctExpr.type.lookup(memberName, location)) {
                        null -> TctErrorExpr(location, "Class '${tctExpr.type.name}' has no member '${memberName}'")
                        is FieldSymbol -> TctMemberExpr(location, tctExpr, field, field.type)
                        is FunctionSymbol -> TctMethodRefExpr(location, tctExpr, field, TypeNothing)
                        else -> error("Unexpected symbol type '${field.getDescription()}' for member '${memberName}' of class '${tctExpr.type.name}'")
                    }
                }

                is TypeClass -> error("Cannot access member of generic class '$tctExpr.type' without type arguments")

                is TypeEnum -> {
                    val field = tctExpr.type.parameters.find {it.name==memberName}
                    if (field==null)
                        TctErrorExpr(location, "Enum '${tctExpr.type.name}' has no member '${memberName}'")
                    else {
                        field.references.add(location)
                        TctEnumEntryExpr(location, tctExpr, field, field.type)
                    }
                }

                is TypeNullable ->
                    TctErrorExpr(location, "Cannot access member as expression may be null")

                is TypeTuple -> {
                    val index = memberName.toIntOrNull()
                    if (index==null || index<0 || index>=tctExpr.type.elementTypes.size)
                        TctErrorExpr(location, "Tuple type '${tctExpr.type}' has no member '${memberName}'")
                    else
                        TctExtractCompoundExpr(location, tctExpr, index, tctExpr.type.elementTypes[index])
                }

                is TypeStruct -> {
                    val field = tctExpr.type.fields.find {it.name==memberName}
                    if (field==null)
                        TctErrorExpr(location, "Struct '${tctExpr.type.name}' has no member '${memberName}'")
                    else
                        TctMemberExpr(location, tctExpr, field, field.type)
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
                    val size = tcArgs[0].checkType(TypeInt)
                    if (arena==Arena.STACK && size !is TctConstant)
                        return TctErrorExpr(location, "Local arrays must have a constant size")
                    if (size is TctConstant && size.value is IntValue && size.value.value < 0)
                        return TctErrorExpr(location, "Cannot create array of negative size")
                    tcLambda?.checkType(type.elementType)
                    val arrayType = TypeArray.create(type.elementType)
                    val clearMem = (tcLambda==null) && !insideUnsafe        // Don't clear memory if a lambda is provided or inside an unsafe block
                    return TctNewArrayExpr(location, type.elementType, size, arena, tcLambda, clearMem, arrayType)
                }

                is TypeClassInstance -> {
                    if (!type.constructor.isCallableWithArgs(tcArgs)) {
                        val argsStr = tcArgs.joinToString(",") { it.type.name }
                        val paramStr = type.constructor.parameters.joinToString(",") { it.type.name }
                        return TctErrorExpr(location,"Constructor '$type' called with ($argsStr) when expecting ($paramStr)")
                    }
                    TctNewClassExpr(location, type.genericType, tcArgs, arena, type)
                }

                is TypeClass -> error("Cannot create new instance of generic class '$type' without type arguments")

                is TypeInlineArray -> {
                    if (tcArgs.isNotEmpty())
                        return TctErrorExpr(location, "InlineArray constructor takes no arguments")
                    val arrayType = TypeInlineArray.create(type.elementType, type.size)
                    tcLambda?.checkType(type.elementType)

                    return TctNewInlineArrayExpr(location, arena, tcLambda, arrayType)
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
            val tcItems = tcArgs.map {it.checkType(type.elementType)}
            if (arena==Arena.CONST) {
                val e = tcItems.find {it !is TctConstant}
                if (e != null)
                    return TctErrorExpr(e.location, "Cannot create constant array literal with non-constant element")
                val values = tcItems.map { (it as TctConstant).value }
                val arrayValue = ArrayValue.create(values, type)
                return TctConstant(location, arrayValue)
            } else
                return TctNewArrayLiteralExpr(location, tcItems, arena, type)
        }

        is AstNegateExpr -> {
            val tcExpr = expr.typeCheckRvalue(scope)
            if (tcExpr.type is TypeError)
                return TctErrorExpr(location, "")
            return when (tcExpr.type) {
                is TypeReal -> {
                    if (tcExpr is TctConstant && tcExpr.value is IntValue) {
                        val v = - (Float.fromBits(tcExpr.value.value))
                        TctConstant(location, IntValue(v.toBits(), TypeReal))
                    } else
                        TctFpuExpr(location, FpuOp.SUB_F, TctConstant(location, IntValue(0, TypeReal)), tcExpr, TypeReal)
                }

                is TypeInt -> {
                    if (tcExpr is TctConstant && tcExpr.value is IntValue)
                        TctConstant(location, IntValue(-tcExpr.value.value, TypeInt))
                    else
                        TctBinaryExpr(location, BinOp.SUB_I, TctConstant(location, IntValue(0, TypeInt)), tcExpr, TypeInt)
                }

                else
                    -> TctErrorExpr(location, "Cannot negate type '${tcExpr.type}'")
            }
        }

        is AstRangeExpr -> {
            val tcStart = start.typeCheckRvalue(scope)
            val tcEnd = end.typeCheckRvalue(scope).checkType(tcStart.type)
            if (tcStart.type is TypeError || tcEnd.type is TypeError)
                return TctErrorExpr(location, "")
            if (tcStart.type!=TypeInt && tcStart.type!=TypeChar)
                return TctErrorExpr(location, "Range start must be of type Int or Char, got '${tcStart.type}'")
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
                TypeReal -> TctRealCompareExpr(location, aluOp, tcLhs, tcRhs)
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
            val typeR = typeExpr.resolveType(scope)

            if (tctExpr.type is TypeError)
                return TctErrorExpr(location, "")
            val typeL = if (tctExpr.type is TypeNullable) tctExpr.type.elementType else tctExpr.type

            if (typeL is TypeErrable && typeR==errorEnum)
                return TctIsErrableTypeExpr(location, tctExpr, 1)
            if (typeL is TypeErrable && typeR==typeL.okType)
                return TctIsErrableTypeExpr(location, tctExpr,0)

            if (typeL !is TypeClassInstance)
                return TctErrorExpr(location, "Got type '${tctExpr.type}' when expecting a class type")
            if (typeR is TypeError)
                return TctErrorExpr(location, "")
            if (typeR !is TypeClassInstance)
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

            if (typeR is TypeEnum && tctExpr is TctConstant && tctExpr.value is IntValue) {
                val intValue = tctExpr.value.value
                if (intValue<0 || intValue>=typeR.entries.size) {
                    Log.error(location, "Value $intValue is out of range for enum '${typeR.name}'")
                    return TctErrorExpr(location, "")
                }
                val entry = typeR.entries[intValue]
                return TctConstant(location, entry.value)
            }

            return if ( (tctExpr.type is TypeEnum && typeR is TypeInt) ||
                (tctExpr.type is TypeInt && typeR is TypeEnum) ||
                (tctExpr.type is TypeChar && typeR is TypeInt) ||
                (tctExpr.type is TypeInt && typeR is TypeChar) ||
                (tctExpr.type is TypeAny) )
                TctAsExpr(location, tctExpr, typeR)
            else if (tctExpr.type is TypeInt && typeR is TypeReal) {
                TctIntToRealExpr(location, tctExpr)
            } else if (tctExpr.type is TypeReal && typeR is TypeInt) {
                TctRealToIntExpr(location, tctExpr)
            } else if (insideUnsafe) {
                if (tctExpr is TctConstant && tctExpr.value is IntValue)
                    TctConstant(location, IntValue(tctExpr.value.value, typeR))
                else if (tctExpr.type is TypeErrable)
                    TctExtractCompoundExpr(location, tctExpr, 1, typeR)
                else
                    TctAsExpr(location, tctExpr, typeR)
            } else
                TctErrorExpr(location, "Cannot cast type '${tctExpr.type}' to '${typeR.name}'")
        }

        is AstTryExpr -> {
            val enclosingFunc = scope.findEnclosingFunction()
                ?: return TctErrorExpr(location, "Return statement outside of a function")
            val tctExpr = expr.typeCheckRvalue(scope)
            if (tctExpr.type is TypeError)
                return TctErrorExpr(location, "")
            if (tctExpr.type !is TypeErrable)
                return TctErrorExpr(location, "Cannot use 'try' on non-errable type '${tctExpr.type}'")
            if (enclosingFunc.returnType !is TypeErrable)
                return TctErrorExpr(location, "Function '${enclosingFunc.name}' must have an errable return type to use 'try'")
            val type = tctExpr.type.okType
            return TctTryExpr(location, tctExpr, type)
        }

        is AstMakeTupleExpr -> {
            val exprs = if (isLvalue)
                elements.map { it.typeCheckLvalue(scope) }
            else
                elements.map { it.typeCheckRvalue(scope) }
            if (exprs.any { it.type is TypeError })
                return TctErrorExpr(location, "")
            val badType = exprs.filter { it.type is TypeErrable || it.type is TypeTuple }
            for(e in badType)
                Log.error(e.location, "Cannot include type '${e.type}' in a tuple")
            val type = TypeTuple.create(exprs.map { it.type })
            TctMakeTupleExpr(location, exprs, type)
        }

        is AstUnsafeExpr -> {
            val oldInsideUnsafe = insideUnsafe
            insideUnsafe = true
            val tctExpr = expr.typeCheckRvalue(scope)
            insideUnsafe = oldInsideUnsafe
            tctExpr
        }

        is AstAbortExpr -> {
            val tcAbortExpr = abortCode.typeCheckRvalue(scope).checkType(TypeInt)
            pathContext = unreachablePathContext
            TctAbortExpr(location, tcAbortExpr)
        }

        is AstIfExpr -> {
            val tcCondition = cond.typeCheckBoolExpr(scope)
            pathContext = tcCondition.trueBranch
            val tcThen = trueExpr.typeCheckRvalue(scope)
            val thenPath = pathContext
            pathContext = tcCondition.falseBranch
            val tcElse = falseExpr.typeCheckRvalue(scope)
            val elsePath = pathContext
            pathContext = listOf(thenPath, elsePath).merge()
            val type = enclosingType(location, tcThen.type, tcElse.type)
            TctIfExpr(location, tcCondition.expr, tcThen, tcElse, type)
        }
    }
}

private fun binopRealTypeCheck(location: Location, op: TokenKind, lhs: TctExpr, rhs: TctExpr): TctExpr {
    val tctLhs = if (lhs.type == TypeInt) TctIntToRealExpr(lhs.location, lhs) else lhs
    val tctRhs = if (rhs.type == TypeInt) TctIntToRealExpr(rhs.location, rhs) else rhs

    if (tctLhs.type != TypeReal || tctRhs.type != TypeReal)
        return TctErrorExpr(location, "Invalid operator '${op}' for types '${lhs.type}' and '${rhs.type}'")

    val fpuOp = when(op) {
        TokenKind.PLUS -> FpuOp.ADD_F
        TokenKind.MINUS -> FpuOp.SUB_F
        TokenKind.STAR -> FpuOp.MUL_F
        TokenKind.SLASH -> FpuOp.DIV_F
        else -> return TctErrorExpr(location, "Invalid operator '${op}' for types '${lhs.type}' and '${rhs.type}'")
    }

    return TctFpuExpr(location, fpuOp, tctLhs, tctRhs, TypeReal)
}

private fun AstMemberExpr.typecheckStaticMember(tctExpr: TctExpr): TctExpr {
    if (tctExpr.type is TypeEnum) {
        // Special case enumName.values -> range of enum values
        if (memberName == "values") {
            val zeroExpr = TctConstant(location, IntValue(0, tctExpr.type))
            val lengthExpr = TctConstant(location, IntValue(tctExpr.type.entries.size, tctExpr.type))
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

fun FunctionInstance.isCallableWithArgs(args: List<TctExpr>) : Boolean {
    if (isVararg) {
        if (args.size < parameters.size-1 ) return false
        for(index in 0..<parameters.size-1) {
            val param = parameters[index]
            val arg = args[index]
            if (!arg.type.isAssignableTo(param.type)) return false
        }
        val varargType = (parameters.last().type as TypeArray).elementType
        for(index in parameters.size-1..<args.size) {
            val arg = args[index]
            if (!arg.type.isAssignableTo(varargType)) return false
        }
        return true
    } else {
        if (parameters.size != args.size) return false
        for ((param, arg) in parameters zip (args))
            if (!arg.type.isAssignableTo(param.type)) return false
        return true
    }
}

fun splitOffVarargs(args: List<TctExpr>, func:FunctionInstance) : List<TctExpr> {
    // Separate out the varargs into a single array argument
    if (!func.isVararg)
        return args

    val countNormalParams = func.parameters.size-1
    val normalArgs = args.take(countNormalParams)
    val varargArgs = args.drop(countNormalParams)
    return normalArgs + TctVarargExpr(nullLocation, varargArgs, func.parameters.last().type)
}

fun FunctionSymbol.resolveOverload(location:Location, args: List<TctExpr>) : FunctionInstance? {
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
    this.parent = scope
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
            if (type is TypeStruct && scope is AstFile)
                Log.error(location, "Global struct variables not yet supported")

            val sym = if (type is TypeStruct)
                StackVarSymbol(location, name, type, mutable)
            else if (scope is AstFile)
                newGlobalVar(location, name, type, mutable)
            else
                VarSymbol(location, name, type, mutable)

            scope.addSymbol(sym)
            val tcr = tctInitializer?.checkType(type)
            if (tcr == null)
                pathContext = pathContext.addUninitialized(sym)

            if (sym is StackVarSymbol)
                TctStructVarDeclStmt(location, sym, tcr)
            else
                TctVarDeclStmt(location, sym, tcr)
        }

        is AstFunctionDefStmt -> {
            pathContext = emptyPathContext
            firstUnreachableStatement = true
            val tctBody = body.map { it.typeCheckStmt(this) }
            if (!pathContext.unreachable && function.returnType!=TypeUnit && qualifier!=TokenKind.EXTERN)
                Log.error(location, "Function '${name}' must return a value along all paths")
            pathContext = emptyPathContext
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

            // make a first pass through the body to get the pathContext for the loop path
            val pathContextIn = pathContext
            val firstTctCondition = condition.typeCheckBoolExpr(scope)
            pathContext = firstTctCondition.trueBranch
            val firstTctBody = body.map { it.typeCheckStmt(this) }

            // Now make a second pass to get the correct pathContext for after the loop
            breakContext = mutableListOf()
            continueContext = mutableListOf()
            secondTypecheckPass = true
            pathContext = (listOf(pathContext, pathContextIn)).merge()   // Path context at condition is either from end of body or from before the loop
            val tctCondition = condition.typeCheckBoolExpr(scope)
            pathContext = tctCondition.trueBranch
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
            var iterableDesc : Pair<FieldSymbol,FunctionInstance>? = null

            val tcRange = range.typeCheckRvalue(scope)
            val type = indexType?.resolveType(scope) ?: when (tcRange.type) {
                is TypeError -> TypeError
                is TypeRange -> tcRange.type.elementType
                is TypeArray -> tcRange.type.elementType
                is TypeInlineArray -> tcRange.type.elementType
                is TypeString -> TypeChar
                is TypeClassInstance -> {
                    iterableDesc = tcRange.type.isIterable()
                    iterableDesc?.second?.returnType ?: reportTypeError(location, "Type '${tcRange.type}' is not iterable")
                }
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
            } else if (tcRange.type is TypeString) {
                if (!TypeChar.isAssignableTo(type))
                    reportTypeError(
                        location,
                        "Cannot iterate over array of type '${tcRange.type}' with index type '${type}'"
                    )
                TctForArrayStmt(location, sym, tcRange, tcBody)
            } else if (tcRange.type is TypeInlineArray) {
                if (!tcRange.type.elementType.isAssignableTo(type))
                    reportTypeError(
                        location,
                        "Cannot iterate over array of type '${tcRange.type}' with index type '${type}'"
                    )
                TctForArrayStmt(location, sym, tcRange, tcBody)
            } else if (tcRange.type is TypeClassInstance)
                TctForIterableStmt(location, sym, tcRange, iterableDesc!!.first, iterableDesc.second, tcBody)
            else
                TODO()

            pathContext = (breakContext+pathContext).merge()
            breakContext = oldBreakContext
            continueContext = oldContinueContext
            secondTypecheckPass = oldSecondTypeCheckPass
            currentLoop = oldLoop
            ret
        }


        is AstAssignStmt -> {
            val lhs = lhs.typeCheckLvalue(scope)
            val rhs = rhs.typeCheckRvalue(scope).checkType(lhs.type)
            if (rhs.type != lhs.type)
                pathContext = pathContext.refineType(lhs, rhs.type)
            if (op==TokenKind.PLUSEQ || op==TokenKind.MINUSEQ) {
                if (lhs.type != TypeInt)
                    Log.error(location, "Operator '$op' not defined for type '${lhs.type}'")
            }
            if (rhs.type.isAggregate()) {
                if (op != TokenKind.EQ)
                    Log.error(location, "Can only assign to aggregate types with '=' operator")
                TctAssignAggregateStmt(location, lhs, rhs)
            } else
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

        is AstDestructuringVarDeclStmt -> {
            val rhs = initializer.typeCheckRvalue(scope)
            if (rhs.type !is TypeTuple) {
                Log.error(location, "Destructuring requires a tuple, got ${rhs.type}")
                return TctEmptyStmt(location)
            }

            val tupleType = rhs.type
            if (names.size != tupleType.elementTypes.size) {
                Log.error(location, "Destructuring expects ${names.size} elements, got ${tupleType.elementTypes.size}")
                return TctEmptyStmt(location)
            }

            val syms = mutableListOf<VarSymbol>()
            for((index,name) in names.withIndex()) {
                val tt = tupleType.elementTypes[index]
                val type = name.type?.resolveType(scope) ?: tt
                val sym = VarSymbol(name.location, name.name, type, mutable)
                if (!tt.isAssignableTo(type))
                    Log.error(name.location, "Cannot assign tuple element of type '${tt}' to variable of type '${type}'")
                scope.addSymbol(sym)
                syms += sym
            }
            return TctDestructuringDeclStmt(location, syms, rhs)
        }

        is AstConstDecl -> {
            val tctInitializer = value.typeCheckRvalue(scope)
            val type = astType?.resolveType(scope) ?: tctInitializer.type
            if (type is TypeUnit)
                Log.error(location, "Constant '${name}' cannot be of type Unit")
            if (tctInitializer !is TctConstant) {
                Log.error(location, "Constant '${name}' must be initialized with a constant expression")
                return TctEmptyStmt(location)
            }
            if (!tctInitializer.type.isAssignableTo(type))
                Log.error(location, "Cannot assign constant of type '${tctInitializer.type}' to constant of type '${type}'")
            val sym = ConstSymbol(location, name, type, tctInitializer.value)
            scope.addSymbol(sym)
            return TctEmptyStmt(location)
        }

        is AstFreeStmt -> {
            val tctExpr = expr.typeCheckRvalue(scope)
            val tt = if (tctExpr.type is TypeNullable) tctExpr.type.elementType else tctExpr.type
            if (tt !is TypeClassInstance && tt !is TypeArray && tt !is TypeInlineArray && tt !is TypePointer && tt !is TypeString) {
                Log.error(location, "Cannot free type '${tctExpr.type}'")
                return TctEmptyStmt(location)
            }
            TctFreeStmt(location, tctExpr)
        }

        is AstWhenCase -> error("Should not be type checking AstWhenCase directly, it should be part of AstWhenStmt")

        is AstWhenStmt -> {
            // TODO - add path contexts
            val tctExpr = expr.typeCheckRvalue(scope)
            val clauses = mutableListOf<TctWhenCase>()
            val pathConextOut = mutableListOf<PathContext>()
            val pathContextIn = pathContext
            for (clause in body.filterIsInstance<AstWhenCase>()) {
                pathContext = pathContextIn
                val tctMatch = clause.patterns.map {
                    it.typeCheckRvalue(scope).checkType(tctExpr.type)
                }
                if (clause.patterns.isEmpty() && clause!= body.last())
                    Log.error(clause.location, "The else case must be the last case in a when statement")
                val tctBody = clause.body.map { it.typeCheckStmt(clause) }
                pathConextOut += pathContext
                clauses += TctWhenCase(location, tctMatch, tctBody)
            }
            pathContext = pathConextOut.merge()
            TctWhenStmt(location, tctExpr, clauses)
        }

        is AstStructDefStmt -> TctEmptyStmt(location)   // Do nothing here as all processing is done in IdentifyFunctions()
    }
}


// ===========================================================================
//                          Types
// ===========================================================================

private fun AstTypeIdentifier.resolve(scope: AstBlock) : Type {
    return when (val sym = scope.lookupSymbol(name, location)) {
        null -> reportTypeError(location, "Unresolved identifier: '$name'")
        is TypeNameSymbol -> sym.type
        else -> reportTypeError(location, "'${sym.name}' is a ${sym.getDescription()} not a type")
    }
}

private fun AstType.resolveType(scope:AstBlock) : Type {
    when(this) {
        is AstTypeIdentifier -> {
            val ret = resolve(scope)
            return if (ret is TypeClass)
                TypeClassInstance.create(ret, emptyMap())
            else
                 ret
        }
        is AstArrayType -> {
            val elementType = elementType.resolveType(scope)
            return TypeArray.create(elementType)
        }

        is AstPointerType -> {
            val elementType = elementType.resolveType(scope)
            return TypePointer.create(elementType)
        }

        is AstInlineArrayType -> {
            val tcElementType = elementType.resolveType(scope)
            val tcSize = size.typeCheckRvalue(scope)
            if (tcSize !is TctConstant || tcSize.value !is IntValue)
                return reportTypeError(location, "Inline array size must be a constant integer")
            val intSize = tcSize.value.value
            if (intSize <= 0)
                reportTypeError(location, "Inline array must have a positive size")
            return TypeInlineArray.create(tcElementType, intSize)
        }

        is AstNullableType -> {
            val elementType = elementType.resolveType(scope)
            return TypeNullable.create(elementType)
        }
        is AstErrableType -> {
            val elementType = elementType.resolveType(scope)
            if (elementType is TypeErrable)
                reportTypeError(location, "Cannot have nested errable types")
            return TypeErrable.create(elementType)
        }

        is AstGenericType -> {
            val baseType = baseType.resolveType(scope)

            if (baseType is TypeError)
                return baseType
            if (baseType !is TypeClassInstance)
                return reportTypeError(location, "Type '${baseType.name}' is not a class")
            if (baseType.typeArguments.isNotEmpty())
                return reportTypeError(location, "Type '${baseType.name}' is not a generic class")
            val genericType = baseType.genericType
            if (genericType.typeParameters.size != typeArgs.size)
                return reportTypeError(location, "Type '${baseType.name}' requires ${genericType.typeParameters.size} type arguments")
            val resolvedArgs = typeArgs.map { it.resolveType(scope) }
            val mapping = (genericType.typeParameters zip resolvedArgs).toMap()
            return TypeClassInstance.create(genericType, mapping)
        }

        is AstTupleType -> {
            val elementTypes = elementTypes.map { it.resolveType(scope) }
            val badElement = elementTypes.filter { it is TypeErrable || it is TypeTuple }
            for(e in badElement)
                Log.error(location, "Cannot include type '${e}' in a tuple")
            return TypeTuple.create(elementTypes)
        }
    }
}

fun TypeClassInstance.isIterable() : Pair<FieldSymbol,FunctionInstance>? {
    // Test to see if a class has an Int field named "length", and a method get(Int)
    val lengthField = lookup("length", null)
    if (lengthField==null || lengthField !is FieldSymbol || lengthField.type!=TypeInt)
        return null

    val getMethod = lookup("get",null)
    if (getMethod==null || getMethod !is FunctionSymbol)
        return null

    val getOverloads = getMethod.overloads.filter { it.parameters.size==1 && it.parameters[0].type==TypeInt }
    if (getOverloads.size!=1)
        return null

    return Pair(lengthField, getOverloads[0])
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
    var type = type.resolveType(scope)
    if (kind== TokenKind.VARARG)
        type = TypeArray.create(type)
    return VarSymbol(location, name, type, false)
}

private fun AstParameter.createFieldSymbol(scope:AstBlock) : FieldSymbol {
    val type = type.resolveType(scope)
    return FieldSymbol(location, name, type, false)
}



// Find class definitions
private fun AstBlock.findClassDefinitions(scope:AstBlock) {
    if (this is AstClassDefStmt) {
        // Add the type parameter symbols to the scope
        val typeParameters = mutableListOf<TypeGenericParameter>()
        for (tp in typeParams) {
            val tgp = TypeGenericParameter(tp.name)
            val sym = TypeNameSymbol(tp.location, tp.name, tgp)
            addSymbol(sym)
            typeParameters += tgp
        }

        // Find the superclass
        var superClass = astSuperClass?.resolveType(this)
        if (superClass!=null && superClass !is TypeClassInstance) {
            reportTypeError(location, "Superclass '$superClass' is not a class")
            superClass = null
        }

        klass = TypeClass(name,typeParameters,  superClass)
        val sym = TypeNameSymbol(location, name, klass)
        scope.addSymbol(sym)


    } else if (this is AstEnumDefStmt) {
        enum = TypeEnum(name)
        val sym = TypeNameSymbol(location, name, enum)
        scope.addSymbol(sym)
        if (name=="Error") {
            if (errorEnum!=null)
                Log.error(location, "Multiple definitions of special enum 'Error'")
            errorEnum = enum
        }
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
            for(errPearam in paramsSymbols.filter { it.type is TypeErrable })
                Log.error(errPearam.location, "Function parameter '${errPearam.name}' cannot be of errable type")
            val returnType = retType?.resolveType(this) ?: TypeUnit
            val longName = (if (scope is AstClassDefStmt) scope.klass.name + "/" else "") +
                name + paramsSymbols.joinToString(separator = ",", prefix = "(", postfix = ")") { it.type.name }
            val thisSym = if (scope is AstClassDefStmt) VarSymbol(location, "this", scope.klass.toClassInstance(), false) else null
            val isVararg = params.any { it.kind==TokenKind.VARARG }
            val syscallNumber = syscall?.value ?: -1
            function = Function(location, longName, thisSym, paramsSymbols, returnType, qualifier, isVararg, syscallNumber)
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
            val functionInstance = function.toFunctionInstance()

            if (qualifier==TokenKind.OVERRIDE)
                scope.addFunctionOverride(location, name, functionInstance)
            else
                scope.addFunctionOverload(location, name, functionInstance)

            if (name=="free") {
                if (params.isNotEmpty())
                    Log.error(location, "Destuctors do not take parameters")
                else if (function.returnType!=TypeUnit)
                    Log.error(location, "Destructor must have return type 'Unit'")
                else if (scope !is AstClassDefStmt)
                    Log.error(location, "Destructor must be a class method")
                else if (scope.klass.destructor!=null)
                    Log.error(location, "Multiple definitions of destructor for class '${scope.klass.name}'")
                else
                    scope.klass.destructor = functionInstance
            }
        }

        is AstClassDefStmt -> {
            // Create the constructor function
            val paramsSymbols = constructorParams.map { it.createSymbol(this) }
            val longName = "$name/constructor"
            val thisSym = VarSymbol(location, "this", klass, false)
            val constructor = Function(location, longName, thisSym, paramsSymbols, TypeUnit, TokenKind.EOL)
            klass.constructor = constructor.toFunctionInstance()
            for (sym in paramsSymbols)
                constructorScope.addSymbol(sym)

            // Add any methods from the superclass
            val superclass = klass.superClass
            if (superclass != null) {
                klass.virtualFunctions.addAll(superclass.genericType.virtualFunctions)
                for (sym in superclass.genericType.fields.filterIsInstance<FunctionSymbol>()) {
                    val clone = sym.clone()
                    klass.add(clone)
                    addSymbol(clone)
                }
            }
        }

        is AstEnumDefStmt -> {
            val paramsSymbols = params.map{ it.createFieldSymbol(this)}
            enum.parameters = paramsSymbols
        }

        is AstStructDefStmt -> {
            val fieldSyms = fields.map { it.createFieldSymbol(this) }
            var offset = 0
            for (field in fieldSyms) {
                field.offset = offset
                if (field.type is TypeStruct)
                    Log.error(field.location, "Nested structs are not supported")
                offset += (field.type.getSize() + 3) and -4  // align to 4 bytes
            }

            val structType = TypeStruct(name, fieldSyms, offset)
            val sym = TypeNameSymbol(location, name, structType)
            scope.addSymbol(sym)
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
            for(field in superclass.genericType.fields.filterIsInstance<FieldSymbol>()) {
                val newField = field.mapType(superclass.typeArguments)
                addSymbol(newField)
                klass.add(newField)
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
                val paramExpr = TctVariable(param.location, paramSym, paramSym.type).checkType(sym.type)
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
            val superCall = TctCallExpr(location, thisExpr, superclass.constructor.function, tcArgs, superclass.constructor.returnType)
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
            if (init!=null)
                initializers += TctFieldInitializer(sym, init.checkType(type))
        }
    } else if (this is AstEnumDefStmt) {
        // Create the enum values
        val enumValues = enum.parameters.associateWith{ mutableListOf<Value>()}

        for (value in values) {
            val index = enum.entries.size
            val sym = ConstSymbol(value.location, value.name, enum, IntValue(index,enum))
            val args = value.args.map { it.typeCheckRvalue(this) }
            val duplicate = enum.lookup(value.name)
            if (duplicate != null)
                Log.error(value.location, "Duplicate enum value '${value.name}' in enum '${name}'")
            enum.entries += sym
            if (args.size != enum.parameters.size)
                Log.error(value.location, "Enum value '${value.name}' in enum '${name}' has ${args.size} parameters, but enum declares ${enum.parameters.size}")
            for ((param, arg) in enum.parameters zip args) {
                val tca = arg.checkType(param.type)
                if (tca is TctConstant)
                    enumValues[param]!!.add(tca.value)
                else
                    Log.error(arg.location, "Enum value parameter must be a constant")

            }
            addSymbol(sym)
        }
        for (param in enum.parameters) {
            val array = ArrayValue.create(enumValues[param]!!, TypeArray.create(param.type))
            enum.values[param] = array
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