package com.github.bhlangonijr.flubber.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.bhlangonijr.flubber.util.Util.Companion.nodeToMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UtilTest {

    private val mapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `test variable binding`() {

        val globalArgs = mapper.readTree(
            """ 
        {
          "session": {
            "user": "ben-hur"      
          },
          "option1":"1000",
          "option2":"2000"
        }
        """.trimIndent()
        )

        val correctArgs = nodeToMap(
            mapper.readTree(
                """{
            "text": "hello {{session.user}}, press {{option1}} to greet or {{option2}} to quit."
        }""".trimIndent()
            )
        )

        Util.bindVars("", correctArgs, globalArgs)

        assertEquals("hello ben-hur, press 1000 to greet or 2000 to quit.", correctArgs["text"] as String)

        val incorrectArgs = nodeToMap(
            mapper.readTree(
                """{
            "text": "hello {{session.user, press {{option1}} to greet or {option2} to quit."
        }""".trimIndent()
            )
        )

        Util.bindVars("", incorrectArgs, globalArgs)

        assertEquals(
            "hello {{session.user, press {{option1}} to greet or {option2} to quit.",
            incorrectArgs["text"] as String
        )
    }

    @Test
    fun `test variable binding on lists`() {

        val globalArgs = mapper.readTree(
            """ 
        {
          "session": {
            "user": "ben-hur"      
          },
          "option1":"1000",
          "option2":"2000"
        }
        """.trimIndent()
        )

        val arrayArgs = nodeToMap(
            mapper.readTree("""{
                "list": [{"prop1": "{{session.user}}"},{"prop1": "one"},{"prop1": "{{session.user}}"}]
                }""".trimIndent()
            )
        )
        Util.bindVars("", arrayArgs, globalArgs)
        assertEquals(
            "ben-hur",
            ((arrayArgs["list"] as List<*>)[0] as Map<*, *>)["prop1"]
        )

        val arrayStringArgs = nodeToMap(
            mapper.readTree("""{
                "list": ["{{session.user}}","one","{{session.user}}"]
                }""".trimIndent()
            )
        )
        Util.bindVars("", arrayStringArgs, globalArgs)
        assertEquals(
            "ben-hur",
            ((arrayStringArgs["list"] as List<*>)[0] as String)
        )
    }
}