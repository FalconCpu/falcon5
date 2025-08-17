import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

// Define AST nodes
sealed class Ast (val location: Location)

// Expression nodes
sealed class AstExpr(location: Location) : Ast(location)
class AstIntLiteral(location: Location, val value: Int) : AstExpr(location)
class AstCharLiteral(location: Location, val value: Int) : AstExpr(location)
class AstStringLiteral(location: Location, val value: String) : AstExpr(location)
class AstIdentifier(location: Location, val name: String) : AstExpr(location)
class AstBinaryExpr(location: Location, val op: TokenKind, val lhs: AstExpr, val rhs: AstExpr) : AstExpr(location)
class AstAndExpr(location: Location, val lhs: AstExpr, val rhs: AstExpr) : AstExpr(location)
class AstOrExpr(location: Location, val lhs: AstExpr, val rhs: AstExpr) : AstExpr(location)
class AstNotExpr(location: Location, val expr: AstExpr) : AstExpr(location)
class AstReturnExpr(location: Location, val expr: AstExpr?) : AstExpr(location)
class AstContinueExpr(location: Location) : AstExpr(location)
class AstBreakExpr(location: Location) : AstExpr(location)
class AstCallExpr(location: Location, val func: AstExpr, val args: List<AstExpr>) : AstExpr(location)
class AstIndexExpr(location: Location, val array: AstExpr, val index: AstExpr) : AstExpr(location)
class AstMemberExpr(location: Location, val objectExpr: AstExpr, val memberName:String) : AstExpr(location)
class AstNewExpr(location: Location, val elementType: AstType, val args:List<AstExpr>, val lambda:AstLambdaExpr?, val arena:Arena) : AstExpr(location)
class AstArrayLiteralExpr(location: Location, val elementType: AstType?, val args:List<AstExpr>, val arena:Arena) : AstExpr(location)
class AstNegateExpr(location: Location, val expr: AstExpr) : AstExpr(location)
class AstRangeExpr(location: Location, val start: AstExpr, val end: AstExpr, val op:TokenKind) : AstExpr(location)

// Statement nodes
sealed class AstStmt(location: Location) : Ast(location)
class AstVarDeclStmt(location: Location, val name: String, val astType:AstType?, val initializer: AstExpr?, val mutable:Boolean) : AstStmt(location)
class AstEmptyStmt(location: Location) : AstStmt(location)
class AstExpressionStmt(location: Location, val expr: AstExpr) : AstStmt(location)
class AstAssignStmt(location: Location, val op:TokenKind, val lhs: AstExpr, val rhs: AstExpr) : AstStmt(location)

// Statement Block nodes
sealed class AstBlock(location: Location, val body:List<AstStmt>) : AstStmt(location) {
    var parent: AstBlock? = null
    val symbols = mutableMapOf<String, Symbol>()
}
class AstFunctionDefStmt(
    location: Location,
    val name: String,
    val params: List<AstParameter>,
    val retType: AstType?,
    body: List<AstStmt>,
    val isExtern: Boolean
) : AstBlock(location, body) {
    lateinit var function : Function
}

class AstWhileStmt(location: Location, val condition: AstExpr, body: List<AstStmt>) : AstBlock(location, body)
class AstRepeatStmt(location: Location, val condition: AstExpr, body: List<AstStmt>) : AstBlock(location, body)
class AstIfClause(location:Location, val condition:AstExpr?, body: List<AstStmt>) : AstBlock(location, body)
class AstIfStmt(location:Location, body:List<AstIfClause>) : AstBlock(location, body)
class AstLambdaExpr(location: Location, body: List<AstStmt>) : AstBlock(location, body)
class AstForStmt(location: Location, val indexName: String, val indexType: AstType?, val range: AstExpr, body: List<AstStmt>) : AstBlock(location, body)

class AstFile(location: Location, body: List<AstStmt>) : AstBlock(location, body)
class AstTop(location: Location, body: List<AstStmt>) : AstBlock(location, body)

// Type nodes
sealed class AstType(location:Location) : Ast(location)
class AstArrayType(location: Location, val elementType: AstType) : AstType(location)
class AstTypeIdentifier(location: Location, val name: String) : AstType(location)

// Other nodes
class AstParameter(location: Location, val name: String, val type: AstType) : Ast(location)

enum class Arena {
    STACK, HEAP, CONST
}



// Dump AST

fun Ast.dump(sb:StringBuilder, indent: Int) {

    val klass: KClass<out Any> = this::class

    sb.append(". ".repeat(indent))
    sb.append(klass.simpleName)
    val props = klass.memberProperties
    val children = mutableListOf<Ast>()
    for (prop in props.sortedBy { it.name }) {
        val name = prop.name
        if (name=="function") continue
        when(val v = prop.getter.call(this)) {
            is Ast -> children.add(v)
            is Location -> {} // Ignore locations
            is List<*> -> v.filterIsInstance<Ast>().forEach { children.add(it) }
            else -> sb.append(" $name=$v")
        }
    }
    sb.append("\n")
    for(child in children)
        child.dump(sb, indent + 1)
}

fun Ast.dump() : String {
    val sb = StringBuilder()
    dump(sb, 0)
    return sb.toString()
}

fun AstBlock.findEnclosingFunction(): Function? {
    var current: AstBlock? = this
    while (current!= null) {
        if (current is AstFunctionDefStmt) return current.function
        current = current.parent
    }
    return null
}