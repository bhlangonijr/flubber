# Flubber: Lightweight Workflow Engine for JSON/YAML DSL Execution

**Flubber** is a versatile Kotlin-based library designed for constructing workflows and automation tasks using custom Domain-Specific Languages (DSLs). Its primary purpose is to empower developers to create tailored automation solutions, such as chatbot logic, ETL pipelines, or business process orchestrators, by decoupling logic from implementation.

## Key Features

* **DSL Development:** Facilitates the creation of domain-specific languages by enabling the definition of customized actions. These actions can be leveraged to interact with internal messaging systems or external APIs.
* **Workflow Orchestration:** Orchestrates complex workflows, streamlines tasks, and automates processes within an application or distributed system.
* **Kotlin and Java Compatibility:** Offers full interoperability with both Kotlin and Java, providing flexibility for JVM-based environments.
* **Dual Format Support:** Scripts can be written in either JSON or YAML format. The engine automatically detects the format, with YAML offering improved readability for complex configuration files.
* **Extensible Architecture:** Designed for extensibility, allowing developers to build upon core functionality to satisfy specific business requirements.

## Installation

### From Source

```bash
git clone git@github.com:bhlangonijr/flubber.git
cd flubber/
mvn clean compile package install

```

### Dependency Management

Flubber is available via the JitPack repository.

#### Maven

Add the repository and dependency to `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.bhlangonijr</groupId>
    <artifactId>flubber</artifactId>
    <version>0.7.1</version>
</dependency>

```

#### Gradle

Add to `build.gradle`:

```kotlin
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.bhlangonijr:flubber:0.7.1'
}

```

## Usage Principles

The fundamental building blocks of any script in Flubber are "actions." Actions are encapsulated logic units (often external JavaScript or Python scripts) that accept a `context` and `args`. These can be hosted on a web server or stored locally.

**Example Action Definition (JavaScript):**

```javascript
// hello.js - Served by URL: https://localhost:8080/myserver/hello.js
var action = function(context, args) {
    console.log("HELLO: " + args.user);
    return "ok";
}

```

### Scripting a Hello World DSL

The following example demonstrates a script that imports a custom `hello` action and executes it within a sequence.

Scripts can be written in JSON or YAML — the format is auto-detected when loading.

### JSON
```json
{
  "import": [
    {
      "action": "hello",
      "url": "https://localhost:8080/myserver/hello.js"
    }
  ],
  "_comment": "sample hello world script",
  "flow": [
    {
      "id": "main",
      "sequence": [
        {
          "action": "hello",
          "args": {
            "user": "{{session.user}}"
          }
        }
      ]
    }
  ]
}
```

### YAML
```yaml
import:
  - action: "hello"
    url: "https://localhost:8080/myserver/hello.js"
_comment: "sample hello world script"
flow:
  - id: "main"
    sequence:
      - action: "hello"
        args:
          user: "{{session.user}}"
```

### Executing the Script

```kotlin
val args = """
    {
      "session": {
        "user":"john"
      }
    }
"""

Script.from(script)
    .with(args)
    .apply {
        this.onException { e -> println("Error: ${e.message}") }        
    }
    .run()

```

## Built-in Actions

Flubber includes several core actions to facilitate standard workflow logic.

### Expression

Evaluates logic expressions for conditional branching or variable assignment. It supports standard JavaScript syntax.

<details>
<summary><strong>JSON Configuration</strong></summary>

```json
{
  "decision": "expression",
  "args": {
    "condition": "{{DIGITS}} == '1000'",
    "do": {
      "sequence": "greetAndExit",
      "args": { "HANGUP_CODE": "normal" }
    },
    "else": {
      "sequence": "exit",
      "args": { "HANGUP_CODE": "normal" }
    }
  }
}

```

</details>

<details>
<summary><strong>YAML Configuration</strong></summary>

```yaml
decision: expression
args:
  condition: "{{DIGITS}} == '1000'"
  do:
    sequence: greetAndExit
    args:
      HANGUP_CODE: normal
  else:
    sequence: exit
    args:
      HANGUP_CODE: normal

```

</details>

### REST

Executes HTTP requests. Supported methods include `post`, `put`, `get`, and `delete`. The response (status, headers, body) is stored in the variable defined by the `set` argument.

<details>
<summary><strong>JSON Configuration</strong></summary>

```json
{
  "action": "rest",
  "args": {
    "url": "https://exampleserver/api/user",
    "method": "post",
    "body": "{\"name\": \"{{session.user}}\"}",
    "headers": "{\"Content-Type\": \"application/json\"}",
    "set": "httpResponse"
  }
}

```

</details>

<details>
<summary><strong>YAML Configuration</strong></summary>

```yaml
action: rest
args:
  url: "https://exampleserver/api/user"
  method: post
  body: '{"name": "{{session.user}}"}'
  headers: '{"Content-Type": "application/json"}'
  set: httpResponse

```

</details>

### JSON & Jolt Transformation

