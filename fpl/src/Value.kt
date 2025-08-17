import kotlin.math.min

sealed class Value (val type:Type) {
    abstract fun emitRef(sb:StringBuilder)
}

class IntValue(val value: Int, type:Type) : Value(type) {
    override fun toString(): String = "IntValue:$value"

    override fun emitRef(sb:StringBuilder) {
        if (type is TypeChar)
            sb.append("dcb $value\n")
        else
            sb.append("dcw $value\n")
    }
}


class StringValue private constructor(val value: String, val index:Int) : Value(TypeString) {

    override fun toString(): String = "STRING$index"

    fun emit(sb:StringBuilder) {
        val comment = value.take(min(value.length, 20)).replace("\n", "")
        sb.append("dcw ${value.length}\n")
        sb.append("$this: # $comment\n")
        for (c in value.chunked(4)) {
            val data = c[0].code +
                    (if (c.length > 1) (c[1].code shl 8) else 0) +
                    (if (c.length > 2) (c[2].code shl 16) else 0) +
                    (if (c.length > 3) (c[3].code shl 24) else 0)
            sb.append("dcw $data\n")
        }
        sb.append("\n")
    }

    override fun emitRef(sb: StringBuilder) {
        sb.append("dcw STRING$index\n")
    }

    companion object {
        val allStrings = mutableMapOf<String, StringValue>()

        fun create(value: String): StringValue = allStrings.getOrPut(value) {
            StringValue(value, allStrings.size)
        }

        fun clear() {
            allStrings.clear()
        }
    }
}

class ArrayValue private constructor (val elements: List<Value>, val index:Int, type:Type) : Value(type) {
    override fun toString(): String = "ARRAY$index"

    override fun emitRef(sb: StringBuilder) {
        sb.append("dcw ARRAY$index\n")
    }

    fun emit(sb: StringBuilder) {
        sb.append("dcw ${elements.size}\n")  // Size of the array
        sb.append("ARRAY$index:\n")
        for (element in elements)
            element.emitRef(sb)
        sb.append("\n")
    }

    companion object {
        val allArrays = mutableMapOf<List<Value>, ArrayValue>()

        fun create(elements:List<Value>, type:Type): ArrayValue = allArrays.getOrPut(elements) {
            ArrayValue(elements, allArrays.size, type)
        }

        fun clear() {
            allArrays.clear()
        }
    }
}


fun emitAllValues(sb: StringBuilder) {
    for (str in StringValue.allStrings.values)
        str.emit(sb)
    for (arr in ArrayValue.allArrays.values)
        arr.emit(sb)
}
