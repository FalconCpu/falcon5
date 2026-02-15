import TokenKind.*

class Parser(val lexer: Lexer) {
    private var currentToken = lexer.nextToken()

    private fun nextToken() : Token {
        val ret = currentToken
        currentToken = lexer.nextToken()
        return ret
    }

    private fun expect(kind: TokenKind) : Token {
        if (currentToken.kind == kind)
            return nextToken()
        throw ParseError(currentToken.location, "Got '$currentToken' when expecting '$kind'")
    }

    private fun expect(kind1: TokenKind, kind2:TokenKind) : Token {
        if (currentToken.kind == kind1 || currentToken.kind == kind2)
            return nextToken()
        throw ParseError(currentToken.location, "Got '$currentToken' when expecting '$kind1' or '$kind2'")
    }


    private fun skipToEol() {
        while (currentToken.kind!= EOL && currentToken.kind!= EOF)
            nextToken()
        nextToken() // Consume EOL
    }

    private fun expectEol() {
        if (currentToken.kind!= EOL && currentToken.kind!= EOF)
            Log.error(currentToken.location, "Got '$currentToken' when expecting end of line")
        skipToEol()
    }

    // Look to see if the current token is of a given kind. If so, consume it and return true. Otherwise, return false.
    private fun canTake(kind: TokenKind) : Boolean {
        if (currentToken.kind == kind) {
            nextToken()
            return true
        } else
            return false
    }

    private fun canTake(value:String) : Boolean {
        if (currentToken.value == value) {
            nextToken()
            return true
        } else
            return false
    }


    // =====================================================================================
    //                                 Expressions
    // =====================================================================================

    private fun parseLongIntLit(tok:Token) : AstLongLiteral {
        try {
            val value = if (tok.value.startsWith("0x",ignoreCase = true))
                java.lang.Long.parseUnsignedLong(tok.value.substring(2, tok.value.length - 1), 16)
            else
                java.lang.Long.parseLong(tok.value.substring(0, tok.value.length - 1))
            return AstLongLiteral(tok.location, value)
        } catch(_: NumberFormatException) {
            Log.error(tok.location, "Malformed long integer literal: '$tok'")
            return AstLongLiteral(tok.location, 0) // Return 0 as default value
        }
    }

    private fun parseIntLit() : AstExpr {
        val tok = expect(INTLITERAL)
        if (tok.value.endsWith("L", ignoreCase = true))
            return parseLongIntLit(tok)

        try {
            val value = if (tok.value.startsWith("0x",ignoreCase = true))
                    Integer.parseUnsignedInt(tok.value.substring(2), 16)
                else
                    Integer.parseInt(tok.value)
            return AstIntLiteral(tok.location, value)
        } catch(_: NumberFormatException) {
            Log.error(tok.location, "Malformed integer literal: '$tok'")
            return AstIntLiteral(tok.location, 0) // Return 0 as default value
        }
    }

    private fun parseRealLit() : AstRealLiteral {
        val tok = expect(REALLITERAL)
        try {
            val value = tok.value.toFloat()
            return AstRealLiteral(tok.location, value)
        } catch(_: NumberFormatException) {
            Log.error(tok.location, "Malformed integer literal: '$tok'")
            return AstRealLiteral(tok.location, 0.0F) // Return 0 as default value
        }
    }


    private fun parseStringLit() : AstStringLiteral {
        val tok = expect(STRINGLITERAL)
        return AstStringLiteral(tok.location, tok.value)
    }

    private fun parseCharLit() : AstCharLiteral {
        val tok = expect(CHARLITERAL)
        return AstCharLiteral(tok.location, tok.value[0].code)

    }

    private fun parseIdentifier() : AstIdentifier {
        val tok = expect(IDENTIFIER)
        return AstIdentifier(tok.location, tok.value)
    }

