Flubber 
=========================

[![](https://jitpack.io/v/bhlangonijr/flubber.svg)](https://jitpack.io/#bhlangonijr/flubber)

Flubber is a simple kotlin/java library for building workflow and automation task [domain-specific languages](https://en.wikipedia.org/wiki/Domain-specific_language).
 

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
  <version>0.1.0</version>
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
    implementation 'com.github.bhlangonijr:flubber:0.1.0'
    ...
}
```

# Usage

## Run a json script

```kotlin
    fun `running script`() {
    
            val engine = FlowEngine()
    
            engine.register("answer", JavascriptAction(answerAction))
            engine.register("hangup", JavascriptAction(hangupAction))
            engine.register("say") {
                object : Action {
                    override fun execute(context: JsonNode, args: Map<String, Any?>): Any? {
                        queue.offer(args["text"] as String)
                        return "ok"
                    }
                }
            }
            engine.register("expression", ExpressionAction())
    
            val script = Script.from(loadResource("/script-example.json"))        
            val args = """
                {
                  "session":{
                  "user":"john"
                  }
                }
            """

            engine.run { script.with(args) }

    }
```

under construction...