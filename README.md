# Charge Watch (native Android app)

A real Android app (Kotlin, not a web page) that runs a background service to
watch your battery level and alert you — by notification, alarm sound, or
both — once it crosses a percentage you choose. Works with the screen off
and the app closed.

## Get the installable APK — no coding required

1. Create a free GitHub account if you don't have one, and create a new
   **public** repository.
2. Upload every file in this project to that repository (drag-and-drop on
   github.com works, or use `git push` if you're comfortable with git).
3. Go to the **Actions** tab of your repo. A workflow called **Build APK**
   runs automatically and produces `charge-watch-debug-apk` as a downloadable
   artifact in a few minutes. (If it doesn't start automatically, click
   **Run workflow**.)
4. Download the artifact zip, unzip it, and you'll have `app-debug.apk`.
5. Transfer that file to your phone (email it to yourself, Google Drive,
   whatever's easiest) and tap it to install. Android will ask you to allow
   installing from this source the first time — that's expected for any
   app installed outside the Play Store.

That's the whole "deploy" step — GitHub's servers compile the app for you.

## Or build it yourself in Android Studio

1. Install [Android Studio](https://developer.android.com/studio).
2. **Open** this folder as a project (not "Import" — just Open).
3. Let it sync (first sync downloads dependencies, takes a few minutes).
4. Plug in your phone via USB with **Developer options → USB debugging**
   enabled, and click the green **Run** button — it installs straight to
   your phone.
5. Or use **Build → Generate Signed Bundle / APK** to produce an APK you can
   share the same way as above.

## How to use it day to day

1. Open the app, drag the slider to the charge % you want to be notified at
   (e.g. 90%, or leave it at 100% for "fully charged").
2. Pick **Message**, **Alarm**, or **Both**.
3. Tap **Start monitoring**.
4. Tap **Allow background running** the first time — this stops Android's
   battery saver from killing the service, so it works reliably.
5. Plug your phone in and walk away. You'll get a notification and/or a
   looping alarm sound (with a **Stop alarm** button right on the
   notification) once you hit your target — even with the screen off.
6. Tap **Stop monitoring** any time to turn it off, or leave it running
   permanently; it only reacts when the phone is actually charging.

## Why this is a real background app (unlike a web page)

It runs as an Android **foreground service** — the small permanent
"Charge Watch is monitoring" notification you'll see is Android's way of
letting an app stay alive and keep listening for battery updates
indefinitely, including with the app closed and the screen locked. That
permanent notification is required by Android for any app that runs in the
background like this; it can't be hidden, but it's tappable to reopen the
app.

## Where to make changes

| What you want to change | Where |
|---|---|
| App name shown on the phone | `app/src/main/res/values/strings.xml` → `app_name` |
| Default target % before first launch | `Prefs.kt` → `getInt(KEY_TARGET, 100)` |
| Colors (background, accent, alarm red, etc.) | `app/src/main/res/values/colors.xml` |
| Alarm sound | `BatteryMonitorService.kt` → `startAlarmSound()` — currently uses the phone's default alarm tone via `RingtoneManager`; swap in your own sound file under `res/raw/` if you want a custom one |
| Vibration pattern | `BatteryMonitorService.kt` → `vibrate()` |
| Notification wording | `BatteryMonitorService.kt` → `fireAlert()` and `sendAlertNotification()` |
| App icon | Regenerate the PNGs in `res/mipmap-*/ic_launcher.png`, or replace with your own art |
| Minimum/maximum slider range | `activity_main.xml` → `android:max="90"` on `targetSlider` (current range is 10%–100%) |

## Known limits (same physics as any Android app)

- Some phone brands (Xiaomi, Huawei, OnePlus, Samsung in some modes, etc.)
  have their own aggressive battery managers on top of stock Android that
  can kill background apps regardless of the in-app "allow background
  running" step. If alerts stop firing reliably, search your phone model +
  "disable battery optimization for apps" for the manufacturer-specific
  setting.
- The permanent "monitoring" notification is required by Android for any
  background service — it's not a bug, it's what makes the background
  alerting possible at all.
