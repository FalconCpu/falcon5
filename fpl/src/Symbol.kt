sealed class Symbol (val location: Location, val name:String, val type: Type, val mutable: Boolean) {
    override fun toString(): String = name
}

class VarSymbol(location: Location, name: String, type: Type, mutable: Boolean) : Symbol(location, name, type, mutable)
class ConstSymbol(location: Location, name: String, type: Type, val value: Value) : Symbol(location, name, type, false)
class GlobalVarSymbol(location: Location, name: String, type: Type, mutable:Boolean) : Symbol(location, name, type, mutable)
class FunctionSymbol(location: Location, name: String) : Symbol(location, name, TypeNothing, false) {
    val overloads = mutableListOf<Function>()
}
class TypeNameSymbol(location: Location, name: String, type:Type) : Symbol(location, name, type, false)
class FieldSymbol(location: Location, name: String, type: Type, mutable:Boolean, var offset:Int=-1) : Symbol(location, name, type, mutable)

val predefinedSymbols : Map<String, Symbol> = genPredefinedSymbols()
val lengthSymbol = FieldSymbol(nullLocation, "length", TypeInt, false, -4)

private fun genPredefinedSymbols(): Map<String, Symbol> {
    val symbols = mutableMapOf<String, Symbol>()
    for (type in listOf(TypeUnit, TypeBool, TypeChar, TypeInt, TypeReal, TypeString, TypeAny, TypeNothing)) {
        val sym = TypeNameSymbol(nullLocation, type.name, type)
        symbols[type.name] = sym
    }

    val trueSym = ConstSymbol(nullLocation, "true", TypeBool, IntValue(1, TypeBool))
    val falseSym = ConstSymbol(nullLocation, "false", TypeBool, IntValue(0, TypeBool))
    symbols[trueSym.name] = trueSym
    symbols[falseSym.name] = falseSym

    return symbols
}



fun AstBlock.lookupSymbol(name: String): Symbol? {
    return predefinedSymbols[name] ?:
           symbols[name]?:
           parent?.lookupSymbol(name)
}

fun AstBlock.addSymbol(symbol: Symbol) {
    val duplicate = symbols[symbol.name]
    if (duplicate!= null)
        Log.error(symbol.location, "Duplicate symbol: ${symbol.name}")
    symbols[symbol.name] = symbol
}

fun AstBlock.addFunctionOverload(location: Location, name: String, function:Function) {
    when (val sym = symbols[name]) {
        null -> {
            val funcSym = FunctionSymbol(location, name)
            funcSym.overloads.add(function)
            symbols[name] = funcSym
        }

        is FunctionSymbol -> {
            val dup = sym.overloads.find { it.hasSameSignature(function) }
            if (dup!= null)
                Log.error(location, "Duplicate function overload: ${function.name}")
            sym.overloads.add(function)
        }

        else -> {
            Log.error(location, "Symbol '${name}' is already defined, but not a function")
        }
    }
}

fun Symbol.getDescription() : String = when (this) {
    is ConstSymbol -> "constant"
    is FunctionSymbol -> "function"
    is GlobalVarSymbol -> "global variable"
    is TypeNameSymbol -> "type name"
    is VarSymbol -> "variable"
    is FieldSymbol -> "field"
}