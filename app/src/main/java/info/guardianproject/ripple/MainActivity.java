package info.guardianproject.ripple;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

import info.guardianproject.panic.Panic;
import info.guardianproject.panic.PanicTrigger;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    private static final int CONNECT_RESULT = 0x01;

    String responders[];
    Set<String> connectedResponders;
    Set<String> respondersThatCanConnect;
    ArrayList<CharSequence> appLabelList;
    ArrayList<Drawable> iconList;

    SharedPreferences prefs;

    String requestPackageName;
    String requestAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (PanicTrigger.checkForConnectIntent(this)
                || PanicTrigger.checkForDisconnectIntent(this)) {
            finish();
            return;
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.activity_main);

        View panicButton = findViewById(R.id.panic_button);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);
        panicButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, PanicActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        responders = PanicTrigger.getAllResponders(this).toArray(new String[0]);
        connectedResponders = PanicTrigger.getConnectedResponders(this);
        respondersThatCanConnect = PanicTrigger.getRespondersThatCanConnect(this);

        PackageManager pm = getPackageManager();
        appLabelList = new ArrayList<CharSequence>(responders.length);
        iconList = new ArrayList<Drawable>(responders.length);
        for (String packageName : responders) {
            try {
                appLabelList.add(pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)));
                iconList.add(pm.getApplicationIcon(packageName));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.addItemDecoration(new SimpleDividerItemDecoration(getApplicationContext()));
        recyclerView.setHasFixedSize(true); // does not change, except in onResume()
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new RecyclerView.Adapter<AppRowHolder>() {
            @Override
            public AppRowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return (new AppRowHolder(getLayoutInflater().inflate(R.layout.row, parent, false)));
            }

            @Override
            public void onBindViewHolder(AppRowHolder holder, int position) {
                String packageName = responders[position];
                boolean canConnect = respondersThatCanConnect.contains(packageName);
                holder.setupForApp(
                        packageName,
                        iconList.get(position),
                        appLabelList.get(position),
                        canConnect);
            }

            @Override
            public int getItemCount() {
                return appLabelList.size();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_test_run:
                Intent intent = new Intent(this, PanicActivity.class);
                intent.putExtra(PanicActivity.EXTRA_TEST_RUN, true);
                startActivity(intent);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK)
            return;

        switch (requestCode) {
            case CONNECT_RESULT:
                /*
                 * Only ACTION_CONNECT needs the confirmation from
                 * onActivityResult(), listView.setOnItemClickListener handles
                 * all the other adding and removing of panic responders.
                 */
                if (TextUtils.equals(requestAction, Panic.ACTION_CONNECT)) {
                    PanicTrigger.addConnectedResponder(this, requestPackageName);
                }
                break;
        }
    }

    class AppRowHolder extends RecyclerView.ViewHolder {

        private View.OnClickListener onClickListener;
        private SwitchCompat onSwitch;
        private TextView editableLabel;
        private String rowPackageName;
        private ImageView iconView;
        private TextView appLabelView;

        AppRowHolder(final View row) {
            super(row);

            iconView = (ImageView) row.findViewById(R.id.iconView);
            appLabelView = (TextView) row.findViewById(R.id.appLabel);
            editableLabel = (TextView) row.findViewById(R.id.editableLabel);
            onSwitch = (SwitchCompat) row.findViewById(R.id.on_switch);
            onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    requestPackageName = rowPackageName;

                    if (respondersThatCanConnect.contains(rowPackageName)) {
                        requestAction = Panic.ACTION_CONNECT;
                        // addReceiver() happens in onActivityResult()
                        // TODO this requires row.xml to have a separate TextView and Switch
                        //} else {
                        //    requestAction = Panic.ACTION_DISCONNECT;
                        //    PanicTrigger.removeConnectedResponder(context, requestPackageName);
                    }
                    Intent intent = new Intent(requestAction);
                    intent.setPackage(requestPackageName);
                    // TODO add TrustedIntents here
                    startActivityForResult(intent, CONNECT_RESULT);
                }
            };

            onSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean enabled) {
                    prefs.edit().putBoolean(rowPackageName, enabled).apply();
                    setEnabled(enabled);
                }
            });
        }

        void setEnabled(boolean enabled) {
            if (enabled) {
                editableLabel.setVisibility(View.VISIBLE);
                appLabelView.setEnabled(true);
                iconView.setEnabled(true);
                iconView.setColorFilter(null);
            } else {
                editableLabel.setVisibility(View.GONE);
                appLabelView.setEnabled(false);
                iconView.setEnabled(false);
                // grey out app icon when disabled
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(0);
                ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
                iconView.setColorFilter(filter);
            }
        }

        void setupForApp(String packageName, Drawable icon, CharSequence appLabel, boolean editable) {
            this.rowPackageName = packageName;
            iconView.setImageDrawable(icon);
            appLabelView.setText(appLabel);
            if (editable) {
                iconView.setOnClickListener(onClickListener);
                appLabelView.setOnClickListener(onClickListener);
                editableLabel.setOnClickListener(onClickListener);
                editableLabel.setText(R.string.edit);
                editableLabel.setTypeface(null, Typeface.BOLD);
                if (Build.VERSION.SDK_INT >= 14)
                    editableLabel.setAllCaps(true);
            } else {
                iconView.setOnClickListener(null);
                appLabelView.setOnClickListener(null);
                editableLabel.setOnClickListener(null);
                editableLabel.setText(R.string.app_locks);
                editableLabel.setTypeface(null, Typeface.NORMAL);
                if (Build.VERSION.SDK_INT >= 14)
                    editableLabel.setAllCaps(false);
            }
            boolean enabled = prefs.getBoolean(packageName, true);
            onSwitch.setChecked(enabled);
            setEnabled(enabled);
        }
    }
}
