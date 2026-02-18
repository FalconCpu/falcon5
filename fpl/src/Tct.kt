import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

// This class structure describes the Type Checked Tree (TCT)

// Define TCT nodes
sealed class Tct (val location: Location)

// Expression nodes
sealed class TctExpr(location: Location, val type:Type) : Tct(location)
class TctConstant(location: Location, val value: Value) : TctExpr(location, value.type)
class TctVariable(location: Location, val sym:VarSymbol, type:Type) : TctExpr(location, type)
class TctStackVariable(location: Location, val sym:StackVarSymbol, type:Type) : TctExpr(location, type)
class TctFunctionName(location: Location, val sym: FunctionSymbol, type:Type) : TctExpr(location, type)
class TctTypeName(location: Location, val sym: TypeNameSymbol) : TctExpr(location, sym.type)
class TctBinaryExpr(location: Location, val op: BinOp, val lhs: TctExpr, val rhs: TctExpr, type:Type) : TctExpr(location, type)
class TctIntCompareExpr(location: Location, val op: BinOp, val lhs: TctExpr, val rhs: TctExpr) : TctExpr(location, TypeBool)
class TctLongCompareExpr(location: Location, val op: BinOp, val lhs: TctExpr, val rhs: TctExpr) : TctExpr(location, TypeBool)
class TctStringCompareExpr(location: Location, val op: BinOp, val lhs: TctExpr, val rhs: TctExpr) : TctExpr(location, TypeBool)
class TctAndExpr(location: Location, val lhs: TctExpr, val rhs: TctExpr) : TctExpr(location, TypeBool)
class TctOrExpr(location: Location, val lhs: TctExpr, val rhs: TctExpr) : TctExpr(location, TypeBool)
class TctReturnExpr(location: Location, val expr: TctExpr?) : TctExpr(location, TypeNothing)
class TctContinueExpr(location: Location) : TctExpr(location, TypeNothing)
class TctBreakExpr(location: Location) : TctExpr(location, TypeNothing)
class TctCallExpr(location: Location, val thisArg:TctExpr?, val func:Function, val args: List<TctExpr>, type:Type) : TctExpr(location, type)
class TctIndexExpr(location: Location, val array: TctExpr, val index: TctExpr, type:Type) : TctExpr(location, type)
class TctMemberExpr(location: Location, val objectExpr: TctExpr, val member:FieldSymbol, type:Type) : TctExpr(location, type)
class TctNewArrayExpr(location: Location, val elementType: Type, val size:TctExpr, val arena: Arena, val lambda:TctLambdaExpr?, val clearMem:Boolean, type:Type) : TctExpr(location, type)
class TctNewInlineArrayExpr(location: Location, val lambda:TctLambdaExpr?, val initializers:List<TctExpr>?, type:Type) : TctExpr(location, type)
class TctNewArrayLiteralExpr(location: Location,val elements: List<TctExpr>,val arena: Arena,type:Type) : TctExpr(location, type)
class TctNegateExpr(location: Location, val expr: TctExpr) : TctExpr(location, expr.type)
class TctNotExpr(location: Location, val expr: TctExpr) : TctExpr(location, TypeBool)
class TctLambdaExpr(location: Location, val expr: TctExpr, val itSym:VarSymbol, type:Type) : TctExpr(location, type)
class TctRangeExpr(location: Location, val start: TctExpr, val end: TctExpr, val op: BinOp, type:Type) : TctExpr(location, type)
class TctNewClassExpr(location: Location, val klass: TypeClass, val args: List<TctExpr>, val arena: Arena, type:Type) : TctExpr(location, type)
class TctNullAssertExpr(location: Location, val expr: TctExpr, type:Type) : TctExpr(location, type)
class TctMethodRefExpr(location: Location, val objectExpr: TctExpr, val methodSym: FunctionSymbol, type:Type) : TctExpr(location, type)
class TctIsExpr(location: Location, val expr: TctExpr, val typeExpr: TypeClassInstance) : TctExpr(location, TypeBool)
class TctAsExpr(location: Location, val expr: TctExpr, typeExpr: Type) : TctExpr(location, typeExpr)
class TctEnumEntryExpr(location: Location, val expr:TctExpr, val field:FieldSymbol, type:Type) : TctExpr(location, type)
class TctMakeErrableExpr(location: Location, val expr:TctExpr, val typeIndex:Int, type:TypeErrable) : TctExpr(location, type)
class TctIsErrableTypeExpr(location: Location, val expr:TctExpr, val typeIndex:Int) : TctExpr(location, TypeBool)
class TctExtractCompoundExpr(location: Location, val expr:TctExpr, val index:Int, type:Type) : TctExpr(location, type)
class TctTryExpr(location: Location, val expr: TctExpr, type:Type) : TctExpr(location, type)
class TctVarargExpr(location: Location, val exprs: List<TctExpr>, type:Type) : TctExpr(location, type)
class TctMakeTupleExpr(location: Location, val elements: List<TctExpr>, type:TypeTuple) : TctExpr(location, type)
class TctAbortExpr(location: Location, val abortCode: TctExpr) : TctExpr(location, TypeNothing)
class TctGlobalVarExpr(location: Location, val sym: GlobalVarSymbol, type:Type) : TctExpr(location, type)
class TctIndirectCallExpr(location: Location, val func:TctExpr, val args:List<TctExpr>, type:Type) : TctExpr(location,type)
class TctIfExpr(location: Location, val cond:TctExpr, val trueExpr:TctExpr, val falseExpr:TctExpr, type:Type) : TctExpr(location,type)
class TctIntToRealExpr(location: Location, val expr:TctExpr) : TctExpr(location, TypeReal)
class TctRealToIntExpr(location: Location, val expr:TctExpr) : TctExpr(location, TypeInt)
class TctIntToLongExpr(location: Location, val expr:TctExpr) : TctExpr(location, TypeLong)
class TctLongToIntExpr(location: Location, val expr:TctExpr) : TctExpr(location, TypeInt)
class TctFpuExpr(location: Location, val op:FpuOp, val lhs:TctExpr, val rhs:TctExpr, type:Type) : TctExpr(location, type)
class TctLongExpr(location: Location, val op:BinOp, val lhs:TctExpr, val rhs:TctExpr, type:Type) : TctExpr(location, type)
class TctRealCompareExpr(location: Location, val op: BinOp, val lhs: TctExpr, val rhs: TctExpr) : TctExpr(location, TypeBool)
class TctMakeStructExpr(location: Location, type:TypeStruct, val fieldValues:List<TctExpr>) : TctExpr(location, type)