    private fun parseInlineArray() : AstExpr {
        val tok = expect(INLINEARRAY)
        expect(LT)
        val elementType = parseType()
        expect(GT)
        val sizeExpr = if (currentToken.kind==OPENB) {
            expect(OPENB)
            val expr = parseExpr()
            expect(CLOSEB)
            expr
        } else null
        val initializer = if (currentToken.kind==OPENSQ) parseInitializerList() else null
        val lambda = if (currentToken.kind==OPENCL) parseLambdaExpr() else null

        return AstInlineArrayExpr(tok.location, elementType, sizeExpr, initializer, lambda)
    }

    private fun parseParentheses() : AstExpr {
        val exprs = mutableListOf<AstExpr>()
        expect(OPENB)
        do {
            exprs += parseExpr()
        } while (canTake(COMMA))
        expect(CLOSEB)
        return if (exprs.size > 1)
            AstMakeTupleExpr(currentToken.location, exprs)
        else
            exprs[0]
    }

    private fun parseReturn() : AstReturnExpr {
        val loc = expect(RETURN)
        val expr = if (currentToken.kind !in listOf(EOL,EOF,CLOSEB,CLOSESQ,CLOSECL)) parseExpr() else null
        return AstReturnExpr(loc.location, expr)
    }

    private fun parseAbort() : AstAbortExpr {
        val loc = expect(ABORT)
        val expr = parseExpr()
        return AstAbortExpr(loc.location, expr)
    }

    private fun parseUnsafe() : AstUnsafeExpr {
        val loc = expect(UNSAFE)
        val expr = parseExpr()
        return AstUnsafeExpr(loc.location, expr)
    }

    private fun parseTry() : AstTryExpr {
        val loc = expect(TRY)
        val expr = parseExpr()
        return AstTryExpr(loc.location, expr)
    }


    private fun parseBreak() : AstBreakExpr {
        val loc = expect(BREAK)
        return AstBreakExpr(loc.location)
    }

    private fun parseContinue() : AstContinueExpr {
        val loc = expect(CONTINUE)
        return AstContinueExpr(loc.location)
    }

    private fun parseNew() : AstExpr {
        val arenaTok = nextToken() // Consume NEW / LOCAL / CONST
        val type = if (currentToken.kind!=OPENSQ) parseType() else null
        val initializer = if (currentToken.kind==OPENSQ) parseInitializerList() else null
        val args = if (currentToken.kind == OPENB) parseArgList() else emptyList()
        val lambda = if (currentToken.kind==OPENCL) parseLambdaExpr() else null
        val arena = when (arenaTok.kind) {
            NEW -> Arena.HEAP
            CONST -> Arena.CONST
            else -> throw ParseError(arenaTok.location, "Got '$arenaTok' when expecting 'new', or 'const'")
        }
        return if (type!=null && initializer==null )
            AstNewExpr(arenaTok.location, type, args, lambda, arena)
        else if (initializer!=null && args.isEmpty() && lambda==null)
            AstArrayLiteralExpr(arenaTok.location, type, initializer, arena)
        else
            throw ParseError(arenaTok.location, "Malformed constructor expression")
    }

    private fun parsePrimaryExpr() : AstExpr {
        return when (currentToken.kind) {
            INTLITERAL -> parseIntLit()
            REALLITERAL -> parseRealLit()
            IDENTIFIER -> parseIdentifier()
            STRINGLITERAL -> parseStringLit()
            CHARLITERAL -> parseCharLit()
            RETURN -> parseReturn()
            TRY -> parseTry()
            BREAK -> parseBreak()
            CONTINUE -> parseContinue()
            ABORT -> parseAbort()
            OPENB -> parseParentheses()
            UNSAFE -> parseUnsafe()
            NEW, CONST -> parseNew()
            INLINEARRAY -> parseInlineArray()
            else -> throw ParseError(currentToken.location, "Got '$currentToken' when expecting primary expression")
        }
    }

    private fun parseArgList() : List<AstExpr> {
        val args = mutableListOf<AstExpr>()
        expect(OPENB) // Consume OPENB
        if (currentToken.kind != CLOSEB)
            do {
                args.add(parseExpr())
            } while (canTake(COMMA))
        expect(CLOSEB) // Consume CLOSEB
        return args
    }

