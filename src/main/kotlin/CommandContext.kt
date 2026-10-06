package top.stellortus.command_parser

open class CommandContext {
    private val values = mutableMapOf<String, Any?>()

    /** Lookup scopes, deepest first, e.g. ["a/b/", "a/"]. Bare names are tried before any of these. */
    private var scopes: List<String> = emptyList()

    internal fun setScopes(ancestorNames: List<String>) {
        scopes = (ancestorNames.size downTo 1).map { ancestorNames.take(it).joinToString("/") + "/" }
    }

    internal fun put(name: String, value: Any?) {
        values[name] = value
    }

    internal fun remove(name: String) {
        values.remove(name)
    }

    internal fun putAll(variables: Map<String, Any?>) {
        values.putAll(variables)
    }

    /** Drops everything parsed by a previous run, so a reused context starts clean. */
    internal fun clear() {
        values.clear()
        scopes = emptyList()
    }

    internal fun has(name: String): Boolean = values.containsKey(name)

    internal fun peek(name: String): Any? = values[name]

    operator fun get(name: String): Any? {
        if (values.containsKey(name)) return values[name]
        for (scope in scopes) {
            val key = scope + name
            if (values.containsKey(key)) return values[key]
        }
        return null
    }
    inline fun <reified T> CommandContext.get(name: String): T = this[name] as T
}
