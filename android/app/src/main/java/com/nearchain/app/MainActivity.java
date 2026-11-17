package com.nearchain.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.nearchain.core.Chain;

public class MainActivity extends Activity {
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
    private String address;

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

        address = Identity.address(this);
        addressView.setText("Address: " + address);

        ((Button) findViewById(R.id.tabMiner)).setOnClickListener(v -> showPanel(true));
        ((Button) findViewById(R.id.tabTrader)).setOnClickListener(v -> showPanel(false));
        ((Button) findViewById(R.id.startButton)).setOnClickListener(v -> MiningService.start(this));
        ((Button) findViewById(R.id.stopButton)).setOnClickListener(v -> MiningService.stop(this));
        showPanel(true);
    }

    private void showPanel(boolean miner) {
        minerPanel.setVisibility(miner ? View.VISIBLE : View.GONE);
        traderPanel.setVisibility(miner ? View.GONE : View.VISIBLE);
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

    private void refreshUi() {
        Chain chain = NodeHolder.chain();
        statusView.setText("Status: " + (NodeHolder.isMining() ? "mining" : "stopped")
                + " (" + NodeHolder.lastEvent() + ")");
        heightView.setText("Block height: " + chain.height());
        difficultyView.setText("Difficulty: " + String.format("%,d", NodeHolder.currentDifficulty()));
        rewardView.setText("Next reward: " + String.format("%,d", NodeHolder.rewardForNextBlock()) + " CDC");
        blocksView.setText("Blocks mined by this phone: " + NodeHolder.blocksMined());
        hashrateView.setText(String.format("Hash rate: %,.0f hashes/s", NodeHolder.hashrate()));
        nonceView.setText("Current nonce: " + String.format("%,d", NodeHolder.currentNonce()));
        logView.setText(NodeHolder.logText());

        balanceView.setText(String.format("Balance: %,d CDC", chain.balance(address)));
        historyView.setText(NodeHolder.history(address));
    }
}
