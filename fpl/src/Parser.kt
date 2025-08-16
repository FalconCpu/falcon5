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

    // =====================================================================================
    //                                 Expressions
    // =====================================================================================


    private fun parseIntLit() : AstIntLiteral {
        val tok = expect(INTLITERAL)
        try {
            val value = Integer.parseInt(tok.value)
            return AstIntLiteral(tok.location, value)
        } catch(_: NumberFormatException) {
            Log.error(tok.location, "Malformed integer literal: '$tok'")
            return AstIntLiteral(tok.location, 0) // Return 0 as default value
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

    private fun parseParentheses() : AstExpr {
        expect(OPENB)
        val expr = parseExpr()
        expect(CLOSEB)
        return expr
    }

    private fun parseReturn() : AstReturnExpr {
        val loc = expect(RETURN)
        val expr = if (currentToken.kind !in listOf(EOL,EOF,CLOSEB,CLOSESQ,CLOSECL)) parseExpr() else null
        return AstReturnExpr(loc.location, expr)
    }

    private fun parseNew() : AstExpr {
        val arenaTok = nextToken() // Consume NEW / LOCAL / CONST
        val type = if (currentToken.kind!=OPENSQ) parseType() else null
        val initializer = if (currentToken.kind==OPENSQ) parseInitializerList() else null
        val args = if (currentToken.kind == OPENB) parseArgList() else emptyList()
        val lambda = if (currentToken.kind==OPENCL) parseLambdaExpr() else null
        val arena = when (arenaTok.kind) {
            NEW -> Arena.HEAP
            LOCAL -> Arena.STACK
            CONST -> Arena.CONST
            else -> throw ParseError(arenaTok.location, "Got '$arenaTok' when expecting 'new', 'local' or 'const'")
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
            IDENTIFIER -> parseIdentifier()
            STRINGLITERAL -> parseStringLit()
            CHARLITERAL -> parseCharLit()
            RETURN -> parseReturn()
            OPENB -> parseParentheses()
            NEW, LOCAL, CONST -> parseNew()
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
        expect(OPENSQ) // Consume OPENSQ
        val index = parseExpr()
        expect(CLOSESQ) // Consume CLOSESQ
        return AstIndexExpr(currentToken.location, array, index)
    }

    private fun parseMemberExpr(objectExpr: AstExpr) : AstExpr {
        expect(DOT) // Consume DOT
        val memberName = expect(IDENTIFIER)
        return AstMemberExpr(currentToken.location, objectExpr, memberName.value)
    }

    private fun parsePostfixExpr() : AstExpr {
        var ret = parsePrimaryExpr()
        while(true)
            ret = when (currentToken.kind) {
                OPENB -> parseFuncCall(ret)
                OPENSQ -> parseIndexExpr(ret)
                DOT -> parseMemberExpr(ret)
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

    private fun parseMultiplicativeExpr() : AstExpr {
        var ret = parsePrefixExpr()
        while (currentToken.kind in listOf(STAR, SLASH, PERCENT, AMPERSAND, LSL, LSR, ASR)) {
            val tok = nextToken()
            val right = parsePrefixExpr()
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

    private fun parseRelationalExpr() : AstExpr {
        var ret = parseAdditiveExpr()
        while (currentToken.kind in listOf(LT, GT, LTE, GTE, EQ, NEQ)) {
            val tok = nextToken()
            val right = parseAdditiveExpr()
            ret = AstBinaryExpr(tok.location, tok.kind, ret, right)
        }
        return ret
    }

    private fun parseAndExpr() : AstExpr {
        var ret = parseRelationalExpr()
        while (currentToken.kind == AND) {
            val tok = nextToken()
            val right = parseRelationalExpr()
            ret = AstBinaryExpr(tok.location, tok.kind, ret, right)
        }
        return ret
    }

    private fun parseOrExpr() : AstExpr {
        var ret = parseAndExpr()
        while (currentToken.kind == OR) {
            val tok = nextToken()
            val right = parseAndExpr()
            ret = AstBinaryExpr(tok.location, tok.kind, ret, right)
        }
        return ret
    }

    private fun parseExpr() : AstExpr {
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

    private fun parseTypeArray() : AstArrayType {
        expect(ARRAY) // Consume ARRAY
        expect(LT)
        val elementType = parseType()
        expect(GT) // Consume GT
        return AstArrayType(currentToken.location, elementType)
    }

    private fun parseType() : AstType {
        when(currentToken.kind) {
            IDENTIFIER -> return parseTypeIdentifier()
            ARRAY -> return parseTypeArray()
            OPENB -> {
                nextToken() // Consume OPENB
                val type = parseType()
                expect(CLOSEB) // Consume CLOSEB
                return type
            }
            else -> throw ParseError(currentToken.location, "Got '$currentToken' when expecting type")
        }
    }


    // =====================================================================================
    //                                 Parameters
    // =====================================================================================

    private fun parseParameter() : AstParameter {
        val id = parseIdentifier()
        expect(COLON)
        val type = parseType()
        return AstParameter(id.location, id.name, type)
    }

    private fun parseParameterList() : List<AstParameter> {
        val params = mutableListOf<AstParameter>()
        expect(OPENB)
        if (currentToken.kind!= CLOSEB)
            do {
                params.add(parseParameter())
            } while( canTake(COMMA))
        expect(CLOSEB)
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
            skipToEol()
        }
    }

    private fun parseVarDecl() : AstVarDeclStmt {
        val tok = nextToken()
        val mutable = tok.kind == VAR
        val id = expect(IDENTIFIER)
        val typeExpr = if (canTake(COLON)) parseType() else null
        val initializer = if (canTake(EQ)) parseExpr() else null
        expectEol()
        return AstVarDeclStmt(tok.location, id.value, typeExpr, initializer, mutable)
    }

    private fun parseFunctionDef() : AstFunctionDefStmt {
        val isExtern = canTake(EXTERN)
        val tok = expect(FUN)
        val name = expect(IDENTIFIER)
        val params = parseParameterList()
        val retType = if (canTake(ARROW)) parseType() else null
        expectEol()
        val body = if (!isExtern) parseIndentedBlock() else emptyList()
        optionalEnd(FUN)
        return AstFunctionDefStmt(tok.location, name.value, params, retType, body, isExtern)
    }

    private fun parseExpressionStmt() : AstStmt {
        val loc = currentToken.location
        val expr = parsePrefixExpr()
        if (currentToken.kind==EQ) {
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
        expectEol()
        val body = parseIndentedBlock()
        return AstIfClause(tok.location, cond, body)
    }

    private fun parseElseClause() : AstIfClause {
        val tok = expect(ELSE)
        expectEol()
        val body = parseIndentedBlock()
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

    private fun parseStmt() : AstStmt {
        val loc = currentToken.location
        return try {
            when (currentToken.kind) {
                EXTERN, FUN -> parseFunctionDef()
                VAL, VAR -> parseVarDecl()
                WHILE -> parseWhileStmt()
                REPEAT -> parseRepeatStmt()
                IF -> parseIfStmt()
                ELSIF -> throw ParseError(currentToken.location, "ELSIF without IF")
                ELSE -> throw ParseError(currentToken.location, "ELSE without IF")
                END -> throw ParseError(currentToken.location, "END without IF, WHILE, REPEAT or FUN")
                IDENTIFIER, OPENB, RETURN, BREAK, CONTINUE -> parseExpressionStmt()
                else -> throw ParseError(currentToken.location, "Got '$currentToken' when expecting statement")
            }
        } catch (e: ParseError) {
            Log.error(e.message!!)
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

class ParseError(location: Location, message: String) : Exception("$location $message")