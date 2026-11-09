# android-tv-home-plus

A light, Apple-TV-style home screen for Android TV, with **weather** and more settings you can change
from the remote. It is a fork of the launcher in
[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT), built to stay fast on old TVs
with about 1–2 GB of RAM.

> **Tested only on one TV:** Philips 55POS9002/12 (TP Vision TPM171E, device `PH7M_EU_5596`,
> Android TV 8.0.0, 1.9 GB RAM). The additions here use plain Android APIs and do not depend on the
> model, and the upstream launcher is reported to work on many other Android TV / Google TV sets, but
> I have **not** verified any other model. Treat other TVs as untested and keep a way back to the stock
> launcher (see [Going back](#going-back)).

## What this adds to the upstream launcher

- **Weather next to the clock**, from [Open-Meteo](https://open-meteo.com/) (free, no account, no API key):
  an icon drawn in code (no images), the temperature, the place, the day's maximum and minimum.
- **More settings on the TV** (Launcher settings, with the remote): the weather city (37 Italian cities,
  or Off), cinematic home on or off, and the dynamic sky (off, light, strong). Choices made on the TV
  win over a theme until you pick *Follow theme*.
- **Any place** through a theme: `weather_city` takes a city name, `"lat,lon"` or `"lat,lon,Name"`.
- A ready theme for the Philips above: [`themes/philips-tpm171e`](themes/philips-tpm171e/config.json).
- Italian strings for the new rows.

### Cost on the TV

Everything is built so that it costs almost nothing:

- The weather runs **only while Home is on screen**: one HTTPS request at most every 30 minutes (5 minutes
  after a failure), on one short-lived thread, with the last answer cached. No service, no alarm, no timer
  in the background.
- The icon is a few shapes on a canvas, redrawn only when the weather changes.
- With `weather_city` empty, which is the default, **the launcher makes no network request at all**.
- On the Philips above the launcher uses about 100 MB of RAM with weather, cinematic home and sky on.

### Privacy and permissions

The upstream launcher had no `INTERNET` permission. This fork adds it **only for the weather**. The only
thing sent is the coordinates of the chosen place to `api.open-meteo.com` (and, for a city name that is not
one of the presets, the name to `geocoding-api.open-meteo.com`, once). The weather needs no account, no
identifier and no location permission: the place is the one you choose, not one read from the TV. (The
upstream launcher separately asks for location access, only to be able to show the Wi-Fi network name.)

## Build

You need a JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). From `launcher/`:

```sh
./gradlew assembleRelease        # Windows: gradlew.bat assembleRelease
```

The APK is `launcher/app/build/outputs/apk/release/app-release.apk`. It is signed with the debug key, so it is
meant for sideloading over ADB, not for a store. The package name is still `com.gathin.tvhome`.

## Install

Turn on USB debugging (and network ADB) on the TV, then:

```sh
adb connect <tv-ip>:5555
adb install -r launcher/app/build/outputs/apk/release/app-release.apk
adb shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
```

Press Home to check that it opened. `launcher/build-and-install.sh` (or `.ps1` on Windows) does the same
and can also disable the stock launcher; **it does that only if you let it**, and you should not unless
you can still reach the TV another way.

Then choose the look and the weather:

```sh
TV_HOST=<tv-ip> tools/theme.sh themes/philips-tpm171e      # Philips theme, or any other theme
```

or use *Launcher settings* from the Settings pill on the Home screen. To set a place for the weather in a
theme, add `"weather_city": "Bologna"` (or `"44.50,11.34,Bologna"`) to its `config.json`.

### Going back

Disabling the stock launcher is optional. With it left alone, you can return to it by disabling or uninstalling
this one (`adb shell pm uninstall --user 0 com.gathin.tvhome`). Android then forgets the default home and asks
which one to use: pick the stock one with *Always*.

## Credits and licence

MIT, see [LICENSE](LICENSE). The launcher is by **Gathin** ([cleanyourtv](https://github.com/Gathin23/cleanyourtv),
commit `66b7214`); the weather, the extra settings and this packaging are additions on top of that code. The
Outfit font keeps its own licence (`launcher/FONT-LICENSE-OFL.txt`). Weather data by
[Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0; free for non-commercial use).

Not affiliated with Philips, Google, Netflix or any other brand named here. Use at your own risk: it changes
the home screen of a device, so keep a way to reach the TV (and its settings) before you try it.

---

## Italiano

Una home leggera in stile Apple TV per Android TV, con **meteo** e altre impostazioni modificabili dal
telecomando. È un fork del launcher di [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT),
pensato per restare fluido su TV vecchie con 1–2 GB di RAM.

**Provato solo su una TV:** Philips 55POS9002/12 (TPM171E, Android TV 8.0.0, 1,9 GB di RAM). Le aggiunte non
dipendono dal modello, ma **non ho verificato altre TV**: considerale non testate e tieni sempre una via per
tornare al launcher originale.

- **Meteo** accanto all'orologio, da Open-Meteo (gratuito, senza account né chiave): icona, temperatura,
  località, massima e minima.
- **Impostazioni sulla TV**: città del meteo, home cinematografica, cielo dinamico.
- Funziona solo mentre la home è visibile: al massimo una richiesta ogni 30 minuti, nessun servizio in
  background. Con `weather_city` vuoto non fa nessuna richiesta di rete.
- Rispetto all'originale aggiunge il permesso `INTERNET`, usato solo per il meteo; viene inviata soltanto la
  posizione (coordinate) della città scelta.

Per compilare e installare vedi le sezioni *Build* e *Install* sopra. Il tema
[`themes/philips-tpm171e`](themes/philips-tpm171e/config.json) contiene le impostazioni usate sulla Philips
(dock Netflix / Disney+ / Prime Video, Live TV `org.droidtv.playtv`, movimento ridotto).
