import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

// This class structure describes the Type Checked Tree (TCT)

// Define TCT nodes
sealed class Tct (val location: Location)

// Expression nodes
sealed class TctExpr(location: Location, val type:Type) : Tct(location)
class TctConstant(location: Location, val value: Value) : TctExpr(location, value.type)
class TctVariable(location: Location, val sym:VarSymbol, type:Type) : TctExpr(location, type)
class TctFunctionName(location: Location, val sym: FunctionSymbol) : TctExpr(location, TypeNothing)
class TctTypeName(location: Location, val sym: TypeNameSymbol) : TctExpr(location, sym.type)
class TctBinaryExpr(location: Location, val op: BinOp, val lhs: TctExpr, val rhs: TctExpr, type:Type) : TctExpr(location, type)
class TctReturnExpr(location: Location, val expr: TctExpr?) : TctExpr(location, TypeNothing)
class TctCallExpr(location: Location, val func:Function, val args: List<TctExpr>) : TctExpr(location, func.returnType)

class TctErrorExpr(location: Location, val message: String = "") : TctExpr(location, TypeNothing) {
    init {
        if (message != "")
            Log.error(location, message)
    }
}

// Statement nodes
sealed class TctStmt(location: Location) : Tct(location)
class TctVarDeclStmt(location: Location, val sym:VarSymbol, val initializer: TctExpr?) : TctStmt(location)
class TctEmptyStmt(location: Location) : TctStmt(location)
class TctExpressionStmt(location: Location, val expr: TctExpr) : TctStmt(location)
class TctAssignStmt(location: Location, val op:TokenKind, val lhs: TctExpr, val rhs: TctExpr) : TctStmt(location)

// Statement Block nodes
sealed class TctBlock(location: Location, val body:List<TctStmt>) : TctStmt(location)
class TctFunctionDefStmt(location: Location, val name: String, val function:Function, body: List<TctStmt>) : TctBlock(location, body)
class TctFile(location: Location, body: List<TctStmt>) : TctBlock(location, body)
class TctTop(location: Location, val function:Function, body: List<TctStmt>) : TctBlock(location, body)
class TctWhileStmt(location: Location, val condition: TctExpr, body: List<TctStmt>) : TctBlock(location, body)
class TctIfClause(location:Location, val condition:TctExpr?, body: List<TctStmt>) : TctBlock(location, body)
class TctIfStmt(location:Location, body:List<TctIfClause>) : TctBlock(location, body)
class TctRepeatStmt(location: Location, val condition: TctExpr, body: List<TctStmt>) : TctBlock(location, body)

// Type nodes


fun Tct.dump(sb:StringBuilder, indent: Int) {

    val klass: KClass<out Any> = this::class

    sb.append(". ".repeat(indent))
    sb.append(klass.simpleName)
    val props = klass.memberProperties
    val children = mutableListOf<Tct>()
    for (prop in props.sortedBy { it.name }) {
        val name = prop.name
        when(val v = prop.getter.call(this)) {
            is Tct -> children.add(v)
            is Location -> {} // Ignore locations
            is List<*> -> v.filterIsInstance<Tct>().forEach { children.add(it) }
            else -> sb.append(" $name=$v")
        }
    }
    sb.append("\n")
    for(child in children)
        child.dump(sb, indent + 1)
}

fun Tct.dump() : String {
    val sb = StringBuilder()
    dump(sb, 0)
    return sb.toString()
}