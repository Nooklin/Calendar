package org.nooklin.calendar.data.caldav

import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** A problem with a message that can be shown to the person as it is. */
class DavException(message: String) : Exception(message)

/** The answer of the server. [url] is the address that answered, after any redirect. */
class DavResponse(val code: Int, val body: String, val etag: String?, val url: HttpUrl)

/**
 * One resource of a multistatus answer. [props] holds the properties that came back with status 200 by their local
 * name; a property that wraps an address (current-user-principal, calendar-home-set) holds that address.
 */
class DavEntry(val href: String, val props: Map<String, String>, val types: Set<String>, val components: Set<String>)

/** The few WebDAV and CalDAV requests this app needs, with Basic login over https. */
class DavClient(private val username: String, private val password: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // A redirect from https to http would send the password in the clear
        .followSslRedirects(false)
        .build()

    fun send(
        method: String,
        url: String,
        body: String? = null,
        contentType: String = XML,
        headers: Map<String, String> = emptyMap()
    ): DavResponse {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password, Charsets.UTF_8))
        headers.forEach { (name, value) -> builder.header(name, value) }
        builder.method(method, body?.toRequestBody(contentType.toMediaType()))
        http.newCall(builder.build()).execute().use { response ->
            return DavResponse(response.code, response.body?.string().orEmpty(), response.header("ETag"), response.request.url)
        }
    }

    companion object {
        const val XML = "application/xml; charset=utf-8"
        const val ICS = "text/calendar; charset=utf-8"
    }
}

private fun Element.kids(): List<Element> {
    val out = ArrayList<Element>()
    var node = firstChild
    while (node != null) {
        if (node is Element) out.add(node)
        node = node.nextSibling
    }
    return out
}

private fun Element.name(): String = localName ?: nodeName.substringAfter(':')

/** Reads the answer to a PROPFIND or REPORT. */
fun parseMultistatus(xml: String): List<DavEntry> {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    try {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    } catch (e: Exception) {
        // This parser does not know the setting
    }
    val root = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    val out = ArrayList<DavEntry>()
    for (response in root.kids().filter { it.name() == "response" }) {
        val href = response.kids().firstOrNull { it.name() == "href" }?.textContent?.trim() ?: continue
        val props = HashMap<String, String>()
        val types = HashSet<String>()
        val components = HashSet<String>()
        for (propstat in response.kids().filter { it.name() == "propstat" }) {
            val status = propstat.kids().firstOrNull { it.name() == "status" }?.textContent.orEmpty()
            if (!status.contains(" 200")) continue
            val prop = propstat.kids().firstOrNull { it.name() == "prop" } ?: continue
            for (item in prop.kids()) {
                when (item.name()) {
                    "resourcetype" -> item.kids().forEach { types.add(it.name()) }
                    "supported-calendar-component-set" -> item.kids().forEach { comp ->
                        comp.getAttribute("name").takeIf { it.isNotEmpty() }?.let { components.add(it.uppercase()) }
                    }
                    else -> {
                        val nested = item.kids().firstOrNull { it.name() == "href" }
                        props[item.name()] = (nested?.textContent ?: item.textContent).trim()
                    }
                }
            }
        }
        out.add(DavEntry(href, props, types, components))
    }
    return out
}