    private fun parseInitializerList() : List<AstExpr> {
        val args = mutableListOf<AstExpr>()
        expect(OPENSQ) // Consume OPENB
        if (currentToken.kind != CLOSESQ)
            do {
                args.add(parseExpr())
            } while (canTake(COMMA))
        expect(CLOSESQ) // Consume CLOSEB
        return args
    }


    private fun parseFuncCall(lhs:AstExpr) : AstExpr {
        val loc = currentToken.location
        val args = parseArgList()
        return AstCallExpr(loc, lhs, args)
    }

    private fun parseIndexExpr(array: AstExpr) : AstExpr {
        val tok = expect(OPENSQ) // Consume OPENSQ
        val index = parseExpr()
        expect(CLOSESQ) // Consume CLOSESQ
        return AstIndexExpr(tok.location, array, index)
    }

    private fun parseMemberExpr(objectExpr: AstExpr) : AstExpr {
        expect(DOT) // Consume DOT
        val memberName = expect(IDENTIFIER,INTLITERAL)
        return AstMemberExpr(memberName.location, objectExpr, memberName.value)
    }

    private fun parseNullAssertExpr(expr: AstExpr) : AstExpr {
        val tok = expect(QMARKQMARK) // Consume QMARKQMARK
        return AstNullAssertExpr(tok.location, expr)
    }

    private fun parsePostfixExpr() : AstExpr {
        var ret = parsePrimaryExpr()
        while(true)
            ret = when (currentToken.kind) {
                OPENB -> parseFuncCall(ret)
                OPENSQ -> parseIndexExpr(ret)
                DOT -> parseMemberExpr(ret)
                QMARKQMARK -> parseNullAssertExpr(ret)
                else -> return ret
            }
    }

    private fun parsePrefixExpr() : AstExpr {
        return when (currentToken.kind) {
            NOT -> {
                val tok = nextToken() // Consume NOT
                val expr = parsePrefixExpr()
                AstNotExpr(tok.location, expr)
            }

            MINUS -> {
                val tok = nextToken() // Consume MINUS
                val expr = parsePrefixExpr()
                AstNegateExpr(tok.location, expr)
            }

            else ->  parsePostfixExpr()
        }
    }

    private fun parseAsExpr() : AstExpr {
        val expr = parsePrefixExpr()
        if (currentToken.kind == AS) {
            val tok = nextToken() // Consume AS
            val type = parseType()
            return AstAsExpr(tok.location, expr, type)
        }
        return expr
    }

    private fun parseMultiplicativeExpr() : AstExpr {
        var ret = parseAsExpr()
        while (currentToken.kind in listOf(STAR, SLASH, PERCENT, AMPERSAND, LSL, LSR, ASR)) {
            val tok = nextToken()
            val right = parseAsExpr()
            ret = AstBinaryExpr(tok.location, tok.kind, ret, right)
        }
        return ret
    }

    private fun parseAdditiveExpr() : AstExpr {
        var ret = parseMultiplicativeExpr()
        while (currentToken.kind in listOf(PLUS, MINUS, BAR, CARET)) {
            val tok = nextToken()
            val right = parseMultiplicativeExpr()
            ret = AstBinaryExpr(tok.location, tok.kind, ret, right)
        }
        return ret
    }

    private fun parseRangeExpr() : AstExpr {
        val start = parseAdditiveExpr()
        if (currentToken.kind == DOTDOT) {
            nextToken() // Consume DOTDOT
            val op = if (currentToken.kind in listOf(LT, GT, LTE, GTE)) nextToken().kind else LTE
            val end = parseAdditiveExpr()
            return AstRangeExpr(currentToken.location, start, end, op)
        } else
            return start
    }

    private fun parseRelationalExpr() : AstExpr {
        val ret = parseRangeExpr()
        return if (currentToken.kind in listOf(LT, GT, LTE, GTE)) {
            val tok = nextToken()
            val right = parseRangeExpr()
            AstCompareExpr(tok.location, tok.kind, ret, right)
        } else if (currentToken.kind == EQ || currentToken.kind == NEQ) {
            val tok = nextToken() // Consume EQ or NEQ
            val right = parseRangeExpr()
            AstEqualityExpr(tok.location, tok.kind, ret, right)
        } else if (currentToken.kind == IS) {
            val tok = nextToken() // Consume IS
            val type = parseType()
            AstIsExpr(tok.location, ret, type)
        } else
            ret
    }

