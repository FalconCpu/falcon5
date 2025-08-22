# Falcon Programming Language

## Language Overview

The FPL language is a statically typed, compiled programming language
aimed at providing a systems programming language for my F32 project.
It is my attempt to combine the semantics of C with the expressiveness
of Kotlin.

The language looks similar to Kotlin, but using indentation to denote
blocks instead of braces - similar to Python. Blocks can be denoted simply
by indentation, or by using `end` keywords to close blocks explicitly. This 
attempts to avoid the verbosity of braces while maintaining clarity, and
avoid the cliff-edge effect of Python.

For example

    fun abs(a:Int) -> Int     # Function definition with parameters and return type
        if a > 0              # Comments start with a hash symbol
            return a          # Indentation shows the block 
        else
            return -a
    end fun                   # Optional end keyword to close the function

The intended style is to use indentation for short blocks, and `end` keywords for
longer or nested blocks to improve readability.

## Language Features

The semantics of the language are similar to C, with manual memory management, 
and explicit flow control (if, while, for, etc.). It supports
static typing, type inference, and a simple type system with primitive types,
arrays, and custom types. Functions can be defined with parameters and return types,
and can be called with arguments. 

## Lexical Structure and Syntax







1. Lexical Structure
   Tokens: Identifiers, keywords (fun, val, var, if, else, while, for, etc.), literals (int, char, string), operators (+, -, *, /, etc.), punctuation ((, ), [, ], {, }, ,, :).
   Comments: (Describe how comments are written, e.g., // for single-line.)
2. Types
   Primitive Types: Int, Char, Bool, String
   Array Types: Array<T>
   Custom Types: (Describe how to declare new types if supported.)
3. Expressions
   Literals: 42, 'a', "hello"
   Identifiers: Variable and function names.
   Binary Operations: a + b, x * y
   Function Calls: foo(x, y)
   Array Indexing: arr[0]
   Member Access: obj.field
   Range Expressions: 1..10, a..b <
4. Statements
   Variable Declaration: val x: Int = 5, var y = 10
   Assignment: x = 42
   If Statement:
   if x > 0
   y = x
   else
   y = -x
   end
   While Loop:
   while x < 10
   x = x + 1
   end
   For Loop:
   for i in 1..10
   print(i)
   end
   Repeat-Until Loop:
   repeat
   x = x - 1
   until x == 0
5. Functions
   Definition:
   fun add(a: Int, b: Int) -> Int
   return a + b
   end
   External Functions: extern fun print(x: String)
6. Lambdas
   Syntax: { expr } or similar (describe your lambda syntax).
7. Error Handling
   (Describe how errors are reported, e.g., type errors, parse errors.)