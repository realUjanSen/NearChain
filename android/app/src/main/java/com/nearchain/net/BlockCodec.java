package com.nearchain.net;

import com.nearchain.core.Block;
import com.nearchain.core.ChainException;
import com.nearchain.core.Header;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Converts blocks to and from the JSON used on the wire. Field names match the Python core's to_dict(). */
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
        return new JSONObject()
                .put("header", header)
                .put("coinbase", coinbase)
                .put("txs", new JSONArray());
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
        JSONArray txs = json.optJSONArray("txs");
        if (txs != null && txs.length() > 0) {
            throw new ChainException("blocks with transactions are not supported on Android yet");
        }
        return new Block(header, coinbase.getString("miner"), coinbase.getLong("reward"));
    }
}
