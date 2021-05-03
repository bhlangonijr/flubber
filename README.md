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
  <version>0.3.3</version>
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
    implementation 'com.github.bhlangonijr:flubber:0.3.3'
    ...
}
```

# Usage

The building blocks of any scripts are the actions. Currently actions written in Javascript and Python are supported as
long as the expected interface is implemented - a simple function having arguments `context` and `args`. These actions
can be served by any web server as dynamic/static content or as local files, e.g.:

```javascript
    // hello action. Example URL: https://mywebsite.com/hello.js
    var action = function(context, args) {
        return "HELLO: " + args["user"]
    }
```  

## Scripting a Hello World DSL

```json
{
  "@id": "hello-world-script",
  "author": {
    "name": "YourName",
    "e-mail": "me@email.com"
  },
  "import": [
    {
      "action": "hello",
      "url": "https://mywebsite.com/hello.js"
    }
  ],
  "_comment": "sample hello world script",
  "flow": [
    {
      "@id": "main",
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
      "@id": "exitWithError",
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

under construction...