# Stellar Command Parser

[简体中文](README.md) | [English](README.en.md)

这是一个用于执行字符串命令的 Kotlin 库。

## 基本使用

1. 先构建一个命令调度器

```kotlin
val dispatcher = buildCommand {
    TODO()
}
```

2. 编辑命令调度器，添加一些**节点**

可以通过**节点函数**添加**节点**，以下是**所有**支持的函数：

- `literal(name) {}`：简单的字面量节点。
- `string(name) {}`：动态字符串参数的占位符。
- `integer(name) {}`：类似于上一个函数，但接受动态整数参数。
- `greedy(name) {}`：接收剩余的所有 *token*。
- `choice(name, vararg choices) {}`：类似于 `literal`，但选项会在*默认帮助文本*中*折叠显示*。

在**节点**上下文中可以进行以下**基本**操作：

- 使用上述函数添加子节点。
- `execute {}`：设置命令执行时的行为。
- `get<>("name")`：获取参数占位符的值。

另外，`execute` 返回 `Any`，可以在执行命令时接收返回值。

```kotlin
val dispatcher = buildCommand {
    literal("hello") {
        execute {
            "Hello!" // 这是一个返回值
        }
        // 添加一些子节点
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

3. 使用 `dispatcher` 执行命令

```kotlin
val result = dispatcher.execute("hello") // "Hello!"
dispatcher.execute("hello Stellortus") // "Hello, the user named Stellortus"
dispatcher.execute("hello 18") // "Hello, the user aged 18"
```

## 帮助文本

1. 声明帮助器

`Stellar Command Parser` 提供了通用的帮助器，只需在 `buildCommand` 上下文中添加 `setHelper()` 即可使用。

```kotlin
val dispatcher = buildCommand {
    setHelper()
}
```

`setHelper()` 会自动获取你添加的所有命令节点。

2. 运行 `help` 命令

`help` 命令会列出指定节点的子节点。

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

3. 设置节点描述

在**节点**上下文中使用 `description(description)` 添加描述，描述会显示在**帮助文本**中。

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

4. 配置帮助器

使用 `setHelper(description, execute)` 配置帮助器：

- `description`：`help` 节点的描述，默认为 `"显示此帮助页面"`。
- `execute`：接收生成的帮助文本，并返回命令执行结果，默认为 `{ it }`，即直接返回文本。

`execute` 回调对 `help` 和 `help <command>` 均生效。

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
// 打印并返回：
// hello
// help → Show command help
```

## 绑定变量

1. 先构建一个命令调度器

与参数占位符类似，绑定的变量也可以通过 `get<>("name")` 读取。

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

2. 使用 `dispatcher.bind(vararg arguments)` 绑定参数

每个参数都是变量名与值组成的键值对，写作 `"name" to value`。

`bind` 返回一个**新的命令调度器**，因此需要保存返回值，或使用链式调用。

```kotlin
val boundDispatcher = dispatcher.bind("name" to "Stellortus", "age" to 18)

boundDispatcher.execute("hello") // "Hello, Stellortus, aged 18!"
```

## 添加上下文变量

1. 创建上下文类，声明所需的变量

你的上下文类需要继承 `CommandContext`。

```kotlin
class UserContext(val name: String, val age: Int) : CommandContext()
```

2. 使用上下文类构建命令调度器

使用 `buildCommand<UserContext>`，即可在 `execute` 块中使用你的上下文。

```kotlin
val dispatcher = buildCommand<UserContext> {
    literal("hello") {
        execute { "Hello!" }
    }
}
```

3. 直接获取变量

`execute` 块以你的上下文作为接收者，因此可以直接读取变量。

```kotlin
val dispatcher = buildCommand<UserContext> {
    literal("hello") {
        execute {
            "Hello, $name, aged $age!"
        }
    }
}
```

4. 使用 `dispatcher.withContext(context)` 传入上下文实例

`withContext` 返回一个使用指定上下文实例的**新命令调度器**。
使用自定义上下文时，若未提供实例就执行命令，会抛出 `ContextUndefinedException`。

```kotlin
val context = UserContext("Stellortus", 18)
val contextDispatcher = dispatcher.withContext(context)

contextDispatcher.execute("hello") // "Hello, Stellortus, aged 18!"
```

## 权限设置

1. 设置节点的最低权限

在**节点**上下文中使用 `requirePermission(permission)`。数值越大，权限越高。
子节点会继承父节点的权限，因此应在添加子节点**之前**设置权限。
将子节点权限设为低于父节点的值，会抛出 `IllegalPermissionSettingException`。

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

2. 提供当前权限

使用 `dispatcher.permissionProvider { context -> ... }` 从上下文中获取权限，也可以返回固定值。
它会返回一个**新的命令调度器**。默认权限为 `Int.MIN_VALUE`，节点的默认最低权限也是此值。

```kotlin
val permittedDispatcher = dispatcher.permissionProvider { 1 }

permittedDispatcher.execute("hello") // "Hello!"
permittedDispatcher.execute("hello admin") // 抛出 PermissionDeniedException
```

3. 在帮助文本中检查权限

**帮助文本**只会列出当前权限允许访问的节点。

```kotlin
permittedDispatcher.execute("help hello") // "hello"

val adminDispatcher = dispatcher.permissionProvider { 2 }
adminDispatcher.execute("help hello")
// hello
// hello admin
```

## 弃用标记

1. 标记弃用节点

使用 `deprecated(replaceWith)` 为旧命令提供替代命令。

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

2. 声明弃用处理器

使用 `dispatcher.deprecatedResolver { replacement -> ... }`，在弃用命令执行前处理替代提示文本。
请在 `bind`、`withContext` 等调用之后，在最终用于执行命令的调度器上注册处理器。

```kotlin
dispatcher.deprecatedResolver { replacement ->
    println("Use $replacement instead")
}

dispatcher.execute("hallo") // 打印 "Use hello instead"，返回 "Hallo!"
dispatcher.execute("hello") // "Hello!"
```

此标记不会重定向命令：如果处理器正常返回，仍会执行旧命令原有的行为。
执行弃用命令时，如果没有注册处理器，会抛出 `DeprecatedResolverUndefinedException`。
