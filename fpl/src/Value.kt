sealed class Value (val type:Type)

class IntValue(val value: Int, type:Type) : Value(type) {
    override fun toString(): String = "IntValue:$value"
}


class StringValue private constructor(val value: String, val index:Int) : Value(TypeString) {

    override fun toString(): String = "STRING$index"

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
