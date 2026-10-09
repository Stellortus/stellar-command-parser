package top.stellortus.command_parser

import top.stellortus.command_parser.exceptions.IllegalPermissionSettingException
import java.util.*

typealias ExecuteBehaviour<T> = T.() -> Any

val comparator = compareBy<CommandNode<*>>({ it.priority }, { it.pathName })

@CommandDslMarker
abstract class CommandNode<T : CommandContext>(private val parent: CommandNode<T>?, val name: String) {
    /**
     * The names from the root down to this node, joined with `/`. Siblings are kept in a
     * [SortedSet] keyed on it, and it is what gives a node its identity (see [equals]/[hashCode]).
     *
     * Reading values back does not go through it: a parsed argument is stored, and read, under its
     * own [name] — see [CommandContext.get].
     */
    val pathName: String get() = "${parent?.pathName ?: ""}/$name"

    internal val nextNodes: SortedSet<CommandNode<T>> =
        sortedSetOf(comparator)

    internal var execute: ExecuteBehaviour<T>? = null
    var description: String? = null
    internal var permission: Int = Int.MIN_VALUE

    open val priority: Int get() = 0

    /** How this node is written in help: its [name], or a placeholder for an argument/choice. */
    internal open fun displayName(): String = name

    /** A node is identified by its [pathName] alone, so [T] plays no part here. */
    override fun equals(other: Any?): Boolean =
        this === other || (other is CommandNode<*> && pathName == other.pathName)

    override fun hashCode(): Int = pathName.hashCode()

    /**
     * Returns the child named [name], creating and registering one via [create] when absent, so that
     * repeated declarations of the same command merge into a single node. The child inherits this
     * node's permission before [block] configures it.
     */
    private fun <R : CommandNode<T>> child(name: String, create: () -> R, block: R.() -> Unit): R {
        @Suppress("UNCHECKED_CAST")
        val node = nextNodes.firstOrNull { it.name == name } as R? ?: create().also { nextNodes.add(it) }
        node.permission = permission
        node.block()
        return node
    }

    fun literal(name: String, block: CommandNode<T>.() -> Unit) =
        child(name, { LiteralNode(this, name) }, block)

    private fun argument(name: String, argument: Argument, block: CommandNode<T>.() -> Unit) =
        child(name, { ArgumentNode(this, name, argument) }, block)

    fun string(name: String, block: CommandNode<T>.() -> Unit) = argument(name, StringArgument, block)

    fun integer(name: String, block: CommandNode<T>.() -> Unit) = argument(name, IntArgument, block)

    fun greedy(name: String, block: CommandNode<T>.() -> Unit) = argument(name, GreedyArgument, block)

    fun choice(variableName: String, vararg names: String, block: ChoiceNode<T>.() -> Unit) =
        child(variableName, { ChoiceNode(this, variableName, names.toSet()) }, block)

    fun execute(block: ExecuteBehaviour<T>) {
        this.execute = block
    }

    fun description(description: String) {
        this.description = description
    }

    fun requirePermission(permission: Int) {
        if (permission < this.permission) throw IllegalPermissionSettingException(
            "The child node cannot have permission $permission lower than the parent node ${this.permission}"
        )

        this.permission = permission
    }
}

/**
 * Assert: current permission is enough to call `node`
 */
context(context: T)
private fun <T : CommandContext> helpLines(
    node: CommandNode<*>,
    permissionProvider: (T) -> Int,
    prefix: List<String> = emptyList(),
): String {
    val permission = permissionProvider(context)
    val commands = buildSet<CommandNode<*>> {
        addAll(node.nextNodes.filter { it.permission <= permission })
    }.toSortedSet(comparator)

    val currentHelp = node.takeIf { it !is RootNode }?.let { node ->
        val path = prefix.joinToString(" ")
        node.description?.let { "$path → $it" } ?: path
    }

    val lines = mutableListOf<String>()
    currentHelp?.let { lines.add(it) }
    lines.addAll(
        commands.map { node ->
            val path = (prefix + node.displayName()).joinToString(" ")
            node.description?.let { "$path → $it" } ?: path
        }
    )
    return lines.joinToString("\n")
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


class RootNode<T : CommandContext> : CommandNode<T>(null, "") {
    /**
     * Handed over by [CommandDispatcher] before each command block runs, so [setHelper] can ask for
     * the caller's permission. Defaults to "no permission configured".
     */
    internal var permissionProvider: (T) -> Int = { Int.MIN_VALUE }

    fun setHelper(description: String = "显示此帮助页面", execute: (String) -> Any = { it }) {
        literal("help") {
            description(description)
            execute {
                return@execute execute.invoke(helpLines(this@RootNode, this@RootNode.permissionProvider))
            }
            greedy("command") {
                execute {
                    val path = get<String>("command")
                    val tokens = path.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    val provider = this@RootNode.permissionProvider
                    val target = findNode(this@RootNode, tokens)
                        ?.takeIf { it.permission <= provider(this) }
                    val listing = target?.let { helpLines(it, provider, tokens) }
                    return@execute execute.invoke(
                        // Nothing the caller may run shows up under that prefix — not even the no-argument
                        // form — so it stays a secret rather than a blank page.
                        listing ?: "命令“$path”不存在或权限不足"
                    )
                }
            }
        }
    }
}

class LiteralNode<T : CommandContext>(parent: CommandNode<T>, name: String) : CommandNode<T>(parent, name)

class ArgumentNode<T : CommandContext>(parent: CommandNode<T>, name: String, val argument: Argument) :
    CommandNode<T>(parent, name) {
    override val priority: Int get() = argument.priority
    override fun displayName(): String = "<$name:${argument.type}>"
}

class ChoiceNode<T : CommandContext>(parent: CommandNode<T>, variableName: String, initialNames: Set<String>) :
    CommandNode<T>(parent, variableName) {
    val names = initialNames.toMutableSet()

    override fun displayName(): String = "<${names.joinToString("|")}>"

    @Suppress("unused")
    fun mutate(block: MutableSet<String>.() -> Unit) {
        names.apply(block)
    }
}
