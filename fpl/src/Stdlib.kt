object Stdlib {
    private val intSym = VarSymbol(nullLocation, "a", TypeInt, false)

    val mallocArray = Function(nullLocation, "mallocArray", listOf(intSym, intSym), TypeInt, isExtern = true)
    val bzero = Function(nullLocation, "bzero", listOf(intSym, intSym), TypeUnit, isExtern = true)
}