Parses JSON strings into structured objects for manipulation. It also supports [Jolt](https://github.com/bazaarvoice/jolt) specifications for complex JSON-to-JSON transformations.

<details>
<summary><strong>JSON Configuration</strong></summary>

```json
{
  "action": "json",
  "args": {
    "text": "{\"users\":[{\"username\":\"john\"}]}",
    "spec": "[{\"operation\": \"shift\",\"spec\":{\"users\": {\"*\": {\"username\": \"usernames\"}}}}]",
    "set": "userProfile"
  }
}

```

</details>

<details>
<summary><strong>YAML Configuration</strong></summary>

```yaml
action: json
args:
  text: '{"users":[{"username":"john"}]}'
  spec: '[{"operation": "shift","spec":{"users": {"*": {"username": "usernames"}}}}]'
  set: userProfile

```

</details>

### ForEach

Iterates over a JSON array, executing a specific sequence for each element.
**Parallel Execution:** Set `isParallel` to `true` to process elements concurrently. Results can be aggregated back into a parent variable.

<details>
<summary><strong>JSON Configuration</strong></summary>

```json
{
  "action": "forEach",
  "args": {
    "iterateOver": "object.users",
    "setElement": "forEachElement",
    "isParallel": true,
    "set": "forEachResult",
    "do": {
      "sequence": "greet"
    }
  }
}

```

</details>

<details>
<summary><strong>YAML Configuration</strong></summary>

```yaml
action: forEach
args:
  iterateOver: object.users
  setElement: forEachElement
  isParallel: true
  set: forEachResult
  do:
    sequence: greet

```

</details>

### Menu

Routes execution to specific sequences based on user input or variable matching. Useful for chatbot decision trees.

<details>
<summary><strong>JSON Configuration</strong></summary>

```json
{
  "action": "menu",
  "args": {
    "text": "{{option}}",
    "options": [
      {
        "code": "1",
        "similar": ["greet", "say hi"],
        "do": { "sequence": "hello" }
      },
      {
        "code": "2",
        "do": { "sequence": "exit" }
      }
    ],
    "else": {
      "sequence": "none"
    }
  }
}

```

</details>

<details>
<summary><strong>YAML Configuration</strong></summary>

```yaml
action: menu
args:
  text: "{{option}}"
  options:
    - code: "1"
      similar: ["greet", "say hi"]
      do:
        sequence: hello
    - code: "2"
      do:
        sequence: exit
  else:
    sequence: none

```

</details>

### Run & Exit

`run` executes a nested sequence and returns to the caller upon completion. `exit` halts the script execution immediately.

<details>
<summary><strong>JSON Configuration</strong></summary>

```json
{
  "action": "run",
  "args": {
    "do": {
      "sequence": "greet",
      "args": { "type": "normal" }
    }
  }
}

```

</details>

<details>
<summary><strong>YAML Configuration</strong></summary>

```yaml
action: run
args:
  do:
    sequence: greet
    args:
      type: normal

```

</details>

## Exception Handling

Flubber supports a global exception handling mechanism defined via the `exceptionally` block. This ensures robust workflow execution by catching runtime errors and redirecting to a recovery sequence.

<details>
<summary><strong>JSON Configuration</strong></summary>

```json
{
  "id": "main-flow",
  "flow": [],
  "exceptionally": {
    "action": "run",
    "args": {
      "do": {
        "sequence": "exitWithError",
        "args": { "ERROR": "{{exception.message}}" }
      }
    }
  }
}

```

</details>

<details>
<summary><strong>YAML Configuration</strong></summary>

```yaml
id: main-flow
flow: []
exceptionally:
  action: run
  args:
    do:
      sequence: exitWithError
      args:
        ERROR: "{{exception.message}}"

```

</details>

## Configuration

Flubber supports configuration via JVM system properties or environment variables. System properties take precedence over environment variables.

### REST Action SSL

By default, the `rest` action uses an insecure SSL context that accepts all certificates. This is convenient for development but should be disabled in production.

| Property | Default | Description |
|---|---|---|
| `rest.ssl.insecure` | `true` | When `true`, accepts all SSL certificates without validation. Set to `false` to use the JVM's default truststore for certificate verification. |

**Example — enable secure SSL:**

```bash
# Via JVM system property
java -Drest.ssl.insecure=false -jar myapp.jar

# Via environment variable
export rest.ssl.insecure=false
```

### Script Engine

The GraalVM JavaScript engine used by `expression` and `javascript` actions can be tuned with the following properties:

| Property | Default | Description |
|---|---|---|
| `script.allowHostAccess` | `true` | Allow scripts to access Java host objects |
| `script.allowNativeAccess` | `true` | Allow native system access |
| `script.allowHostClassLookup` | `true` | Allow scripts to look up Java classes |
| `script.allowExperimentalOptions` | `true` | Allow experimental GraalVM options |
| `script.allowCreateThread` | `true` | Allow scripts to create threads |
| `script.js.nashorn-compat` | `true` | Enable Nashorn compatibility mode |