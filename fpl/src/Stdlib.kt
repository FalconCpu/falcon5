object Stdlib {
    private val intSym = VarSymbol(nullLocation, "a", TypeInt, false)
    private val stringSym = VarSymbol(nullLocation, "s", TypeString, false)

    val mallocArray = Function(nullLocation, "mallocArray", listOf(intSym, intSym), TypeInt, isExtern = true)
    val bzero = Function(nullLocation, "bzero", listOf(intSym, intSym), TypeUnit, isExtern = true)
    val strcmp = Function(nullLocation, "strcmp", listOf(stringSym, stringSym), TypeBool, isExtern = true)
    val strequal = Function(nullLocation, "strequal", listOf(stringSym, stringSym), TypeBool, isExtern = true)
}