package org.nooklin.calendar.data.caldav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultistatusTest {
    @Test
    fun readsCalendarsAndTheirProperties() {
        val xml = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:ic="http://apple.com/ns/ical/">
              <d:response>
                <d:href>/dav/home/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/home/work/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
                    <d:displayname>Work</d:displayname>
                    <ic:calendar-color>#FF3B30FF</ic:calendar-color>
                    <c:supported-calendar-component-set><c:comp name="VEVENT"/><c:comp name="VTODO"/></c:supported-calendar-component-set>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
                <d:propstat><d:prop><d:getetag/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>
              </d:response>
            </d:multistatus>"""
        val entries = parseMultistatus(xml)
        assertEquals(2, entries.size)
        val work = entries[1]
        assertEquals("/dav/home/work/", work.href)
        assertTrue("calendar" in work.types)
        assertEquals("Work", work.props["displayname"])
        assertEquals("#FF3B30FF", work.props["calendar-color"])
        assertEquals(setOf("VEVENT", "VTODO"), work.components)
        assertTrue(!work.props.containsKey("getetag"))
    }

    @Test
    fun anAddressInsideAPropertyIsTakenOut() {
        val xml = """<d:multistatus xmlns:d="DAV:"><d:response><d:href>/</d:href><d:propstat><d:prop>
            <d:current-user-principal><d:href>/principals/me/</d:href></d:current-user-principal>
            </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""
        assertEquals("/principals/me/", parseMultistatus(xml)[0].props["current-user-principal"])
    }
}
