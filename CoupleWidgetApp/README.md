# Couple Widget App

Eine private Android-App für dich und deine Freundin.
Schickt Bilder und Nachrichten direkt aufs Homescreen-Widget — kein Internet, kein Account, kein Cloud.

## Features
- Nachrichten direkt aufs Homescreen-Widget schicken
- Bilder direkt aufs Homescreen-Widget schicken
- 100% lokal — keine Server, keine Accounts
- Automatische Verbindung wenn beide die App offen haben
- Funktioniert nur zwischen 2 Geräten

## Voraussetzung
- Android 8.0+ auf beiden Handys
- **Beide Handys müssen im selben WLAN sein** (z.B. Heim-WLAN)
- Kein Internet nötig

## Build & Install

1. [Android Studio](https://developer.android.com/studio) installieren
2. Diesen Ordner `CoupleWidgetApp/` in Android Studio öffnen
3. **Build → Generate Signed Bundle/APK → APK**
4. APK auf beide Handys installieren (USB oder AirDrop)

## Setup

1. App auf **beiden** Handys installieren
2. Beide Handys mit **gleichem WLAN** verbinden
3. App auf beiden Handys öffnen
4. In der Benachrichtigungsleiste erscheint: *"Partner verbunden"*
5. Homescreen Widget hinzufügen: Homescreen lang halten → Widgets → "Couple Widget"
6. Auf das Widget tippen → Nachricht oder Bild auswählen → Senden
7. Erscheint sofort auf dem Widget deiner Freundin!

## Wie es funktioniert

```
Handy A ──[WLAN]──► findet Handy B via mDNS
         ◄────────── TCP Verbindung auf Port 8765
             Kein Internet nötig!
```

Die App nutzt Android's **Network Service Discovery (NSD/mDNS)** um sich automatisch
im lokalen WLAN zu finden — genau wie AirDrop bei Apple.
Danach werden Daten direkt über **TCP Sockets** übertragen.

## Widget Größe

Das Widget ist 2×2 Zellen groß. Es zeigt:
- Das letzte empfangene Bild
- Oder den letzten empfangenen Text
- Verbindungsstatus (Online/Offline)