    private fun parseAndExpr() : AstExpr {
        var ret = parseRelationalExpr()
        while (currentToken.kind == AND) {
            val tok = nextToken()
            val right = parseRelationalExpr()
            ret = AstAndExpr(tok.location, ret, right)
        }
        return ret
    }

    private fun parseOrExpr() : AstExpr {
        var ret = parseAndExpr()
        while (currentToken.kind == OR) {
            val tok = nextToken()
            val right = parseAndExpr()
            ret = AstOrExpr(tok.location, ret, right)
        }
        return ret
    }

    private fun parseExpr() : AstExpr {
        if (canTake(IF)) {
            val cond = parseExpr()
            expect(THEN)
            val trueExpr = parseExpr()
            expect(ELSE)
            val falseExpr = parseExpr()
            return AstIfExpr(cond.location, cond, trueExpr, falseExpr)
        } else
            return parseOrExpr()
    }

    private fun parseLambdaExpr() : AstLambdaExpr {
        val tok = expect(OPENCL)
        val expr = parseExpr()
        expect(CLOSECL) // Consume CLOSECL
        val body = listOf( AstExpressionStmt(tok.location, expr) )
        return AstLambdaExpr(tok.location, body)
    }

    // =====================================================================================
    //                                 Types
    // =====================================================================================

    private fun parseTypeIdentifier() : AstTypeIdentifier {
        val tok = expect(IDENTIFIER)
        return AstTypeIdentifier(tok.location, tok.value)
    }

    private fun parseTypeArgs() : List<AstType> {
        val args = mutableListOf<AstType>()
        if (!canTake(LT))
            return args
        if (currentToken.kind!= GT)
            do {
                args.add(parseType())
            } while( canTake(COMMA))
        expect(GT)
        return args
    }

    private fun parseTypeArray() : AstArrayType {
        expect(ARRAY) // Consume ARRAY
        expect(LT)
        val elementType = parseType()
        expect(GT) // Consume GT
        return AstArrayType(currentToken.location, elementType)
    }

    private fun parseTypePointer() : AstPointerType {
        expect(POINTER) // Consume ARRAY
        expect(LT)
        val elementType = parseType()
        expect(GT) // Consume GT
        return AstPointerType(currentToken.location, elementType)
    }


    private fun parseInlineArrayType() : AstInlineArrayType {
        expect(INLINEARRAY) // Consume ARRAY
        expect(LT)
        val elementType = parseType()
        expect(GT)
        expect(OPENB)
        val size = parseExpr()
        expect(CLOSEB) // Consume GT
        return AstInlineArrayType(currentToken.location, elementType,size)
    }

    private fun parseTypeTuple() : AstType {
        val elementTypes = mutableListOf<AstType>()
        expect(OPENB) // Consume OPENB
        do
            elementTypes.add(parseType())
        while (canTake(COMMA))
        expect(CLOSEB) // Consume CLOSEB
        return if (elementTypes.size == 1)
            elementTypes[0]
        else
            AstTupleType(currentToken.location, elementTypes)
    }

    private fun parseType() : AstType {
        var ret = when(currentToken.kind) {
            IDENTIFIER -> parseTypeIdentifier()
            ARRAY -> parseTypeArray()
            INLINEARRAY -> parseInlineArrayType()
            POINTER -> parseTypePointer()
            OPENB -> parseTypeTuple()
            else -> throw ParseError(currentToken.location, "Got '$currentToken' when expecting type")
        }

        if (currentToken.kind==LT) {
            val typeArgs = parseTypeArgs()
            if (ret !is AstTypeIdentifier)
                throw ParseError(ret.location, "Type arguments can only be applied to type identifiers")
            ret = AstGenericType(ret.location, ret, typeArgs)
        }

        if (canTake(QMARK))
            ret = AstNullableType(currentToken.location, ret)
        if (canTake(EMARK))
            ret = AstErrableType(currentToken.location, ret)
        return ret
    }


