import org.junit.jupiter.api.assertThrows
import top.stellortus.command_parser.CommandContext
import top.stellortus.command_parser.CommandDispatcher
import top.stellortus.command_parser.buildCommand
import top.stellortus.command_parser.exceptions.EmptyContextException
import top.stellortus.command_parser.exceptions.IllegalPermissionSettingException
import top.stellortus.command_parser.exceptions.PermissionDeniedException
import kotlin.test.Test
import kotlin.test.assertEquals


class Main {

    context(dispatcher: CommandDispatcher<*>)
    fun assertCommandEquals(expected: String, command: String) =
        assertEquals(expected, dispatcher.execute(command))

    context(dispatcher: CommandDispatcher<*>)
    inline fun <reified T : Throwable> assertCommandThrows(command: String) =
        assertThrows<T> { dispatcher.execute(command) }

    @Test
    fun basicTest() {
        val dispatcher = buildCommand {
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
                }
                choice("language", "JS", "TS") {
                    description("JavaScript / TypeScript")
                    execute {
                        "${get<String>("language")} Command!"
                    }
                }
                choice("letter") {
                    mutate {
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
        }

        fun assertCommandEquals(expected: String, command: String) =
            assertEquals(expected, dispatcher.execute(command))
        assertCommandEquals("Hello world!", "hello")
        assertCommandEquals("Kotlin command!", "hello kotlin")
        assertCommandEquals("JS Command!", "hello JS")
        assertCommandEquals("TS Command!", "hello TS")
        assertCommandEquals("Hello, Stellortus!", "hello Stellortus")
        assertCommandEquals(
            """
            hello → 你好
            help → 显示此帮助页面
        """.trimIndent(), "help"
        )
        assertCommandEquals(
            """
            hello → 你好
            hello kotlin → show Kotlin
            hello <JS|TS> → JavaScript / TypeScript
            hello <a|b>
            hello <age:Int>
            hello <world:String>
        """.trimIndent(), "help hello"
        )
        assertCommandEquals("""
            help → 显示此帮助页面
            help <command:String...>
        """.trimIndent(), "help help")
        assertCommandEquals("Hello, 1!", "hello 1")
        assertCommandEquals("Hello, 1!", "hello 01")
    }

    @Test
    fun bindTest() {
        val dispatcher = buildCommand {
            literal("hello") {
                literal("name") {
                    execute {
                        val name = get<String>("name")
                        val age = get<Int>("age")
                        "Hello, $name, $age"
                    }
                }
                literal("error") {
                    execute {
                        val name = get<Int>("name")
                        // Impossible to reach here
                        println("Error, $name")
                    }
                }
            }
        }.bind("name" to "Stellortus", "age" to 1)
        with(dispatcher) {
            assertCommandEquals("Hello, Stellortus, 1", "hello name")
            assertCommandThrows<ClassCastException>("hello error")
        }
    }

    @Test
    fun contextTest() {
        class IdContext(private var hiddenId: Int) : CommandContext() {
            val id: Int
                get() {
                    hiddenId++
                    return hiddenId
                }
        }

        val dispatcher = buildCommand<IdContext> {
            literal("hello") {
                execute {
                    val name = get<String>("name")
                    "Hello $name $id!"
                }
            }
        }.bind("name" to "Stellortus")
        with(dispatcher) {
            assertCommandThrows<EmptyContextException>("hello")
            withContext(IdContext(1)).apply {
                assertCommandEquals("Hello Stellortus 2!", "hello")
                assertCommandEquals("Hello Stellortus 3!", "hello")
            }
            withContext(IdContext(1)).apply {
                assertCommandEquals("Hello Stellortus 2!", "hello")
            }
        }
    }

    @Test
    fun argumentTest() {
        val dispatcher = buildCommand {
            literal("hello") {
                literal("name") {
                    string("address") {
                        execute {
                            val address = get<String>("address")
                            "Hello name, $address"
                        }
                    }
                }
                string("address") {
                    execute {
                        val address = get<String>("address")
                        "Hello address, $address"
                    }
                    integer("id") {
                        choice("group", "a", "b", "c"){
                            mutate {
                                add("d")
                            }
                            execute {
                                "Hello!! ${get("address")}!! ${get("id")}!! ${get("group")}!!"
                            }
                        }
                    }
                }
            }
        }
        with(dispatcher) {
            assertCommandEquals("Hello name, 111", "hello name 111")
            assertCommandEquals("Hello address, 111", "hello 111")
            assertCommandEquals("Hello!! China!! 111!! a!!", "hello China 111 a")
        }
    }

    @Test
    fun permissionTest() {
        assertThrows<IllegalPermissionSettingException> {
            buildCommand {
                literal("hello") {
                    requirePermission(4)
                    literal("error") {
                        requirePermission(3)
                    }
                }
            }
        }

        val dispatcher = buildCommand {
            literal("say") {
                requirePermission(0)
                execute {
                    "say hello"
                }
            }
            literal("hello") {
                requirePermission(1)
                execute { "hello" }
                literal("world") {
                    requirePermission(2)
                    execute { "world" }
                }
            }
        }
        with(dispatcher) {
            assertCommandThrows<PermissionDeniedException>("say")
            permissionProvider { 1 }.apply {
                assertCommandEquals("say hello", "say")
                assertCommandEquals("hello", "hello")
                assertCommandThrows<PermissionDeniedException>("hello world")
            }
            permissionProvider { 2 }.apply {
                assertCommandEquals("world", "hello world")
            }
        }
    }

    @Test
    fun duplicateTest() {
        val dispatcher = buildCommand {
            literal("hello") {
                literal("a") {
                    execute { "a" }
                }
            }
            literal("hello") {
                literal("b") {
                    execute { "b" }
                }
            }
        }
        with(dispatcher) {
            assertCommandEquals("a", "hello a")
            assertCommandEquals("b", "hello b")
        }
    }

    @Test
    fun helpWithPermissionTest() {
        val dispatcher = buildCommand {
            setHelper()
            literal("hello") {
                requirePermission(1)
                literal("a") {
                    requirePermission(2)
                }
                literal("b") {
                    requirePermission(3)
                }
            }
        }
        with(dispatcher) {
            assertCommandThrows<PermissionDeniedException>("hello")
            assertCommandEquals("help → 显示此帮助页面", "help")
            assertCommandEquals("命令“hello”不存在或权限不足", "help hello")
            this.permissionProvider { 1 }.apply {
                assertCommandEquals("hello", "help hello")
            }
            this.permissionProvider { 2 }.apply {
                assertCommandEquals("""
                    hello
                    hello a
                """.trimIndent(), "help hello")
            }
            this.permissionProvider { 3 }.apply {
                assertCommandEquals("""
                    hello
                    hello a
                    hello b
                """.trimIndent(), "help hello")
            }
        }
    }
}