package top.stellortus.command_parser

open class CommandContext {
    private val values = mutableMapOf<String, Any?>()

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
    }

    internal fun has(name: String): Boolean = values.containsKey(name)

    internal fun peek(name: String): Any? = values[name]

    /** Whatever a command declared — with `bind(...)` or an argument — is read back by that same name. */
    operator fun get(name: String): Any? = values[name]

    inline fun <reified T> CommandContext.get(name: String): T = this[name] as T
}
