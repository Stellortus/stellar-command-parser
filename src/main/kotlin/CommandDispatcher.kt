package top.stellortus.command_parser


class CommandDispatcher(val root: RootNode) {
    private fun execute(tokens: List<String>): Any {
        val context = CommandContext()
        val node = resolve(root, tokens, 0, context)
            ?: throw IllegalArgumentException("Unknown command: ${tokens.joinToString(" ")}")

        return node.execute?.invoke(context)
            ?: throw IllegalStateException("Command is not executable: ${tokens.joinToString(" ")}")
    }

    private fun resolve(node: CommandNode, tokens: List<String>, step: Int, context: CommandContext): CommandNode? {
        if (step >= tokens.size) return node

        val token = tokens[step]
        for (child in node.nextNodes.sortedBy { it.priority }) {
            when (child) {
                is LiteralNode -> if (child.name == token) {
                    resolve(child, tokens, step + 1, context)?.let { return it }
                }

                is ArgumentNode<*> -> {
                    val end = if (child.argument.greedy) tokens.size else step + 1
                    val label = tokens.subList(step, end).joinToString(" ")
                    if (child.argument.match(label)) {
                        context.put(child.name, child.argument.parse(label))
                        resolve(child, tokens, end, context)?.let { return it }
                        context.remove(child.name)
                    }
                }

                is ChoiceNode -> if (token in child.names) {
                    context.put(child.variableName, token)
                    resolve(child, tokens, step + 1, context)?.let { return it }
                    context.remove(child.variableName)
                }

                else -> {}
            }
        }
        return null
    }

    fun execute(command: String): Any {
        return this.execute(command.trim().split(Regex("\\s+")).filter { it.isNotEmpty() })
    }

}

fun build(block: RootNode.() -> Unit): CommandDispatcher {
    val root = RootNode()
    with(root) {
        block()
    }
    return CommandDispatcher(root)
}
