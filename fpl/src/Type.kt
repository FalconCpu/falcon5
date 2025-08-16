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

class TypeArray private constructor(val elementType:Type) : Type("Array<$elementType>") {
    companion object {
        val allArrayTypes = mutableMapOf<Type, TypeArray>()
        fun create(elementType: Type) = allArrayTypes.getOrPut(elementType) {
            TypeArray(elementType)
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
    TypeAny -> 0
    TypeError -> 0
    TypeNothing -> 0
}