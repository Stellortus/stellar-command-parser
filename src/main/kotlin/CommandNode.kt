package top.stellortus.command_parser

typealias ExecuteBehaviour<T> = T.() -> Any

abstract class CommandNode<T : CommandContext>(val name: String) {
    internal val nextNodes = mutableSetOf<CommandNode<T>>()
    internal var execute: ExecuteBehaviour<T>? = null
    var description: String? = null

    open val priority: Int get() = 0

    private fun addNode(node: CommandNode<T>) {
        nextNodes.add(node)
    }


    fun CommandNode<T>.literal(name: String, block: CommandNode<T>.() -> Unit) {
        val child = LiteralNode<T>(name).apply(block)
        this.addNode(child)
    }

    private fun CommandNode<T>.argument(name: String, argument: Argument, block: CommandNode<T>.() -> Unit) {
        val child = ArgumentNode<T>(name, argument).apply(block)
        this.addNode(child)
    }

    fun CommandNode<T>.string(name: String, block: CommandNode<T>.() -> Unit) = argument(name, StringArgument, block)

    fun CommandNode<T>.integer(name: String, block: CommandNode<T>.() -> Unit) = argument(name, IntArgument, block)

    fun CommandNode<T>.greedy(name: String, block: CommandNode<T>.() -> Unit) = argument(name, GreedyArgument, block)

    fun CommandNode<T>.choice(variableName: String, vararg names: String, block: ChoiceNode<T>.() -> Unit) {
        val child = ChoiceNode<T>(variableName, names.toSet()).apply(block)
        this.addNode(child)
    }

    fun CommandNode<T>.execute(block: ExecuteBehaviour<T>) {
        this.execute = block
    }

    fun CommandNode<T>.description(description: String) {
        this.description = description
    }
}


private fun CommandNode<*>.displayName(): String = when (this) {
    is ArgumentNode<*> -> "<${name}:${argument.type}>"
    is ChoiceNode<*> -> "<${names.joinToString("|")}>"
    else -> name
}

private fun helpLines(node: CommandNode<*>, prefix: List<String> = emptyList()): String =
    node.nextNodes.joinToString("\n") { child ->
        val path = (prefix + child.displayName()).joinToString(" ")
        child.description?.let { "$path → $it" } ?: path
    }

private fun findNode(node: CommandNode<*>, path: List<String>): CommandNode<*>? {
    var current: CommandNode<*> = node
    for (token in path) {
        current = current.nextNodes.firstOrNull {
            (it is LiteralNode<*> && it.name == token) || (it is ChoiceNode<*> && token in it.names)
        } ?: return null
    }
    return current
}


class RootNode<T : CommandContext> : CommandNode<T>("") {
    fun RootNode<T>.setHelper(description: String = "显示此帮助页面", execute: (String) -> Any = { it }) {
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

class LiteralNode<T : CommandContext>(name: String) : CommandNode<T>(name)
class ArgumentNode<T : CommandContext>(name: String, val argument: Argument) : CommandNode<T>(name) {
    override val priority: Int get() = argument.priority
}

class ChoiceNode<T : CommandContext>(variableName: String, initialNames: Set<String>) : CommandNode<T>(variableName) {
    val names = initialNames.toMutableSet()

    fun add(name: String) {
        names += name
    }

    fun ChoiceNode<T>.addChoices(block: MutableSet<String>.() -> Unit) {
        names.apply(block)
    }
}