    // =====================================================================================
    //                                 Parameters
    // =====================================================================================

    private fun parseParameter(allowVal:Boolean) : AstParameter {
        val kindLoc = currentToken.location
        val kind = if (canTake(VAL)) VAL else
                   if (canTake(VAR)) VAR else
                   if (canTake(VARARG)) VARARG else
                                     EOL
        if ((kind==VAL || kind==VAR) && !allowVal)
            Log.error(kindLoc, "'val' or 'var' not allowed on function parameters ")
        val id = parseIdentifier()
        expect(COLON)
        val type = parseType()
        return AstParameter(id.location, kind, id.name, type)
    }

    private fun parseParameterList(allowVal:Boolean) : List<AstParameter> {
        val params = mutableListOf<AstParameter>()
        expect(OPENB)
        if (currentToken.kind!= CLOSEB)
            do {
                params.add(parseParameter(allowVal))
            } while( canTake(COMMA))
        expect(CLOSEB)

        // Check that vararg is last parameter
        val varargs = params.filter { it.kind == VARARG }
        if (varargs.size > 1)
            Log.error(varargs[1].location, "Only one vararg parameter allowed")
        if (varargs.size == 1 && params.last().kind != VARARG)
            Log.error(varargs[0].location, "Vararg parameter must be the last parameter")

        return params
    }

    private fun parseTypeParams() : MutableList<AstTypeIdentifier> {
        val params = mutableListOf<AstTypeIdentifier>()
        expect(LT)
        if (currentToken.kind!= GT)
            do {
                val id = expect(IDENTIFIER)
                params.add(AstTypeIdentifier(id.location, id.value))
            } while( canTake(COMMA))
        expect(GT)
        return params
    }

    // =====================================================================================
    //                                 Statements
    // =====================================================================================

    private fun optionalEnd(kind:TokenKind) {
        val loc = currentToken.location
        if (canTake(END)) {
            if (currentToken.kind != kind && currentToken.kind!= EOL)
                Log.error(loc, "Got 'end $currentToken' when expecting 'end $kind'")
            canTake(kind)
            expectEol()
        }
    }

    private fun parseDeclNode() : AstDeclNode {
        val id = expect(IDENTIFIER)
        val typeExpr = if (canTake(COLON)) parseType() else null
        return AstDeclNode(id.location, id.value, typeExpr)
    }

    private fun parseVarDecl() : AstStmt {
        val tok = nextToken()
        val mutable = tok.kind == VAR
        if (currentToken.kind==OPENB) {
            val ids = mutableListOf<AstDeclNode>()
            expect(OPENB) // Consume OPENB
            do {
                ids += parseDeclNode()
            } while (canTake(COMMA))
            expect(CLOSEB) // Consume CLOSEB
            expect(EQ)
            val expr = parseExpr()
            expectEol()
            return AstDestructuringVarDeclStmt(tok.location, ids, expr, mutable)
        } else {
            val id = expect(IDENTIFIER)
            val typeExpr = if (canTake(COLON)) parseType() else null
            val initializer = if (canTake(EQ)) parseExpr() else null
            expectEol()
            return AstVarDeclStmt(id.location, id.value, typeExpr, initializer, mutable)
        }
    }

    private fun parseConstDecl() : AstStmt {
        expect(CONST)
        val id = expect(IDENTIFIER)
        val astType = if (canTake(COLON)) parseType() else null
        expect(EQ)
        val expr = parseExpr()
        expectEol()
        return AstConstDecl(id.location, id.value, astType, expr)
    }

    private fun parseFunctionDef() : AstFunctionDefStmt {
        val qualifier = if (currentToken.kind in listOf(EXTERN,OVERRIDE,VIRTUAL)) nextToken().kind else EOL
        expect(FUN)
        val name = expect(IDENTIFIER, FREE)
        val params = parseParameterList(false)
        val retType = if (canTake(ARROW)) parseType() else null
        val syscall = if (canTake("syscall")) (parseIntLit() as AstIntLiteral) else null
        expectEol()
        val body = if (currentToken.kind== INDENT) parseIndentedBlock() else emptyList()
        optionalEnd(FUN)
        return AstFunctionDefStmt(name.location, name.value, params, retType, body, qualifier, syscall)
    }

