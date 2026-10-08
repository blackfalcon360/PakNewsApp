# Pak News | پاکستان خبریں 📰

Headlines from Pakistan's famous news channels in one list, English + Urdu (full right-to-left).

- 12 channels on by default: Geo, ARY, Express News, Dunya, Samaa, Dawn, Express Tribune, 92 News, Aaj, BOL, Hum, Neo.
  More in ⚙: 24 News, GNN, Public News, Geo Urdu, Dunya Urdu, 92 News Urdu, BOL Urdu, Independent Urdu, Arab News Pakistan.
- Channel chips to filter, search box, "NEW" tag for stories under 30 minutes old, tap = open the story, long-press = share.
- Each channel uses its own RSS feed when it has one, otherwise a Google News search of its website. If one address fails, the next is tried.
- Last headlines are saved, so you can still read them with no internet.
- ⚙ → "Add RSS link" lets you add any news site's feed yourself.
- Refreshes by itself every 5 minutes while the app is open.

Needs internet to load new headlines. Brought to you By: Black Falcon 🦅

## Build
Push to GitHub -> Actions -> "Build APK" -> download `PakNews-APK`.
Local: `gradle assembleDebug` (JDK 17, Gradle 8.7+).
