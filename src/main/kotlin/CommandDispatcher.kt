package top.stellortus.command_parser

class CommandDispatcher<T : CommandContext> internal constructor(
    val root: RootNode<T>,
    private val variables: Map<String, Any?>,
    /** Null when nobody has supplied a context yet, i.e. `withContext(...)` is still required. */
    private val contextFactory: (() -> T)?,
) {

    fun bind(vararg variables: Pair<String, Any?>): CommandDispatcher<T> =
        CommandDispatcher(root, this.variables + variables.toList(), contextFactory)

    fun withContext(context: T): CommandDispatcher<T> =
        CommandDispatcher(root, variables) { context }

    private fun execute(tokens: List<String>, context: T): Any {
        val match = resolve(root, tokens, 0, context, emptyList())
            ?: throw IllegalArgumentException("Unknown command: ${tokens.joinToString(" ")}")

        context.setScopes(match.path.dropLast(1))
        return match.node.execute?.invoke(context)
            ?: throw IllegalStateException("Command is not executable: ${tokens.joinToString(" ")}")
    }

    private fun resolve(
        node: CommandNode<T>,
        tokens: List<String>,
        step: Int,
        context: T,
        path: List<String>,
    ): Match<T>? {
        if (step >= tokens.size) return Match(node, path)

        val token = tokens[step]
        for (child in node.nextNodes.sortedBy { it.priority }) {
            val childPath = path + child.name
            when (child) {
                is LiteralNode<*> -> if (child.name == token) {
                    resolve(child, tokens, step + 1, context, childPath)?.let { return it }
                }

                is ArgumentNode<*> -> {
                    val end = if (child.argument.greedy) tokens.size else step + 1
                    val label = tokens.subList(step, end).joinToString(" ")
                    if (child.argument.match(label)) {
                        advance(context, childPath.joinToString("/"), child.argument.parse(label)) {
                            resolve(child, tokens, end, context, childPath)
                        }?.let { return it }
                    }
                }

                is ChoiceNode<*> -> if (token in child.names) {
                    advance(context, childPath.joinToString("/"), token) {
                        resolve(child, tokens, step + 1, context, childPath)
                    }?.let { return it }
                }

                else -> {}
            }
        }
        return null
    }

    /**
     * Binds [key] while [descend] runs. A successful match keeps the binding (the executing block
     * needs it); backtracking restores the previous value instead of dropping it.
     */
    private fun advance(context: T, key: String, value: Any?, descend: () -> Match<T>?): Match<T>? {
        val had = context.has(key)
        val previous = context.peek(key)
        context.put(key, value)
        val match = descend()
        if (match == null) {
            if (had) context.put(key, previous) else context.remove(key)
        }
        return match
    }

    private class Match<T : CommandContext>(val node: CommandNode<T>, val path: List<String>)

    fun execute(command: String): Any {
        val context = contextFactory?.invoke()
            ?: throw IllegalStateException("No context set: call withContext(...) before execute(...)")

        context.clear()
        context.putAll(variables)
        return execute(command.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }, context)
    }

}

/** A plain [CommandContext] needs no arguments, so this form is directly executable. */
@JvmName("buildCommandOf")
fun buildCommand(block: RootNode<CommandContext>.() -> Unit): CommandDispatcher<CommandContext> {
    val root = RootNode<CommandContext>()
    root.block()
    return CommandDispatcher(root, emptyMap()) { CommandContext() }
}

/** [T] has to be constructed by the caller, so `withContext(...)` is required before executing. */
fun <T : CommandContext> buildCommand(block: RootNode<T>.() -> Unit): CommandDispatcher<T> {
    val root = RootNode<T>()
    root.block()
    return CommandDispatcher(root, emptyMap(), null)
}
