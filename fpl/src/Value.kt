import kotlin.math.min

sealed class Value (val type:Type)

class IntValue(val value: Int, type:Type) : Value(type) {
    override fun toString(): String = "IntValue:$value"
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


fun emitAllValues(sb: StringBuilder) {
    for (str in StringValue.allStrings.values) {
        str.emit(sb)
    }
}
