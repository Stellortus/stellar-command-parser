package top.stellortus.command_parser

class CommandContext {
    private val values = mutableMapOf<String, Any?>()

    internal fun put(name: String, value: Any?) {
        values[name] = value
    }

    internal fun remove(name: String) {
        values.remove(name)
    }

    operator fun get(name: String): Any? = values[name]
}

inline fun <reified T> CommandContext.get(name: String): T = this[name] as T
