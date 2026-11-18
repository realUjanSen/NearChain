package com.nearchain.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.graphics.Bitmap;
import android.content.Intent;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nearchain.core.Consensus;

import com.nearchain.core.Chain;
import com.nearchain.net.Log;
import com.nearchain.core.TxCodec;
import com.nearchain.core.Sha;
import com.nearchain.wallet.Wallet;
import com.nearchain.wallet.WalletStore;

import java.io.File;

public class MainActivity extends Activity {
    /** Matches MIN_FEE in src/nearchain/consensus.py. */
    private static final long MIN_FEE = 1L;
    private static final int MIN_PASSWORD_LENGTH = 8;

    private View minerPanel;
    private View traderPanel;
    private TextView statusView;
    private TextView heightView;
    private TextView difficultyView;
    private TextView rewardView;
    private TextView blocksView;
    private TextView hashrateView;
    private TextView nonceView;
    private TextView logView;
    private TextView addressView;
    private TextView balanceView;
    private TextView historyView;
    private TextView walletStatusView;
    private EditText recipientEdit;
    private EditText amountEdit;
    private EditText feeEdit;
    private TextView signedView;
    private TextView updateStatusView;
    private Button startButton;
    private ImageView qrView;
    private boolean qrShown;
    private Button stopButton;
    private ScrollView logScroll;
    private EditText logLimitEdit;
    private EditText hostEdit;
    private TextView peersView;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            handler.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        minerPanel = findViewById(R.id.minerPanel);
        traderPanel = findViewById(R.id.traderPanel);
        statusView = findViewById(R.id.statusView);
        heightView = findViewById(R.id.heightView);
        difficultyView = findViewById(R.id.difficultyView);
        rewardView = findViewById(R.id.rewardView);
        blocksView = findViewById(R.id.blocksView);
        hashrateView = findViewById(R.id.hashrateView);
        nonceView = findViewById(R.id.nonceView);
        logView = findViewById(R.id.logView);
        addressView = findViewById(R.id.addressView);
        balanceView = findViewById(R.id.balanceView);
        historyView = findViewById(R.id.historyView);
        walletStatusView = findViewById(R.id.walletStatusView);
        recipientEdit = findViewById(R.id.recipientEdit);
        amountEdit = findViewById(R.id.amountEdit);
        feeEdit = findViewById(R.id.feeEdit);
        signedView = findViewById(R.id.signedView);
        updateStatusView = findViewById(R.id.updateStatusView);
        try {
            updateStatusView.setText("Version " + getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception ignored) {
            updateStatusView.setText("");
        }
        hostEdit = findViewById(R.id.hostEdit);
        peersView = findViewById(R.id.peersView);

        NodeHolder.startNetwork(this);
        ((Button) findViewById(R.id.checkUpdateButton)).setOnClickListener(v -> checkForUpdate());
        ((Button) findViewById(R.id.connectButton)).setOnClickListener(v -> connectToPeer());

        ((Button) findViewById(R.id.tabMiner)).setOnClickListener(v -> showPanel(true));
        ((Button) findViewById(R.id.tabTrader)).setOnClickListener(v -> showPanel(false));
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        logScroll = findViewById(R.id.logScroll);
        logLimitEdit = findViewById(R.id.logLimitEdit);
        startButton.setOnClickListener(v -> startMining());
        stopButton.setOnClickListener(v -> MiningService.stop(this));
        ((Button) findViewById(R.id.deleteWalletButton)).setOnClickListener(v -> confirmDeleteWallet());
        qrView = findViewById(R.id.qrView);
        ((Button) findViewById(R.id.showQrButton)).setOnClickListener(v -> toggleQr());
        ((Button) findViewById(R.id.explorerButton)).setOnClickListener(
                v -> startActivity(new Intent(this, ExplorerActivity.class)));
        setupLogLimit();
        ((Button) findViewById(R.id.createWalletButton)).setOnClickListener(v -> createWallet());
        ((Button) findViewById(R.id.unlockButton)).setOnClickListener(v -> unlockWallet());
        ((Button) findViewById(R.id.sendButton)).setOnClickListener(v -> buildSignedTransfer());
        showPanel(true);
    }

    /** Start is green while idle; Stop is red while mining. The inactive button is grey. */
    private void updateMiningButtons() {
        boolean running = NodeHolder.isMining();
        startButton.setEnabled(!running);
        startButton.setBackgroundColor(running ? 0xFF9E9E9E : 0xFF2E7D32);
        startButton.setTextColor(0xFFFFFFFF);
        stopButton.setEnabled(running);
        stopButton.setBackgroundColor(running ? 0xFFC62828 : 0xFF9E9E9E);
        stopButton.setTextColor(0xFFFFFFFF);
    }

