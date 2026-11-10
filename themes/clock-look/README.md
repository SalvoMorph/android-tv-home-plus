# clock-look

A phone lock screen on the TV: a dusk photograph of Ama Dablam, a huge glass clock whose lower part sits **behind
the mountain**, the date above it, round liquid-glass buttons top right, the weather bottom left and a quote bottom
right. Nothing else is on screen: press **Down** and the dock of square, rounded icon cards comes up from the
bottom, split into three: streaming, games (if there are any) and everything else, each in its own glass band with a heading (the weather and the quote fade out meanwhile). **Up** from the dock, or **Back**, puts it away again, and
coming back to Home from an app starts with the dock hidden.

How it is drawn, and why it is cheap enough for an old TV:

- The clock is drawn by `LockScreenView`, not by a `TextClock`, because only a layer between the photograph and
  the mountains can be hidden behind them. The mountains in front are the photograph clipped to its skyline
  (`scenic_skyline` in `res/values`), cached as one bitmap of just the clock's region, made once.
- The glass is not blurred live: a tiny blurred copy of the picture (made once) shows through the clock, the
  buttons and the dock band, lifted in colour, with a white tint, a highlight and a bright rim.
- Nothing animates but the dock sliding up; the view redraws once a minute for the time.

The picture is "Ama Dablam under a Painted Sky" by Megaurab09, CC BY-SA 4.0: see `CREDITS.md`.

Turn it on from the TV (*Launcher settings → Theme → Mountain*; *Lite* is the default) or with `"clock_look": true` in any theme. It overrides
`single_row`, `dock_title`, `columns`, `cinematic_home`, `uniform_tiles`, the glows and the sky, which it needs, and
it draws its own picture, so a theme's `wallpaper.jpg` does not apply to it.

Weather: set `weather_city` (and `weather_enabled`) as in any theme; with no city, the hint shows instead.
It costs more than the default Lite theme: on the Philips about 15 MB more memory and about 0.15 s longer to start. The measurements are in the main README, under Performance.

## More pictures

The theme can sit on other pictures: **Glacier** (a pale, misty sky, with a navy glass clock) and **Alpenglow** (a dusk with lit peaks) exist in the author's build, but their pictures are not in this repository (one is Apple's macOS wallpaper, the other has no known licence). The Theme choice offers a picture only when it is in the build, so a build from this repository offers *Lite* and *Mountain*.

To add your own: prepare the photograph with [`tools/make-mountain-scene.ps1`](../../tools/make-mountain-scene.ps1), add a `Scene` for it in `MountainScenes.java` with a name string, and rebuild. Set `clockTop` so the mountains cover the clock's lower part, and `lightSky` for a pale sky. A theme can pick one with `"mountain_scene": "<id>"`.