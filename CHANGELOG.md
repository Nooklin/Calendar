# Changelog

All notable changes to Nooklin Calendar, newest first. Each GitHub release has its own notes: https://github.com/Nooklin/Calendar/releases

## Version 1.0.2

- Backup and restore (Settings > Import and export): one encrypted file (.nkb) with your calendars and events, CalDAV accounts with their password, link calendars and settings. You choose what goes in (all on by default) and protect it with a password (at least 8 characters) or a 6-digit PIN. A restore replaces the parts that are in the backup, after a confirmation, and a backup from another version is read as far as possible, with a list of what could not be taken over. The app lock and the screenshot setting are not restored.

## Version 1.0.1

- App icon update: a new launcher icon in the Nooklin colors (yellow, terracotta, cocoa and cream), also as a single color icon for themed icons.

## Version 1.0.0

- First release version. The look follows Nooklin Photos: the settings with a heading above every group, one short line of explanation under it and choices in a list, About with Changelog, GitHub, Privacy and Terms, shared design tokens, a calmer dark glass (90 percent for the cards in black mode), the same empty state in the lists (a short text and a New event button), everything you tap is at least 48 dp, and a setting for vibration feedback (Settings > Appearance, on by default) on the time wheels, the view menu and the switches, and the Developer screen is removed.

## Version 0.0.43

- Background sync of followed calendars and CalDAV with WorkManager (on or off, how often from every 15 minutes to every day, Wi-Fi only), and the widgets and reminders are updated after each sync; the widgets are also redrawn every 15 minutes.

## Version 0.0.42

- Fix for the build error of 0.0.40 and 0.0.41 (quotes in a Help text).

## Version 0.0.41

- Every widget has a fixed preview with example events in the list where you choose a widget (no longer set by the app, so all nine show one, on every Android version from 12).

## Version 0.0.40

- The travel time shows in the Overview, the Month Details and List layouts, the dashboard and the list widgets, and the end time buttons are a grid of two by two with a short title.

## Version 0.0.39

- Travel time is a wheel, and the end time buttons (+15 min and so on) scroll sideways in one row.

## Version 0.0.38

- The minute wheel carries over to the hour (55 to 00 goes to the next hour, and back), and Travel time for a new or changed event (5 min to 2 hours): a light block before the start in the week and day view, a line in the details, reminders that go off that much earlier, and kept in .ics files and on CalDAV servers.

## Version 0.0.37

- Time picked with wheels (5 minute steps, and +15 min, +30 min, +1 h, +2 h buttons for the end), accent colour in the date picker, colour dots in the calendar menu of a new event, and a fix for the build error of 0.0.36 (missing import).

## Version 0.0.36

- Date and time typed in the title show as chips above the keyboard (English and Dutch), a question when you save an event without a location, Dutch holidays (can be turned off in Settings > Calendar), and the Year view is removed.

## Version 0.0.35

- The widget of today and tomorrow fills the space when you make it bigger or smaller (one layout per height, chosen by the launcher).

## Version 0.0.34

- New 4x2 widget Calendar today and tomorrow (events of today on the left, of tomorrow on the right).

## Version 0.0.33

- Fix for widgets that said Can't load widget (a View in the widget layouts is not allowed), and real previews in the list of widgets on Android 15 and newer.

## Version 0.0.32

- Tour with short bullet points and a taller card.

## Version 0.0.31

- The tour takes you through the app: the button pulses, you tap it, the screen opens and a card explains it (views, new event, calendars, dashboard, adding calendars in Settings).

## Version 0.0.30

- New welcome text of the tour (readable in dark mode) and the button that a tour step explains pulses.

## Version 0.0.29

- Tour at the first start (and under Settings > Tour), a switch to turn the dashboard off, centred values in the dashboard settings and shorter notes.

## Version 0.0.28

- A bottom bar in three pills (Dashboard and Calendar switch, plus for a new event, Today and Calendars), and one View menu in the top bar with the view and the month layout.

## Version 0.0.27

- Dashboard with twelve cards (tonight, free evenings, up next, today in numbers, free blocks, week load, weekend, overlaps, find time, all-day events, reminders, sync), a Dashboard and Calendar button at the bottom, and Settings > Dashboard (on or off, drag to reorder, compact or extended, evening and daytime, calendars that count).

## Version 0.0.26

- The selected day in the List layout shows stronger in dark mode.

## Version 0.0.25

- Swipe left or right on the day list in the List layout to change the day.

## Version 0.0.24

- CalDAV kinds of server (Nextcloud, ownCloud, Radicale, Baikal, Synology, Fastmail, iCloud, Posteo) with the right path added, and CalDAV calendars grouped per service.

## Version 0.0.23

- Wider CalDAV dialog and a message when the app has no internet access.

## Version 0.0.22

- Fix of a missing import.

## Version 0.0.21

- Privacy and Security in the settings (app lock, screenshots off, reminders hidden on the lock screen) and link addresses stored encrypted.

## Version 0.0.20

- CalDAV accounts (read and write, generic RFC 4791) and a new event takes over the details of a similar earlier event.

## Version 0.0.19

- Help and Support in the settings, with the how-to in categories that open and close.

## Version 0.0.18

- Four new widgets (up next small and wide, day planner, list), Inter in all widgets.

## Version 0.0.17

- Month grid drawn in one piece per month (light switching between layouts and scrolling), switch slides out and in.

## Version 0.0.16

- Only the months in view animate when the layout changes.

## Version 0.0.15

- Faster layout switching animation.

## Version 0.0.14

- Fix for the custom repeat build error.

## Version 0.0.13

- Details text centred, List shows one whole month with snap, custom repeat, location opens a navigation app, animated switching between layouts.

## Version 0.0.12

- Look and Feel group, readable text and icons in the bottom sheets in dark and black mode.

## Version 0.0.11

- Dialogs, date and time pickers, bottom sheets, text fields and chips in the glass style.

## Version 0.0.10

- Settings > Calendars (local, .ics file, .ics link with status), settings grouped, today circle follows the contrast colour, internet permission for links only.

## Version 0.0.9

- Colourful settings icons with a switch in Appearance.

## Version 0.0.8

- Calendars menu with add, edit (name, colour) and delete for local calendars.

## Version 0.0.6

- Month view scrolls on through the months with four layouts (Compact, Stacked, Details, List); Overview replaces Agenda in the view menu; package org.nooklin.calendar.

## Version 0.0.3

- Month (iOS style grid), week, day, overview and year views, four widgets (today, week, month, date), a Quick add tile, search, .ics import and export, calendars list, week start and week numbers in the settings, local database with repeating events, event editor (new, edit, duplicate, delete, with scope for series) and reminders (exact alarms, snooze, dismiss, set again after restart). Internet only to download calendars that you follow by a link.
