package com.example

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {

    @Test
    fun `health endpoint reports the service is up`() = testApplication {
        application {
            rootModule()
        }
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/health").status)
    }

    @Test
    fun `inspection routes require authentication`() = testApplication {
        application {
            rootModule()
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/inspections").status)
    }
}
