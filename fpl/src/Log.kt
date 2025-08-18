object Log {
    private val errors = mutableMapOf<Location,String>()

    fun hasErrors() = errors.isNotEmpty()

    fun getMessages() = errors.values.joinToString("\n")

    fun error(location: Location, message: String) {
        errors[location] = "$location $message"
    }
    
    fun clear() {
        errors.clear()
    }
}