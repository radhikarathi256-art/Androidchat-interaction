# AndroidChat — Android (astrologer)

Jetpack Compose client for a realtime astrology chat demo. This app is the
**astrologer** side. It talks to an iOS app (the seeker) and a web console over
Supabase Realtime broadcast; all three interoperate.

## Setup

Credentials are not in this repo. Add them to `local.properties` in the project
root (this file is gitignored and Android Studio creates it for you):

```properties
SUPABASE_HOST=your-project-ref.supabase.co
SUPABASE_KEY=your-publishable-anon-key
```

`app/build.gradle.kts` reads these and generates `BuildConfig.SUPABASE_HOST`
and `BuildConfig.SUPABASE_KEY`, which `ChatClient.kt` uses.

If the values are missing the app still builds — the fields fall back to empty
strings and the app simply fails to connect. Check `local.properties` first if
the UI is stuck on "Connecting…".

Get the values either from whoever runs the shared Supabase project, or by
creating your own free project at supabase.com and using its publishable
(anon) key. Only Realtime is used — no database tables are required.

## Running

Open in Android Studio and run, or:

```sh
./gradlew installDebug
```

`minSdk` is 26. Keyboard/content sync behaves best on API 30+.

## Troubleshooting

**Stuck on "Connecting…"** — most often the Wi-Fi network is pushing an HTTP
proxy that the device cannot reach. Check with:

```sh
adb shell dumpsys connectivity | grep -i proxy
```

Ping will still succeed in that state because ICMP bypasses proxies, so ping is
not a useful test here.

## Wire contract

- Channel topic: `realtime:chat:<ROOM>` — `ROOM` is `demo` by default
- Presence key: `astrologer` for this app, `seeker` for the iOS app
- Broadcast events: `msg`, `typing`, `read`
- `msg` payload: `{ id, kind, from, ts, text?, imageUrl?, replyToId? }`

Both platforms must agree on all of the above to interoperate.

## Notes on motion

Entrance animations are tuned to match the iOS client and are documented inline
in `MainActivity.kt`. Shared easing is `cubic-bezier(0, 0, 0.5, 1)`.

Two things that look like bugs but are not, and are easy to reintroduce:

- The entrance scale must sit on the **bubble**, not on `BubbleView`'s
  `fillMaxWidth` row. On the row, `TransformOrigin(1f, 1f)` means the corner of
  the *screen*, so the bubble slides in from the screen edge instead of growing
  out of its own tail.
- The typing indicator's exit must release its height in a single frame when a
  message is replacing it (`typingHandoff`). Any non-zero exit duration puts two
  rows in the layout at once and the list visibly shakes.
