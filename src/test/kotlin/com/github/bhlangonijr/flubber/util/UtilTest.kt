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

        Util.bindVars(correctArgs, globalArgs)

        assertEquals("hello ben-hur, press 1000 to greet or 2000 to quit.", correctArgs["text"] as String)

        val incorrectArgs = nodeToMap(
            mapper.readTree(
                """{
            "text": "hello {{session.user, press {{option1}} to greet or {option2} to quit."
        }""".trimIndent()
            )
        )

        Util.bindVars(incorrectArgs, globalArgs)

        assertEquals(
            "hello {{session.user, press {{option1}} to greet or {option2} to quit.",
            incorrectArgs["text"] as String
        )

    }
}