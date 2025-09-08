// Type system

var errorEnum : TypeEnum? = null

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

class TypeClass(name:String, val typeParameters:List<TypeGenericParameter>, val superClass:TypeClassInstance?) : Type(name) {
    val fields = mutableListOf<Symbol>()
    val virtualFunctions = mutableListOf<Function>()
    lateinit var constructor : FunctionInstance
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

class TypeEnum(name:String) : Type(name) {
    lateinit var parameters : List<FieldSymbol>
    val entries = mutableListOf<ConstSymbol>()
    val values = mutableMapOf<FieldSymbol, ArrayValue>()

    fun lookup(name:String) : ConstSymbol? {
        return entries.firstOrNull { it.name == name }
    }
}

fun Type.isSuperClassOf(other: Type): Boolean {
    if (this == other) return true
    if (this !is TypeClassInstance) return false
    if (other !is TypeClassInstance) return false
    var current: TypeClass? = other.genericType
    while (current != null) {
        if (this.genericType == current) {
            return true
        }
        current = current.superClass?.genericType
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

class TypeErrable private constructor(val okType: Type) : Type("$okType!") {
    companion object {
        val allErrableTypes = mutableMapOf<Type, TypeErrable>()
        fun create(okType: Type) = allErrableTypes.getOrPut(okType) {
            TypeErrable(okType)
        }
    }
}

class TypeGenericParameter(name: String) : Type(name)

class TypeClassInstance private constructor(val genericType: TypeClass, val typeArguments: Map<TypeGenericParameter,Type>, name:String) : Type(name) {
    private val fields = mutableMapOf<String,Symbol>()

    val constructor by lazy { genericType.constructor.mapTypes(typeArguments) }

    fun lookup(name:String, location: Location?) : Symbol? {
        val sym = fields[name]
        if (sym != null) {
            if (location!=null)
                sym.references += location
            return sym
        }

        val lok = genericType.lookup(name) ?: return null
        val mapped = lok.mapType(typeArguments)
        fields[name] = mapped
        if (location!=null)
            mapped.references += location
        return mapped
    }

    companion object {
        val allInstances = mutableListOf<TypeClassInstance>()
        fun create(genericType: TypeClass, typeArguments: Map<TypeGenericParameter,Type>) : TypeClassInstance {
            val ret = allInstances.find{ it.genericType == genericType && it.typeArguments == typeArguments }
            if (ret != null) return ret
            val name = if (typeArguments.isEmpty())
                genericType.name
            else
                genericType.name + "<" + typeArguments.values.joinToString(",") + ">"
            val instance = TypeClassInstance(genericType, typeArguments, name)
            allInstances.add(instance)
            return instance
        }
    }
}

fun TypeClass.toClassInstance() = TypeClassInstance.create(this, emptyMap())

class TypeTuple private constructor(val elementTypes: List<Type>) : Type("(${elementTypes.joinToString(",")})") {
    companion object {
        val allTupleTypes = mutableMapOf<List<Type>, TypeTuple>()
        fun create(elementTypes: List<Type>) = allTupleTypes.getOrPut(elementTypes) {
            TypeTuple(elementTypes)
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

    if (this==errorEnum && other is TypeErrable) return true
    if (other is TypeErrable && this.isAssignableTo(other.okType)) return true

    return false
}

fun TctExpr.checkType(expectedType:Type) : TctExpr {
    // Handle wrapping a value into an errable type
    if (expectedType is TypeErrable && this.type==errorEnum)
        return TctMakeErrableExpr(location, this, 1, expectedType)
    if (expectedType is TypeErrable && this.type==expectedType.okType)
        return TctMakeErrableExpr(location, this, 0, expectedType)

    if (!type.isAssignableTo(expectedType))
        return TctErrorExpr(location, "Type mismatch got '$type' when expecting '$expectedType'")
    return this
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
    is TypeEnum -> 4
    is TypeNullable -> 4
    is TypeErrable -> 8
    is TypeGenericParameter -> 4
    is TypeClassInstance -> 4
    is TypeTuple -> 4*elementTypes.size
}

// ========================================================================
//                          Map Type
// ========================================================================
// Map a generic type to a concrete type

fun Type.mapType(typeMap: Map<TypeGenericParameter, Type>): Type {
    return when(this) {
        is TypeGenericParameter -> typeMap[this] ?: this
        is TypeClassInstance -> {
            val newTypeArgs = typeArguments.mapValues { it.value.mapType(typeMap) }
            TypeClassInstance.create(genericType, newTypeArgs)
        }
        is TypeArray -> TypeArray.create(elementType.mapType(typeMap))
        is TypeRange -> TypeRange.create(elementType.mapType(typeMap))
        is TypeNullable -> TypeNullable.create(elementType.mapType(typeMap))
        is TypeErrable -> TypeErrable.create(okType.mapType(typeMap))
        is TypeClass -> {
            val newTypeArgs = typeParameters.associateWith { (typeMap[it] ?: it) }
            TypeClassInstance.create(this, newTypeArgs)
        }
        is TypeTuple -> TypeTuple.create(elementTypes.map { it.mapType(typeMap) })
        TypeAny,
        TypeBool,
        TypeChar,
        is TypeEnum,
        TypeError,
        TypeInt,
        TypeNothing,
        TypeNull,
        TypeReal,
        TypeString,
        TypeUnit -> this
    }
}

