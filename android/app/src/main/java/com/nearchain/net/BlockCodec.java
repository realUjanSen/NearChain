package com.nearchain.net;

import com.nearchain.core.Block;
import com.nearchain.core.ChainException;
import com.nearchain.core.Header;
import com.nearchain.core.Tx;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Converts blocks and transfers to and from the JSON used on the wire. Field names match the Python core. */
public final class BlockCodec {
    private BlockCodec() {
    }

    public static JSONObject toJson(Block block) throws JSONException {
        Header h = block.header;
        JSONObject header = new JSONObject()
                .put("version", h.version)
                .put("height", h.height)
                .put("prev_hash", h.prevHash)
                .put("merkle_root", h.merkleRoot)
                .put("timestamp", h.timestamp)
                .put("difficulty", h.difficulty)
                .put("nonce", h.getNonce());
        JSONObject coinbase = new JSONObject()
                .put("miner", block.miner)
                .put("reward", block.reward);
        JSONArray txs = new JSONArray();
        for (Tx t : block.txs) {
            txs.put(new JSONObject(t.signedJson()));
        }
        return new JSONObject()
                .put("header", header)
                .put("coinbase", coinbase)
                .put("txs", txs);
    }

    public static Block fromJson(JSONObject json) throws JSONException, ChainException {
        JSONObject h = json.getJSONObject("header");
        Header header = new Header(
                h.getLong("version"),
                h.getLong("height"),
                h.getString("prev_hash"),
                h.getString("merkle_root"),
                h.getLong("timestamp"),
                h.getLong("difficulty"),
                h.getLong("nonce"));
        JSONObject coinbase = json.getJSONObject("coinbase");
        List<Tx> txs = new ArrayList<>();
        JSONArray array = json.optJSONArray("txs");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                txs.add(txFromJson(array.getJSONObject(i)));
            }
        }
        return new Block(header, coinbase.getString("miner"), coinbase.getLong("reward"), txs);
    }

    public static Tx txFromJson(JSONObject o) throws JSONException {
        return new Tx(
                o.getString("sender_pub"),
                o.getString("sender"),
                o.getString("recipient"),
                o.getLong("amount"),
                o.getLong("fee"),
                o.getLong("nonce"),
                o.getString("network"),
                o.getString("signature"));
    }
}
