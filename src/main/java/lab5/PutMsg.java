package lab5;

import javafx.util.Pair;

public class PutMsg {
    private Pair<String, Pair<Integer, Integer>> msg;

    public PutMsg(Pair<String, Pair<Integer, Integer>> msg) {
        this.msg = msg;
    }

    public String getUrl() {
        return msg.getKey();
    }

    public Integer getCount() {
        return msg.getValue().getKey();
    }

    public Integer getTime() {
        return msg.getValue().getValue();
    }
}
