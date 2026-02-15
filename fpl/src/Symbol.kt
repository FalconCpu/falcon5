import java.io.File

val allSymbols = mutableListOf<Symbol>()
val allGlobals = mutableListOf<GlobalVarSymbol>()
var globalsSize = 0

sealed class Symbol (val location: Location, val name:String, val type: Type, val mutable: Boolean) {
    override fun toString(): String = name

    init {
        allSymbols.add(this)
    }

    val references = mutableSetOf<Location>()
}

class VarSymbol(location: Location, name: String, type: Type, mutable: Boolean) : Symbol(location, name, type, mutable)
class ConstSymbol(location: Location, name: String, type: Type, val value: Value) : Symbol(location, name, type, false)
class GlobalVarSymbol(location: Location, name: String, type: Type, mutable:Boolean, val offset:Int) : Symbol(location, name, type, mutable)
class FunctionSymbol(location: Location, name: String) : Symbol(location, name, TypeNothing, false) {
    val overloads = mutableListOf<FunctionInstance>()
    fun clone() : FunctionSymbol {
        val clone = FunctionSymbol(location, name)
        clone.overloads.addAll(overloads)
        return clone
    }
}
class TypeNameSymbol(location: Location, name: String, type:Type) : Symbol(location, name, type, false)
class FieldSymbol(location: Location, name: String, type: Type, mutable:Boolean, var offset:Int=-1) : Symbol(location, name, type, mutable)
class StackVarSymbol(location: Location, name: String, type: Type, mutable:Boolean, var offset:Int=-1) : Symbol(location, name, type, mutable)

// Represents accessing a field or property of an object, e.g., obj.field
// Only used during type checking, not in the final IR
class AccessSymbol(location: Location, val lhs:Symbol, val rhs:Symbol, type:Type) : Symbol(location, "${lhs.name}.${rhs.name}", type, rhs.mutable)

val predefinedSymbols : Map<String, Symbol> = genPredefinedSymbols()
val lengthSymbol = FieldSymbol(nullLocation, "length", TypeInt, false, -4)

private fun genPredefinedSymbols(): Map<String, Symbol> {
    val symbols = mutableMapOf<String, Symbol>()
    for (type in listOf(TypeUnit, TypeBool, TypeChar, TypeInt, TypeReal, TypeString, TypeAny, TypeNothing, TypeNull, TypeLong)) {
        val sym = TypeNameSymbol(nullLocation, type.name, type)
        symbols[type.name] = sym
    }

    val trueSym = ConstSymbol(nullLocation, "true", TypeBool, IntValue(1, TypeBool))
    val falseSym = ConstSymbol(nullLocation, "false", TypeBool, IntValue(0, TypeBool))
    val nullSym = ConstSymbol(nullLocation, "null", TypeNull, IntValue(0, TypeNull))
    symbols[trueSym.name] = trueSym
    symbols[falseSym.name] = falseSym
    symbols[nullSym.name] = nullSym

    return symbols
}

val allAccessSymbols = mutableMapOf<Pair<Symbol, Symbol>, AccessSymbol>()
fun createAccessSymbol(location: Location, lhs:Symbol, rhs:Symbol, type:Type) : AccessSymbol {
    val key = Pair(lhs, rhs)
    return allAccessSymbols.getOrPut(key) {
        val sym = AccessSymbol(location, lhs, rhs, type)
//        println("Creating AccessSymbol $sym for ${lhs.name}.${rhs.name}")
        sym
    }
}

fun newGlobalVar(location: Location, name: String, type: Type, mutable: Boolean): GlobalVarSymbol {
    val sym = GlobalVarSymbol(location, name, type, mutable, globalsSize)
    globalsSize += type.getSize()
    // round up to next multiple of 4
    if (globalsSize % 4 != 0)
        globalsSize += 4 - (globalsSize % 4)
    allGlobals.add(sym)
    return sym
}

fun AstBlock.lookupSymbol(name: String, location: Location): Symbol? {
    val ret = predefinedSymbols[name] ?:
           symbols[name]?:
           parent?.lookupSymbol(name, location)
    ret?.references?.add(location)
    return ret
}

fun AstBlock.addSymbol(symbol: Symbol) {
    if (this is AstFile) {
        parent!!.addSymbol(symbol)
        return
    }

    val duplicate = symbols[symbol.name]
    if (duplicate!= null && !secondTypecheckPass)
        Log.error(symbol.location, "Duplicate symbol: ${symbol.name}")
    symbols[symbol.name] = symbol
}

