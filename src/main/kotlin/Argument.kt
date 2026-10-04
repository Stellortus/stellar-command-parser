package top.stellortus.command_parser

abstract class Argument {
    open val greedy: Boolean get() = false

    /** Match order across sibling arguments: lower is tried first. */
    abstract val priority: Int

    abstract fun parse(label: String): Any?

    open fun match(label: String): Boolean = parse(label) != null

    abstract val type: String
}

object IntArgument : Argument() {
    override val priority: Int = 1
    override fun parse(label: String): Any? = label.toIntOrNull()
    override val type: String = "Int"
}

object StringArgument : Argument() {
    override val priority: Int = 2
    override fun parse(label: String): Any = label
    override val type: String = "String"
}

object GreedyArgument : Argument() {
    override val greedy: Boolean get() = true
    override val priority: Int = 3
    override fun parse(label: String): Any = label
    override val type: String = "String..."
}
