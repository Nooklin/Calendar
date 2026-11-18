package org.nooklin.calendar.data.caldav

/**
 * The kinds of CalDAV server the connect dialog knows. Each has the address and the login that it wants, taken from the
 * documentation of the server or the service. [path] is added to the address that is entered, unless it holds [marker].
 */
enum class CaldavService(
    val key: String,
    val label: String,
    val example: String,
    // A fixed server of a service is filled in already
    val prefill: String,
    val path: String,
    val marker: String,
    val userLabel: String,
    val passwordLabel: String,
    val hint: String
) {
    OTHER(
        "other", "Other", "https://caldav.example.com", "", "", "", "Username", "Password",
        "Enter the address of your CalDAV server. It must start with https://. If you only enter the website and no calendars are found, " +
            "add the path of the server software: pick the kind of server above to see which. Google and Microsoft accounts are not supported."
    ),
    NEXTCLOUD(
        "nextcloud", "Nextcloud", "https://cloud.example.com", "", "/remote.php/dav", "remote.php", "Username", "App password",
        "Enter the web address of your Nextcloud, for example https://cloud.example.com. The app adds /remote.php/dav. " +
            "If Nextcloud runs in a folder, include it: https://example.com/nextcloud. " +
            "Use an app password, made in the security settings of your Nextcloud. It is required when you use two-factor login."
    ),
    OWNCLOUD(
        "owncloud", "ownCloud", "https://cloud.example.com", "", "/remote.php/dav", "remote.php", "Username", "Password",
        "Enter the web address of your ownCloud. The app adds /remote.php/dav, which is right for ownCloud 9 and newer."
    ),
    RADICALE(
        "radicale", "Radicale", "https://example.com/radicale/", "", "", "", "Username", "Password",
        "Enter the address of Radicale including its folder, for example https://example.com/radicale/. " +
            "Use the username and password of your Radicale users."
    ),
    BAIKAL(
        "baikal", "Baikal", "https://example.com/baikal", "", "/dav.php", "dav.php", "Username", "Password",
        "Enter the web address of Baikal with its folder, for example https://example.com/baikal. The app adds /dav.php. " +
            "For newer versions the address ends in /html/dav.php: type it in full then."
    ),
    SYNOLOGY(
        "synology", "Synology", "https://diskstation.local:5001/caldav/username", "", "", "", "Username", "Password",
        "Open Synology Calendar in the browser, go to Calendar > CalDAV Account and copy the macOS / iOS URL. " +
            "It looks like https://diskstation.local:5001/caldav/username. The server needs a certificate that your phone trusts."
    ),
    FASTMAIL(
        "fastmail", "Fastmail", "https://caldav.fastmail.com/", "https://caldav.fastmail.com/", "", "", "Email address", "App password",
        "Use your full Fastmail email address and an app password with access to calendars. Your normal password does not work."
    ),
    ICLOUD(
        "icloud", "iCloud", "https://caldav.icloud.com/", "https://caldav.icloud.com/", "", "", "Apple Account email", "App-specific password",
        "Use your Apple Account email address and an app-specific password, made at account.apple.com under Sign-In and Security. " +
            "Your normal password does not work."
    ),
    POSTEO(
        "posteo", "Posteo", "https://posteo.de:8443/", "https://posteo.de:8443/", "", "", "Email address", "App password",
        "Use your full Posteo email address and an app password."
    );

    /** The address as it is used: with a scheme, and with [path] added when it is not in it yet. */
    fun address(input: String): String {
        val text = input.trim()
        if (text.isEmpty() || path.isEmpty()) return text
        val withScheme = if (text.contains("://")) text else "https://$text"
        return if (withScheme.contains(marker, ignoreCase = true)) withScheme else withScheme.trimEnd('/') + path
    }

    companion object {
        fun fromKey(key: String): CaldavService = entries.find { it.key == key } ?: OTHER
    }
}
