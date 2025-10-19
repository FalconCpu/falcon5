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

class TypePointer private constructor(val elementType:Type) : Type("Pointer<$elementType>") {
    companion object {
        val allPointerTypes = mutableMapOf<Type, TypePointer>()
        fun create(elementType: Type) = allPointerTypes.getOrPut(elementType) {
            TypePointer(elementType)
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
    var destructor : FunctionInstance? = null
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
    if (this !is TypeClassInstance && this !is TypeClass) return false
    if (other !is TypeClassInstance) return false
    val generic = if (this is TypeClassInstance) this.genericType else this
    var current: TypeClass? = other.genericType
    while (current != null) {
        if (generic == current)
            return true
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

class TypeInlineArray private constructor(val elementType:Type, val size:Int) : Type("InlineArray<$elementType>($size)") {
    companion object {
        val allInlineArrayTypes = mutableMapOf<Pair<Type,Int>, TypeInlineArray>()
        fun create(elementType: Type, size:Int) = allInlineArrayTypes.getOrPut(Pair(elementType,size)) {
            TypeInlineArray(elementType, size)
        }
    }
}

class TypeFunction private constructor(val parameterType:List<Type>, val returnType:Type, name:String) : Type(name) {
    companion object {
        val allFunctionTypes = mutableListOf<TypeFunction>()
        fun create(parameterType:List<Type>, returnType:Type,): TypeFunction {
            val ret = allFunctionTypes.find{it.parameterType==parameterType && it.returnType==returnType}
            if (ret!=null)
                return ret
            val name = parameterType.joinToString(prefix="(", postfix = ")->", separator = ",") + returnType.toString()
            val new = TypeFunction(parameterType, returnType, name)
            allFunctionTypes += new
            return new
        }
        fun create(func:FunctionInstance): TypeFunction {
            val paramTypes = func.parameters.map{it.type}
            return create(paramTypes, func.returnType)
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

    // Char can be promoted to Int
    // if (this==TypeChar && other==TypeInt) return true

    // Arrays can be converted to Pointer
    if (this is TypeArray && other is TypePointer && this.elementType.isAssignableTo(other.elementType)) return true
    if (this is TypeString && other is TypePointer && TypeChar.isAssignableTo(other.elementType)) return true

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
    if (this.type==TypeInt && expectedType==TypeChar && this is TctConstant && this.value is IntValue && this.value.value in -128..127)
        return TctConstant(location, IntValue(this.value.value, TypeChar))

    if (!type.isAssignableTo(expectedType))
        return TctErrorExpr(location, "Type mismatch got '$type' when expecting '$expectedType'")
    return this
}

fun reportTypeError(location: Location, message: String) : TypeError {
    Log.error(location, message)
    return TypeError
}

fun commonAncestorType(type1:Type, type2:Type) : Type? {
    if (type1 !is TypeClassInstance) return null
    if (type2 !is TypeClassInstance) return null
    if (type1.typeArguments != type2.typeArguments)
        return null
    var current: TypeClass? = type1.genericType
    while (current != null) {
        println("Checking if ${current.name} is super class of ${type2.genericType.name}")
        if (current.isSuperClassOf(type2))
            return TypeClassInstance.create(current, type1.typeArguments)
        current = current.superClass?.genericType
    }
    return null
}

fun enclosingType(location: Location, typ1:Type, type2:Type) : Type {
    val isNullable = (typ1 is TypeNullable) || (type2 is TypeNullable)
    val base1 = if (typ1 is TypeNullable) typ1.elementType else typ1
    val base2 = if (type2 is TypeNullable) type2.elementType else type2

    val ret =
        if      (base1 is TypeNothing)          base2
        else if (base2 is TypeNothing)          base1
        else if (base1==base2)                  base1
        else if (base1==TypeError)              base2
        else if (base2==TypeError)              base1
        else if (base1==TypeAny)                TypeAny
        else if (base2==TypeAny)                TypeAny
        else if (base1==TypeNull)               TypeNullable.create(base2)
        else if (base2==TypeNull)               TypeNullable.create(base1)
        else commonAncestorType(base1, base2)
            ?: reportTypeError(location, "Types '$typ1' and '$type2' have no common ancestor type")

    return if (isNullable) TypeNullable.create(ret) else ret
}

fun Type.getSize(): Int = when (this) {
    TypeChar -> 1
    TypeUnit, TypeBool -> 4
    TypeInt, TypeReal -> 4
    TypeString -> 4
    is TypeArray -> 4
    is TypeRange -> 4
    TypeAny -> 4
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
    is TypeInlineArray -> elementType.getSize() * size
    is TypeFunction -> 4
    is TypePointer -> 4
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
        is TypePointer -> TypePointer.create(elementType.mapType(typeMap))
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
        is TypeInlineArray -> TypeInlineArray.create(elementType.mapType(typeMap), size)
        is TypeFunction -> this // TODO
    }
}