    private fun parseClassDef() : AstClassDefStmt {
        expect(CLASS)
        val name = expect(IDENTIFIER)
        val typeParams = if (currentToken.kind==LT) parseTypeParams() else emptyList()
        val constructorArgs = if (currentToken.kind==OPENB) parseParameterList(true) else emptyList()
        val parentClass = if (canTake(COLON)) parseType() else null
        val parentArgs = if (currentToken.kind==OPENB && parentClass!=null) parseArgList() else emptyList()
        expectEol()
        val body = if (currentToken.kind== INDENT) parseIndentedBlock() else emptyList()
        optionalEnd(CLASS)
        return AstClassDefStmt(name.location, name.value, typeParams, parentClass, constructorArgs, parentArgs, body)
    }

    private fun parseStructDef() : AstStructDefStmt {
        expect(STRUCT)
        val name = expect(IDENTIFIER)
        val fields = parseParameterList(false)
        expectEol()
        return AstStructDefStmt(name.location, name.value, fields)
    }

    private fun parseExpressionStmt() : AstStmt {
        val loc = currentToken.location
        val expr = parsePrefixExpr()
        if (currentToken.kind==EQ || currentToken.kind==PLUSEQ || currentToken.kind==MINUSEQ) {
            val op = nextToken()
            val value = parseExpr()
            expectEol()
            return AstAssignStmt(loc, op.kind, expr, value)
        } else {
            expectEol()
            return AstExpressionStmt(loc, expr)
        }
    }

    private fun parseWhileStmt() : AstWhileStmt {
        val tok = expect(WHILE)
        val condition = parseExpr()
        expectEol()
        val body = parseIndentedBlock()
        optionalEnd(WHILE)
        return AstWhileStmt(tok.location, condition, body)
    }

    private fun parseForStmt() : AstForStmt {
        expect(FOR)
        val id = expect(IDENTIFIER)
        val type = if (canTake(COLON)) parseType() else null
        expect(IN)
        val range = parseExpr()
        expectEol()
        val body = parseIndentedBlock()
        optionalEnd(FOR)
        return AstForStmt(id.location, id.value, type, range, body)
    }

    private fun parseRepeatStmt() : AstRepeatStmt {
        val tok = expect(REPEAT)
        expectEol()
        val body = parseIndentedBlock()
        expect(UNTIL)
        val condition = parseExpr()
        expectEol()
        return AstRepeatStmt(tok.location, condition, body)
    }

    private fun parseIfClause() : AstIfClause {
        val tok = nextToken()  // Consume the IF or ELSEIF
        val cond = parseExpr()
        val body = if (canTake(THEN)) {
            listOf(parseStmt())
        } else {
            expectEol()
            parseIndentedBlock()
        }
        return AstIfClause(tok.location, cond, body)
    }

    private fun parseElseClause() : AstIfClause {
        val tok = expect(ELSE)
        val body = if (canTake(EOL)) parseIndentedBlock() else listOf(parseStmt())
        return AstIfClause(tok.location, null, body)
    }

    private fun parseIfStmt() : AstIfStmt {
        val loc = currentToken.location
        val clauses = mutableListOf<AstIfClause>()
        clauses.add(parseIfClause())
        while (currentToken.kind == ELSIF)
            clauses.add(parseIfClause())
        if (currentToken.kind == ELSE)
            clauses.add(parseElseClause())
        optionalEnd(IF)
        return AstIfStmt(loc, clauses)
    }

    private fun parseWhenCase() : AstWhenCase {
        val patterns = mutableListOf<AstExpr>()
        if (canTake(ELSE)) {
            // Else case
        } else do {
            patterns += parseExpr()
        } while (canTake(COMMA))
        expect(ARROW)
        val body = if(canTake(EOL)) parseIndentedBlock() else listOf(parseStmt())
        return AstWhenCase(currentToken.location, patterns, body)
    }

