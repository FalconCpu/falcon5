import java.io.Reader
import TokenKind.*

class Lexer(private val fileHandle:Reader, private val filename: String) {

    // Keep track of the current character and position in the file
    private var lineNumber = 1
    private var columnNumber = 1
    private var atEof = false
    private var currentChar = readChar()

    // keep track of whether we are at the start of a line
    private var atStartOfLine = true
    private var lineContinues = true

    // keep track of the bounds of the current token
    private var firstLine = 1
    private var firstColumn = 1
    private var lastLine = 1
    private var lastColumn = 1

    private val indentStack = mutableListOf(1)

    // Read the next character from the file
    private fun readChar(): Char {
        val c = fileHandle.read()
        if (c == -1) {
            atEof = true
            return '\u0000'
        } else
            return c.toChar()
    }

    // Consume a character from the input stream and return it
    private fun nextChar(): Char {
        lastLine = lineNumber
        lastColumn = columnNumber
        val ret = currentChar
        currentChar = readChar()

        if (ret == '\n') {
            lineNumber++
            columnNumber = 1
        } else
            columnNumber++
        return ret
    }

    // Build a location structure
    private fun getLocation() = Location(filename, firstLine, firstColumn, lastLine, lastColumn)

    // skip over any whitespace and comments
    private fun skipWhitespaceAndComments() {
        while (true)
            when (currentChar) {
                ' ',
                '\t',
                '\r' -> nextChar()

                '\n' -> if (lineContinues)
                    nextChar()
                else
                    return

                '#' -> while (currentChar != '\n' && !atEof)
                    nextChar()

                else -> return
            }
    }

    // Read a word from the input stream and return it
    private fun readWord(): String {
        val sb = StringBuilder()
        while (currentChar.isLetterOrDigit() || currentChar == '_')
            sb.append(nextChar())
        return sb.toString()
    }

    private fun readEscapedChar() : Char {
        val c = nextChar()
        return if (c=='\\') {
            when (val c2 = nextChar()) {
                'n' -> '\n'
                't' -> '\t'
                'r' -> '\r'
                '\'' -> '\''
                '\\' -> '\\'
                '\"' -> '\"'
                else -> c2
            }
        } else
            c
    }

    private fun readStringLiteral(): String {
        val sb = StringBuilder()
        nextChar() // consume the opening quote
        while (currentChar!= '"' && !atEof)
            sb.append(readEscapedChar())
        nextChar() // Consume the closing quote
        return sb.toString()
    }

    private fun readCharLiteral(): String {
        val sb = StringBuilder()
        nextChar() // consume the opening quote
        while (currentChar!= '\'' &&!atEof)
            sb.append(readEscapedChar())
        nextChar() // Consume the closing quote
        return sb.toString()
    }

    // Read a punctuation symbol from the input stream and return it
    private fun readPunctuation(): String {
        val c = nextChar()
        if ((c == '<' && currentChar == '=') ||
            (c == '>' && currentChar == '=') ||
            (c == '!' && currentChar == '=') ||
            (c == '+' && currentChar == '=') ||
            (c == '-' && currentChar == '=') ||
            (c == '-' && currentChar == '>') ||
            (c == '.' && currentChar == '.') ||
            (c == '?' && currentChar == '?')
        ) {
            val c2 = nextChar()
            return "$c$c2"
        } else
            return c.toString()
    }

    // main lexical analyzer function
    private fun getNextToken(): Token {
        skipWhitespaceAndComments()
        firstLine = lineNumber
        firstColumn = columnNumber
        lastLine = lineNumber
        lastColumn = columnNumber

        val kind: TokenKind
        val value: String
        if (atEof) {
            if (!atStartOfLine) {
                kind = EOL
            } else if (indentStack.size>1) {
                kind = DEDENT
                indentStack.removeAt(indentStack.lastIndex)
            } else
                kind = EOF
            value = kind.value

        } else if (atStartOfLine && columnNumber>indentStack.last()) {
            kind = INDENT
            value = kind.value
            indentStack.add(columnNumber)

        } else if (atStartOfLine && columnNumber<indentStack.last()) {
            kind = DEDENT
            value = kind.value
            indentStack.removeAt(indentStack.lastIndex)
            if (columnNumber>indentStack.last()) {
                Log.error(getLocation(), "Indentation error - got column $columnNumber, expected ${indentStack.last()}")
                indentStack.add(columnNumber)
            }

        } else if (currentChar == '\n') {
            kind = EOL
            value = kind.value

        } else if (currentChar.isLetter() || currentChar == '_') {
            value = readWord()
            kind = TokenKind.toKind.getOrDefault(value, IDENTIFIER)

        } else if (currentChar.isDigit()) {
            value = readWord()
            kind = INTLITERAL

        } else if (currentChar == '"') {
            value = readStringLiteral()
            kind = STRINGLITERAL

        } else if (currentChar == '\'') {
            value = readCharLiteral()
            kind = CHARLITERAL

        } else {
            value = readPunctuation()
            kind = TokenKind.toKind.getOrDefault(value, ERROR)
        }

        val loc = getLocation()
        lineContinues = kind.lineContinues
        atStartOfLine = kind == EOL || kind==DEDENT
        val ret = Token(loc, kind, value)
        return ret
    }

    fun nextToken(): Token {
        while (true) {
            val token = getNextToken()
            if (token.kind != ERROR)
                return token
            Log.error(token.location, "Invalid token '${token.value}'")
        }
    }
}