fun AstBlock.addFunctionOverload(location: Location, name: String, function:FunctionInstance) {
    if (this is AstFile) {
        parent!!.addFunctionOverload(location, name, function)
        return
    }

    when (val sym = symbols[name]) {
        null -> {
            val funcSym = FunctionSymbol(location, name)
            funcSym.overloads.add(function)
            symbols[name] = funcSym
            if (this is AstClassDefStmt)
                klass.add(funcSym)
        }

        is FunctionSymbol -> {
            val dup = sym.overloads.find { it.hasSameSignature(function) }
            if (dup!= null && !secondTypecheckPass)
                Log.error(location, "Duplicate function overload: ${function.name}")
            sym.overloads.add(function)
        }

        else -> {
            Log.error(location, "Symbol '${name}' is already defined, but not a function")
        }
    }
}

fun AstBlock.addFunctionOverride(location: Location, name: String, function:FunctionInstance) {
    if (this is AstFile) {
        parent!!.addFunctionOverride(location, name, function)
        return
    }

    when (val sym = symbols[name]) {
        null -> {
            Log.error(location, "Function '${name}' has nothing to override")
            addFunctionOverload(location, name, function)
        }

        is FunctionSymbol -> {
            val dup = sym.overloads.find { it.hasSameSignature(function) }
            if (dup== null)
                Log.error(location, "Function '${name}' has nothing to override")
            else {
                function.function.virtualFunctionNumber = dup.function.virtualFunctionNumber // Set the virtual function number
                if(function.function.virtualFunctionNumber!=-1) {
                    // Update the vtable for the class
                    val klass = (function.function.thisSymbol!!.type as TypeClassInstance).genericType
                    klass.virtualFunctions[function.function.virtualFunctionNumber] = function.function
                }
                sym.overloads.remove(dup) // Remove the original function
            }
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
    is AccessSymbol -> "fieldAccess"
    is StackVarSymbol -> "stack variable"
}


// A FunctionInstance represents a specific instantiation of a generic function with concrete type arguments
class FunctionInstance(
    val name : String,
    val function: Function,
    val parameters: List<VarSymbol>,
    val thisSymbol : VarSymbol? = null,
    val returnType: Type,
    val isVararg : Boolean
)

fun FunctionInstance.mapTypes(typeMap: Map<TypeGenericParameter, Type>) : FunctionInstance {
    val newParams = parameters.map { VarSymbol(it.location, it.name, it.type.mapType(typeMap), it.mutable) }
    val newThisSymbol = thisSymbol?.let { VarSymbol(it.location, it.name, it.type.mapType(typeMap), it.mutable) }
    val newReturnType = returnType.mapType(typeMap)
    val newName = name.substringBefore("(")+newParams.joinToString(prefix="(", postfix=")", separator = ",") { it.type.name }
    return FunctionInstance(newName, this.function, newParams, newThisSymbol, newReturnType, isVararg)
}

fun Function.toFunctionInstance() : FunctionInstance {
    return FunctionInstance(name, this, parameters, thisSymbol, returnType, isVararg)
}

fun Symbol.mapType(typeMap: Map<TypeGenericParameter, Type>) : Symbol {
    return when(this) {
        is ConstSymbol -> ConstSymbol(location, name, type.mapType(typeMap), value)
        is FieldSymbol -> FieldSymbol(location, name, type.mapType(typeMap), mutable, offset)
        is FunctionSymbol -> {
            val clone = this.clone()
            clone.overloads.clear()
            clone.overloads.addAll(overloads.map { it.mapTypes(typeMap) })
            clone
        }
        is GlobalVarSymbol -> this
        is TypeNameSymbol -> TypeNameSymbol(location, name, type.mapType(typeMap))
        is StackVarSymbol -> StackVarSymbol(location, name, type.mapType(typeMap), mutable, offset)
        is VarSymbol -> VarSymbol(location, name, type.mapType(typeMap), mutable)
        is AccessSymbol -> error("AccessSymbol should not be mapped")
    }
}

// ============================================================
//                    SymbolMap
// ============================================================
// Output a JSON file containing a list of all symbols with their
// types and locations.

private fun Symbol.toJson():String {
    val kind = when (this) {
        is ConstSymbol -> "constant"
        is FieldSymbol -> "field"
        is FunctionSymbol -> "function"
        is GlobalVarSymbol -> "global"
        is TypeNameSymbol -> when (type) {
            is TypeClass -> "class"
            is TypeClassInstance -> "class"
            is TypeEnum -> "enum"
            else -> "type"
        }
        is VarSymbol -> "var"
        is AccessSymbol -> "fieldAccess"
        is StackVarSymbol -> "stackVar"
    }

    val type = if (this is FunctionSymbol) this.overloads[0].returnType else this.type

    return """{"name":"$name", "kind":"$kind", "type":"$type", "definition":${location.toJson()}, "references":[${references.joinToString{ it.toJson() }}], "mutable":$mutable}"""
}

fun AstTop.writeSymbolMap() {
    val fh = File("symbol-map.json").bufferedWriter()
    val json = """{"symbols":[
        |${allSymbols.joinToString(",\n") { it.toJson() }}
        |]}""".trimMargin()
    fh.write(json)
    fh.close()
}