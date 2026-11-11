package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Remote-friendly settings owned by the launcher: categories on the left, the chosen category's settings on the
 * right. Right opens a category, Left/Right change a value in place, Left (or Back) returns to the categories.
 * System settings stay one row away.
 */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private static final int PICK = 0, TOGGLE = 1, STEP = 2, ACTION = 3;
    private static final int SKY_LIGHT = 35;
    private static final int SKY_STRONG = 70;

    private SharedPreferences choices;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;
    private boolean clockLook;
    private String mountainScene = "mountain";
    private String weatherCity;
    private boolean weatherOn;
    private boolean cinematic;
    private int sky;
    private String quotesMode;

    /** One line of the right-hand pane. */
    private final class Row {
        final int kind;
        final String title;
        Supplier<String> value = () -> "";
        Runnable click;
        BooleanSupplier enabled = () -> true;
        BooleanSupplier on;
        int count;
        IntSupplier index;
        IntConsumer set;
        IntFunction<String> label;
        int hint;
        LinearLayout view;
        TextView valueView;

        Row(int kind, String title) {
            this.kind = kind;
            this.title = title;
        }
    }

    private final List<String> categoryTitles = new ArrayList<>();
    private final List<List<Row>> categoryRows = new ArrayList<>();
    private final List<TextView> categoryViews = new ArrayList<>();
    private LinearLayout rowsView;
    private TextView paneTitle;
    private TextView hintView;
    private int current = -1;
    private int forwardKey = KeyEvent.KEYCODE_DPAD_RIGHT;
    private int backKey = KeyEvent.KEYCODE_DPAD_LEFT;
    private Config config;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        config = Config.load(this);
        readValues(config);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            forwardKey = KeyEvent.KEYCODE_DPAD_LEFT;
            backKey = KeyEvent.KEYCODE_DPAD_RIGHT;
        }
        rowsView = findViewById(R.id.settingsRows);
        paneTitle = findViewById(R.id.settingsPaneTitle);
        hintView = findViewById(R.id.settingsHint);
        buildCategories();
        LinearLayout list = findViewById(R.id.settingsCategories);
        for (int i = 0; i < categoryTitles.size(); i++) {
            final int index = i;
            TextView item = new TextView(this);
            item.setText(categoryTitles.get(i));
            item.setTextColor(0xFFFFFFFF);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(22), 0, dp(22), 0);
            item.setBackgroundResource(R.drawable.settings_category_bg);
            item.setFocusable(true);
            item.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
            lp.bottomMargin = dp(6);
            list.addView(item, lp);
            item.setOnFocusChangeListener((v, focused) -> {
                if (focused) {
                    showCategory(index);
                    hintView.setVisibility(View.INVISIBLE);
                }
            });
            item.setOnClickListener(v -> focusRows());
            item.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN || keyCode != forwardKey) return false;
                focusRows();
                return true;
            });
            categoryViews.add(item);
        }
        showCategory(0);
        categoryViews.get(0).requestFocus();
    }

    private void buildCategories() {
        // Look
        List<Row> look = new ArrayList<>();
        look.add(pick(titleOf(R.string.theme_state), () -> getString(clockLook
                ? MountainScenes.find(mountainScene).nameRes : R.string.theme_lite), this::showThemePicker, R.string.theme_hint));
        look.add(pick(titleOf(R.string.wallpaper_state), () -> {
            String chosen = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
            return "none".equals(chosen) ? getString(R.string.wallpaper_none)
                    : chosen != null ? chosen : getString(R.string.wallpaper_theme_default);
        }, this::showWallpaperPicker, 0));
        look.add(toggle(titleOf(R.string.cinematic_state), () -> cinematic, () -> {
            cinematic = !cinematic;
            choices.edit().putBoolean(Config.CINEMATIC_CHOICE_KEY, cinematic).apply();
        }, 0));
        look.add(step(titleOf(R.string.sky_state), 3,
                () -> sky == 0 ? 0 : sky <= SKY_LIGHT ? 1 : 2,
                i -> {
                    sky = i == 0 ? 0 : i == 1 ? SKY_LIGHT : SKY_STRONG;
                    choices.edit().putInt(Config.SKY_CHOICE_KEY, sky).apply();
                },
                i -> getString(i == 0 ? R.string.off : i == 1 ? R.string.sky_low : R.string.sky_high), 0));
        look.add(toggle(titleOf(R.string.animations_state), () -> animations, () -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
        }, 0));
        Row advanced = toggle(titleOf(R.string.advanced_effects_state), () -> advancedEffects, () -> { },
                R.string.advanced_effects_warning);
        advanced.enabled = () -> animations;
        advanced.click = () -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        };
        look.add(advanced);
        look.add(step(getString(R.string.dream_name), CLOCK_FORMATS.length,
                () -> Math.max(0, Arrays.asList(CLOCK_FORMATS).indexOf(clockFormat)),
                i -> {
                    clockFormat = CLOCK_FORMATS[i];
                    choices.edit().putString(Config.CLOCK_FORMAT_KEY, clockFormat).apply();
                },
                i -> getString(i == 0 ? R.string.clock_format_system
                        : i == 1 ? R.string.clock_format_12 : R.string.clock_format_24), 0));
        addCategory(getString(R.string.settings_cat_look), look);

        // Dock
        List<Row> dock = new ArrayList<>();
        if (config.launcherSettingsButton) {
            dock.add(action(getString(R.string.reorder), () -> {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
                finish();
            }, 0));
        }
        dock.add(step(getString(R.string.columns_label), 4,
                () -> Math.max(0, Math.min(columns - 4, 3)),
                i -> {
                    columns = i + 4;
                    choices.edit().putInt("columns", columns).apply();
                }, i -> String.valueOf(i + 4), 0));
        dock.add(step(getString(R.string.rows_below_dock_label), 4,
                () -> Math.max(0, Math.min(rowsBelowDock, 3)),
                i -> {
                    rowsBelowDock = i;
                    choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, i).apply();
                }, String::valueOf, 0));
        dock.add(step(getString(R.string.dock_opacity_label), DOCK_OPACITIES.length,
                () -> {
                    int best = 0;
                    for (int i = 1; i < DOCK_OPACITIES.length; i++) {
                        if (Math.abs(DOCK_OPACITIES[i] - dockOpacity) < Math.abs(DOCK_OPACITIES[best] - dockOpacity)) best = i;
                    }
                    return best;
                },
                i -> {
                    dockOpacity = DOCK_OPACITIES[i];
                    choices.edit().putInt(Config.DOCK_OPACITY_KEY, dockOpacity).apply();
                }, i -> DOCK_OPACITIES[i] + "%", 0));
        addCategory(getString(R.string.settings_cat_dock), dock);

        // Weather and quotes
        List<Row> weather = new ArrayList<>();
        weather.add(toggle(titleOf(R.string.weather_show_state), () -> weatherOn, () -> {
            weatherOn = !weatherOn;
            choices.edit().putBoolean(Config.WEATHER_ENABLED_KEY, weatherOn).apply();
        }, R.string.weather_hint));
        Row city = pick(titleOf(R.string.weather_city_state),
                // A city may be stored as "lat,lon,Name": show the name only.
                () -> weatherCity.isEmpty() ? getString(R.string.weather_city_unset)
                        : weatherCity.replaceFirst("^\\s*-?[0-9.]+\\s*,\\s*-?[0-9.]+\\s*,\\s*", ""),
                this::showWeatherPicker, R.string.weather_hint);
        city.enabled = () -> weatherOn;
        weather.add(city);
        weather.add(step(titleOf(R.string.quotes_state), 3,
                () -> Quotes.OFF.equals(quotesMode) ? 0 : Quotes.LOCAL.equals(quotesMode) ? 1 : 2,
                i -> {
                    quotesMode = i == 0 ? Quotes.OFF : i == 1 ? Quotes.LOCAL : Quotes.ONLINE;
                    choices.edit().putString(Config.QUOTES_CHOICE_KEY, quotesMode).apply();
                },
                i -> getString(i == 0 ? R.string.off : i == 1 ? R.string.quotes_list : R.string.quotes_online),
                R.string.quotes_hint));
        addCategory(getString(R.string.settings_cat_weather), weather);

        // System
        List<Row> system = new ArrayList<>();
        if (config.openOnStartChoice) {
            system.add(pick(titleOf(R.string.open_on_start_state), this::openOnStartLabel,
                    this::showOpenOnStartPicker, 0));
        }
        Row follow = action(getString(R.string.follow_theme), this::followTheme, R.string.follow_theme_hint);
        follow.enabled = this::hasOnTvChoices;
        system.add(follow);
        system.add(action(getString(R.string.home_settings),
                () -> open(new Intent(this, HomeSettingsActivity.class)), R.string.home_settings_help));
        system.add(action(getString(R.string.tv_settings),
                () -> open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)), 0));
        addCategory(getString(R.string.settings_cat_system), system);
    }

    private void addCategory(String title, List<Row> rows) {
        categoryTitles.add(title);
        categoryRows.add(rows);
    }

    private Row pick(String title, Supplier<String> value, Runnable open, int hint) {
        Row row = new Row(PICK, title);
        row.value = value;
        row.click = open;
        row.hint = hint;
        return row;
    }

    private Row toggle(String title, BooleanSupplier on, Runnable flip, int hint) {
        Row row = new Row(TOGGLE, title);
        row.on = on;
        row.click = () -> {
            flip.run();
            changed();
            refresh();
        };
        row.hint = hint;
        return row;
    }

    private Row action(String title, Runnable go, int hint) {
        Row row = new Row(ACTION, title);
        row.click = go;
        row.hint = hint;
        return row;
    }

    private Row step(String title, int count, IntSupplier index, IntConsumer set, IntFunction<String> label, int hint) {
        Row row = new Row(STEP, title);
        row.count = count;
        row.index = index;
        row.set = set;
        row.label = label;
        row.hint = hint;
        row.click = () -> step(row, 1, true);
        return row;
    }

    /** Moves a value one step; false when it is already at that end (and may not wrap). */
    private boolean step(Row row, int dir, boolean wrap) {
        int i = row.index.getAsInt() + dir;
        if (wrap) i = (i + row.count) % row.count;
        else if (i < 0 || i >= row.count) return false;
        row.set.accept(i);
        changed();
        refresh();
        return true;
    }

    /** A state string such as "Theme: %1$s" without its value: "Theme". */
    private String titleOf(int stateRes) {
        String marker = "\u0001";
        String s = getString(stateRes, marker);
        int i = s.indexOf(marker);
        String t = i > 0 ? s.substring(0, i) : s.substring(Math.max(0, i) + 1);
        return t.replaceAll("^[\\s:\uFF1A]+|[\\s:\uFF1A]+$", "");
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showCategory(int index) {
        if (index == current) return;
        current = index;
        for (int i = 0; i < categoryViews.size(); i++) categoryViews.get(i).setSelected(i == index);
        paneTitle.setText(categoryTitles.get(index));
        rowsView.removeAllViews();
        for (Row row : categoryRows.get(index)) {
            if (row.view == null) buildRowView(row);
            rowsView.addView(row.view);
        }
        refresh();
    }

    private void buildRowView(Row row) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(24), 0, dp(24), 0);
        view.setBackgroundResource(R.drawable.settings_row_bg);
        view.setFocusable(true);
        view.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        lp.bottomMargin = dp(6);
        view.setLayoutParams(lp);
        TextView title = new TextView(this);
        title.setText(row.title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = new TextView(this);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setMaxWidth(dp(300));
        value.setGravity(Gravity.END);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMarginStart(dp(24));
        view.addView(value, vp);
        row.view = view;
        row.valueView = value;
        view.setOnClickListener(v -> row.click.run());
        view.setOnFocusChangeListener((v, focused) -> {
            updateValue(row);
            if (!focused) return;
            if (row.hint != 0) {
                hintView.setText(row.hint);
                hintView.setVisibility(View.VISIBLE);
            } else {
                hintView.setVisibility(View.INVISIBLE);
            }
        });
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            boolean forward = keyCode == forwardKey;
            if (!forward && keyCode != backKey) return false;
            if (row.kind == STEP && step(row, forward ? 1 : -1, false)) return true;
            if (!forward) focusCategory();
            return true;
        });
    }

    private void updateValue(Row row) {
        if (row.valueView == null) return;
        boolean focused = row.view.isFocused();
        String text;
        int color = 0xFFB9E7C7;
        if (row.kind == TOGGLE) {
            boolean on = row.on.getAsBoolean();
            text = getString(on ? R.string.on : R.string.off);
            if (!on) color = 0xFF9AA7B2;
        } else if (row.kind == STEP) {
            int i = row.index.getAsInt();
            text = (focused && i > 0 ? "\u2039  " : "") + row.label.apply(i)
                    + (focused && i < row.count - 1 ? "  \u203A" : "");
        } else if (row.kind == PICK) {
            text = row.value.get() + "  \u203A";
        } else {
            text = "\u203A";
            color = 0xFF9AA7B2;
        }
        row.valueView.setText(text);
        row.valueView.setTextColor(color);
    }

    private void focusRows() {
        for (int i = 0; i < rowsView.getChildCount(); i++) {
            View child = rowsView.getChildAt(i);
            if (child.isEnabled() && child.requestFocus()) return;
        }
    }

    private void focusCategory() {
        if (current >= 0) categoryViews.get(current).requestFocus();
    }

    @Override
    public void onBackPressed() {
        View focus = getCurrentFocus();
        if (focus != null && focus.getParent() == rowsView) focusCategory();
        else super.onBackPressed();
    }

    private void followTheme() {
        if (!hasOnTvChoices()) return;
        choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY)
                .remove(Config.WEATHER_CHOICE_KEY).remove(Config.WEATHER_ENABLED_KEY).remove(Config.CINEMATIC_CHOICE_KEY)
                .remove(Config.CLOCK_LOOK_KEY).remove(Config.MOUNTAIN_SCENE_KEY).remove(Config.SKY_CHOICE_KEY).remove(Config.QUOTES_CHOICE_KEY).commit();
        readValues(Config.load(this));
        changed();
        refresh();
    }

    private void readValues(Config c) {
        columns = c.columns;
        rowsBelowDock = c.rowsBelowDock;
        dockOpacity = c.dockOpacity;
        clockFormat = c.clockFormat;
        animations = c.animations;
        advancedEffects = c.animations && c.advancedEffects;
        readLook(c);
    }

    private void readLook(Config c) {
        weatherCity = c.weatherCity;
        weatherOn = c.weatherEnabled;
        cinematic = c.cinematicHome;
        clockLook = c.clockLook;
        mountainScene = c.mountainScene;
        sky = c.skyIntensity;
        quotesMode = c.quotesMode;
    }

    /** Lite, the light default, then each Mountain picture this build has (config clock_look). */
    private void showThemePicker() {
        List<MountainScenes.Scene> scenes = new ArrayList<>();
        for (MountainScenes.Scene scene : MountainScenes.ALL) if (MountainScenes.available(this, scene)) scenes.add(scene);
        String[] labels = new String[scenes.size() + 1];
        labels[0] = getString(R.string.theme_lite);
        int checked = 0;
        for (int i = 0; i < scenes.size(); i++) {
            labels[i + 1] = getString(scenes.get(i).nameRes);
            if (clockLook && scenes.get(i).id.equals(mountainScene)) checked = i + 1;
        }
        final int before = checked;
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == before) return;
                    clockLook = which > 0;
                    SharedPreferences.Editor editor = choices.edit().putBoolean(Config.CLOCK_LOOK_KEY, clockLook);
                    if (which > 0) {
                        mountainScene = scenes.get(which - 1).id;
                        editor.putString(Config.MOUNTAIN_SCENE_KEY, mountainScene);
                    }
                    editor.apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showWeatherPicker() {
        Weather.City[] cities = Weather.PRESETS;
        String[] labels = new String[cities.length];
        int checked = -1;
        for (int i = 0; i < cities.length; i++) {
            labels[i] = cities[i].name;
            if (checked < 0 && Weather.normalize(cities[i].name).equals(Weather.normalize(weatherCity))) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.weather_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    weatherCity = cities[which].name;
                    choices.edit().putString(Config.WEATHER_CHOICE_KEY, weatherCity).apply();
                    changed();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WEATHER_CHOICE_KEY).apply();
                    readLook(Config.load(this));
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String chosen = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(chosen) ? images.size()
                : indexOf(images, chosen);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String chosen = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(chosen)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    /** Redraws the values (and the enabled state) of the visible rows. */
    private void refresh() {
        if (current < 0) return;
        for (Row row : categoryRows.get(current)) {
            if (row.view == null) continue;
            boolean enabled = row.enabled.getAsBoolean();
            row.view.setEnabled(enabled);
            row.view.setAlpha(enabled ? 1f : 0.45f);
            updateValue(row);
        }
    }

    private boolean hasOnTvChoices() {
        return choices.contains(Config.WEATHER_CHOICE_KEY) || choices.contains(Config.WEATHER_ENABLED_KEY)
                || choices.contains(Config.CINEMATIC_CHOICE_KEY) || choices.contains(Config.CLOCK_LOOK_KEY)
                || choices.contains(Config.MOUNTAIN_SCENE_KEY) || choices.contains(Config.SKY_CHOICE_KEY) || choices.contains(Config.QUOTES_CHOICE_KEY)
                || choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
