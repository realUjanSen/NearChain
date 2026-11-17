package com.nearchain.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nearchain.core.Chain;
import com.nearchain.core.TxCodec;
import com.nearchain.core.Sha;
import com.nearchain.wallet.Wallet;
import com.nearchain.wallet.WalletStore;

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

        ((Button) findViewById(R.id.tabMiner)).setOnClickListener(v -> showPanel(true));
        ((Button) findViewById(R.id.tabTrader)).setOnClickListener(v -> showPanel(false));
        ((Button) findViewById(R.id.startButton)).setOnClickListener(v -> startMining());
        ((Button) findViewById(R.id.stopButton)).setOnClickListener(v -> MiningService.stop(this));
        ((Button) findViewById(R.id.createWalletButton)).setOnClickListener(v -> createWallet());
        ((Button) findViewById(R.id.unlockButton)).setOnClickListener(v -> unlockWallet());
        ((Button) findViewById(R.id.sendButton)).setOnClickListener(v -> buildSignedTransfer());
        showPanel(true);
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
        logView.setText(NodeHolder.logText());

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