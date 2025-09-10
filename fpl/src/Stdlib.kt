object Stdlib {
    private val intSym = VarSymbol(nullLocation, "a", TypeInt, false)
    private val stringSym = VarSymbol(nullLocation, "s", TypeString, false)

    val mallocArray = Function(nullLocation, "mallocArray(Int,Int)", null, listOf(intSym, intSym), TypeInt, TokenKind.EXTERN)
    val mallocObject = Function(nullLocation, "mallocObject(ClassDescriptor)", null, listOf(intSym), TypeInt, TokenKind.EXTERN)
    val malloc       = Function(nullLocation, "malloc", null, listOf(intSym), TypeInt, TokenKind.EXTERN)
    val bzero = Function(nullLocation, "bzero", null, listOf(intSym, intSym), TypeUnit, TokenKind.EXTERN)
    val strcmp = Function(nullLocation, "strcmp", null, listOf(stringSym, stringSym), TypeBool, TokenKind.EXTERN)
    val strequal = Function(nullLocation, "strequal", null, listOf(stringSym, stringSym), TypeBool, TokenKind.EXTERN)
    val free = Function(nullLocation, "freeMem(Int)", null, listOf(intSym), TypeUnit, TokenKind.EXTERN)
    val abort = Function(nullLocation, "abort", null, listOf(intSym), TypeNothing, TokenKind.EXTERN)
}