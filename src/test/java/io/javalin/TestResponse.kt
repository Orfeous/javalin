/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 *
 */

package io.javalin

import com.mashape.unirest.http.HttpMethod
import io.javalin.core.util.Header
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import javax.servlet.http.Cookie

class TestResponse {

    private val MY_BODY = (""
            + "This is my body, and I live in it. It's 31 and 6 months old. "
            + "It's changed a lot since it was new. It's done stuff it wasn't built to do. "
            + "I often try to fill if up with wine. - Tim Minchin")

    @Test
    fun `setting a String result works`() = TestUtil.test { app, http ->
        app.get("/hello") { ctx ->
            ctx.status(418).result(MY_BODY).header("X-HEADER-1", "my-header-1").header("X-HEADER-2", "my-header-2")
        }
        val response = http.call(HttpMethod.GET, "/hello")
        assertThat(response.status).isEqualTo(418)
        assertThat(response.body).isEqualTo(MY_BODY)
        assertThat(response.headers.getFirst("X-HEADER-1")).isEqualTo("my-header-1")
        assertThat(response.headers.getFirst("X-HEADER-2")).isEqualTo("my-header-2")
    }

    @Test
    fun `setting an InputStream result works`() = TestUtil.test { app, http ->
        val buf = ByteArray(65537) // big and not on a page boundary
        Random().nextBytes(buf)
        app.get("/stream") { ctx -> ctx.result(ByteArrayInputStream(buf)) }
        val response = http.call(HttpMethod.GET, "/stream")
        val bout = ByteArrayOutputStream()
        assertThat(IOUtils.copy(response.rawBody, bout)).isEqualTo(buf.size)
        assertThat(buf).isEqualTo(bout.toByteArray())
    }

    @Test
    fun `setting an InputStream result works and InputStream is closed`() = TestUtil.test { app, http ->
        val path = "src/test/my-file.txt"
        File(path).printWriter().use { out ->
            out.print("Hello, World!")
        }
        app.get("/file") { ctx ->
            ctx.result(FileUtils.openInputStream(File(path)))
        }
        assertThat(http.getBody("/file")).isEqualTo("Hello, World!")
        assertThat(File(path).delete()).isEqualTo(true)
    }

    @Test
    fun `redirect in before-handler works`() = TestUtil.test { app, http ->
        app.before("/before") { ctx -> ctx.redirect("/redirected") }
        app.get("/redirected") { ctx -> ctx.result("Redirected") }
        assertThat(http.getBody("/before")).isEqualTo("Redirected")
    }

    @Test
    fun `redirect in exception-mapper works`() = TestUtil.test { app, http ->
        app.get("/get") { throw Exception() }
        app.exception(Exception::class.java) { _, ctx -> ctx.redirect("/redirected") }
        app.get("/redirected") { ctx -> ctx.result("Redirected") }
        assertThat(http.getBody("/get")).isEqualTo("Redirected")
    }

    @Test
    fun `redirect in normal handler works`() = TestUtil.test { app, http ->
        app.get("/hello") { ctx -> ctx.redirect("/hello-2") }
        app.get("/hello-2") { ctx -> ctx.result("Redirected") }
        assertThat(http.getBody("/hello")).isEqualTo("Redirected")
    }

    @Test
    fun `redirect with status works`() = TestUtil.test { app, http ->
        app.get("/hello") { ctx -> ctx.redirect("/hello-2", 301) }
        app.get("/hello-2") { ctx -> ctx.result("Redirected") }
        http.disableUnirestRedirects()
        assertThat(http.call(HttpMethod.GET, "/hello").status).isEqualTo(301)
        http.enableUnirestRedirects()
        assertThat(http.call(HttpMethod.GET, "/hello").body).isEqualTo("Redirected")
    }

    @Test
    fun `redirect to absolute path works`() = TestUtil.test { app, http ->
        app.get("/hello-abs") { ctx -> ctx.redirect("${http.origin}/hello-abs-2", 303) }
        app.get("/hello-abs-2") { ctx -> ctx.result("Redirected") }
        http.disableUnirestRedirects()
        assertThat(http.call(HttpMethod.GET, "/hello-abs").status).isEqualTo(303)
        http.enableUnirestRedirects()
        assertThat(http.call(HttpMethod.GET, "/hello-abs").body).isEqualTo("Redirected")
    }

    @Test
    fun `setting a cookie works`() = TestUtil.test { app, http ->
        app.get("/create-cookie") { ctx -> ctx.cookie("Test", "Tast") }
        app.get("/get-cookie") { ctx -> ctx.result(ctx.cookie("Test")!!) }
        assertThat(http.get("/create-cookie").headers.getFirst(Header.SET_COOKIE)).isEqualTo("Test=Tast;Path=/")
        assertThat(http.getBody("/get-cookie")).isEqualTo("Tast")
    }

    @Test
    fun `setting a Cookie object works`() = TestUtil.test { app, http ->
        app.get("/create-cookie") { ctx -> ctx.cookie(Cookie("Hest", "Hast").apply { maxAge = 7 }) }
        assertThat(http.get("/create-cookie").headers.getFirst(Header.SET_COOKIE)).contains("Hest=Hast")
        assertThat(http.get("/create-cookie").headers.getFirst(Header.SET_COOKIE)).contains("Max-Age=7")
    }

    // Fix for https://github.com/tipsy/javalin/issues/543
    @Test
    fun `reading the result string resets the stream`() = TestUtil.test { app, http ->
        val result = "Hello World"

        app.get("/test") { context ->
            context.result(result)
            context.resultString()
        }

        assertThat(http.getBody("/test")).isEqualTo(result)
    }

}
