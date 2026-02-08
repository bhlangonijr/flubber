**Flubber**: A Lightweight Workflow Engine for JSON/YAML DSL Script Execution

[![](https://jitpack.io/v/bhlangonijr/flubber.svg)](https://jitpack.io/#bhlangonijr/flubber)

Flubber is a versatile Kotlin-based library designed for constructing workflows and automation tasks using custom [domain-specific languages (DSLs)](https://en.wikipedia.org/wiki/Domain-specific_language). Its primary purpose is to empower developers to create tailored automation solutions, such as chatbot DSLs, uniquely suited for specific business needs.

**Key Features:**

- **DSL Development:** Flubber facilitates the creation of domain-specific languages by enabling you to define customized actions. These actions can be leveraged to interact with your business's internal messaging system, allowing you to send and receive messages seamlessly.

- **Workflow Orchestration:** Flubber empowers you to orchestrate complex workflows, streamlining tasks, and automating processes within your application or system.

- **Kotlin and Java Compatibility:** Whether you're working with Kotlin or Java, Flubber offers compatibility with both programming languages, giving you the flexibility to choose your preferred environment.

- **JSON and YAML Support:** Scripts can be written in either JSON or YAML format. YAML offers improved readability for complex workflows. The format is detected automatically — no configuration needed.

- **Extensible and Lightweight:** Flubber is designed with extensibility in mind, allowing you to build on top of its core functionality to cater to your specific use cases. It is lightweight and easy to integrate into your projects.

If you're looking for a practical way to build custom languages and streamline workflows, Flubber is a handy library to explore.

Feel free to dive into Flubber today to simplify automation and tailor it to your business needs!

# Building/Installing

## From source

```
$ git clone git@github.com:bhlangonijr/flubber.git
$ cd flubber/
$ mvn clean compile package install
```

## From repo

Flubber dependency can be added via the jitpack repository.

## Maven

```xml

<repositories>
    ...
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

```xml

<dependency>
    <groupId>com.github.bhlangonijr</groupId>
    <artifactId>flubber</artifactId>
    <version>0.7.0</version>
</dependency>
```

## Gradle

```
repositories {
    ...
    maven { url 'https://jitpack.io' }
}
```

```
dependencies {
    ...
    implementation 'com.github.bhlangonijr:flubber:0.7.0'
    ...
}
```

# Usage

In this scripting language, the fundamental building blocks of any script are referred to as "actions." These actions are encapsulated within external JavaScript or Python files, each containing a function that accepts two arguments: context and args. These actions can be hosted on any web server as dynamic or static content or can be stored as local files. For example:

```javascript
// hello action. Served by URL: https://localhost:8080/myserver/hello.js
var action = function(context, args) {
    console.log("HELLO: " + args.user);
    return "ok";
}
```  

## Scripting a Hello World DSL

To demonstrate the usage of this language, let's create a simple "Hello World" script. This script writes a welcome message to the console using a custom action named `hello`, which is imported into the script. The `hello` action is fetched from the specified URL.

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

## Running the script

```kotlin
val args = 
    """
        {
          "session":{
          "user":"john"
          }
        }
    """

Script
    .from(script)
    .with(args)
    .apply {
        this.onException { e -> println("Oops ${e.message}") }        
    }
    .run()
```

## Built-in actions

Some out-of-box actions are available for building basic flows:

## expression

Evaluates a logic expression for conditionally executing sequences.

```json

{
  "decision": "expression",
  "args": {
    "condition": "{{DIGITS}} == '1000'",
    "do": {
      "sequence": "greetAndExit",
      "args": {
        "HANGUP_CODE": "normal"
      }
    },
    "else": {
      "sequence": "exit",
      "args": {
        "HANGUP_CODE": "normal"
      }
    }
  }
}
```

Alternatively, it can be used to evaluate arbitrary javascript statements.

```json

{
  "action": "expression",
  "args": {
    "text": "\"{{DIGITS}}\".substring(0, 4)",
    "set": "firstDigits"
  }
}
```

The attribute `set` instructs the engine to store the result of the expression in the variable `firstDigits`.

## exit

Halts execution of a script.

```json

{
  "action": "exit"
}
```

## run

Executes a sequence, returning to the calling sequence after finished.

```json

{
  "action": "run",
  "args": {
    "do": {
      "sequence": "greet",
      "args": {
        "greet_type": "normal"
      }
    }
  }
}
```

## rest

Call a REST/HTTP endpoint using specified params. Available methods: `post`, `put`, `get`, `delete`.

```json

{
  "action": "rest",
  "args": {
    "url": "https://exampleserver/api/user",
    "method": "post",
    "body": "{\"name\": \"{{session.user}}\"}",
    "headers": "{\"Content-Type\": \"application/json\", \"Accept\": \"*/*\"}",
    "set": "httResponse"
  }
}
```

The response object contains a HTTP `status` code, `headers` and an optional `body`, e.g.,

```json

{
  "status": "200",
  "body": {
    "result": "OK"
  },
  "headers": {
    "content-length": 20,
    "content-type": "application/json; charset=utf-8"
  }
}
```

## json

The `json` action aids parsing json strings into structured objects so that it can be easily manipulated by other
actions as when you want to extract certain attribute values.

In the example below `body` from the `httpResponse` has been parsed as a JSON object and result set to `userProfile`:

```json

{
  "action": "json",
  "args": {
    "text": "{{httResponse.body}}",
    "set": "userProfile"
  }
}
```

The field values can be resolved using mustaches further on `{{userProfile.name}}` and accessed through the
use of Json Pointer [specification](https://www.rfc-editor.org/rfc/rfc6901).

### json specs

JSON to JSON transformation is possible by specifying [jolt specs](https://github.com/bazaarvoice/jolt).

```json

{
  "action": "json",
  "args": {
    "text": "{\"users\":[{\"username\":\"john\"},{\"username\":\"mary\"},{\"username\":\"alice\"}]}",
    "spec": "[{\"operation\": \"shift\",\"spec\":\"users\": {\"*\": {\"username\": \"usernames\"}}}}]",
    "set": "userProfile"
  }
}
```

input json:

```json
{
  "users": [
    {
      "username": "john"
    },
    {
      "username": "mary"
    },
    {
      "username": "alice"
    }
  ]
}

```

output json by using the transformation spec:

```json
{
  "usernames": [
    "john",
    "mary",
    "alice"
  ]
}
```

## forEach

Iterates over a JSON array by calling a specified sequence for each of its elements.

```json
{
  "action": "forEach",
  "args": {
    "iterateOver": "object.users",
    "setElement": "forEachElement",
    "do": {
      "sequence": "greet"
    }
  }
}
```

### Iteration parallelism

To execute iteration in parallel for each input array element, set the `isParallel` property to `true` 
and use the `forEach` action. If the child sequence sets a local variable with the same name as the 
parent variable `forEach`'s action it will collect and aggregate all child values in the 
parent array variable.  


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

Example:

```json

{
  "id": "greet",
  "sequence": [
    {
      "action": "expression",
      "args": {
        "text": "Hello {{username}}",
        "set": "forEachResult"
      }
    }
  ]
}
```

Variable `forEachResult` declared in the local scope of parent sequence containing `forEach` will be set to:

```json
["Hello john", "Hello mary", "Hello alice"]
```

## menu

Run a specific sequence based on the option selected by the user.

```json
        {
  "action": "menu",
  "args": {
    "text": "{{option}}",
    "options": [
      {
        "code": "1",
        "similar": ["greet", "say hi"],
        "do": {
          "sequence": "hello",
          "args": {
            "username": "{{username}}"
          }
        }
      },
      {
        "code": "2",
        "similar": ["bye", "say goodbye"],
        "do": {
          "sequence": "exit",
          "args": {
            "username": "{{username}}"
          }
        }
      }
    ],
    "else": {
      "sequence": "none",
      "args": {
        "username": "none selected"
      }
    }
  }
}
```

## Handling exceptions

Exceptions can be caught and handled by adding the `exceptionally` object and calling a custom sequence with the
`run` action.

```json
{
  "id": "simple-call-flow",
  "flow": [
     
  ],
  "exceptionally": {
    "action": "run",
    "args": {
      "do": {
        "sequence": "exitWithError",
        "args": {
          "ERROR": "{{exception.message}}"
        }
      }
    }
  }
}
```