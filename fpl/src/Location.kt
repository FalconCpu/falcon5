class Location (val filename:String, val firstLine: Int, val firstColumn: Int, val lastLine: Int, val lastColumn: Int) {
    override fun toString(): String = "$filename:${firstLine}.${firstColumn}-${lastLine}.${lastColumn}: "
}

val nullLocation = Location("null", 0, 0, 0, 0)