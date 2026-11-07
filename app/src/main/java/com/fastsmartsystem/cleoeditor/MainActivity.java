package com.fastsmartsystem.cleoeditor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import android.content.SharedPreferences;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;

import com.fastsmartsystem.cleo.IDECollector;
import com.fastsmartsystem.cleo.OpcodesLoader;
import com.fastsmartsystem.cleo.ScriptCompiler;
import com.fastsmartsystem.cleo.ScriptDecompiler;
import com.fastsmartsystem.cleo.SannySourceParser;
import com.fastsmartsystem.cleoeditor.code.CodeEngine;
import com.fastsmartsystem.cleoeditor.intent.RequestOpenFile;
import com.fastsmartsystem.cleoeditor.intent.RequestSaveFile;
import com.fastsmartsystem.cleoeditor.views.CodeInspectorDialog;
import com.github.ahmadaghazadeh.editor.processor.TextNotFoundException;
import com.github.ahmadaghazadeh.editor.processor.TextProcessor;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    CodeEngine engine;
    IDECollector ide_collector;
    OpcodesLoader scm_loader;
    Menu menu;
    String file_name = null;
    public boolean saved_file = true;
    private DrawerLayout drawerLayout;
    ScriptCompiler compiler;
    ScriptDecompiler decompiler;
    private static final String PREFS_NAME = "cleo_editor_prefs";
    private static final String PREF_GAME_TARGET = "game_target";
    public static final int GAME_PC = 0;
    public static final int GAME_MOBILE = 1;
    private int gameTarget = GAME_MOBILE;
    private static final int NEW_FILE = 1;
    private static final int OPEN_FILE = 2;
    private static final int SAVE_FILE = 3;
    private static final int DECOMPILE = 4;
    private static final int COMPILE = 5;
    private int type_function = -1;
    private int save_temp_func = -1;
    private ActivityResultLauncher<Void> OpenLauncher;
    private ActivityResultLauncher<String> SaveLauncher;
    private Button btnopenFile;
    Toolbar toolbar;
    private boolean waitingForAllFilesAccess = false;
    private static final String CUSTOM_SA_MOBILE_DIR = "/sdcard/sr-builder/sa_mobile/";
    private static final String CUSTOM_SA_PC_DIR = "/sdcard/sr-builder/sa_pc/";

    private static class EditorDocument {
        String name;
        String text;
        boolean dirty;
        Uri uri;
        final ArrayList<String> undoHistory = new ArrayList<>();
        final ArrayList<String> redoHistory = new ArrayList<>();
        int cursorStart;
        int cursorEnd;
        int scrollX;
        int scrollY;
        EditorDocument(String name, String text, boolean dirty, Uri uri) {
            this.name = name;
            this.text = text == null ? "" : text;
            this.dirty = dirty;
            this.uri = uri;
        }
    }
    private final ArrayList<EditorDocument> openDocuments = new ArrayList<>();
    private EditorDocument activeDocument;
    private LinearLayout documentTabs;
    private boolean restoringDocument = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupKeyboardInsets(findViewById(R.id.linear_layout));
        drawerLayout = findViewById(R.id.drawer);
        toolbar = findViewById(R.id.toolbar);
        documentTabs = findViewById(R.id.document_tabs);
        setSupportActionBar(toolbar);
        drawerLayout.addDrawerListener(
                new ActionBarDrawerToggle(
                        this,
                        drawerLayout,
                        toolbar,
                        R.string.open_drawer,
                        R.string.close_drawer));
        btnopenFile = findViewById(R.id.open_file);
        btnopenFile.setOnClickListener((v) -> {
            performFunction(OPEN_FILE);
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        NavigationView navigationViewStart = findViewById(R.id.navigation_view_start);
        initNavigationView(navigationViewStart);
        initFiles();
        initEngine();
        initIntent();
        requestCustomFolderAccessIfNeeded();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor_menu, menu);
        this.menu = menu;
        updateDocumentMenuVisibility();
        updateMenuIcon();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int menuItemId = item.getItemId();
        if (menuItemId == R.id.undo) {
            undoActiveDocument();
        }
        else if (menuItemId == R.id.redo) {
            redoActiveDocument();
        } else if (menuItemId == R.id.save_current) {
            saveCurrentDocument();
        } else if(menuItemId == R.id.code_errors) {
            new CodeInspectorDialog(engine).show(getSupportFragmentManager(), "CodeInspector");
        }
        return super.onOptionsItemSelected(item);
    }

    private void initFiles() {
        App.dataDirectory = getExternalFilesDir(null).getAbsolutePath() + "/";
        extractAssets();
        loadGameTarget();
        ide_collector = new IDECollector();
        File customDir = new File(gameTarget == GAME_PC ? CUSTOM_SA_PC_DIR : CUSTOM_SA_MOBILE_DIR);
        ensureCustomFolderExists();
        File customPeds = new File(customDir, "peds.ide");
        File customVehicles = new File(customDir, "vehicles.ide");
        // Load user overrides first so custom model names/IDs take precedence over bundled IDE data.
        if (customPeds.isFile() && customPeds.canRead()) ide_collector.collect(customPeds.getAbsolutePath());
        if (customVehicles.isFile() && customVehicles.canRead()) ide_collector.collect(customVehicles.getAbsolutePath());
        ide_collector.collect(App.dataDirectory + "default.ide");
        ide_collector.collect(App.dataDirectory + "globals.ide");
        if (!customPeds.isFile() || !customPeds.canRead()) ide_collector.collect(App.dataDirectory + "peds.ide");
        if (!customVehicles.isFile() || !customVehicles.canRead()) ide_collector.collect(App.dataDirectory + "vehicles.ide");
        loadOpcodes();
    }

    private boolean hasCustomFolderAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return android.os.Environment.isExternalStorageManager();
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                 checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private void ensureCustomFolderExists() {
        if (!hasCustomFolderAccess()) return;
        File mobileFolder = new File(CUSTOM_SA_MOBILE_DIR);
        if (!mobileFolder.exists()) {
            boolean created = mobileFolder.mkdirs();
            if (created) android.util.Log.i("SRBuilder", "Created custom data folder: " + mobileFolder.getAbsolutePath());
        }
        File pcFolder = new File(CUSTOM_SA_PC_DIR);
        if (!pcFolder.exists()) {
            boolean created = pcFolder.mkdirs();
            if (created) android.util.Log.i("SRBuilder", "Created custom data folder: " + pcFolder.getAbsolutePath());
        }
    }

    private void requestCustomFolderAccessIfNeeded() {
        if (hasCustomFolderAccess()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        new AlertDialog.Builder(this)
                .setTitle("Akses folder custom")
                .setMessage("Agar SR Builder dapat membuat folder " + CUSTOM_SA_MOBILE_DIR + " dan " + CUSTOM_SA_PC_DIR + ", serta membaca file konfigurasi di dalamnya, izinkan akses semua file pada halaman pengaturan berikut.")
                .setPositiveButton("Buka pengaturan", (dialog, which) -> {
                    try {
                        waitingForAllFilesAccess = true;
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        waitingForAllFilesAccess = false;
                        Toast.makeText(this, "Pengaturan akses file tidak tersedia di perangkat ini.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Nanti", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForAllFilesAccess) {
            waitingForAllFilesAccess = false;
            if (hasCustomFolderAccess()) {
                ensureCustomFolderExists();
                initFiles();
                initEngine();
                Toast.makeText(this, "Folder custom eksternal dimuat ulang.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadOpcodes() {
        String datFile = gameTarget == GAME_PC ? "sa.dat" : "sa_mobile.dat";
        scm_loader = new OpcodesLoader(App.dataDirectory + datFile);
        // PC and Mobile opcode overrides are kept in separate custom folders.
        // Only read the folder matching the currently selected game target.
        File customDir = new File(gameTarget == GAME_PC ? CUSTOM_SA_PC_DIR : CUSTOM_SA_MOBILE_DIR);
        // Load every SASCM*.ini override in the PC folder (for example
        // SASCM.CLEO+.ini, SASCM.Clipboard.ini, SASCM.Debug.ini, etc.).
        // Mobile keeps its established, explicitly supported file list.
        boolean loadedCustomCleo = false;
        if (hasCustomFolderAccess() && customDir.isDirectory()) {
            if (gameTarget == GAME_PC) {
                File[] iniFiles = customDir.listFiles((dir, name) ->
                        name.regionMatches(true, 0, "SASCM", 0, 5)
                                && name.toLowerCase(java.util.Locale.ROOT).endsWith(".ini"));
                if (iniFiles != null) {
                    java.util.Arrays.sort(iniFiles, (a, b) ->
                            String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
                    for (File ini : iniFiles) {
                        if (ini.isFile() && ini.canRead()) {
                            scm_loader.loadIni(ini.getAbsolutePath());
                            if ("SASCM.CLEO.ini".equalsIgnoreCase(ini.getName())) {
                                loadedCustomCleo = true;
                            }
                        }
                    }
                }
            } else {
                String[] iniNames = {"SASCM.ini", "SASCM.Mobile.ini", "SASCM.CLEO.ini"};
                for (String iniName : iniNames) {
                    File ini = new File(customDir, iniName);
                    if (ini.isFile() && ini.canRead()) {
                        scm_loader.loadIni(ini.getAbsolutePath());
                    }
                }
            }
        }
        // Keep the bundled CLEO definitions as a fallback when using the PC opcode database.
        if (gameTarget == GAME_PC && !loadedCustomCleo) {
            scm_loader.loadIni(App.dataDirectory + "SASCM.CLEO.ini");
        }
    }


    public boolean permissionsAllowed() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 100) {
            if(grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                performFunction(type_function);
            } else {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package",getPackageName(),null);
                intent.setData(uri);
                startActivity(intent);
            }
        } else if (requestCode == 101) {
            if (hasCustomFolderAccess()) {
                ensureCustomFolderExists();
                initFiles();
                initEngine();
                Toast.makeText(this, "Folder custom PC dan Mobile siap digunakan.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Izin baca/tulis diperlukan untuk membuat folder sa_mobile dan membaca file konfigurasi.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Lifts the whole content (editor + symbol toolbar) above the soft keyboard.
     * Edge-to-edge windows (targetSdk 35) are not resized by the keyboard and the
     * DrawerLayout does not pass the keyboard inset on, so it is applied here as bottom
     * padding (replacing the nav-bar padding while the keyboard is open).
     */
    private int basePaddingBottom = 0;

    private void setupKeyboardInsets(View content) {
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            WindowInsetsCompat result = ViewCompat.onApplyWindowInsets(v, insets);
            basePaddingBottom = v.getPaddingBottom(); // padding from fitsSystemWindows
            applyKeyboardPadding(v);
            return result;
        });
        // Safety net: if the keyboard is shown/hidden without the inset being re-dispatched
        // to this view, recompute from the root insets after every layout pass.
        content.getViewTreeObserver().addOnGlobalLayoutListener(() -> applyKeyboardPadding(content));
        ViewCompat.requestApplyInsets(content);
    }

    private void applyKeyboardPadding(View v) {
        WindowInsetsCompat root = ViewCompat.getRootWindowInsets(v);
        boolean imeVisible = root != null && root.isVisible(WindowInsetsCompat.Type.ime());
        int bottom = basePaddingBottom;
        if (imeVisible) {
            // Measure instead of trusting inset values (they differ between Android
            // versions / nav modes and left a gap above the keyboard): the content's
            // inner bottom must land exactly on the top of the keyboard.
            android.graphics.Rect visible = new android.graphics.Rect();
            v.getWindowVisibleDisplayFrame(visible);
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            int outerBottom = loc[1] + v.getHeight();
            bottom = Math.max(0, outerBottom - visible.bottom);
        }
        if (v.getPaddingBottom() != bottom) {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bottom);
        }
    }

    private void initEngine() {
        // Retire the previous engine so its thread and stale error lines are gone.
        if (engine != null) engine.stop();
        engine = new CodeEngine(this, findViewById(R.id.editor), ide_collector, scm_loader);
        compiler = new ScriptCompiler(scm_loader, ide_collector);
        decompiler = new ScriptDecompiler(scm_loader, ide_collector);
        engine.getEditor().addOnTextChange(text -> {
            if (!restoringDocument && activeDocument != null) {
                String previousText = activeDocument.text == null ? "" : activeDocument.text;
                if (!previousText.equals(text)) {
                    activeDocument.undoHistory.add(previousText);
                    if (activeDocument.undoHistory.size() > 500) {
                        activeDocument.undoHistory.remove(0);
                    }
                    activeDocument.redoHistory.clear();
                }
                activeDocument.text = text;
                activeDocument.dirty = true;
                saved_file = false;
                refreshDocumentTabs();
            }
        });
        // The new engine (new game target) must analyse the current text right away:
        // this refreshes engine.format and the error list/red lines for the new target.
        if (activeDocument != null) engine.start();
        updateMenuIcon();
    }

    private void initIntent() {
        OpenLauncher = registerForActivityResult(new RequestOpenFile(), (data) -> {
            if(data != null) {
                try { getContentResolver().takePersistableUriPermission(data,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
                catch (Exception ignored) { }
                file_name = getFileName(data);
                try{
                    InputStream is = getContentResolver().openInputStream(data);
                    if(type_function == DECOMPILE && (file_name.endsWith(".cs") || file_name.endsWith(".csa") || file_name.endsWith(".csi"))) {
                        new Thread(() -> {
                            String decompiled = decompiler.decompile(is, file_name.substring(file_name.lastIndexOf(".")));
                            new File(App.dataDirectory + file_name).delete();
                            file_name = file_name.replaceAll(".csa|.csi|.cs", ".txt");
                            engine.enqueue(() -> runOnUiThread(() -> {
                                openDocument(file_name, decompiled, false, null);
                            }));
                        }).start();
                    } else if(type_function == OPEN_FILE && file_name.endsWith(".txt")) {
                        engine.enqueue(() -> runOnUiThread(() -> {
                            openDocument(file_name, readFileText(is), true, data);
                        }));
                    } else {
                        is.close();
                        Toast.makeText(getApplicationContext(), type_function == DECOMPILE ? getString(R.string.no_cleo_file) : getString(R.string.no_txt_file), Toast.LENGTH_SHORT).show();
                    }
                }catch (Exception e) {}
            }
        });
        SaveLauncher = registerForActivityResult(new RequestSaveFile(), (data) -> {
            if(data != null) {
                try {
                    try { getContentResolver().takePersistableUriPermission(data,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
                    catch (Exception ignored) { }
                    OutputStream out = getContentResolver().openOutputStream(data);
                    if(type_function == COMPILE) {
                       new Thread(() -> {
                           String source = engine.getEditor().getText();
                           String result;
                           try {
                               source = SannySourceParser.parse(source);
                               result = compiler.compile(source, out);
                           } catch (IllegalArgumentException parseError) {
                               try { out.close(); } catch (Exception ignored) {}
                               result = parseError.getMessage();
                           }
                           final String dialogResult = result;
                           runOnUiThread(() -> showDialogResult(dialogResult));
                       }).start();
                    } else if(type_function == SAVE_FILE) {
                        if (activeDocument != null) activeDocument.uri = data;
                        saved_file = true;
                        saveFileText(out);
                        if (activeDocument != null) {
                            activeDocument.text = engine.getEditor().getText();
                            activeDocument.dirty = false;
                            refreshDocumentTabs();
                        }
                        if(save_temp_func != -1) {
                            performFunction(save_temp_func);
                            save_temp_func = -1;
                        }
                    }
                } catch (Exception e) {
                }
            }
        });
    }

    @SuppressLint("NonConstantResourceId")
    private void initNavigationView(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                menuItem -> {
                    int itemId = menuItem.getItemId();
                    if (itemId == R.id.new_file) {
                        performFunction(NEW_FILE);
                    } else if (itemId == R.id.open) {
                        performFunction(OPEN_FILE);
                    } else if (itemId == R.id.save) {
                        performFunction(SAVE_FILE);
                    } else if (itemId == R.id.search) {
                        Intent intent = new Intent(MainActivity.this, SearchOpcodeActivity.class);
                        startActivity(intent);
                    } else if (itemId == R.id.compile) {
                        performFunction(COMPILE);
                    } else if (itemId == R.id.decompile) {
                        performFunction(DECOMPILE);
                    } else if (itemId == R.id.select_game) {
                        showSelectGameDialog();
                    }
                    drawerLayout.closeDrawer(navigationView);
                    return true;
                });
    }

    private void performFunction(int function) {
        this.type_function = function;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || permissionsAllowed() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(file_name == null && (function == COMPILE || function == SAVE_FILE)) {
                Toast.makeText(getApplicationContext(), getString(R.string.open_a_file), Toast.LENGTH_SHORT).show();
                return;
            }
            // Opening or creating another document no longer replaces the active
            // buffer; each tab retains its own unsaved text.
            if(function == OPEN_FILE || function == DECOMPILE) {
                OpenLauncher.launch(null);
            } else if(function == SAVE_FILE) {
                SaveLauncher.launch(file_name);
            } else if(function == COMPILE) {
                String format = engine.format;
                // PC CLEO scripts are always .cs; mobile takes .cs/.csa/.csi from {$CLEO ...}.
                if((format == null || format.isEmpty()) && gameTarget == GAME_PC) format = ".cs";
                if(format != null && !format.isEmpty()) {
                    String base = file_name.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")
                            ? file_name.substring(0, file_name.length() - 4) : file_name;
                    SaveLauncher.launch(base + format);
                } else {
                    Toast.makeText(this, getString(R.string.no_cleo_format), Toast.LENGTH_SHORT).show();
                }
            } else if(function == NEW_FILE) {
                showNewFileDialog();
            }
        } else {
            requestPermissions(new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },100);
        }
    }

    private void loadTextFile(String text) {
        openDocument(file_name == null ? "Untitled.txt" : file_name, text, false);
    }

    private void openDocument(String name, String text, boolean clean) {
        openDocument(name, text, clean, null);
    }

    private void openDocument(String name, String text, boolean clean, Uri uri) {
        if (activeDocument != null) captureActiveDocument();
        EditorDocument existing = null;
        for (EditorDocument document : openDocuments) {
            if (document.name.equals(name)) { existing = document; break; }
        }
        if (existing == null) {
            existing = new EditorDocument(name, text, !clean, uri);
            openDocuments.add(existing);
        } else if (text != null && !existing.dirty) {
            existing.text = text;
            if (uri != null) existing.uri = uri;
        }
        activateDocument(existing);
    }

    private void captureActiveDocument() {
        if (activeDocument == null || engine == null) return;
        activeDocument.text = engine.getEditor().getText();
        activeDocument.cursorStart = Math.max(0, engine.getEditor().getTextProcessor().getSelectionStart());
        activeDocument.cursorEnd = Math.max(0, engine.getEditor().getTextProcessor().getSelectionEnd());
        activeDocument.scrollX = engine.getEditor().getTextProcessor().getScrollX();
        activeDocument.scrollY = engine.getEditor().getTextProcessor().getScrollY();
    }

    private void activateDocument(EditorDocument document) {
        activeDocument = document;
        file_name = document.name;
        saved_file = !document.dirty;
        restoringDocument = true;
        engine.getEditor().setText(document.text, 1);
        // Restore selection and viewport only after the new document has been
        // measured. Restoring scroll before layout clamps it to the old/empty
        // content range and can send long documents back to line 1.
        final TextProcessor processor = engine.getEditor().getTextProcessor();
        final android.view.ViewTreeObserver observer = processor.getViewTreeObserver();
        observer.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (processor.getViewTreeObserver().isAlive()) {
                    processor.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                int length = engine.getEditor().getText().length();
                int start = Math.min(document.cursorStart, length);
                int end = Math.min(document.cursorEnd, length);
                int maxX = Math.max(0, processor.getLayout() == null ? 0
                        : processor.getLayout().getWidth() - processor.getWidth());
                int maxY = Math.max(0, processor.getLayout() == null ? 0
                        : processor.getLayout().getHeight() - processor.getHeight());
                // Restore the viewport first. TextView's insertion/selection controller
                // derives handle coordinates from the current scroll offsets; setting
                // selection before scroll restoration can leave its waterdrop stale.
                processor.scrollTo(Math.min(document.scrollX, maxX),
                        Math.min(document.scrollY, maxY));
                processor.setSelection(start, end);
                processor.postInvalidateOnAnimation();
                restoringDocument = false;
                return true;
            }
        });
        processor.requestLayout();
        updateDocumentMenuVisibility();
        engine.getEditor().setVisibility(View.VISIBLE);
        btnopenFile.setVisibility(View.GONE);
        engine.start();
        toolbar.setTitle("SR-Builder");
        refreshDocumentTabs();
    }

    private void refreshDocumentTabs() {
        if (documentTabs == null) return;
        documentTabs.removeAllViews();
        for (EditorDocument document : openDocuments) {
            LinearLayout tab = new LinearLayout(this);
            tab.setGravity(android.view.Gravity.CENTER_VERTICAL);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setPadding(dp(5), 0, dp(3), 0);
            android.graphics.drawable.GradientDrawable tabBg = new android.graphics.drawable.GradientDrawable();
            tabBg.setColor(document == activeDocument ? 0xFF303030 : 0xFF1B1B1B);
            tabBg.setCornerRadius(dp(5));
            tab.setBackground(tabBg);
            TextView label = new TextView(this);
            label.setText((document.dirty ? "• " : "") + document.name);
            label.setTextSize(11);
            label.setSingleLine(true);
            label.setOnClickListener(v -> { captureActiveDocument(); activateDocument(document); });
            tab.addView(label, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(22)));
            TextView close = new TextView(this);
            close.setText("×");
            close.setTextSize(18);
            close.setPadding(dp(4), 0, dp(2), 0);
            close.setOnClickListener(v -> closeDocument(document));
            tab.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(22)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(22));
            lp.rightMargin = dp(4);
            documentTabs.addView(tab, lp);
        }
        documentTabs.setVisibility(openDocuments.isEmpty() ? View.GONE : View.VISIBLE);
        updateDocumentMenuVisibility();
    }

    private void updateDocumentMenuVisibility() {
        if (menu == null) return;
        boolean hasOpenDocument = !openDocuments.isEmpty() && activeDocument != null;
        menu.findItem(R.id.code_errors).setVisible(hasOpenDocument);
        menu.findItem(R.id.undo).setVisible(hasOpenDocument);
        menu.findItem(R.id.redo).setVisible(hasOpenDocument);
        menu.findItem(R.id.save_current).setVisible(hasOpenDocument);
    }

    private void undoActiveDocument() {
        if (activeDocument == null || activeDocument.undoHistory.isEmpty()) return;
        String current = engine.getEditor().getText();
        String previous = activeDocument.undoHistory.remove(activeDocument.undoHistory.size() - 1);
        activeDocument.redoHistory.add(current);
        restoringDocument = true;
        engine.getEditor().setText(previous, 1);
        activeDocument.text = previous;
        restoringDocument = false;
        activeDocument.dirty = true;
        saved_file = false;
        refreshDocumentTabs();
    }

    private void redoActiveDocument() {
        if (activeDocument == null || activeDocument.redoHistory.isEmpty()) return;
        String current = engine.getEditor().getText();
        String next = activeDocument.redoHistory.remove(activeDocument.redoHistory.size() - 1);
        activeDocument.undoHistory.add(current);
        restoringDocument = true;
        engine.getEditor().setText(next, 1);
        activeDocument.text = next;
        restoringDocument = false;
        activeDocument.dirty = true;
        saved_file = false;
        refreshDocumentTabs();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void closeDocument(EditorDocument document) {
        if (document == activeDocument) captureActiveDocument();
        if (document.dirty) {
            new AlertDialog.Builder(this).setMessage("File " + document.name + " memiliki perubahan yang belum disimpan. Tutup tanpa menyimpan?")
                    .setPositiveButton("Tutup", (d, w) -> removeDocument(document))
                    .setNegativeButton("Batal", null).show();
        } else removeDocument(document);
    }

    private void removeDocument(EditorDocument document) {
        int index = openDocuments.indexOf(document);
        openDocuments.remove(document);
        if (document == activeDocument) {
            activeDocument = null;
            if (!openDocuments.isEmpty()) activateDocument(openDocuments.get(Math.max(0, index - 1)));
            else {
                file_name = null;
                saved_file = true;
                engine.getEditor().setText("", 1);
                engine.getEditor().setVisibility(View.GONE);
                btnopenFile.setVisibility(View.VISIBLE);
                toolbar.setTitle("SR-Builder");
                refreshDocumentTabs();
                updateDocumentMenuVisibility();
            }
        } else refreshDocumentTabs();
    }

    public void updateMenuIcon() {
        if(menu != null) {
            if(engine.hasErrors()) {
                menu.findItem(R.id.code_errors).setIcon(R.drawable.ic_error);
            } else {
                menu.findItem(R.id.code_errors).setIcon(R.drawable.ic_code);
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    public String readFileText(InputStream is) {
        try {
            byte[] data = new byte[is.available()];
            is.read(data);
            is.close();
            is = null;
            return new String(data);
        } catch (Exception e) {}
        return null;
    }

    private void saveCurrentDocument() {
        if (activeDocument == null) {
            Toast.makeText(this, getString(R.string.open_a_file), Toast.LENGTH_SHORT).show();
            return;
        }
        if (activeDocument.uri == null) {
            // New/decompiled documents have no writable source URI yet.
            performFunction(SAVE_FILE);
            return;
        }
        try {
            OutputStream out = getContentResolver().openOutputStream(activeDocument.uri, "wt");
            if (out == null) throw new IOException("Tidak dapat membuka file untuk ditulis");
            out.write(engine.getEditor().getText().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            out.close();
            activeDocument.text = engine.getEditor().getText();
            activeDocument.dirty = false;
            saved_file = true;
            refreshDocumentTabs();
            Toast.makeText(this, getString(R.string.file_saved), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.save_failed) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void saveFileText(OutputStream out) {
        try{
            out.write(engine.getEditor().getText().getBytes());
            out.close();
            saved_file = true;
        } catch(Exception e) {
        }
    }

    private void showSaveConfirmationDialog(int current_func) {
        save_temp_func = current_func;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.save_changes));
        builder.setMessage(getString(R.string.save_changes_content));
        builder.setPositiveButton(getString(R.string.save), (dialog, which) -> {
            performFunction(SAVE_FILE);
            dialog.dismiss();
        });
        builder.setNegativeButton(getString(R.string.no_save), (dialog, which) -> {
            saved_file = true;
            performFunction(current_func);
            saved_file = false;
        });
        builder.setNeutralButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showSelectGameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.select_game_title));
        String[] options = { getString(R.string.game_pc), getString(R.string.game_mobile) };
        int selected = gameTarget == GAME_PC ? 0 : 1;
        builder.setSingleChoiceItems(options, selected, (dialog, which) -> {
            gameTarget = which == 0 ? GAME_PC : GAME_MOBILE;
            saveGameTarget();
            loadOpcodes();
            initEngine();
            dialog.dismiss();
        });
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void loadGameTarget() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        gameTarget = prefs.getInt(PREF_GAME_TARGET, GAME_MOBILE);
    }

    private void saveGameTarget() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(PREF_GAME_TARGET, gameTarget).apply();
    }

    private void showNewFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.create_new_file));
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_new_file, null);
        final EditText editTextFileName = dialogView.findViewById(R.id.editTextFileName);
        builder.setView(dialogView);
        builder.setPositiveButton(getString(R.string.create), (dialog, which) -> {
            file_name = editTextFileName.getText().toString() + ".txt";
            loadTextFile("");
            saved_file = false;
        });
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) ->  dialog.dismiss());
        AlertDialog alertDialog = builder.create();
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    private void showDialogResult(String result) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(compiler.hasError() ? "Error" : getString(R.string.result));
        builder.setMessage(result);
        builder.setPositiveButton(getString(R.string.accept), (dialog, which) -> {
            if(compiler.hasError()) {
                try {
                    engine.getEditor().gotoLine(compiler.line_idx);
                } catch (TextNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            dialog.dismiss();
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    public void extractAssets() {
        try{
            String[] files = getAssets().list("gtasa");
            for(String file : files) {
                if(!new File(App.dataDirectory + file).exists()) {
                    InputStream is = getAssets().open("gtasa/" + file);
                    FileOutputStream os = new FileOutputStream(App.dataDirectory + file);
                    byte[] data = new byte[is.available()];
                    is.read(data);
                    os.write(data);
                    is.close();
                    os.close();
                }
            }
        } catch(Exception ignored) {
        }
    }
}