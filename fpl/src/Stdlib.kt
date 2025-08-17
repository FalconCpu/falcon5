object Stdlib {
    private val intSym = VarSymbol(nullLocation, "a", TypeInt, false)
    private val stringSym = VarSymbol(nullLocation, "s", TypeString, false)

    val mallocArray = Function(nullLocation, "mallocArray", null, listOf(intSym, intSym), TypeInt, isExtern = true)
    val mallocObject = Function(nullLocation, "mallocObject", null, listOf(intSym), TypeInt, isExtern = true)
    val bzero = Function(nullLocation, "bzero", null, listOf(intSym, intSym), TypeUnit, isExtern = true)
    val strcmp = Function(nullLocation, "strcmp", null, listOf(stringSym, stringSym), TypeBool, isExtern = true)
    val strequal = Function(nullLocation, "strequal", null, listOf(stringSym, stringSym), TypeBool, isExtern = true)
}