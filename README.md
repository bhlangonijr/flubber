Flubber
=========================

[![](https://jitpack.io/v/bhlangonijr/flubber.svg)](https://jitpack.io/#bhlangonijr/flubber)

Flubber is a simple kotlin/java library for building workflow and automation
task [domain-specific languages](https://en.wikipedia.org/wiki/Domain-specific_language).

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
  <version>0.3.4</version>
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
    implementation 'com.github.bhlangonijr:flubber:0.3.4'
    ...
}
```

# Usage

The building blocks of any scripts are the actions. Currently, Javascript and Python actions are supported 
as long as they implement the expected interface - a simple function having arguments `context` and `args`. These actions
can be served by any web server as dynamic/static content or as local files, e.g.,

```javascript
    // hello action. Served by URL: https://localhost:8080/myserver/hello.js
    var action = function(context, args) {
        return "HELLO: " + args["user"]
    }
```  

## Scripting a Hello World DSL

```json
{
  "id": "hello-world-script",
  "author": {
    "name": "YourName",
    "e-mail": "me@email.com"
  },
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
            "user": "user {{session.user}}"
          }
        }
      ]
    },
    {
      "id": "exitWithError",
      "sequence": [
        {
          "action": "hello",
          "args": {
            "text": "user {{session.user}} your got an error {{ERROR}}"
          }
        }
      ]
    }
  ],
  "exceptionally": {
    "do": {
      "sequence": "exitWithError",
      "args": {
        "ERROR": "{{exception.message}}"
      }
    }
  }
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
            "condition": "{{DIGITS}} == '1000'"
          },
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
```

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
          "do": {
            "sequence": "greet",
            "args": {
              "greet_type": "normal"
            }
          }
        }
```

## rest

Call a REST/HTTP endpoint using specified params.
Available methods: `post`, `put`, `get`, `delete`.

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

The `json` action aids parsing json strings into structured objects so that it can be easily 
manipulated by other actions as when you want to extract certain attribute values.

In the example below `body` from the `httpResponse` has been parsed as a JSON object and result
set to `userProfile`:
```json

        {
          "action": "json",
          "args": {
            "text": "{{httResponse.body}}",
            "set": "userProfile"
          }
        }
```

The field values can be resolved using mustaches further on `{{userProfile.name}}`.

