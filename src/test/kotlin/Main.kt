import top.stellortus.command_parser.CommandContext
import top.stellortus.command_parser.buildCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class IdContext(var hiddenId: Int) : CommandContext() {
    val id: Int
        get() {
            hiddenId++
            return hiddenId
        }
}

class Main {

    @Test
    fun test() {
        val context = IdContext(1110)

        val dispatcher = buildCommand<IdContext> {
            setHelper()
            literal("hello") {
                description("你好")
                execute {
                    "Hello world!"
                }
                literal("kotlin") {
                    description("show Kotlin")
                    execute {
                        "Kotlin command!"
                    }
                    literal("idd") {
                        execute {
                            val idd = get<String>("id")
                            "Hello Kotlin $idd!"
                        }
                    }
                    literal("id") {
                        execute {
                            "Hello Kotlin $id!"
                        }
                    }
                }
                choice("language", "JS", "TS") {
                    description("JavaScript / TypeScript")
                    execute {
                        "${get<String>("language")} Command!"
                    }
                }
                choice("letter") {
                    addChoices {
                        add("a")
                        add("b")
                    }
                    execute {
                        "Letter: ${get<String>("letter")}"
                    }
                }
                string("world") {
                    execute {
                        "Hello, ${get<String>("world")}!"
                    }
                }
                integer("age") {
                    execute {
                        "Hello, ${get<Int>("age")}!"
                    }
                }
            }
        }.withContext(context)
        fun assertCommandEquals(expected: String, command: String, vararg arguments: Pair<String, Any?>) =
            assertEquals(expected, dispatcher.bind(*arguments).execute(command))
        assertCommandEquals("Hello world!", "hello")
        assertCommandEquals("Kotlin command!", "hello kotlin")
        assertCommandEquals("JS Command!", "hello JS")
        assertCommandEquals("TS Command!", "hello TS")
        assertCommandEquals("Hello, Stellortus!", "hello Stellortus")
        assertCommandEquals(
            """
            help → 显示此帮助页面
            hello → 你好
        """.trimIndent(), "help"
        )
        assertCommandEquals(
            """
            hello kotlin → show Kotlin
            hello <JS|TS> → JavaScript / TypeScript
            hello <a|b>
            hello <world:String>
            hello <age:Int>
        """.trimIndent(), "help hello"
        )
        assertCommandEquals("help <command:String...>", "help help")
        assertCommandEquals("Hello, 1!", "hello 1")
        assertCommandEquals("Hello, 1!", "hello 01")

        val id = "123123"
        assertCommandEquals("Hello Kotlin 123123!", "hello kotlin idd", "id" to id)
        assertCommandEquals("Hello Kotlin 1111!", "hello kotlin id")
        assertCommandEquals("Hello Kotlin 1112!", "hello kotlin id")
    }
}