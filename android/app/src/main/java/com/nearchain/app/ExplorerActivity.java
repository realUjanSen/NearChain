package com.nearchain.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.nearchain.core.Block;
import com.nearchain.core.Chain;
import com.nearchain.wallet.WalletStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Shows every known wallet's balance, recent blocks, and the transactions recorded in them. */
public class ExplorerActivity extends Activity {
    private static final int MAX_BLOCKS_SHOWN = 500;

    private TextView balancesView;
    private TextView blocksView;
    private TextView transactionsView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explorer);
        balancesView = findViewById(R.id.balancesView);
        blocksView = findViewById(R.id.blocksView);
        transactionsView = findViewById(R.id.transactionsView);
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

    private void render() {
        Chain chain = NodeHolder.chain();
        String me = WalletStore.address(this);
        List<Block> blocks = chain.blocks();

        // Balances, largest first. The phone's own wallet is marked.
        Map<String, Long> balances = chain.balances();
        List<Map.Entry<String, Long>> rows = new ArrayList<>(balances.entrySet());
        // Collections.sort rather than List.sort, which needs Android API 24.
        Collections.sort(rows, (a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder bal = new StringBuilder();
        for (Map.Entry<String, Long> row : rows) {
            bal.append(row.getKey());
            if (row.getKey().equals(me)) {
                bal.append("  (this phone)");
            }
            bal.append('\n').append(String.format("    %,d CDC%n", row.getValue()));
        }
        balancesView.setText(bal.length() == 0 ? "No wallet has been paid yet." : bal.toString());

        // Blocks, newest first.
        StringBuilder blockText = new StringBuilder();
        int shown = 0;
        for (int i = blocks.size() - 1; i >= 0 && shown < MAX_BLOCKS_SHOWN; i--, shown++) {
            Block b = blocks.get(i);
            blockText.append(String.format("#%d  %s  miner %s  reward %,d  txs 0%n",
                    b.header.height, b.hash().substring(0, 12), shortAddress(b.miner), b.reward));
        }
        blocksView.setText(blockText.length() == 0 ? "No blocks." : blockText.toString());

        // Transactions. Only mining rewards exist yet; see the note on the screen.
        StringBuilder tx = new StringBuilder();
        for (Block b : blocks) {
            if (b.reward > 0) {
                tx.append(String.format("#%d  mining reward  %s  +%,d CDC%n",
                        b.header.height, shortAddress(b.miner), b.reward));
            }
        }
        transactionsView.setText(tx.length() == 0 ? "No transactions yet." : tx.toString());
    }

    private static String shortAddress(String address) {
        return address.length() > 14 ? address.substring(0, 14) + "..." : address;
    }
}
