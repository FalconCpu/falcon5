// Type system

sealed class Type (val name:String) {
    override fun toString(): String = name
}

object TypeUnit   : Type("Unit")
object TypeBool   : Type("Bool")
object TypeChar   : Type("Char")
object TypeInt    : Type("Int")
object TypeString : Type("String")
object TypeReal   : Type("Float")
object TypeError  : Type("Error")
object TypeAny    : Type("Any")
object TypeNothing: Type("Nothing")
object TypeNull   : Type("Null")

class TypeArray private constructor(val elementType:Type) : Type("Array<$elementType>") {
    companion object {
        val allArrayTypes = mutableMapOf<Type, TypeArray>()
        fun create(elementType: Type) = allArrayTypes.getOrPut(elementType) {
            TypeArray(elementType)
        }
    }
}

class TypeRange private constructor(val elementType:Type) : Type("Range<$elementType>") {
    companion object {
        val allRangeTypes = mutableMapOf<Type, TypeRange>()
        fun create(elementType: Type) = allRangeTypes.getOrPut(elementType) {
            TypeRange(elementType)
        }
    }
}

class TypeClass(name:String, val superClass:TypeClass?) : Type(name) {
    val fields = mutableListOf<Symbol>()
    val virtualFunctions = mutableListOf<Function>()
    lateinit var constructor : Function
    var instanceSize = 0
    val descriptor = ClassValue.create(this)

    fun add(sym: Symbol) {
        // TODO - think about padding
        if (sym is FieldSymbol) {
            if (sym.offset != -1 && sym.offset != instanceSize)
                error("Field '${sym.name}' already has offset of ${sym.offset} but attempting to change to $instanceSize")
            sym.offset = instanceSize
            instanceSize += sym.type.getSize()
        }
        fields += sym
    }

    fun lookup(name:String) : Symbol? {
        return fields.firstOrNull { it.name == name }
    }
}

fun Type.isSuperClassOf(other: Type): Boolean {
    if (this == other) return true
    if (this !is TypeClass) return false
    if (other !is TypeClass) return false
    var current: TypeClass? = other
    while (current != null) {
        if (this == current) {
            return true
        }
        current = current.superClass
    }
    return false
}

class TypeNullable private constructor (val elementType: Type) : Type("$elementType?") {
    companion object {
        val allNullableTypes = mutableMapOf<Type, TypeNullable>()
        fun create(elementType: Type) = allNullableTypes.getOrPut(elementType) {
            TypeNullable(elementType)
        }
    }
}

// Type checking
fun Type.isAssignableTo(other: Type): Boolean {
    if (this == other) return true

    // Silently allow Error type to be assigned to any other type
    if (this==TypeError || other == TypeError) return true

    // Nothing type can be assigned to any other type
    if (this==TypeNothing) return true

    // any type can be assigned to TypeAny
    if (other == TypeAny) return true

    if (other.isSuperClassOf(this)) return true
    if (other is TypeNullable && other.elementType.isSuperClassOf(this)) return true
    if (other is TypeNullable && this is TypeNullable && other.elementType.isSuperClassOf(this.elementType)) return true
    if (other is TypeNullable && this == TypeNull) return true
    return false
}

fun TctExpr.checkType(expectedType:Type) {
    if (!type.isAssignableTo(expectedType))
        reportTypeError(location, "Type mismatch got '$type' when expecting '$expectedType'")
}

fun reportTypeError(location: Location, message: String) : TypeError {
    Log.error(location, message)
    return TypeError
}

fun Type.getSize(): Int = when (this) {
    TypeUnit, TypeBool, TypeChar -> 1
    TypeInt, TypeReal -> 4
    TypeString -> 4
    is TypeArray -> 4
    is TypeRange -> 4
    TypeAny -> 0
    TypeError -> 0
    TypeNothing -> 0
    is TypeClass -> 4
    TypeNull -> 4
    is TypeNullable -> 4
}