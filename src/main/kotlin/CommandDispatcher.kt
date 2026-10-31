package top.stellortus.command_parser

import top.stellortus.command_parser.exceptions.CommandNotExecutableException
import top.stellortus.command_parser.exceptions.ContextUndefinedException
import top.stellortus.command_parser.exceptions.DeprecatedResolverUndefinedException
import top.stellortus.command_parser.exceptions.PermissionDeniedException
import top.stellortus.command_parser.exceptions.UnknownCommandException

class CommandDispatcher<T : CommandContext> internal constructor(
    val root: RootNode<T>,
    private val variables: Map<String, Any?>,
    /** Null when nobody has supplied a context yet, i.e. `withContext(...)` is still required. */
    private val contextFactory: (() -> T)?,
    internal val permissionProvider: ((T) -> Int) = { Int.MIN_VALUE }
) {

    private var resolver: ((String) -> Unit)? = null

    @Suppress("unused")
    fun bind(vararg variables: Pair<String, Any?>): CommandDispatcher<T> =
        CommandDispatcher(root, this.variables + variables.toList(), contextFactory, permissionProvider)

    @Suppress("unused")
    fun withContext(context: T): CommandDispatcher<T> =
        CommandDispatcher(root, variables, { context }, permissionProvider)

    @Suppress("unused")
    fun permissionProvider(provider: (T) -> Int): CommandDispatcher<T> =
        CommandDispatcher(root, variables, contextFactory, provider)

    @Suppress("unused")
    fun deprecatedResolver(resolver: (String) -> Unit) {
        this.resolver = resolver
    }

    private data class Match<T : CommandContext>(
        val node: CommandNode<T>,
        val nodeCount: Int,
        val arguments: Map<String, Any?>,
    )

    private fun resolve(
        node: CommandNode<T>,
        tokens: List<String>,
        step: Int = 0,
        nodeCount: Int = 0,
        arguments: Map<String, Any?> = emptyMap(),
    ): List<Match<T>> = buildList {
        if (step >= tokens.size) {
            add(Match(node, nodeCount, arguments))
            return@buildList
        }

        val token = tokens[step]
        for (child in node.nextNodes) {
            when (child) {
                is LiteralNode<*> -> if (child.name == token) {
                    addAll(resolve(child, tokens, step + 1, nodeCount + 1, arguments))
                }

                is ArgumentNode<*> -> {
                    val end = if (child.argument.greedy) tokens.size else step + 1
                    val label = tokens.subList(step, end).joinToString(" ")
                    if (child.argument.match(label)) {
                        addAll(resolve(
                            child, tokens, end, nodeCount + 1,
                            arguments + (child.name to child.argument.parse(label)),
                        ))
                    }
                }

                is ChoiceNode<*> -> if (token in child.names) {
                    addAll(resolve(
                        child, tokens, step + 1, nodeCount + 1,
                        arguments + (child.name to token),
                    ))
                }

                else -> {}
            }
        }
    }

    @Suppress("unused")
    fun execute(command: String): Any {
        val context = contextFactory?.invoke()
            ?: throw ContextUndefinedException("No context set: call withContext(...) before execute(...)")

        context.clear()
        context.putAll(variables)
        val tokens = command.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        val matches = resolve(root, tokens)
        // Count matched nodes, not consumed tokens: a greedy argument is still only one node.
        // maxByOrNull keeps the first match on ties, preserving the sibling priority order.
        val match = matches.filter { it.node.executeBehaviour != null }.maxByOrNull { it.nodeCount }
            // Retain permission/error handling for complete paths without an execute block.
            ?: matches.maxByOrNull { it.nodeCount }
            ?: throw UnknownCommandException("Unknown command: ${tokens.joinToString(" ")}")
        context.putAll(match.arguments)
        val matchingNode = match.node

        val currentPermission = permissionProvider(context)
        if (currentPermission < matchingNode.permission) {
            throw PermissionDeniedException(
                "Current permission($currentPermission)" +
                        " isn't allowed to execute “$command”(${matchingNode.permission} required)!"
            )
        }
        root.permissionProvider = permissionProvider
        matchingNode.deprecatedValue?.let {
            resolver?.invoke(it)
                ?: throw DeprecatedResolverUndefinedException("No `dispatcher.deprecatedResolver` invoked with a node deprecated!")
        }
        return matchingNode.executeBehaviour?.invoke(context)
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