    /** Loads the saved line limit and saves changes as the user types. */
    private void setupLogLimit() {
        int limit = getSharedPreferences("nearchain", MODE_PRIVATE).getInt("log_limit", 200);
        NodeHolder.setLogLimit(limit);
        logLimitEdit.setText(String.valueOf(limit));
        logLimitEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                try {
                    int lines = Integer.parseInt(text.toString().trim());
                    if (lines >= 20 && lines <= 5000) {
                        NodeHolder.setLogLimit(lines);
                        getSharedPreferences("nearchain", MODE_PRIVATE).edit().putInt("log_limit", lines).apply();
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore partial input such as an empty field.
                }
            }
        });
    }

    /** Shows the wallet address as a QR code so another phone can scan it and send CDC here. */
    private void toggleQr() {
        String address = WalletStore.address(this);
        if (address == null) {
            toast("Create a wallet first.");
            return;
        }
        if (qrShown) {
            qrView.setVisibility(View.GONE);
            qrShown = false;
            return;
        }
        try {
            Bitmap code = QrCodes.encode(address, 660);
            qrView.setImageBitmap(code);
            qrView.setVisibility(View.VISIBLE);
            qrShown = true;
            toast("Scan this code to send CDC to " + address);
        } catch (Exception e) {
            toast("Could not make the QR code: " + e.getMessage());
        }
    }

    // ----- wallet deletion -----

    private void confirmDeleteWallet() {
        if (!WalletStore.exists(this)) {
            toast("There is no wallet to delete.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Delete wallet?")
                .setMessage("This removes the wallet from this phone. Any CDC held by this address is lost "
                        + "unless you backed up the key. Mining will stop.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    MiningService.stop(this);
                    NodeHolder.setWallet(null);
                    if (WalletStore.delete(this)) {
                        toast("Wallet deleted.");
                    } else {
                        toast("Could not delete the wallet file.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPanel(boolean miner) {
        minerPanel.setVisibility(miner ? View.VISIBLE : View.GONE);
        traderPanel.setVisibility(miner ? View.GONE : View.VISIBLE);
    }

    private void startMining() {
        if (!WalletStore.exists(this)) {
            showPanel(false);
            toast("Create a wallet on this screen first. Mined coins are paid to it.");
            return;
        }
        MiningService.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    // ----- network -----

    private void connectToPeer() {
        String entry = hostEdit.getText().toString().trim();
        int colon = entry.lastIndexOf(':');
        if (colon <= 0) {
            toast("Enter the address as host:port, for example 192.168.1.20:8333");
            return;
        }
        try {
            int port = Integer.parseInt(entry.substring(colon + 1));
            NodeHolder.net().connect(entry.substring(0, colon), port);
            hostEdit.setText("");
        } catch (NumberFormatException e) {
            toast("The port must be a number.");
        }
    }

    // ----- updates -----

    private void checkForUpdate() {
        updateStatusView.setText("Checking GitHub...");
        runInBackground(() -> {
            try {
                Updater.Release release = Updater.latest();
                int installed = Updater.installedVersionCode(this);
                if (release == null || release.number <= installed) {
                    setUpdateStatus("You have the latest version (build " + installed + ").");
                    return;
                }
                setUpdateStatus("Update available: " + release.tag);
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("Update NearChain")
                        .setMessage("Install " + release.tag + "? Android will ask you to confirm.")
                        .setPositiveButton("Install", (dialog, which) -> installUpdate(release))
                        .setNegativeButton("Later", null)
                        .show());
            } catch (Exception e) {
                setUpdateStatus("Update check failed: " + e.getMessage());
            }
        });
    }

    private void installUpdate(Updater.Release release) {
        if (!Updater.canInstall(this)) {
            toast("Allow NearChain to install apps, then press Install again.");
            startActivity(Updater.unknownSourcesSettings(this));
            return;
        }
        runInBackground(() -> {
            try {
                File apk = Updater.download(this, release, NodeHolder::log);
                Updater.install(this, apk, NodeHolder::log);
                setUpdateStatus("Install started for " + release.tag + ".");
            } catch (Exception e) {
                setUpdateStatus("Update failed: " + e.getMessage());
            }
        });
    }

    private void setUpdateStatus(String text) {
        runOnUiThread(() -> updateStatusView.setText(text));
    }

    // ----- wallet -----

    private void createWallet() {
        if (WalletStore.exists(this)) {
            toast("A wallet already exists on this phone.");
            return;
        }
        askPassword("Create wallet", "Choose a password (at least 8 characters). It cannot be recovered if lost.",
                true, password -> {
                    if (password.length() < MIN_PASSWORD_LENGTH) {
                        toast("Password is too short.");
                        return;
                    }
                    runInBackground(() -> {
                        try {
                            Wallet wallet = WalletStore.create(this, password);
                            NodeHolder.setWallet(wallet);
                            toast("Wallet created. Address: " + wallet.address());
                        } catch (WalletStore.WalletException e) {
                            toast(e.getMessage());
                        }
                    });
                });
    }

    private void unlockWallet() {
        if (!WalletStore.exists(this)) {
            toast("No wallet yet. Create one first.");
            return;
        }
        askPassword("Unlock wallet", "Enter your wallet password.", false, password ->
                runInBackground(() -> {
                    try {
                        NodeHolder.setWallet(WalletStore.unlock(this, password));
                        toast("Wallet unlocked.");
                    } catch (WalletStore.WalletException e) {
                        toast(e.getMessage());
                    }
                }));
    }

    private interface PasswordCallback {
        void onPassword(String password);
    }

    private void askPassword(String title, String message, boolean confirm, PasswordCallback callback) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText first = passwordField("Password");
        form.addView(first);
        EditText second = null;
        if (confirm) {
            second = passwordField("Repeat password");
            form.addView(second);
        }
        final EditText repeat = second;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(form)
                .setPositiveButton("OK", (dialog, which) -> {
                    String password = first.getText().toString();
                    if (repeat != null && !password.equals(repeat.getText().toString())) {
                        toast("Passwords do not match.");
                        return;
                    }
                    callback.onPassword(password);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText passwordField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setTransformationMethod(PasswordTransformationMethod.getInstance());
        return field;
    }

    // ----- transfers -----

    private void buildSignedTransfer() {
        Wallet wallet = NodeHolder.wallet();
        if (wallet == null) {
            toast("Unlock your wallet first.");
            return;
        }
        String recipient = recipientEdit.getText().toString().trim();
        if (!TxCodec.isValidAddress(recipient)) {
            toast("That is not a valid CDC address.");
            return;
        }
        if (recipient.equals(wallet.address())) {
            toast("You cannot send to your own address.");
            return;
        }
        long amount;
        long fee;
        try {
            amount = Long.parseLong(amountEdit.getText().toString().trim());
            fee = Long.parseLong(feeEdit.getText().toString().trim());
        } catch (NumberFormatException e) {
            toast("Amount and fee must be whole numbers.");
            return;
        }
        if (amount <= 0) {
            toast("Amount must be more than zero.");
            return;
        }
        if (fee < MIN_FEE) {
            toast("Fee must be at least " + MIN_FEE + " CDC.");
            return;
        }
        long balance = NodeHolder.chain().balance(wallet.address());
        if (amount + fee > balance) {
            toast("Not enough CDC. Balance is " + balance + ".");
            return;
        }
        // Transfers are not yet included in Android blocks, so the confirmed nonce is 0.
        long nonce = 0L;
        String signing = TxCodec.signingJson(wallet.publicKeyHex(), wallet.address(), recipient,
                amount, fee, nonce, com.nearchain.core.Consensus.NETWORK_ID);
        String signatureHex = Sha.toHex(wallet.sign(signing.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        String signed = TxCodec.signedJson(wallet.publicKeyHex(), wallet.address(), recipient,
                amount, fee, nonce, com.nearchain.core.Consensus.NETWORK_ID, signatureHex);
        signedView.setText("Signed transfer. Tx ID: " + TxCodec.txId(signed) + "\n\n" + signed);
    }

    // ----- display -----

    private void refreshUi() {
        Chain chain = NodeHolder.chain();
        Wallet wallet = NodeHolder.wallet();
        String address = WalletStore.address(this);

        statusView.setText("Status: " + (NodeHolder.isMining() ? "mining" : "stopped")
                + " (" + NodeHolder.lastEvent() + ")");
        heightView.setText("Block height: " + chain.height());
        difficultyView.setText("Difficulty: " + String.format("%,d", NodeHolder.currentDifficulty()));
        rewardView.setText("Next reward: " + String.format("%,d", NodeHolder.rewardForNextBlock()) + " CDC");
        blocksView.setText("Blocks mined by this phone: " + NodeHolder.blocksMined());
        hashrateView.setText(String.format("Hash rate: %,.0f hashes/s", NodeHolder.hashrate()));
        nonceView.setText("Current nonce: " + String.format("%,d", NodeHolder.currentNonce()));
        updateMiningButtons();
        String logText = NodeHolder.logText();
        if (!logText.equals(logView.getText().toString())) {
            logView.setText(logText);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }

        peersView.setText(NodeHolder.peersText());
        addressView.setText(address == null ? "No wallet yet" : "Address: " + address);
        walletStatusView.setText(wallet != null ? "Wallet unlocked"
                : address == null ? "No wallet. Create one to start mining." : "Wallet locked");
        if (address != null) {
            balanceView.setText(String.format("Balance: %,d CDC", chain.balance(address)));
            historyView.setText(NodeHolder.history(address));
        } else {
            balanceView.setText("Balance: -");
            historyView.setText("");
        }
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void runInBackground(Runnable task) {
        new Thread(task, "nearchain-wallet").start();
    }
}