class TctErrorExpr(location: Location, val message: String = "") : TctExpr(location, TypeError) {
    init {
        if (message != "")
            Log.error(location, message)
    }
}

// Statement nodes
sealed class TctStmt(location: Location) : Tct(location)
class TctVarDeclStmt(location: Location, val sym:Symbol, val initializer: TctExpr?) : TctStmt(location)
class TctStructVarDeclStmt(location: Location, val sym:StackVarSymbol, val initializer:TctExpr?) : TctStmt(location)
class TctDestructuringDeclStmt(location: Location, val syms: List<VarSymbol>, val initializer: TctExpr) : TctStmt(location)
class TctEmptyStmt(location: Location) : TctStmt(location)
class TctExpressionStmt(location: Location, val expr: TctExpr) : TctStmt(location)
class TctAssignStmt(location: Location, val op:TokenKind, val lhs: TctExpr, val rhs: TctExpr) : TctStmt(location)
class TctAssignAggregateStmt(location: Location, val lhs: TctExpr, val rhs:TctExpr) : TctStmt(location)
class TctClassDefStmt(location: Location, val klass: TypeClass, val initializers: List<TctFieldInitializer>, val methods:List<TctFunctionDefStmt>) : TctStmt(location)
class TctFreeStmt(location: Location, val expr: TctExpr) : TctStmt(location)

// Statement Block nodes
sealed class TctBlock(location: Location, val body:List<TctStmt>) : TctStmt(location)
class TctFunctionDefStmt(location: Location, val name: String, val function:Function, body: List<TctStmt>) : TctBlock(location, body)
class TctFile(location: Location, body: List<TctStmt>) : TctBlock(location, body)
class TctTop(location: Location, val function:Function, body: List<TctStmt>) : TctBlock(location, body)
class TctWhileStmt(location: Location, val condition: TctExpr, body: List<TctStmt>) : TctBlock(location, body)
class TctIfClause(location:Location, val condition:TctExpr?, body: List<TctStmt>) : TctBlock(location, body)
class TctIfStmt(location:Location, body:List<TctIfClause>) : TctBlock(location, body)
class TctRepeatStmt(location: Location, val condition: TctExpr, body: List<TctStmt>) : TctBlock(location, body)
class TctForRangeStmt(location: Location, val index:VarSymbol, val range:TctRangeExpr, body: List<TctStmt>) : TctBlock(location, body)
class TctForArrayStmt(location: Location, val index:VarSymbol, val array:TctExpr, body: List<TctStmt>) : TctBlock(location, body)
class TctForIterableStmt(location: Location, val loopVar:VarSymbol, val iterable:TctExpr, val lengthSym:FieldSymbol, val getMethod:FunctionInstance, body: List<TctStmt>) : TctBlock(location, body)
class TctFieldInitializer(val field: FieldSymbol?, val value: TctExpr)
class TctWhenStmt(location: Location, val expr: TctExpr, body: List<TctWhenCase>) : TctBlock(location, body)
class TctWhenCase(location: Location, val matchExprs: List<TctExpr>, body: List<TctStmt>) : TctBlock(location, body)



fun TctExpr.isAlwaysTrue() : Boolean {
    return this is TctConstant && this.value is IntValue && this.value.value!=0
}

fun TctExpr.isAlwaysFalse() : Boolean {
    return this is TctConstant && this.value is IntValue && this.value.value==0
}


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