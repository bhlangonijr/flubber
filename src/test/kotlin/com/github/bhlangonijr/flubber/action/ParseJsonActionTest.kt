package com.github.bhlangonijr.flubber.action

import com.github.bhlangonijr.flubber.util.Util.Companion.makeJson
import com.github.bhlangonijr.flubber.util.Util.Companion.objectToNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ParseJsonActionTest {


    private val context = makeJson()

    private val json = """
    {
       "users":[
          {
             "username":"john"
          },
          {
             "username":"mary"
          },
          {
             "username":"alice"
          }
       ]
    }        
    """
    private val spec = """
    [
        {
            "operation": "shift",
            "spec": {
                "users": {
                    "*": {
                        "username": "usernames"
                    }
                }
            }
        }
    ]        
    """

    @Test
    fun `test simple json object parsing`() {

        val action = ParseJsonAction()

        val result = objectToNode(action.execute(context, mutableMapOf(Pair("text", json))))

        assertEquals("john", result["users"][0]["username"].asText())
        assertEquals("mary", result["users"][1]["username"].asText())
        assertEquals("alice", result["users"][2]["username"].asText())
    }


    @Test
    fun `test json object parsing using transformation spec`() {

        val action = ParseJsonAction()

        val result = objectToNode(action.execute(context, mutableMapOf(Pair("text", json), Pair("spec", spec))))

        assertEquals("john", result["usernames"][0].asText())
        assertEquals("mary", result["usernames"][1].asText())
        assertEquals("alice", result["usernames"][2].asText())
    }
}