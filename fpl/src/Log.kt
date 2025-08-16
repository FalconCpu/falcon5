object Log {
    private val errors = mutableListOf<String>()

    fun hasErrors() = errors.isNotEmpty()

    fun getMessages() = errors.joinToString("\n")

    fun error(message: String) {
        errors.add(message)
    }

    fun error(location: Location, message: String) {
        errors.add("$location $message")
    }
    
    fun clear() {
        errors.clear()
    }
}