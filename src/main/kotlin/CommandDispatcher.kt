package top.stellortus.command_parser

class CommandDispatcher(val root: RootNode, private val variables: Map<String, Any?> = emptyMap()) {

    /** Returns a dispatcher with [variables] additionally bound; this instance is left untouched. */
    fun bind(vararg variables: Pair<String, Any?>): CommandDispatcher =
        CommandDispatcher(root, this.variables + variables.toList())

    private fun execute(tokens: List<String>, context: CommandContext): Any {
        val match = resolve(root, tokens, 0, context, emptyList())
            ?: throw IllegalArgumentException("Unknown command: ${tokens.joinToString(" ")}")

        context.setScopes(match.path.dropLast(1))
        return match.node.execute?.invoke(context)
            ?: throw IllegalStateException("Command is not executable: ${tokens.joinToString(" ")}")
    }

    private fun resolve(
        node: CommandNode,
        tokens: List<String>,
        step: Int,
        context: CommandContext,
        path: List<String>,
    ): Match? {
        if (step >= tokens.size) return Match(node, path)

        val token = tokens[step]
        for (child in node.nextNodes.sortedBy { it.priority }) {
            val childPath = path + child.name
            when (child) {
                is LiteralNode -> if (child.name == token) {
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

                is ChoiceNode -> if (token in child.names) {
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
    private fun advance(context: CommandContext, key: String, value: Any?, descend: () -> Match?): Match? {
        val had = context.has(key)
        val previous = context.peek(key)
        context.put(key, value)
        val match = descend()
        if (match == null) {
            if (had) context.put(key, previous) else context.remove(key)
        }
        return match
    }

    private class Match(val node: CommandNode, val path: List<String>)

    fun execute(command: String): Any {
        val context = CommandContext()
        context.putAll(variables)
        return execute(command.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }, context)
    }

}

fun buildCommand(block: RootNode.() -> Unit): CommandDispatcher {
    val root = RootNode()
    with(root) {
        block()
    }
    return CommandDispatcher(root)
}
