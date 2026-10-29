package com.fastsmartsystem.cleoeditor;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fastsmartsystem.cleo.OpcodeInfo;
import com.fastsmartsystem.cleo.OpcodesLoader;
import com.fastsmartsystem.cleoeditor.views.OnOpcodeItemListener;
import com.fastsmartsystem.cleoeditor.views.OpcodeAdapter;
import com.fastsmartsystem.cleoeditor.views.OpcodeItem;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SearchOpcodeActivity extends AppCompatActivity implements OnOpcodeItemListener {

    private OpcodeAdapter adapter;
    OpcodesLoader op_loader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_opcodes);

        // Configurar el RecyclerView
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Opcode search intentionally uses the plain-text opcodes.txt as its sole source.
        // Keep the line exactly as authored there (e.g. "0004: $120 = 0") instead of
        // generating a template from the binary opcode database.
        op_loader = new OpcodesLoader(App.dataDirectory + "sa_mobile.dat");
        ArrayList<OpcodeItem> opcodes = new ArrayList<>();
        java.io.File customOpcodeFile = new java.io.File("/sdcard/sr-builder/sa_mobile/opcodes.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                customOpcodeFile.isFile() && customOpcodeFile.canRead()
                        ? new java.io.FileInputStream(customOpcodeFile)
                        : getAssets().open("mobile/opcodes.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String rawLine = line.trim();
                if (rawLine.isEmpty() || rawLine.startsWith(";") || rawLine.startsWith("#")) continue;
                int colon = rawLine.indexOf(':');
                if (colon <= 0) continue;
                int id;
                try {
                    id = Integer.parseInt(rawLine.substring(0, colon).trim(), 16);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                opcodes.add(new OpcodeItem(rawLine, id));
            }
        } catch (Exception e) {
            android.util.Log.e("SearchOpcodeActivity", "Could not read mobile/opcodes.txt", e);
            Toast.makeText(this, "Tidak dapat membaca opcodes.txt", Toast.LENGTH_LONG).show();
        }
        adapter = new OpcodeAdapter(opcodes, this);
        recyclerView.setAdapter(adapter);
        // Configurar el Toolbar como la barra de acción
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.opcode_menu_search, menu);
        MenuItem searchItem = menu.findItem(R.id.actionTenantSearch);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                adapter.filter(s); // Llamar al método de filtrado con el texto de búsqueda
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void OnClick(OpcodeItem item) {
        OpcodeInfo info = op_loader.ops.get(item.id);
        if (info != null) {
            showOpcodeInfoDialog(info, item.id);
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(String.format("Opcode %04X", item.id))
                    .setMessage(item.name + "\n\nTahan teks opcode untuk menyalinnya ke clipboard.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void showOpcodeInfoDialog(OpcodeInfo info, int id) {
        String format_id = String.format("%04X", id);
        View view = LayoutInflater.from(this).inflate(R.layout.opcode_info_dialog, null);
        TextView tvOpcodeTitle = view.findViewById(R.id.opcode_id_dialog);
        TextView tvOpcodeInfo = view.findViewById(R.id.tvCodeInfoDiag);
        TextView tvOpcodeExample = view.findViewById(R.id.tvCodeExample);
        TextView tvOpcodeLink = view.findViewById(R.id.tvGTALink);
        tvOpcodeLink.setPaintFlags(tvOpcodeLink.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        tvOpcodeTitle.setText("Opcode ID: " + format_id);
        tvOpcodeInfo.setText(info.description);
        tvOpcodeExample.setText(info.example.length() > 0 ? info.example : getString(R.string.no_example_available));
        ImageView ivClose = view.findViewById(R.id.ivCloseDiag);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(view);
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setPositiveButton(getString(R.string.copy_template), (dialog, which) -> {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = ClipData.newPlainText("Opcode Copied",format_id +": " + info.template);
            clipboardManager.setPrimaryClip(clipData);
            Toast.makeText(this, getString(R.string.copied_correctly), Toast.LENGTH_SHORT).show();
        });
        tvOpcodeLink.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://gtamods.com/wiki/Main_Page"));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        });
        AlertDialog dialog = alertDialogBuilder.create();
        dialog.show();
        // Force the entire opcode information dialog surface (including the window frame)
        // to use a white background rather than inheriting the app's dark dialog theme.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
        }
        view.setBackgroundColor(android.graphics.Color.WHITE);
        tvOpcodeTitle.setTextColor(android.graphics.Color.BLACK);
        tvOpcodeInfo.setTextColor(android.graphics.Color.BLACK);
        tvOpcodeExample.setTextColor(android.graphics.Color.BLACK);
        tvOpcodeLink.setTextColor(android.graphics.Color.BLACK);
        // Keep the COPY TEMPLATE action readable in the light opcode information dialog.
        android.widget.Button copyButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (copyButton != null) {
            copyButton.setTextColor(android.graphics.Color.BLACK);
            copyButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
        }
        ivClose.setOnClickListener((v) -> dialog.dismiss());
    }
}
