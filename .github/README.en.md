# Stellar Command Parser

[简体中文](README.md) | [English](README.en.md)

This is a kotlin lib to execute string commands.

## Basic Use

1. Build a command dispatcher first

```kotlin
val dispatcher = buildCommand {
    TODO()
}
```

2. Edit the dispatcher to add some **nodes**

**nodes** can be added with **node functions**, here are **all of** supported functions:

- `literal(name) {}`: simple literal node.
- `string(name) {}`: a placeholder of a dynamic string argument.
- `integer(name) {}`: similarly, but a dynamic integer argument.
- `greedy(name) {}`: taking all the rest *tokens*.
- `choice(name, vararg choices) {}`: similar to `literal`, but the choices *being folded* in the *default help text*.

The **node** context allows the following **basic** behaviours:

- adding children nodes using the functions above.
- `execute {}`: setting the behaviour when the command executes.
- `get<>("name")`: get the value of argument placeholders.

Also, `execute` returns `Any`, allowing receiving the return value when the command executes.

```kotlin
val dispatcher = buildCommand {
    literal("hello") {
        execute {
            "Hello!" // This is a return value
        }
        // Add some children nodes
        string("name") {
            execute {
                "Hello, the user named ${get<String>("name")}"
            }
        }
        integer("age") {
            execute {
                "Hello, the user aged ${get<Int>("age")}"
            }
        }
    }
}
```

3. Use `dispatcher` to execute commands

```kotlin
val result = dispatcher.execute("hello") // "Hello!"
dispatcher.execute("hello Stellortus") // "Hello, the user named Stellortus"
dispatcher.execute("hello 18") // "Hello, the user aged 18"
```

## Help Text

1. Declare the helper

`Stellar Command Parser` provides a universal helper, just add `setHelper()` in the `buildCommand` context to use it.

```kotlin
val dispatcher = buildCommand {
    setHelper()
}
```

`setHelper()` will automatically get all the command nodes you add.

2. Run the `help` command

The `help` command will list the children node of the given node.

```kotlin
val dispatcher = buildCommand {
    setHelper()
    literal("hello") {
        execute { "Hello!" }
        string("name") {
            execute { "Hello, ${get<String>("name")}!" }
        }
    }
}

dispatcher.execute("help")
// hello
// help → 显示此帮助页面
dispatcher.execute("help hello")
// hello
// hello <name:String>
```

3. Set node description

Use `description(description)` in the **node** context to add a description, shown in the **help text**.

```kotlin
val dispatcher = buildCommand {
    setHelper()
    literal("hello") {
        description("Say hello")
        execute { "Hello!" }
        string("name") {
            description("Say hello to a user")
            execute { "Hello, ${get<String>("name")}!" }
        }
    }
}

dispatcher.execute("help hello")
// hello → Say hello
// hello <name:String> → Say hello to a user
```

4. Configure the helper

Use `setHelper(description, execute)` to configure the helper:

- `description`: the description of the `help` node, defaulting to `"显示此帮助页面"`.
- `execute`: receiving the generated help text and returning the command result, defaulting to `{ it }` to return the text directly.

The `execute` callback applies to both `help` and `help <command>`.

```kotlin
val dispatcher = buildCommand {
    setHelper(
        description = "Show command help",
        execute = { text ->
            println(text)
            text
        }
    )
    literal("hello") {
        execute { "Hello!" }
    }
}

dispatcher.execute("help")
// Prints and returns:
// hello
// help → Show command help
```

## Bind Variables

1. Build a command dispatcher first

Bound variables can be read with `get<>("name")`, similarly to argument placeholders.

```kotlin
val dispatcher = buildCommand {
    literal("hello") {
        execute {
            val name = get<String>("name")
            val age = get<Int>("age")
            "Hello, $name, aged $age!"
        }
    }
}
```

2. Use `dispatcher.bind(vararg arguments)` to bind arguments

Each argument is a pair of a variable name and its value, written as `"name" to value`.

`bind` returns a **new dispatcher**, so keep the returned dispatcher or chain the call.

```kotlin
val boundDispatcher = dispatcher.bind("name" to "Stellortus", "age" to 18)

boundDispatcher.execute("hello") // "Hello, Stellortus, aged 18!"
```

## Add Context Variable

1. Create a context class and declare the variables required

Your context class should extend `CommandContext`.

```kotlin
class UserContext(val name: String, val age: Int) : CommandContext()
```

2. Build a command dispatcher with the context class

Use `buildCommand<UserContext>` to make your context available in the `execute` block.

```kotlin
val dispatcher = buildCommand<UserContext> {
    literal("hello") {
        execute { "Hello!" }
    }
}
```

3. Get the variables directly

The `execute` block uses your context as its receiver, so the variables can be read directly.

```kotlin
val dispatcher = buildCommand<UserContext> {
    literal("hello") {
        execute {
            "Hello, $name, aged $age!"
        }
    }
}
```

4. Use `dispatcher.withContext(context)` to input your context instance

`withContext` returns a **new dispatcher** with the given context instance.
For a custom context, executing before providing an instance throws `ContextUndefinedException`.

```kotlin
val context = UserContext("Stellortus", 18)
val contextDispatcher = dispatcher.withContext(context)

contextDispatcher.execute("hello") // "Hello, Stellortus, aged 18!"
```

## Permission Setting

1. Set the minimal permission of a node

Use `requirePermission(permission)` in the **node** context. A higher number means a higher permission.
Children nodes inherit their parent's permission, so set it **before** adding children nodes.
Setting a child's permission lower than its parent's throws `IllegalPermissionSettingException`.

```kotlin
val dispatcher = buildCommand {
    setHelper()
    literal("hello") {
        requirePermission(1)
        execute { "Hello!" }
        literal("admin") {
            requirePermission(2)
            execute { "Hello, admin!" }
        }
    }
}
```

2. Provide the current permission

Use `dispatcher.permissionProvider { context -> ... }` to get the permission from the context, or return a fixed value.
It returns a **new dispatcher**. The default permission is `Int.MIN_VALUE`, also the default requirement of a node.

```kotlin
val permittedDispatcher = dispatcher.permissionProvider { 1 }

permittedDispatcher.execute("hello") // "Hello!"
permittedDispatcher.execute("hello admin") // Throws PermissionDeniedException
```

3. Check permission in the help text

The **help text** only lists nodes allowed by the current permission.

```kotlin
permittedDispatcher.execute("help hello") // "hello"

val adminDispatcher = dispatcher.permissionProvider { 2 }
adminDispatcher.execute("help hello")
// hello
// hello admin
```

## Deprecated Marker

1. Mark a deprecated node

Use `deprecated(replaceWith)` to provide a replacement for an old command.

```kotlin
val dispatcher = buildCommand {
    literal("hello") {
        execute { "Hello!" }
    }
    literal("hallo") {
        deprecated("hello")
        execute { "Hallo!" }
    }
}
```

2. Declare the deprecated resolver

Use `dispatcher.deprecatedResolver { replacement -> ... }` to handle the replacement text before the deprecated command executes.
Register it on the dispatcher you will execute, after calls such as `bind` or `withContext`.

```kotlin
dispatcher.deprecatedResolver { replacement ->
    println("Use $replacement instead")
}

dispatcher.execute("hallo") // Prints "Use hello instead", returns "Hallo!"
dispatcher.execute("hello") // "Hello!"
```

The marker does not redirect the command: the old execution behaviour still runs if the resolver returns normally.
Executing a deprecated command without a resolver throws `DeprecatedResolverUndefinedException`.
