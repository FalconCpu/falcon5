# Falcon Programming Language (FPL) - Language Guide

## Table of Contents
1. [Introduction](#introduction)
2. [Lexical Structure](#lexical-structure)
3. [Types](#types)
4. [Variables and Constants](#variables-and-constants)
5. [Operators and Expressions](#operators-and-expressions)
6. [Control Flow](#control-flow)
7. [Functions](#functions)
8. [Structs](#structs)
9. [Classes and Methods](#classes-and-methods)
10. [Inheritance](#inheritance)
11. [Generics](#generics)
12. [Enums](#enums)
13. [Error Handling](#error-handling)
14. [Tuples](#tuples)
15. [Arrays](#arrays)
16. [Inline Arrays](#inline-arrays)
17. [Ranges](#ranges)
18. [Value Types vs Reference Types](#value-types-vs-reference-types)
19. [Path-Dependent Typing](#path-dependent-typing)
20. [Duck Typing for Array-Like Classes](#duck-typing-for-array-like-classes)
21. [Varargs](#varargs)
22. [Memory Management](#memory-management)
23. [Standard Library](#standard-library)

---

## Introduction

The Falcon Programming Language (FPL) is a statically typed, compiled programming language designed for systems programming. It combines the semantics of C with the expressiveness of Kotlin, offering manual memory management with modern language features.

FPL uses indentation to denote blocks, similar to Python, but also supports explicit `end` keywords for improved clarity in longer or nested blocks. This approach aims to balance the conciseness of indentation-based syntax with the readability of explicit block terminators.

### Design Philosophy
- **Static typing** with type inference where possible
- **Manual memory management** for predictable performance
- **Expressive syntax** inspired by Kotlin
- **Flexible block notation** using indentation or `end` keywords
- **Modern features**: generics, tuples, pattern matching, error handling

---

## Lexical Structure

### Comments
Comments start with `#` and continue to the end of the line:
```fpl
# This is a comment
val x = 42  # Inline comment
```

### Identifiers
Identifiers start with a letter or underscore, followed by letters, digits, or underscores:
```fpl
myVariable
_privateVar
counter42
```

### Keywords
Reserved keywords include:
- **Control flow**: `if`, `elsif`, `else`, `while`, `repeat`, `until`, `for`, `in`, `return`, `break`, `continue`
- **Declarations**: `fun`, `val`, `var`, `class`, `struct`, `enum`, `const`
- **Types**: `Int`, `Float`, `Bool`, `Char`, `String`, `Array`, `InlineArray`
- **Modifiers**: `extern`, `new`, `vararg`
- **Memory**: `free`, `abort`
- **Other**: `this`, `end`, `as`, `is`, `null`

### Literals

**Integer Literals:**
```fpl
42
0
-17
```

**Floating-Point Literals:**
```fpl
3.14
-0.5
1.0
```

**Character Literals:**
```fpl
'a'
'Z'
'\n'
```

**String Literals:**
```fpl
"Hello, World!"
"Multiple words"
""  # Empty string
```

**Boolean Literals:**
```fpl
true
false
```

---

## Types

### Primitive Types

- **`Int`**: Integer type (32-bit signed)
- **`Float`**: Floating-point type (32-bit IEEE 754)
- **`Bool`**: Boolean type (`true` or `false`)
- **`Char`**: Single character
- **`String`**: Text string

### Array Types
```fpl
Array<Int>
Array<String>
Array<Point>
```

### Inline Array Types
Fixed-size arrays with compile-time known size:
```fpl
InlineArray<Int>(10)
InlineArray<String>(5)
```

### Nullable Types
Types can be made nullable with `?`:
```fpl
Int?
String?
```

### Tuple Types
```fpl
(Int, String)
(Float, Float, String)
```

### Custom Types
User-defined types via `struct`, `class`, or `enum`.

---

## Variables and Constants

### Variable Declaration
Use `var` for mutable variables:
```fpl
var x = 42
var name = "Alice"
var counter: Int = 0
```

### Constant Declaration
Use `val` for immutable values:
```fpl
val pi = 3.14159
val name = "Bob"
val size: Int = 100
```

### Type Inference
The compiler infers types when possible:
```fpl
val x = 42        # Inferred as Int
val y = 3.14      # Inferred as Float
val s = "hello"   # Inferred as String
```

### Explicit Types
Types can be specified explicitly:
```fpl
var count: Int = 0
val ratio: Float = 0.5
```

---

## Operators and Expressions

### Arithmetic Operators
```fpl
a + b   # Addition
a - b   # Subtraction
a * b   # Multiplication
a / b   # Division
a % b   # Modulo
-a      # Negation
```

### Bitwise Operators
```fpl  
a & b   # Bitwise AND
a | b   # Bitwise OR
a ^ b   # Bitwise XOR
a lsl b  # Left shift
a lsr b  # Logical right shift
a asr b  # Arithmetic right shift
```

### Comparison Operators
```fpl
a = b   # Equality (note: single = for comparison)
a != b  # Inequality
a < b   # Less than
a > b   # Greater than
a <= b  # Less than or equal
a >= b  # Greater than or equal
```

### Logical Operators
```fpl
a and b  # Logical AND  (short-circuiting)
a or b   # Logical OR  (short-circuiting)
not a    # Logical NOT
```

### Assignment Operators
```fpl
x = 42      # Assignment
x += 1      # Compound addition
x -= 1      # Compound subtraction
```

### Type Casting
```fpl
val i = 42
val f = i as Float
val c = (65 as Char)  # 'A'
```

### Type Checking
```fpl
if (result is Error)
    print("Error occurred")
```

---

## Control Flow

### If Statement
```fpl
if x > 0
    print("Positive")
elsif x < 0
    print("Negative")
else
    print("Zero")
```

With explicit `end`:
```fpl
if condition
    # Many lines
    # of code
end if
```

### While Loop
```fpl
var i = 0
while i < 10
    print(i)
    i = i + 1
```

### Repeat-Until Loop
```fpl
var i = 0
repeat
    print(i)
    i = i + 1
until i >= 10
```

### For Loop
```fpl
for i in 1..10
    print(i)
```

### For-In Loop  
```fpl
val arr = new [1, 2, 3, 4, 5]
for item in arr
    print(item)
```

### Break and Continue
```fpl
for i in 1..100
    if i % 15 = 0
        continue  # Skip multiples of 15
    if i > 50
        break  # Stop at 50
    print(i)
```

---

## Functions

### Basic Function
```fpl
fun add(a: Int, b: Int) -> Int
    return a + b
```

### Void Function
```fpl
fun greet()
    print("Hello!")
```

### Multiple Return Values (Tuples)
```fpl
fun divMod(a: Int, b: Int) -> (Int, Int)
    return (a / b, a % b)

val (quotient, remainder) = divMod(10, 3)
```

### Function Overloading
```fpl
fun print(i: Int)
fun print(s: String)
fun print(f: Float)
```

---

## Structs

### Basic Struct
```fpl
struct Point(x: Float, y: Float)

val p = Point(1.0, 2.0)
print(p.x)
```

### Mutable Fields
```fpl
struct Counter(var count: Int)

val c = Counter(0)
c.count = c.count + 1
```

### Nested Structs
```fpl
struct Point(x: Float, y: Float)
struct Line(start: Point, end: Point)

val line = Line(Point(0.0, 0.0), Point(1.0, 1.0))
```

---

## Classes and Methods

### Basic Class
```fpl
class Person(val name: String, var age: Int)

val p = new Person("Alice", 30)
print(p.name)
free p
```

### Methods
```fpl
class Counter(var count: Int)
    fun increment()
        count = count + 1
    
    fun get() -> Int
        return count

val c = new Counter(0)
c.increment()
print(c.get())
free c
```

### Constructor
```fpl
class Rectangle(val width: Float, val height: Float)
    fun area() -> Float
        return width * height
```

---

## Inheritance

### Basic Inheritance
```fpl
class Animal(val name: String)
    fun speak()
        print("...")

class Dog(name: String, val breed: String) : Animal(name)
    fun speak()
        print("Woof!")

val dog = new Dog("Rex", "Labrador")
dog.speak()
free dog
```

### Type Checking
```fpl
val animal: Animal = new Dog("Spot", "Beagle")
if animal is Dog
    print(animal.breed)  # Can access Dog-specific fields
```

---

## Generics

### Generic Functions
```fpl
fun identity<T>(x: T) -> T
    return x

val i = identity<Int>(42)
val s = identity<String>("hello")
```

### Generic Classes
```fpl
class Box<T>(val value: T)
    fun get() -> T
        return value

val intBox = new Box<Int>(42)
val strBox = new Box<String>("hello")
```

### Generic Constraints (Future)
Currently, FPL generics are simple and don't support constraints.

---

## Enums

### Basic Enum
```fpl
enum Color [RED, GREEN, BLUE]

val c = Color.RED
if c = Color.RED
    print("Red!")
```

### Enum with Fields
```fpl
enum Result(val code: Int, val message: String) [
    OK(0, "Success"),
    ERROR(1, "Failed")
]

val r = Result.OK
print(r.code)
print(r.message)
```

### Enum in Type Checking
```fpl
if result = Result.OK
    print("Success!")
```

---

## Error Handling

### Nullable Types
```fpl
fun find(arr: Array<Int>, target: Int) -> Int?
    for i in 0..<arr.length
        if arr[i] = target
            return i
    return null

val result = find(myArr, 42)
if result = null
    print("Not found")
else
    print(result)  # result is refined to Int here
```

### Errable Types
```fpl
enum FileError(desc: String) [
    NOT_FOUND("File not found"),
    PERMISSION_DENIED("Permission denied")
]

fun openFile(name: String) -> Int!
    if fileExists(name)
        return getFileHandle(name)
    else
        return FileError.NOT_FOUND

val result = openFile("data.txt")
if result is FileError
    print(result.desc)
else
    print(result)  # result is Int here
```

### Try Keyword
```fpl
fun processFile(name: String) -> Int!
    val handle = try openFile(name)  # Early return on error
    # handle is Int here
    return handle
```

---

## Tuples

### Tuple Creation
```fpl
val t = (1, "Hello")
val triple = (42, "answer", true)
```

### Tuple Type Annotation
```fpl
val t: (Int, String) = (1, "x")
```

### Tuple Indexing
```fpl
val t = (1, "x")
print(t.0)  # Prints 1
print(t.1)  # Prints "x"
```

### Tuple Destructuring
```fpl
val t = (42, "answer", true)
val (i, s, b) = t
print(i)  # 42
print(s)  # "answer"
print(b)  # true
```

### Destructuring Assignment
```fpl
var a: Int
var b: String
(a, b) = (1, "x")
```

### Function Return Tuples
```fpl
fun makeTuple() -> (Int, String, Bool)
    return (42, "answer", true)

fun main()
    val (i, s, b) = makeTuple()
```

---

## Arrays

### Array Creation
```fpl
val arr = new Array<Int>(10)
```

### Array Initialization
```fpl
val arr = new [1, 2, 3, 4, 5]
```

### Array with Lambda Initializer
```fpl
val arr = new Array<Int>(5) { it * 2 }
# Creates [0, 2, 4, 6, 8]
```

### Array Access
```fpl
arr[0] = 42
val x = arr[0]
```

### Array Length
```fpl
val len = arr.length
```

### Array Iteration
```fpl
for item in arr
    print(item)

for i in 0..<arr.length
    print(arr[i])
```

### Literal Array Syntax
```fpl
val points = new Array<Point3d>[
    Point3d(1, 2, 3),
    Point3d(4, 5, 6),
    Point3d(7, 8, 9)
]
```

---

## Inline Arrays

Inline arrays have a fixed size known at compile time and are allocated inline (on the stack or within structs/classes). They are value types that don't require `new` or `free`.

### Basic Inline Array

Create with explicit size:
```fpl
val arr = InlineArray<String>(5)
arr[0] = "Hello"
arr[1] = "World"
```

Or with initializer list (size is inferred):
```fpl
val arr = InlineArray<Int>[1, 2, 3, 4, 5]
```

### Inline Array with Lambda Initializer
```fpl
val arr = InlineArray<Int>(5) { it * 2 }
# Creates [0, 2, 4, 6, 8]
```

### Inline Array as Parameter
```fpl
fun printArray(arr: InlineArray<String>(5))
    for i in arr
        print(i)
```

Note: The size must match exactly:
```fpl
val a = InlineArray<String>(5)
printArray(a)  # OK

val b = InlineArray<String>(4)
printArray(b)  # ERROR: size mismatch
```

### Inline Array as Class Field
```fpl
class Container
    val arr: InlineArray<String>(5)
    
    fun set(index: Int, value: String)
        arr[index] = value
```

### Inline Array with Complex Types
```fpl
val arr = InlineArray<List<Int>>(4) { new List<Int>() }
# Creates 4 separate List instances
```

### Key Points
- **No `new` keyword**: InlineArrays are value types, created directly
- **No `free` needed**: Automatically managed by the compiler
- **Fixed size**: Size must be known at compile time (constant or inferred from initializers)
- **Inline allocation**: Stored on stack or inline within containing structures
- **Value semantics**: Assignment copies the entire array

---

## Ranges

### Inclusive Range
```fpl
for i in 1..10
    print(i)  # Prints 1 through 10
```

### Exclusive Range
```fpl
for i in 0..<10
    print(i)  # Prints 0 through 9
```

### Reverse Range
```fpl
for i in 10..>=0
    print(i)  # Prints 10 down to 0
```

### Ranges with Variables
```fpl
val start = 0
val end = 9
for i in start..end
    print(i)
```

---

## Value Types vs Reference Types

FPL distinguishes between **value types** and **reference types**, which differ fundamentally in how they are stored, copied, and managed in memory. Understanding this distinction is crucial for writing correct and efficient FPL code.

### Reference Types (Classes and Arrays)

**Classes** and **Arrays** are reference types:

- **Heap allocation**: Created using `new`, stored on the heap
- **Reference semantics**: Variables hold references (pointers) to the data, not the data itself
- **Shared state**: Assignment copies the reference, so multiple variables can refer to the same object
- **Manual lifetime**: Must be explicitly freed with `free`
- **Programmer-controlled lifetime**: You decide when objects are destroyed

```fpl
class Cat(val name: String)

fun main()
    val cat1 = new Cat("Whiskers")
    val cat2 = cat1  # Both reference the same Cat object
    
    # Modifying through one reference affects the other
    # (if fields were mutable)
    
    free cat1  # Frees the Cat object
    # Warning: cat2 is now a dangling reference!
```

```fpl
val arr1 = new Array<Int>(10)
val arr2 = arr1  # Both reference the same array
arr2[0] = 42     # Modifies the array that arr1 also sees
print(arr1[0])   # Prints 42

free arr1        # Must manually free when done
```

### Value Types (Structs and InlineArrays)

**Structs** and **InlineArrays** are value types:

- **Flexible allocation**: Can be allocated on the stack or inline within other types
- **Value semantics**: Variables hold the actual data, not a reference
- **Independent copies**: Assignment copies the entire data structure
- **Automatic lifetime**: Compiler tracks lifetime and automatically frees when out of scope
- **No `new` or `free`**: Created directly, freed automatically

```fpl
struct Point(x: Float, y: Float)

fun main()
    val p1 = Point(1.0, 2.0)
    val p2 = p1  # Creates a complete copy of p1
    
    # p1 and p2 are completely independent
    # Changes to p2 don't affect p1
    
    # Both automatically freed at end of scope - no free needed
```

```fpl
fun example()
    val arr = InlineArray<Int>(10)
    arr[0] = 42
    
    # arr is stack-allocated
    # Automatically freed when function returns
```

### Key Differences Summary

| Aspect | Reference Types | Value Types |
|--------|----------------|-------------|
| **Types** | `class`, `Array<T>` | `struct`, `InlineArray<T>(n)` |
| **Allocation** | Heap (via `new`) | Stack or inline |
| **Storage** | Variables hold references | Variables hold actual data |
| **Assignment** | Copies reference (aliasing) | Copies data (independent) |
| **Sharing** | Multiple references to same object | Each variable is independent |
| **Lifetime** | Manual (`new`/`free`) | Compiler-tracked (automatic) |
| **Size** | Dynamic/flexible | Fixed at compile time (InlineArray) |
| **Identity** | Object identity matters | No identity, only values |

### When to Use Each

**Use reference types (classes/Arrays) when:**
- You need dynamic, flexible sizing
- You want to share mutable state between multiple references
- You need polymorphism and inheritance
- Object identity matters (e.g., comparing if two references point to the same object)
- Data lifetime should outlive the current scope
- Working with large data structures (avoid copying overhead)

**Use value types (structs/InlineArrays) when:**
- Data is small and simple (e.g., coordinates, colors, small tuples)
- You want independent copies on assignment (no aliasing)
- You need predictable, automatic lifetime management
- You want stack allocation for performance
- Size is known at compile time (InlineArray)
- You want to avoid manual memory management

### Mixing Reference and Value Types

You can freely combine both kinds of types:

```fpl
struct Point(x: Float, y: Float)  # Value type

class Shape(center: Point)  # Reference type with value field
    var offset: Point       # Value field in reference type
    
fun main()
    val p = Point(0.0, 0.0)
    val s = new Shape(p)    # Point is copied into Shape
    
    # p and s.center are independent copies
    
    val arr = new Array<Point>(10)  # Array (reference) of Points (values)
    arr[0] = Point(1.0, 2.0)        # Point copied into array
    
    free s
    free arr
    # p is automatically freed at end of scope
```

### Struct in Struct (Nested Value Types)

```fpl
struct Point(x: Float, y: Float)
struct Line(start: Point, end: Point)

fun main()
    val line = Line(Point(0.0, 0.0), Point(1.0, 1.0))
    # All allocated together, all freed together automatically
```

### Classes with Value Type Fields

```fpl
struct Velocity(dx: Float, dy: Float)

class Particle(var pos: Point, var vel: Velocity)
    fun update()
        pos.x = pos.x + vel.dx
        pos.y = pos.y + vel.dy

fun main()
    val particle = new Particle(
        Point(0.0, 0.0),
        Velocity(1.0, 0.5)
    )
    particle.update()
    
    free particle  # Only the Particle needs freeing
    # Point and Velocity are inline and freed automatically
```

### Performance Considerations

**Value types (structs) are better for:**
- Small data (a few fields)
- Frequent local variables
- Avoiding heap allocation overhead
- Cache-friendly data layout

**Reference types (classes) are better for:**
- Large data structures
- Data that needs to be shared
- Polymorphic behavior
- When you need precise control over lifetime

### Common Pitfalls

**Dangling references** with reference types:
```fpl
var globalCat: Cat

fun makeCat()
    val cat = new Cat("Local")
    globalCat = cat
    free cat  # Oops! globalCat now points to freed memory
```

**Unexpected copies** with value types:
```fpl
struct LargeStruct(a: Int, b: Int, c: Int, ... )  # Many fields

fun process(s: LargeStruct)  # Copies entire struct!
    # ...
```

For large structs passed to functions frequently, consider using a reference type instead, or be aware of the copy cost.

---

## Path-Dependent Typing

FPL's type system uses **path-dependent typing** (also known as flow-sensitive typing) to track type refinements through control flow. After type checks or null checks, the compiler narrows the type of a variable for the subsequent code path.

### Nullable Type Refinement

After checking for `null`, the compiler knows the value is non-null:

```fpl
fun printLength(s: String?)
    if s = null
        print("String is null")
        return
    
    # After the null check, s is known to be String (not String?)
    print(s.length)  # OK: s is refined to non-null String
```

The type refinement is path-dependent - it only applies to the code path where the check succeeded:

```fpl
fun example(s: String?)
    if s != null
        print(s.length)  # OK: s is String here
    else
        print(s.length)  # ERROR: s is still String? here
```

### Error Type Refinement

Similar refinement occurs with error-returning functions:

```fpl
enum FileError(desc: String) [
    NOT_FOUND("File not found"),
    PERMISSION_DENIED("Permission denied")
]

fun openFile(name: String) -> Int!
    # ... implementation

fun main()
    val result = openFile("data.txt")
    
    if result is FileError
        # result is refined to FileError here
        print("Error: ")
        print(result.desc)  # Can access desc field
        return
    
    # result is refined to Int here (success case)
    print("File handle: ")
    print(result)  # result is known to be Int
```

### Subtype Refinement

After an `is` check for a subclass, the compiler refines the type:

```fpl
class Animal(val name: String)
class Dog(name: String, val breed: String) : Animal(name)
class Cat(name: String, val indoor: Bool) : Animal(name)

fun describe(animal: Animal)
    if animal is Dog
        # animal is refined to Dog here
        print(animal.name)
        print(" is a ")
        print(animal.breed)  # Can access Dog-specific field
    elsif animal is Cat
        # animal is refined to Cat here
        print(animal.name)
        if animal.indoor
            print(" is an indoor cat")
        else
            print(" is an outdoor cat")
    else
        # animal remains Animal here
        print(animal.name)
```

### Multiple Checks

Type refinements work with multiple checks:

```fpl
fun process(result: Int!)
    if result is Error
        # Early return eliminates Error from remaining paths
        print("Failed with error")
        return
    
    # From here on, result is known to be Int
    val doubled = result * 2
    print(doubled)
```

### Scope of Refinement

Type refinement is scoped to the block where the check occurred:

```fpl
fun example(s: String?)
    if s != null
        print(s.length)  # OK: s is String
        
        if someCondition
            print(s.length)  # Still OK: refinement carries through nested blocks
        
    # Outside the if block, s reverts to String?
    print(s.length)  # ERROR: s is String? again
```

### Limitations

Type refinement does **not** track changes through reassignment:

```fpl
fun example(s: String?)
    if s != null
        print(s.length)  # OK: s is String
        
        s = getSomeOtherString()  # s is reassigned
        
        print(s.length)  # ERROR: s is String? again after reassignment
```

Type refinement also doesn't work across function boundaries:

```fpl
var globalString: String?

fun example()
    if globalString != null
        helper()
        # Cannot assume globalString is still non-null
        # because helper() might have modified it
        print(globalString.length)  # ERROR: still String?
```

### The `try` Keyword

FPL provides the `try` keyword (borrowed from Zig) for concise error handling with errable types. A `try` expression evaluates its argument and:
- If the result is an `Error`, immediately returns that error from the containing function
- If the result is the success value, unwraps it and continues execution

The compiler enforces that the containing function has a compatible error return type (`!`).

**Basic usage:**

```fpl
enum Error(desc: String) [
    FILE_NOT_FOUND("File not found")
]

fun open(filename: String) -> Int!
    if filename = "data.txt"
        return 42  # Success: file handle
    else
        return Error.FILE_NOT_FOUND

fun processFile(filename: String) -> Int!
    val handle = try open(filename)  # Early return if error
    
    # If we reach here, handle is Int (not Int!)
    print("File opened with handle = ")
    print(handle)
    return handle * 2
```

If `open()` returns an error, `try` immediately returns that error from `processFile()`. Otherwise, `handle` is bound to the success value (an `Int`).

**Multiple `try` expressions:**

```fpl
fun processFiles() -> Int!
    val h1 = try openFile("data.txt")
    val h2 = try openFile("config.txt")
    val h3 = try openFile("log.txt")
    
    # If any openFile fails, the error is returned immediately
    # Otherwise, all three handles are available here
    return h1 + h2 + h3
```

**Type checking:**

The compiler ensures the function signature is compatible:

```fpl
fun process(filename: String) -> Int!  # OK: returns Int!
    val handle = try open(filename)
    return handle

fun invalid(filename: String) -> Int  # ERROR: try requires Int! return type
    val handle = try open(filename)  # Can't use try here
    return handle
```

**Comparison with explicit checking:**

Without `try`:
```fpl
fun processFile(filename: String) -> Int!
    val result = open(filename)
    if result is Error
        return result  # Manual error propagation
    
    # result is refined to Int here
    print("Handle: ")
    print(result)
    return result * 2
```

With `try`:
```fpl
fun processFile(filename: String) -> Int!
    val handle = try open(filename)  # Concise error propagation
    
    print("Handle: ")
    print(handle)
    return handle * 2
```

The `try` keyword makes error propagation more concise while maintaining type safety and explicit error handling.

### Best Practices

**Use early returns** to simplify control flow:

```fpl
fun process(value: Int?)
    if value = null
        return  # Early return
    
    # Value is non-null for rest of function
    print(value * 2)
    print(value + 10)
```

**Use `try` for error propagation**:

```fpl
fun loadConfig() -> Config!
    val handle = try openFile("config.txt")  # Concise!
    val data = try readFile(handle)
    val config = try parseConfig(data)
    return config
```

**Use explicit `is` checks when you need to handle errors locally**:

```fpl
fun processFile(filename: String)
    val result = openFile(filename)
    if result is Error
        print("Warning: Could not open ")
        print(filename)
        print(" - ")
        print(result.desc)
        return  # Handle error locally
    
    # Process the file with result (now known to be Int)
    processHandle(result)
```

**Use `is` for type-safe polymorphism**:

```fpl
fun processAnimals(animals: Array<Animal>)
    for animal in animals
        if animal is Dog
            walkDog(animal)  # animal refined to Dog
        elsif animal is Cat
            feedCat(animal)  # animal refined to Cat
```

Path-dependent typing, combined with the `try` keyword, makes null-safety and error handling more ergonomic while maintaining type safety, eliminating entire classes of runtime errors at compile time.

---

## Duck Typing for Array-Like Classes

FPL supports duck typing to allow classes to behave like arrays. If a class implements specific methods and fields, it can be used with array syntax and in for loops without explicitly implementing an array interface.

### Array Access Syntax

If a class has a method named **`get`**, it can be accessed using array subscript syntax:

```fpl
class List
    var items = new Array<Int>(10)
    
    fun get(index: Int) -> Int
        return items[index]

fun main()
    val lst = new List()
    # Can use array syntax because List has a get method
    val value = lst[0]  # Calls lst.get(0)
    print(value)
```

Similarly, if a class has a **`set`** method, array assignment syntax works:

```fpl
class List
    var items = new Array<Int>(10)
    
    fun get(index: Int) -> Int
        return items[index]
    
    fun set(index: Int, value: Int)
        items[index] = value

fun main()
    val lst = new List()
    lst[0] = 42  # Calls lst.set(0, 42)
    print(lst[0])  # Calls lst.get(0)
```

### Iteration with For Loops

If a class has both:
- A method named **`get`** that takes an integer index
- A field named **`length`**

Then it can be iterated over with a for loop:

```fpl
extern fun print(i: Int)
extern fun print(s: String)

class List
    var length = 0
    var items = new Array<Int>(4)
    
    fun get(index: Int) -> Int
        return items[index]
    
    fun add(value: Int)
        if items.length = length
            # Grow array if needed
            val oldItems = items
            items = new Array<Int>(oldItems.length * 2)
            for index in 0..<oldItems.length
                items[index] = oldItems[index]
            free(oldItems)
        items[length] = value
        length = length + 1
    
    fun set(index: Int, value: Int)
        items[index] = value

fun main()
    val lst = new List()
    lst.add(10)
    lst.add(20)
    lst.add(30)
    
    # Can use array subscript syntax
    print(lst[1])  # Prints 20
    print("\n")
    
    # Can iterate because List has get() and length
    for item in lst
        print(item)  # Prints 10, 20, 30
        print("\n")
```

### How It Works

When the compiler sees:
- `obj[index]` → it looks for a `get(Int)` method on `obj`
- `obj[index] = value` → it looks for a `set(Int, T)` method on `obj`
- `for item in obj` → it looks for a `get(Int)` method and a `length` field

This duck typing approach provides flexibility without requiring classes to inherit from a specific interface or base class. Any class that has the right shape can be used as an array-like object.

### Requirements

To work as an array-like class:
- **`get`** method must take exactly one `Int` parameter and return a value
- **`set`** method (optional) must take two parameters: `Int` (index) and the value type
- **`length`** field must be an `Int` for iteration support

The compiler performs these checks at compile time, so type safety is maintained.

### Example: Custom String Buffer

```fpl
class StringBuilder
    var length = 0
    var buffer = new Array<Char>(16)
    
    fun get(index: Int) -> Char
        return buffer[index]
    
    fun append(c: Char)
        if length = buffer.length
            # Grow buffer
            val old = buffer
            buffer = new Array<Char>(old.length * 2)
            for i in 0..<old.length
                buffer[i] = old[i]
            free(old)
        buffer[length] = c
        length = length + 1

fun main()
    val sb = new StringBuilder()
    sb.append('H')
    sb.append('i')
    
    # Can access like an array
    print(sb[0])  # 'H'
    print(sb[1])  # 'i'
    
    # Can iterate over characters
    for ch in sb
        print(ch)
```

This duck typing feature makes FPL more expressive while maintaining static type safety and zero runtime overhead—the method calls are resolved at compile time.
---

## Error Handling

FPL uses a Result-style error handling with enums:

### Error Return Types
Use `!` suffix to indicate a function may return an error:
```fpl
fun openFile(name: String) -> Int!
    if name = "missing.txt"
        return Error.FILE_NOT_FOUND
    else
        return 42  # File handle
```

### Checking for Errors
```fpl
val handle = openFile("data.txt")
if handle is Error
    print("Error: ")
    print(handle.desc)
else
    print("Success! Handle = ")
    print(handle)
```

### Multiple Error Types
```fpl
enum Error(desc: String) [
    FILE_NOT_FOUND("File not found"),
    PERMISSION_DENIED("Permission denied")
]

fun openFile(name: String, user: String) -> Int!
    if name = "secret.txt"
        return Error.PERMISSION_DENIED
    elsif name = "missing.txt"
        return Error.FILE_NOT_FOUND
    else
        return 42
```

### The `try` Keyword for Error Propagation

For concise error handling, use the `try` keyword to automatically propagate errors:

```fpl
fun processFile(filename: String) -> Int!
    val handle = try openFile(filename)  # Returns error if openFile fails
    val data = try readFile(handle)       # Otherwise continues with success value
    return data
```

See [Path-Dependent Typing](#path-dependent-typing) for a detailed explanation of `try` and type refinement.

---

## Tuples

### Tuple Creation
```fpl
val t = (1, "Hello")
val triple = (42, "answer", true)
```

### Tuple Type Annotation
```fpl
val t: (Int, String) = (1, "x")
```

### Tuple Indexing
```fpl
val t = (1, "x")
print(t.0)  # Prints 1
print(t.1)  # Prints "x"
```

### Tuple Destructuring
```fpl
val t = (42, "answer", true)
val (i, s, b) = t
print(i)  # 42
print(s)  # "answer"
print(b)  # true
```

### Destructuring Assignment
```fpl
var a: Int
var b: String
(a, b) = (1, "x")
```

### Function Return Tuples
```fpl
fun makeTuple() -> (Int, String, Bool)
    return (42, "answer", true)

fun main()
    val (i, s, b) = makeTuple()
```

---

## Arrays

### Array Creation
```fpl
val arr = new Array<Int>(10)
```

### Array Initialization
```fpl
val arr = new [1, 2, 3, 4, 5]
```

### Array with Lambda Initializer
```fpl
val arr = new Array<Int>(5) { it * 2 }
# Creates [0, 2, 4, 6, 8]
```

### Array Access
```fpl
arr[0] = 42
val x = arr[0]
```

### Array Length
```fpl
val len = arr.length
```

### Array Iteration
```fpl
for item in arr
    print(item)

for i in 0..<arr.length
    print(arr[i])
```

### Literal Array Syntax
```fpl
val points = new Array<Point3d>[
    Point3d(1, 2, 3),
    Point3d(4, 5, 6),
    Point3d(7, 8, 9)
]
```

---


## Varargs

Functions can accept variable numbers of arguments:

### Basic Varargs
```fpl
fun printAll(vararg args: String)
    for a in args
        print(a)
        print(" ")
    print("\n")

fun main()
    printAll("Hello", "world!")
    printAll("One", "Two", "Three", "Four")
```

### Mixed Parameters
```fpl
fun log(level: Int, vararg messages: String)
    print(level)
    for msg in messages
        print(" ")
        print(msg)
    print("\n")

fun main()
    log(1)
    log(2, "Hello")
    log(3, "Multiple", "messages")
```

### Empty Varargs
```fpl
printAll()  # Valid, args will be empty array
```

### Varargs with Expressions
```fpl
fun sum(vararg nums: Int) -> Int
    var total = 0
    for n in nums
        total = total + n
    return total

print(sum(1, 2, 3))
print(sum(4 + 1, 2 * 2, 3))
```

---

## Memory Management

FPL uses manual memory management:

### Allocating Memory
```fpl
val obj = new MyClass(args)
val arr = new Array<Int>(100)
```

### Freeing Memory
```fpl
free obj
free arr
```

### Destructors

Classes can define a **destructor** by implementing a method named `free` with no parameters. This method will be automatically called when an object is freed, allowing cleanup of resources:

```fpl
extern fun print(s: String)

class File(val name: String)
    var handle: Int = 0
    
    fun open()
        # Open file and store handle
        handle = 42
    
    fun free()
        print("Closing file: ")
        print(name)
        print("\n")
        # Perform cleanup operations here

fun main()
    val f = new File("data.txt")
    f.open()
    free f  # Automatically calls f.free() before deallocating
```

**Key points about destructors:**
- The destructor method must be named `free` exactly
- It must take no parameters
- It is called automatically when `free` is used on the object
- Use destructors to release resources like file handles, network connections, etc.
- Destructors run before the object's memory is deallocated

**Example with cleanup:**

```fpl
class Cat(val name: String, val age: Int)
    fun free()
        print("Freeing cat ")
        print(name)
        print("\n")

fun main()
    val c = new Cat("Mittens", 3)
    print(c.name)
    print("\n")
    free c  # Calls c.free(), then deallocates memory
```

Output:
```
Mittens
Freeing cat Mittens
```

### Stack Allocation with InlineArrays

InlineArrays and structs are value types that are automatically allocated on the stack or inline within their containing structure. They don't require `new` or `free`:

```fpl
fun processData()
    val buffer = InlineArray<Char>(16)
    # Automatically allocated on stack
    # Automatically freed when function returns
    
    val point = Point(1.0, 2.0)
    # Struct also stack-allocated automatically
```

Unlike heap-allocated objects (classes and Arrays), value types:
- Are created without the `new` keyword
- Don't need to be freed
- Are automatically cleaned up when they go out of scope
- Have their lifetimes tracked by the compiler

### Abort
Terminate the program with an error code:
```fpl
const ABORT_INVALID_ARGUMENT = 1

if invalid
    abort ABORT_INVALID_ARGUMENT
```

---

## Standard Library

### Built-in Functions
```fpl
extern fun print(s: String)
extern fun print(i: Int)
extern fun print(c: Char)
extern fun print(b: Bool)
extern fun print(f: Float)  # Requires printFloat.fpl
```

### List Class (stdlib/list.fpl)
```fpl
class List<T>
    fun add(item: T)
    fun get(index: Int) -> T
    fun set(index: Int, item: T)
    fun isEmpty() -> Bool
    fun isNotEmpty() -> Bool
    fun take() -> T
    fun clear()
    fun removeAt(index: Int) -> T
    fun remove(item: T) -> Bool
    fun indexOf(item: T) -> Int
    fun addAt(index: Int, item: T)
    fun contains(item: T) -> Bool
    fun last() -> T?
    fun first() -> T?

# Usage
val list = new List<String>()
list.add("Hello")
list.add("World")
for s in list
    print(s)
```

### Constants
```fpl
const ABORT_INVALID_ARGUMENT = 1
```

---

## Complete Examples

### Hello World
```fpl
extern fun print(s: String)

fun main()
    print("Hello, World!")
```

### Fibonacci Sequence
```fpl
extern fun print(i: Int)

fun fibonacci(n: Int) -> Int
    if n <= 1
        return n
    else
        return fibonacci(n - 1) + fibonacci(n - 2)

fun main()
    for i in 0..10
        print(fibonacci(i))
        print(" ")
```

### Struct with Methods via Functions
```fpl
extern fun print(s: String)

struct Point(x: Float, y: Float)

fun distance(p: Point) -> Float
    return (p.x * p.x + p.y * p.y) as Float

fun main()
    val p = Point(3.0, 4.0)
    print(distance(p))  # 25.0
```

### Generic List Processing
```fpl
extern fun print(i: Int)

fun sum(list: List<Int>) -> Int
    var total = 0
    for item in list
        total += item
    return total

fun main()
    val numbers = new List<Int>()
    numbers.add(1)
    numbers.add(2)
    numbers.add(3)
    numbers.add(4)
    numbers.add(5)
    print(sum(numbers))  # 15
```

### Error Handling Example
```fpl
extern fun print(s: String)
extern fun print(i: Int)

enum FileError(desc: String) [
    NOT_FOUND("File not found"),
    PERMISSION_DENIED("Permission denied")
]

fun openFile(name: String) -> Int!
    if name = "secret.txt"
        return FileError.PERMISSION_DENIED
    elsif name = "missing.txt"
        return FileError.NOT_FOUND
    else
        return 42  # Success

fun main()
    val result = openFile("data.txt")
    if result is FileError
        print("Error: ")
        print(result.desc)
    else
        print("File opened with handle: ")
        print(result)
```

---

## Block Notation Style Guide

FPL supports two styles for block notation:

### Indentation-Only (Short Blocks)
Use for simple, short blocks:
```fpl
fun abs(a: Int) -> Int
    if a > 0
        return a
    else
        return -a
```

### Explicit End Keywords (Long Blocks)
Use for complex or nested blocks:
```fpl
fun processData(arr: Array<Int>) -> Int
    var sum = 0
    for i in arr
        if i > 0
            sum += i
        elsif i < 0
            sum -= i
        end if
    end for
    return sum
end fun
```

Both styles are valid; choose based on code complexity and readability.

---

## Conclusion

The Falcon Programming Language combines low-level control with high-level expressiveness, making it suitable for systems programming while maintaining modern language features. Its flexible syntax, static typing, and comprehensive feature set enable both safe and efficient code.

For more examples, see the test suite in the `test/` directory.

