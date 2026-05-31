# AssistBridge v0.2.0

Initial public release of the AssistBridge phone-to-Rokid relay.

## What's Included

- Phone app that captures visible Gemini / Google Assistant answers through Android Accessibility.
- Rokid glasses app that displays answers in an AR-safe popup.
- CXR-L relay path using Global Hi Rokid through `Anezium/CxrGlobal`.
- Bundled glasses APK inside the phone APK for authorized CXR-L install / update.
- Setup UI for phone and glasses accessibility services.
- Popup controls: tap / OK / Back to dismiss, left / right swipe to scroll.

## APKs

- `AssistBridge-phone-v0.2.0.apk`
- `AssistBridge-glasses-v0.2.0.apk`

Both APKs are debug-key signed for sideload testing.

## Known Limits

- Gemini / Google Assistant UI capture is best-effort and can break if Google changes the accessibility tree.
- Lock-screen operation depends on Gemini / Google Assistant lock-screen settings on the phone.
- The relay requires Global Hi Rokid CXR-L authorization.
