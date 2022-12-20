Flubber
=========================

[![](https://jitpack.io/v/bhlangonijr/flubber.svg)](https://jitpack.io/#bhlangonijr/flubber)

Flubber is a simple kotlin/java library for building workflow and automation
task [domain-specific languages](https://en.wikipedia.org/wiki/Domain-specific_language). 
A typical use case is for example the creation of a chatbot DSL for a certain business. 
One could do that by adding customized actions which can be used to send and receive messages 
within the internal messaging system of that business. 


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
    <version>0.3.12</version>
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
    implementation 'com.github.bhlangonijr:flubber:0.3.12'
    ...
}
```

# Usage

The building blocks of any scripts are the actions: an external Javascript or Python file containing 
a function having arguments `context` and `args`. 
These actions can be served by any web server as dynamic/static content or as local files, e.g.,

```javascript
// hello action. Served by URL: https://localhost:8080/myserver/hello.js
var action = function(context, args) {
    console.log("HELLO: " + args.user);
    return "ok";
}
```  

## Scripting a Hello World DSL

The hello world script below writes a hello message in the console by using the custom action
`hello` imported in the script: 

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

## Running the script

```kotlin

val script = Script.from(scriptText)
val args = """
        {
          "session":{
          "user":"john"
          }
        }
    """
FlowEngine().run { script.with(args) }
    .onException { e -> e.printStackTrace() }

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