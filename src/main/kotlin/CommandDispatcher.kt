package top.stellortus.command_parser

import top.stellortus.command_parser.exceptions.CommandNotExecutableException
import top.stellortus.command_parser.exceptions.EmptyContextException
import top.stellortus.command_parser.exceptions.PermissionDeniedException
import top.stellortus.command_parser.exceptions.UnknownCommandException

class CommandDispatcher<T : CommandContext> internal constructor(
    val root: RootNode<T>,
    private val variables: Map<String, Any?>,
    /** Null when nobody has supplied a context yet, i.e. `withContext(...)` is still required. */
    private val contextFactory: (() -> T)?,
    internal val permissionProvider: ((T) -> Int) = { Int.MIN_VALUE }
) {

    @Suppress("unused")
    fun bind(vararg variables: Pair<String, Any?>): CommandDispatcher<T> =
        CommandDispatcher(root, this.variables + variables.toList(), contextFactory, permissionProvider)

    @Suppress("unused")
    fun withContext(context: T): CommandDispatcher<T> =
        CommandDispatcher(root, variables, { context }, permissionProvider)

    @Suppress("unused")
    fun permissionProvider(provider: (T) -> Int): CommandDispatcher<T> =
        CommandDispatcher(root, variables, contextFactory, provider)


    private fun resolve(
        node: CommandNode<T>,
        tokens: List<String>,
        step: Int,
        context: T,
    ): CommandNode<T>? {
        if (step >= tokens.size) return node

        val token = tokens[step]
        for (child in node.nextNodes) {
            when (child) {
                is LiteralNode<*> -> if (child.name == token) {
                    resolve(child, tokens, step + 1, context)?.let { return it }
                }

                is ArgumentNode<*> -> {
                    val end = if (child.argument.greedy) tokens.size else step + 1
                    val label = tokens.subList(step, end).joinToString(" ")
                    if (child.argument.match(label)) {
                        advance(context, child.name, child.argument.parse(label)) {
                            resolve(child, tokens, end, context)
                        }?.let { return it }
                    }
                }

                is ChoiceNode<*> -> if (token in child.names) {
                    advance(context, child.name, token) {
                        resolve(child, tokens, step + 1, context)
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
    private fun advance(context: T, key: String, value: Any?, descend: () -> CommandNode<T>?): CommandNode<T>? {
        val had = context.has(key)
        val previous = context.peek(key)
        context.put(key, value)
        val match = descend()
        if (match == null) {
            if (had) context.put(key, previous) else context.remove(key)
        }
        return match
    }

    @Suppress("unused")
    fun execute(command: String): Any {
        val context = contextFactory?.invoke()
            ?: throw EmptyContextException("No context set: call withContext(...) before execute(...)")

        context.clear()
        context.putAll(variables)
        val tokens = command.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        val matchingNode = resolve(root, tokens, 0, context)
            ?: throw UnknownCommandException("Unknown command: ${tokens.joinToString(" ")}")

        val currentPermission = permissionProvider(context)
        if (currentPermission < matchingNode.permission) {
            throw PermissionDeniedException("Current permission($currentPermission)" +
                    " isn't allowed to execute “$command”(${matchingNode.permission} required)!")
        }
        // Refreshed per run rather than installed once at construction: bind/withContext/permissionProvider
        // all derive new dispatchers that share one root, so an install-time value would be whichever
        // dispatch happened to be built last, not the one running now.
        root.permissionProvider = permissionProvider
        return matchingNode.execute?.invoke(context)
            ?: throw CommandNotExecutableException("Command is not executable: ${tokens.joinToString(" ")}")
    }
}

/** A plain [CommandContext] needs no arguments, so this form is directly executable. */
@Suppress("unused")
fun buildCommand(block: RootNode<CommandContext>.() -> Unit): CommandDispatcher<CommandContext> {
    val root = RootNode<CommandContext>()
    root.block()
    return CommandDispatcher(root, emptyMap(), { CommandContext() })
}

/** [T] has to be constructed by the caller, so `withContext(...)` is required before executing. */
@Suppress("unused")
@JvmName("buildCommandWithContext")
fun <T : CommandContext> buildCommand(block: RootNode<T>.() -> Unit): CommandDispatcher<T> {
    val root = RootNode<T>()
    root.block()
    return CommandDispatcher(root, emptyMap(), null)
}
