# PocketScan: documents & receipts scanner

An Android app that turns paper into PDFs and receipts into a spending list.
Everything runs **on the phone** with Google ML Kit, so there is no server, no API key and no running cost.

## Features (v0.1)

| Tab | What it does |
| --- | --- |
| **Documents** | Scan many pages → clean PDF (auto edge detection, crop, filters). Open, share, rename, delete. Gallery import. No watermark. |
| **Receipts** | Scan a receipt → total, shop and date are read automatically → confirm, pick a category. Monthly total by category. Export to CSV. Manual entry. |
| **QR code** | Scan QR codes and barcodes. Open links, copy, share. History of the last 50 scans. |

No camera permission is requested: the scanners are Google Play services screens.

## Get the APK without installing anything

Every push builds the app on GitHub:

1. Open the repository on GitHub → **Actions** → **PocketScan Android build**.
2. Open the latest green run → download **PocketScan-debug-apk** at the bottom.
3. Unzip it, copy `app-debug.apk` to your phone and open it (allow "install unknown apps").

## Build locally

Install Android Studio, open the `scanner-app` folder and press Run. Or from a terminal:

```bash
cd scanner-app
./gradlew testDebugUnitTest assembleDebug
```

## Project layout

```
app/src/main/java/com/elyacine/pocketscan/
  MainActivity.kt          entry point
  ui/                      Compose screens (Documents, Receipts, QR) and theme
  scan/Scanners.kt         ML Kit document scanner + text recognition
  receipt/ReceiptParser.kt reads total / shop / date from receipt text (unit-tested)
  data/                    local storage (PDF files, receipts JSON, QR history)
```

## Road to the Play Store

- [ ] Test on 2–3 real phones (cheap and recent)
- [ ] Ads (AdMob banner on lists, interstitial after export)
- [ ] One-time "Pro" unlock (Google Play Billing): no ads, PDF password, batch export
- [ ] Choose the final name and check it is not already taken on Google Play
- [ ] Signed release build, privacy policy page, store listing and screenshots
- [ ] Closed test: 12 testers for 14 days (required for new personal developer accounts)
- [ ] Before upload, set `targetSdk` to the level Google Play requires at that time