    private fun parseWhenStmt() : AstWhenStmt {
        val tok = expect(WHEN)
        val expr = parseExpr()
        expectEol()
        expect(INDENT)
        val cases = mutableListOf<AstWhenCase>()
        while(currentToken.kind!= DEDENT && currentToken.kind!=EOF)
            cases += parseWhenCase()
        expect(DEDENT)
        optionalEnd(WHEN)
        return AstWhenStmt(tok.location, expr, cases)
    }

    private fun parseEnumEntry() : AstEnumEntry {
        val name = expect(IDENTIFIER)
        val args = if (currentToken.kind == OPENB) parseArgList() else emptyList()
        return AstEnumEntry(name.location, name.value, args)
    }

    private fun parseEnum() : AstEnumDefStmt {
        expect(ENUM)
        val name = expect(IDENTIFIER)
        val params = if (currentToken.kind==OPENB) parseParameterList(false) else emptyList()
        expect(OPENSQ) // Consume OPENB
        val values = mutableListOf<AstEnumEntry>()
        if (currentToken.kind != CLOSESQ)
            do {
                val id = parseEnumEntry()
                values += id
            } while (canTake(COMMA))
        expect(CLOSESQ) // Consume CLOSEB
        expectEol()
        return AstEnumDefStmt(name.location, name.value, params, values, emptyList())
    }

    private fun parseFreeStmt() : AstStmt {
        val tok = expect(FREE)
        val expr = parseExpr()
        expectEol()
        return AstFreeStmt(tok.location, expr)
    }

    private fun parseStmt() : AstStmt {
        val loc = currentToken.location
        return try {
            when (currentToken.kind) {
                EXTERN, OVERRIDE, VIRTUAL, FUN -> parseFunctionDef()
                VAL, VAR -> parseVarDecl()
                CONST -> parseConstDecl()
                WHILE -> parseWhileStmt()
                REPEAT -> parseRepeatStmt()
                IF -> parseIfStmt()
                FOR -> parseForStmt()
                CLASS -> parseClassDef()
                ENUM -> parseEnum()
                FREE -> parseFreeStmt()
                WHEN -> parseWhenStmt()
                STRUCT -> parseStructDef()
                ELSIF -> throw ParseError(currentToken.location, "ELSIF without IF")
                ELSE -> throw ParseError(currentToken.location, "ELSE without IF")
                END -> throw ParseError(currentToken.location, "END without IF, WHILE, REPEAT or FUN")
                IDENTIFIER, OPENB, RETURN, BREAK, CONTINUE, ABORT -> parseExpressionStmt()
                else -> throw ParseError(currentToken.location, "Got '$currentToken' when expecting statement")
            }
        } catch (e: ParseError) {
            Log.error(e.location, e.message!!)
            skipToEol()
            return AstEmptyStmt(loc)
        }
    }

    fun parseIndentedBlock() : MutableList<AstStmt> {
        val ret = mutableListOf<AstStmt>()
        if (currentToken.kind!= INDENT) {
            Log.error(currentToken.location, "Missing indented block")
            return ret
        }
        nextToken() // Consume INDENT
        while(currentToken.kind!= DEDENT && currentToken.kind!=EOF)
            ret += parseStmt()
        nextToken() // Consume DEDENT
        return ret
    }

    fun parseFile() : AstFile {
        val loc = currentToken.location
        val stmtList = mutableListOf<AstStmt>()
        while (currentToken.kind!= EOF)
            stmtList += parseStmt()
        return AstFile(loc, stmtList)
    }

    companion object {
        fun parseAll(lexers:List<Lexer>): AstTop {
            val astFiles = mutableListOf<AstFile>()
            for (lexer in lexers) {
                val parser = Parser(lexer)
                val astFile = parser.parseFile()
                astFiles.add(astFile)
            }

            return AstTop(nullLocation, astFiles)
        }
    }
}

class ParseError(val location: Location, message: String) : Exception("$location $message")