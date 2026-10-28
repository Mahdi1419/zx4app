# SMS Module Control — Flutter UI

This folder contains a Flutter UI prototype for the SMS relay-module controller. It is kept separate from the existing Android implementation while native features are migrated and validated.

## Run the UI prototype

Install Flutter stable, then:

```sh
cd flutter_app
flutter pub get
flutter run
```

## Included in this prototype

- Liquid-glass inspired UI using Flutter `BackdropFilter`, layered translucent surfaces and ambient gradients.
- Light and charcoal dark themes with high-contrast text.
- Editable in-app title, device name, accent color and minimum command gap.
- Command list, demo command creation, schedules and activity-history screens.

## Not migrated yet

**This is not yet a production SMS controller.** Sending SMS, receiving module replies, persistent schedules, Android boot recovery, exact alarms, and durable settings are not connected to Flutter. Command taps intentionally show a demo message rather than sending a real SMS.

The existing Kotlin implementation contains the real SMS/alarm engine. A production migration needs a Flutter Android host and a typed MethodChannel (or maintained plugin) to call that engine, preserve preferences, request permissions, satisfy Android SMS-role/policy requirements, and re-arm alarms after reboot/time changes. Keep the Kotlin app intact until these flows and regression tests pass.

Flutter can render the same design on iOS, including Cupertino-adaptive controls, but third-party iOS apps cannot silently send carrier SMS using Android's `SmsManager` flow. iOS generally requires a user-confirmed compose sheet or another supported hardware/network API for the module. Background scheduling is also subject to iOS platform restrictions.
