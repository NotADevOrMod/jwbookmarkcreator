# Scripture Bookmarks

An Android app that gets around JW Library's bookmark limit. Save as many scriptures as you like, add short notes, and tap a bookmark to open it in the official JW Library app.

## Features
- Add a scripture by typing it ("John 3:16", "1 Cor 13:4-7", "Ps 23") or by tapping book, chapter and verse (ranges supported)
- Bookmarks open in JW Library via a generated link
- Short notes and tags on each bookmark, plus search and tag filters
- Share articles from JW Library into the app and attach them to bookmarks
- Export / import backup as a JSON file (menu, top right)
- Everything is stored on the device only
- JW-style purple theme with dark mode

## Get the app (no Android Studio needed)
1. Open the **Actions** tab and pick the latest **Build APK** run
2. Download **ScriptureBookmarks-debug-apk** from the Artifacts section and unzip it
3. Copy `app-debug.apk` to your phone and open it (Android will ask you to allow installs from that source)

## Updating
Every build is signed with the same key (`app/debug.keystore`), so a new APK installs over the old one and keeps your bookmarks.

## Build it yourself
Open the folder in Android Studio, let Gradle sync, and press Run.

## Project layout
- `app/src/main/java/com/scripturebookmarks/bible` - book table, reference parser, link builder
- `app/src/main/java/com/scripturebookmarks/data` - Room database
- `app/src/main/java/com/scripturebookmarks/ui` - theme
- `app/src/main/java/com/scripturebookmarks/MainActivity.kt` - screens

## Note
Unofficial personal project, not affiliated with or endorsed by Jehovah's Witnesses or the makers of JW Library.
