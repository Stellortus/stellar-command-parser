package top.stellortus.command_parser

typealias ExecuteBehaviour = CommandContext.() -> Any

abstract class CommandNode(val name: String) {
    val nextNodes = mutableSetOf<CommandNode>()
    var execute: ExecuteBehaviour? = null
    var description: String? = null

    open val priority: Int get() = 0

    private fun addNode(node: CommandNode) {
        nextNodes.add(node)
    }


    fun CommandNode.literal(name: String, block: CommandNode.() -> Unit) {
        val child = LiteralNode(name).apply(block)
        this.addNode(child)
    }

    private fun CommandNode.argument(name: String, argument: Argument, block: CommandNode.() -> Unit) {
        val child = ArgumentNode(name, argument).apply(block)
        this.addNode(child)
    }

    fun CommandNode.string(name: String, block: CommandNode.() -> Unit) = argument(name, StringArgument, block)

    fun CommandNode.integer(name: String, block: CommandNode.() -> Unit) = argument(name, IntArgument, block)

    fun CommandNode.greedy(name: String, block: CommandNode.() -> Unit) = argument(name, GreedyArgument, block)

    fun CommandNode.choice(variableName: String, vararg names: String, block: ChoiceNode.() -> Unit) {
        val child = ChoiceNode(variableName, names.toSet()).apply(block)
        this.addNode(child)
    }

    fun CommandNode.execute(block: ExecuteBehaviour) {
        this.execute = block
    }

    fun CommandNode.description(description: String) {
        this.description = description
    }
}


private fun CommandNode.displayName(): String = when (this) {
    is ArgumentNode<*> -> "<${name}:${argument.type}>"
    is ChoiceNode -> "<${names.joinToString("|")}>"
    else -> name
}

private fun helpLines(node: CommandNode, prefix: List<String> = emptyList()): String =
    node.nextNodes.joinToString("\n") { child ->
        val path = (prefix + child.displayName()).joinToString(" ")
        child.description?.let { "$path → $it" } ?: path
    }

private fun findNode(node: CommandNode, path: List<String>): CommandNode? {
    var current: CommandNode = node
    for (token in path) {
        current = current.nextNodes.firstOrNull {
            (it is LiteralNode && it.name == token) || (it is ChoiceNode && token in it.names)
        } ?: return null
    }
    return current
}


class RootNode : CommandNode("") {
    fun RootNode.setHelper(description: String = "显示此帮助页面", execute: (String) -> Any = { it }) {
        literal("help") {
            description(description)
            execute {
                return@execute execute.invoke(helpLines(this@RootNode))
            }
            greedy("command") {
                execute {
                    val path = get<String>("command")
                    val tokens = path.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val target = findNode(this@RootNode, tokens)
                    return@execute execute.invoke(if (target == null) "Unknown command: $path" else helpLines(target, tokens))
                }
            }
        }
    }
}

class LiteralNode(name: String) : CommandNode(name)
class ArgumentNode<T : Argument>(name: String, val argument: T) : CommandNode(name) {
    override val priority: Int get() = argument.priority
}

class ChoiceNode(val variableName: String, initialNames: Set<String>) : CommandNode(variableName) {
    val names = initialNames.toMutableSet()

    fun add(name: String) {
        names += name
    }

    fun ChoiceNode.addChoices(block: MutableSet<String>.() -> Unit) {
        names.apply(block)
    }
}
